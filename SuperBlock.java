class SuperBlock {
	private final static int defaultInodeBlocks = 64; 
	public int totalBlocks;
	public int inodeBlocks;
	public int freeList;

	public SuperBlock(int diskSize) {
		byte[] superBlock = new byte[Disk.blockSize];
		SysLib.rawread(0, superBlock);
		totalBlocks = SysLib.bytes2int(superBlock, 0);
		inodeBlocks = SysLib.bytes2int(superBlock, 4);
		freeList    = SysLib.bytes2int(superBlock, 8);

		if (totalBlocks == diskSize && inodeBlocks > 0 && freeList >= 2) {
			return;
		} else {
			totalBlocks = diskSize;
			SysLib.cerr("Formatting...\n");
			format(totalBlocks);
			SysLib.cerr("Finished\n");
		}
	}

	// write totalBlock, inodeBlocks, and freeList to disk
	void sync() {
		byte[] data = new byte[Disk.blockSize];

		SysLib.int2bytes(totalBlocks, data, 0);
		SysLib.int2bytes(inodeBlocks, data, 4);
		SysLib.int2bytes(freeList, data, 8);
		SysLib.rawwrite(0, data);
	}

	// reformat Inodes and SuperBlocks by numBlock
	void format(int numBlocks) {
		byte[] block = null;

		// reset the Inode count
		inodeBlocks = numBlocks;

		// create a new node for each Inode, then write it to disk
		for (int i = 0; i < inodeBlocks; i++) {
			Inode newInode = new Inode();
			newInode.toDisk((short) i);
		}

		// set the freeList depending on the number of blocks
		if (inodeBlocks % 16 == 0) {
			freeList = numBlocks / 16 + 1;
		} else {
			freeList = numBlocks / 16 + 2;
		}

		// create new free blocks and write them to disk
		for (int i = totalBlocks - 2; i >= freeList; i--) {
			block = new byte[Disk.blockSize];

			for (int j = 0; j < Disk.blockSize; j++) {
				block[j] = (byte)0;
			}

			SysLib.int2bytes(i + 1, block, 0);
			SysLib.rawwrite(i, block);
		}

		// the last block is a null pointer
		// create it and write it to disk
		//byte[] lastBlock = new byte[Disk.blockSize];
		SysLib.int2bytes(-1, block, 0);
		SysLib.rawwrite(totalBlocks - 1, block);

		sync();
	}

	// dequeue top block in freeList
	public int getFreeBlock() {
		if (freeList < 0 || freeList > totalBlocks) {
			return -1;
		}

		short freeBlock = (short) freeList;
		byte[] block = new byte[Disk.blockSize];

		SysLib.rawread(freeList, block);
		SysLib.int2bytes(0, block, 0);
		SysLib.rawwrite(freeList, block);

		freeList = SysLib.bytes2int(block, 0);

		return freeBlock;
	}

	// enqueue oldBlockNumber to top of freeList
	public boolean returnBlock(int oldBlockNumber) {
		if (oldBlockNumber < 0 || oldBlockNumber > totalBlocks) {
			return false;
		}

		byte[] block = new byte[Disk.blockSize];

		SysLib.int2bytes(freeList, block, 0);
		SysLib.rawwrite(oldBlockNumber, block);

		freeList = oldBlockNumber;

		return true;
	}
}