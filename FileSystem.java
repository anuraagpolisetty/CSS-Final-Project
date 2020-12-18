public class FileSystem {
	private SuperBlock superblock;
	private Directory directory;
	private FileTable filetable;

	public FileSystem(int diskBlocks){
		superblock = new SuperBlock(diskBlocks);
		directory  = new Directory(superblock.inodeBlocks);
		filetable  = new FileTable(directory);
		FileTableEntry dirEnt = open("/", "r");
		int dirSize = fsize(dirEnt);

		if (dirSize > 0) {
			byte[] dirData = new byte[dirSize];
			read(dirEnt, dirData);
			directory.bytes2directory(dirData);
		}

		close(dirEnt);
	}

	void sync() {
		// write file from the disk
		FileTableEntry dirEnt = open("/", "w");
		// get the direcotry data in bytes
		byte[] dirData = directory.directory2bytes();
		// write the entry
		write(dirEnt, dirData);
		close(dirEnt);
		// superblock writes to disk
		superblock.sync();
	}

	boolean format(int files) {
		// file contents must be empty
		if (!filetable.fempty()) {
			return false;
		}

		superblock.format(files);
		directory = new Directory(superblock.inodeBlocks);
		filetable = new FileTable(directory);

		return true;
	}

	public FileTableEntry open(String filename, String mode) {

		FileTableEntry ftEnt = filetable.falloc(filename, mode);

		if (mode.equals("w") && !deallocAllBlocks(ftEnt)) {
			return null;
		}

		return ftEnt;
	}

	boolean close(FileTableEntry ftEnt) {
		if (ftEnt == null) {
			return false;
		}

		synchronized(ftEnt) {
			// decrement this entry's count
			ftEnt.count--;

			// if other threads still have access
			if (ftEnt.count > 0) {
				return true;
			}
		}

		// otherwise, free this entry from the table
		return filetable.ffree(ftEnt);
	}

	// returns th size of the FileTableEntry
	int fsize(FileTableEntry ftEnt) {
		synchronized(ftEnt) {
			return ftEnt.inode.length;
		}
	}

	int read(FileTableEntry ftEnt, byte[] buffer) {
		// validate FileTableEntry
		if (ftEnt == null) {
			return -1;
		} else if (ftEnt.inode == null)	{
			return -1;
		} else if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a")) {
			return -1;
		}

		int bufferLength = buffer.length;
		int seekPtr, idx, offset, availBytes, remainingBytes, readLength;
		byte[] data;

		synchronized(ftEnt) {
			seekPtr = ftEnt.seekPtr;
			idx     = 0;
			data    = new byte[Disk.blockSize];

			while (idx < bufferLength) {
				offset         = seekPtr % Disk.blockSize;
				availBytes     = Disk.blockSize - offset;
				remainingBytes = bufferLength - idx;

				// only read up to the buffer size, or the end of the disk
				// whichever is smaller
				readLength = Math.min(availBytes, remainingBytes);

				// get block
				int block = ftEnt.inode.findTargetBlock(offset);

				// check if block was found
				if (block == -1) {
					return -1;
				}

				// validate block
				if (block < 0 || block > superblock.totalBlocks) {
					break;
				}

				// read into data
				SysLib.rawread(block, data);

				// copy data into buffer
				System.arraycopy(data, offset, buffer, idx, readLength);

				idx     += readLength;
				seekPtr += readLength;
			}

			// set new seekPtr
			seek(ftEnt, idx, 1);
		}

		return idx;
	}

	int write(FileTableEntry ftEnt, byte[] buffer) {
		Inode theInode;

		// validate FileTableEntry
		if (ftEnt == null) {
			return -1;
		}

		theInode = ftEnt.inode;

		if (theInode == null || ftEnt.mode.equals("r")) {
			return -1;
		}

		int bufferLength = buffer.length;
		int seekPtr, idx, offset, availBytes, remainingBytes, writeLength;
		byte[] data;

		synchronized(ftEnt) {
			// if the mode is append, set seekPtr to the end of the block
			if (ftEnt.mode.equals("a")) {
				seekPtr = seek(ftEnt, 0, 2);
			} else {
				seekPtr = ftEnt.seekPtr;
			}

			// set flag to write
			theInode.flag = 3;
			idx = 0;
			data = new byte[Disk.blockSize];

			while(idx < bufferLength) {
				offset 		   = seekPtr % Disk.blockSize;
				availBytes 	   = Disk.blockSize - offset;
				remainingBytes = bufferLength - idx;
				// write until as much as is available, or the whole thing
				writeLength = Math.min(availBytes, remainingBytes);

				// get block
				int block = theInode.findTargetBlock(offset);

				// validate block
				if (block == -1) {
					// check if out of memory
					block = superblock.getFreeBlock();

					if (block == -1) {
						return -1;
					}

					// read file to the block
					if (theInode.registerTargetBlock(seekPtr, (short) block) == -1) {
						// out of bounds
						if (theInode.registerIndexBlock((short) block) == false) {
							return -1;
						}

						// get a new free block
						block = superblock.getFreeBlock();

						if (block == -1) {
							return -1;
						}

						// setup new block
						if (theInode.registerTargetBlock(seekPtr, (short) block) == -1) {
							return -1;
						}
					}
				}

				// if the block is not in range
				if (block >= superblock.totalBlocks) {
					return -1;
				}

				// read into data
				SysLib.rawread(block, data);
				// copy data into buffer
				System.arraycopy(buffer, idx, data, offset, writeLength);
				// write to disk
				SysLib.rawwrite(block, data);

				idx     += writeLength;
				seekPtr += writeLength;
			}

			// if the file size increased, update Inode
			if (seekPtr > theInode.length) {
				theInode.length = seekPtr;
			}

			// set new seekPtr
			seek(ftEnt, idx, 1);

			// set flag
			theInode.flag = 1;

			// save to disk
			theInode.toDisk(ftEnt.iNumber);
		}

		return idx;
	}

	boolean delete(String filename) {
		if (filename == "") {
			return false;
		}

		// get Inode number
		short iNodeNum = directory.namei(filename);

		if (iNodeNum == -1) {
			return false;
		}

		return directory.ifree(iNodeNum);
	}

	int seek(FileTableEntry ftEnt, int offset, int whence) {
		if (ftEnt == null) {
			return -1;
		}

		int seekPtr, eofPtr;

		synchronized(ftEnt) {
			seekPtr = ftEnt.seekPtr;
			eofPtr  = fsize(ftEnt);

			switch (whence) {
				// SEEK_SET
				case 0:
					seekPtr = offset;
					break;
				// SEEK_CUR
				case 1:
					seekPtr += offset;
					break;
				// SEEK_END
				case 2:
					seekPtr = eofPtr + offset;
					break;
				default:
					// error
			}

			// if seekPtr is negative, set to 0
			if (seekPtr < 0) {
				seekPtr = 0;
			// if seekPtr is greater than file size, set to end of file
			} else if (seekPtr > eofPtr) {
				seekPtr = eofPtr;
			}

			ftEnt.seekPtr = seekPtr;
		}

		return seekPtr;
	}

	// helper function that empties inode and deletes blocks
	private boolean deallocAllBlocks(FileTableEntry ftEnt) {
		if (ftEnt == null || ftEnt.inode.count != 1) {
			return false;
		}

		// deallocate direct blocks
		for (int i = 0; i < ftEnt.inode.direct.length; i++) {
			if (ftEnt.inode.direct[i] != -1) {
				superblock.returnBlock(ftEnt.inode.direct[i]);
				ftEnt.inode.direct[i] = -1;
			}
		}

		// unregister indexBlock
		byte[] freeBlocks = ftEnt.inode.unregisterIndexBlock();

		// deallocate indirect blocks
		if (freeBlocks != null) {
			int block = SysLib.bytes2short(freeBlocks, 0);

			while (block != -1) {
				superblock.returnBlock(block);
			}
		}

		// write back to disk
		ftEnt.inode.toDisk(ftEnt.iNumber);

		return true;
	}
}