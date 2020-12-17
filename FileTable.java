import java.util.Vector;

public class FileTable {
   private Vector table; // the actual entity of this file table
   private Directory dir; // the root directory
   
   public FileTable( Directory directory ) { // constructor
      table = new Vector( );  // instantiate a file (structure) table
      dir = directory;        // receive a reference to the Director
   }                          // from the file system

   // major public methods
   public synchronized FileTableEntry falloc( String filename, String mode ) {
      // allocate a new file (structure) table entry for this file name
      short iNum = -1; // dir.ialloc(filename);
      Inode iNode = null;

      // allocate/retrieve and register the corresponding inode using dir
      while( true ) {
         iNum = ( filename.equals( "/" ) ? 0 : dir.namei( filename ) );

         if (iNum < 0) { // file does not exist, create new iNode not from disk
            iNode = new Inode();
         } 
         else {
            iNode = new Inode(iNum);
            if ( mode.equals( "r" ) ) {
               
               // break if Inode flag is "read"
               if (iNode.flag == 2) {
                  break;
               }
               // if Inode flag is "write" then wait
               else if(iNode.flag == 3) {
                  try {
                     wait( );
                  } catch (InterruptedException e) {
                     SysLib.cerr("Error");
                  }
               }
            }
            else {

               // if inode flag is ready to be used
               if (iNode.flag == 0 || iNode.flag == 1) {
                  break;
               }
               else {
                  try {
                     wait( );
                  }
                  catch (InterruptedException e) {
                     SysLib.cerr("Error");
                  }
               }
            }
            // else if (mode.equals( "w" )) {

            // }
            // else if (mode.equals( "w+" )) {

            // }
            // else if (mode.equals( "a" )) {

            // }
         }
      
      }
      // increment this inode's count
      iNode.count++;
      // immediately write back this inode to the disk
      iNode.toDisk(iNum);
      // return a reference to this file (structure) table entry
      FileTableEntry entry = new FileTableEntry(iNode, iNum, mode);
      table.addElement(entry);
      return entry;
   }

   public synchronized boolean ffree( FileTableEntry e ) {
      // receive a file table entry reference

      // free this file table entry.
      if(table.removeElement(e)) {
         Inode inode = new Inode(e.iNumber);

         if (inode.flag == 2 ) {     // 2 == read
            if (inode.count == 1) {
               notify();
               inode.flag = 1;      // set flag to used
            }
         }
         else if (inode.flag == 3)  { // 3 == write
            inode.flag = 1;         // set flag to used
            notifyAll();
         }

         inode.count--;
         // save the corresponding inode to the disk
         inode.toDisk(e.iNumber);
         // return true if this file table entry found in my table
         return true;
      }
      // else return false if file table entry not found
      return false;
   }

   public synchronized boolean fempty( ) {
      return table.isEmpty( );   // return if table is empty
   }                             // should be called before starting a format
 }