public class Directory {
  private static int maxChars = 30; // max characters of each file name

  // Directory entries
  private int fsize[]; // each element stores a different file size.
  private char fnames[][]; // each element stores a different file name.
  private int dirSize;

  public Directory( int maxInumber ) { // directory constructor
    fsize = new int[maxInumber]; // maxInumber = max files
    for ( int i = 0; i < maxInumber; i++ )
      fsize[i] = 0; // all file size initialized to 0
    fnames = new char[maxInumber][maxChars];
    String root = "/"; // entry(inode) 0 is "/"
    fsize[0] = root.length( ); // fsize[0] is the size of "/".
    root.getChars( 0, fsizes[0], fnames[0], 0 ); // fnames[0] includes "/"

    // stores number of bytes to be used for reading and writing
    dirSize = maxInumber * 4 + maxInumber * maxChars * 2;
  }

  public int bytes2directory( byte data[] ) {
    // assumes data[] received directory information from disk
    // initializes the Directory instance with this data[]
  }

  public byte[] directory2bytes( ) {
    // converts and return Directory information into a plain byte array
    // this byte array will be written back to disk
    // note: only meaningfull directory information should be converted
    // into bytes.

    byte[] info = new byte[dirSize];
    int diff = 0;

    for(int i = 0; i < dirSize; i++) {
      // write into info array offset by difference of 4 indices
      SysLib.int2bytes(fsize[i], info, diff);
      diff += 4;
    }

    // write to file name array
    for (int i = 0; i < fnames.length; i++) {
      String fname = new String(fnames[i], 0, fsize[i]);
      byte[] data = fname.getBytes();
      System.arraycopy(data, 0, info, diff, data.length);
      diff += maxChars * 2;
    }

    return info;

  }

  public short ialloc( String filename ) {
    // filename is the one of a file to be created.
    // allocates a new inode number for this filename

    // search for empty spot in directory
    for (int i = 0; i < fsize.length; i++) {
      // if spot is empty
      if(fsize[i] == 0) {
        int filesize = filename.length() > maxChars ? maxChars : filename.length();
        fsize[i] = filesize;

        // copy from string into fnames
        filename.getChars(0, filesize, fnames[i], 0);
        return (short) i;
      }
    }
    // return -1 if no space exists
    return (short) -1;
  }

  public boolean ifree( short iNumber ) {
    // the corresponding file will be deleted.
    if (iNumber < maxChars && fsize[iNumber] > 0) {

      // if iNumber is valid, deallocates this inumber (inode number)
      fsize[iNumber] = 0;

      // remove the filename from fnames
      for( int i = 0; i < maxChars; i++)
        fnames[iNumber][i] = '0';

      return true;

    } else {
      return false;
    }
  }

  public short namei( String filename ) {
    // returns the inumber corresponding to this filename
  }
}