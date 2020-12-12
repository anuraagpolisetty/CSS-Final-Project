import java.io.*;

/**
 * Is the very first ThreadOS thread tid[0] that launches a user
 * thread and waits for its termination.
 */
public class Loader extends Thread
{
    static final int OK = 0;
    static final int ERROR = -1;

    /**
     * Is a default constructor.
     */
    public Loader( ) {
    }

    /**
     * Is a constructor that receives arguments but the current
     * version does not care about arguments.
     *
     * @param args[] is not used at present.
     */
    public Loader( String args[] ) {
    }

    /**
     * Is a help function to summarize what commands the loader can accept.
     */
    private static void help( ) {
	SysLib.cout( "?:       print a help message\n" );
	SysLib.cout( "q:       exit from threadOS\n" );
	SysLib.cout( "l prog:  load prog\n" );
    }

    /**
     * Is the Loader thread's body. It repeatedly prints out a prompt
     * (--&gt;); interprets a given command such as '?', 'q', 'l', and 'r';
     * and launch a user application as a child thead if 'l' was
     * given.
     */
    public void run( ) {
	String cmdLine = "";
	char cmd = ' ';

	while ( true ) {
	    do {
		StringBuffer inputBuf = new StringBuffer( );
		SysLib.cerr( "-->" );
		SysLib.cin( inputBuf );
		cmdLine = inputBuf.toString( );
	    } while ( cmdLine.length( ) == 0 );
	    System.out.println( cmdLine );
	    cmd = cmdLine.charAt( 0 );
	    switch( cmd ) {
	    case '?': // help
		help( );
		break;
	    case 'q': // quit
		SysLib.sync( );
		System.exit( 1 );
		break;
	    case 'l': // launch a thread
		try {
		    String intrArgs[]
			= SysLib.stringToArgs( cmdLine.substring( 2 ) );
		    if ( SysLib.exec( intrArgs ) == ERROR ) {
			SysLib.cerr( intrArgs[0] + " failed in loading\n" );
			break;
		    }
		    SysLib.join( );
		} catch( Exception e ) { e.printStackTrace( ); }
		break;
	    case 'r': // repeat
		break;
	    }
	}
    }
}
