import java.util.*;

public class Cache {
	private int blockSize;            // 512 bytes
	private Vector<byte[]> pages;     // This is actual pages that include data
	private int victim;

   private class Entry {
		public static final int INVALID = -1;
		public boolean reference;
		public boolean dirty;
		public int frame;
		public Entry( ) {
			reference = false;
			dirty = false;
			frame = INVALID;
		}
   }

   private Entry[] pageTable = null; // This is a page table that includes only attributes

   public Cache( int blockSize, int cacheBlocks ) { // cacheBlocks = 10
		this.blockSize = blockSize;                  // blockSize = 512
		pages = new Vector<byte[]>( );
		for ( int i = 0; i < cacheBlocks; i++ ) {
			byte[] p = new byte[blockSize];
			pages.addElement( p );
		}
		victim = cacheBlocks - 1; // set the last frame as a previous victim
		pageTable = new Entry[ cacheBlocks ];
		for ( int i = 0; i < cacheBlocks; i++ )
			pageTable[i] = new Entry( );
   }

	private int findFreePage( ) {
	// Return a free index of pageTable[]. If the table is rull, return -1
		if (pageTable == null) {
			return -1;
		}
		for(int i = 0; i < pageTable.length; i++) {
			if(pageTable[i].frame == -1) {
				return i;
			}
		}
		return -1;
   }

	private int nextVictim( ) {
	// Return a next victim index of pageTable[]
		int cacheblock = 10;
		while(true) {
			victim = (victim + 1) % cacheblock;
			if(pageTable[victim].reference == false) {
				return victim;
			} else {
				pageTable[victim].reference = false;
			}
		}
	}

	private void writeBack( int victimEntry ) {
	// Writes back this victim page to disk if it's dirty is true
		if(pageTable[victimEntry].frame != -1) {
			SysLib.rawwrite(pageTable[victimEntry].frame, pages.elementAt(victimEntry));
			pageTable[victimEntry].dirty = false;
		}		
	}

	public synchronized boolean read( int blockId, byte buffer[] ) {
		if ( blockId < 0 ) {
			SysLib.cerr( "threadOS: a wrong blockId for cread\n" );
			return false;
		}

		// locate the valid page
		for(int i = 0; i < pageTable.length; i++) {
			if(pageTable[i].frame == blockId) {
				// if found, copy from page table to buffer
				System.arraycopy((byte[])pages.elementAt(i), 0, buffer, 0, blockSize);
				pageTable[i].reference = true;
				return true;
			}
		}

		// if no valid page found, find free page
		int index = findFreePage();

		// if findfreepage is -1 then find a victim 
		if (index == -1) {
			index = nextVictim();
		} 

		//if victim is dirty,  write back a dirty copy
		if(pageTable[index].dirty) {
			writeBack(index);
		}
		// read a requested block from disk
		SysLib.rawread(blockId, buffer);

		//cache to pages
		byte[] data = new byte[blockSize];
		System.arraycopy(buffer, 0, data, 0, blockSize);
		pages.set(index, data);
		pageTable[index].frame = blockId;
		pageTable[index].reference = true;

		return true;
	}

	public synchronized boolean write( int blockId, byte buffer[] ) {
		if ( blockId < 0 ) {
			SysLib.cerr( "threadOS: a wrong blockId for cwrite\n" );
			return false;
		}

		// locate the valid page
		for(int i = 0; i < pageTable.length; i++) {
			if(pageTable[i].frame == blockId) {
				// if found, copy from buffer to pageTable
				byte[] data = new byte[blockSize];
				System.arraycopy(buffer, 0, data, 0, blockSize);
				pages.set(i, data);
				pageTable[i].reference = true;
				pageTable[i].dirty = true;
			}
		}

		// if no valid page found, find free page
		int index = findFreePage();

		// if no free pages, find a new victim
		if(index == -1) {
			index = nextVictim();
		}

		// if victim is dirty, write back a dirty copy
		if(pageTable[index].dirty) {
			writeBack(index);
		}

		// cache it to pages
		byte[] data = new byte[blockSize];
		System.arraycopy(buffer, 0, data, 0, blockSize);
		pages.set(index, data);

		pageTable[index].frame = blockId;
		pageTable[index].reference = true;
		pageTable[index].dirty = true;

		return true;
	}

	public synchronized void sync( ) {
		for ( int i = 0; i < pageTable.length; i++ )
			writeBack( i );
		SysLib.sync( );
	}

	public synchronized void flush( ) {
		for ( int i = 0; i < pageTable.length; i++ ) {
				writeBack( i );
				pageTable[i].reference = false;
				pageTable[i].frame = Entry.INVALID;
		}
		SysLib.sync( );
	}
}
