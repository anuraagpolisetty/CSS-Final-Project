public class Inode {
	private final static int iNodeSize  = 32;  // fix to 32 bytes
	private final static int directSize = 11;  // # direct pointers

	public int length; 							   // file size in bytes
	public short count; 						   // # file-table entries pointing to this
	public short flag; 							   // 0 = unused, 1 = used, , 2 = read, 3 = write, 4 = delete
	public short direct[] = new short[directSize]; // direct pointers
	public short indirect;						   // a indirect pointer

	Inode() {
		length = 0;
		count  = 0;
		flag   = 1;

		for (int i = 0; i < directSize; i++) {
			direct[i] = -1;
		}

		indirect = -1;
	}

	// retrieving inode from disk
	Inode(short iNumber) {
		int blockNum = getBlockNum(iNumber);
		int offset   = getOffset(iNumber);
		byte[] data  = new byte[Disk.blockSize];

		SysLib.rawread(blockNum, data);

		length = SysLib.bytes2int(data, offset);
		offset += 4;
		count  = SysLib.bytes2short(data, offset);
		offset += 2;
		flag   = SysLib.bytes2short(data, offset);
		offset += 2;

		for (int i = 0; i < directSize; i++) {
			direct[i] = SysLib.bytes2short(data, offset);
			offset += 2;
		}

		indirect = SysLib.bytes2short(data, offset);
	}

	// save to disk as the i-th node
	int toDisk(short iNumber) {
		int blockNum = getBlockNum(iNumber);
		int offset   = getOffset(iNumber);
		byte[] data  = new byte[Disk.blockSize];

		SysLib.int2bytes(length, data, offset);
		offset += 4;
		SysLib.short2bytes(count, data, offset);
		offset += 2;
		SysLib.short2bytes(flag, data, offset);
		offset += 2;

		for (int i = 0; i < directSize; i++) {
			SysLib.short2bytes(direct[i], data, offset);
			offset += 2;
		}

		SysLib.short2bytes(indirect, data, offset);
		SysLib.rawwrite(blockNum, data);
	}

	// calculates and returns a block number
	int getBlockNum(iNumber) {
		return 1 + iNumber / 16;
	}

	// calculates and returns an offset
	int getOffset(iNumber) {
		return (iNumber % 16) * iNodeSize;
	}

	// returns location of indirect block
	int findIndexBlock() {
		return indirect;
	}

	// create an empty block to use as an indirect block
	boolean registerIndexBlock(short indexBlockNumber) {
		if (indexBlockNumber < 0 || indirect != -1) {
			return false;
		}

		for (int i = 0; i < directSize; i++) {
			if (direct[i] == -1) {
				return false;
			}
		}

		indirect = indexBlockNumber;
		byte[] data = new byte[Disk.blockSize];

		for (int i = 0; i < Disk.blockSize / 2; i++) {
			SysLib.short2bytes((short)-1, data, i * 2);
		}

		SysLib.rawwrite(indexBlockNumber, data);
		return true;
	}

	// find and return a specific block
	int findTargetBlock(int offset) {
		int blockNum = offset / Disk.blockSize;

		if (blockNum < directSize) {
			return direct[blockNum];
		}

		if (indirect != -1) {
			byte data = new byte[Disk.blockSize];
			SysLib.rawread(indirect, data);

			int location = blockNum - directSize;
			return SysLib.bytes2short(data, location * 2);
		} else {
			return -1;
		}
	}

	// register the target block into either direct or indirect
	int registerTargetBlock(int offset, short targetBlockNumber) {
		if (offset < 0) {
			return -1;
		}

		int blockNum = offset / Disk.blockSize;

		if (blockNum < directSize) {
			if (direct[blockNum] != -1) {
				return -1;
			}

			if (blockNum > 0 && direct[blockNum - 1] == -1) {
				return -1;
			}

			direct[blockNum] = targetBlockNumber;
			return 1;
		}

		if (indirect == -1) {
			return -1;
		}

		byte[] data = new byte[Disk.blockSize];
		SysLib.rawread(indirect, data);

		int iBlock = blockNum - directSize;

		if (SysLib.bytes2short(data, iBlock * 2) > 0) {
			return -1;
		}

		SysLib.short2bytes(targetBlockNumber, data, iBlock * 2);
		SysLib.rawwrite(indirect, data);
		return 1;
	}

	// delete a registered block in indirect
	byte[] unregisterIndexBlock() {
		if (indirect == -1) {
			return -1;
		}
		
		byte[] data = new byte[Data.blockSize];
		SysLib.rawread(indirect, data);

		indirect = -1;

		return data;
	}
}