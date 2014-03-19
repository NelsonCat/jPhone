package jPhone;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.swing.JOptionPane;

/**
 * this class manages the call status<br/>
 * IMPORTANT: this class runs in a thread, and opens a TCP socket to receive probes and calls from other participant
 * @author terry
 *
 */
public class JPhoneStatus extends Thread {

	/**
	 * the TCP port receiving probes and calls
	 */
	public static final int PHONE_STATUS_PORT = 14999;
	/**
	 * the timeout when probing
	 */
	public static final int PROBE_TIME_OUT = 1000;
	
	/**
	 * the probe message
	 */
	public static final String HANDSHAKE_PROBE = "probe";
	/**
	 * the start call message
	 */
	public static final String HANDSHAKE_START = "start";
	/**
	 * the quit message
	 */
	public static final String HANDSHAKE_QUIT = "quit";
	
	/**
	 * when the transmission of participant list completes, this message is sent
	 */
	public static final String STARTED = "initialized";
	
	/**
	 * idle status
	 */
	public static final int PHONE_STATUS_IDLE = 1;
	/**
	 * the idle message sent back to the calling guy
	 */
	public static final String IDLE_MSG = "idle";
	/**
	 * in-session status
	 */
	public static final int PHONE_STATUS_SESSION = 2;
	/**
	 * the in-session message sent back to the calling guy
	 */
	public static final String SESSION_MSG = "insession";
	/**
	 * the error status
	 */
	public static final int PHONE_STATUS_ERROR = 3;
	/**
	 * the error message sent back to the calling guy, so, don't call me as I'm in error state
	 */
	public static final String ERROR_MSG = "error";

	/**
	 * my current status, should be one of PHONE_STATUS_IDLE, PHONE_STATUS_SESSION, or PHONE_STATUS_ERROR
	 */
	private int status;
	
	/**
	 * the main window
	 */
	private JPhone m_phone;
	/**
	 * the call window
	 */
	private JPhoneCall m_callPanel;
	
	/**
	 * probe the participant to check if he is available now
	 * @param IPAddr the participant's IP address
	 * @return the participant's status, should be one of PHONE_STATUS_IDLE, PHONE_STATUS_SESSION, or PHONE_STATUS_ERROR
	 * @throws IOException
	 */
	public static int probe(String IPAddr) throws IOException
	{
		
		
		
		int result = PHONE_STATUS_ERROR; // init the probe result (pessimistically)
		Socket s = new Socket(); // create a client TCP socket
		s.connect(new InetSocketAddress(IPAddr, PHONE_STATUS_PORT), PROBE_TIME_OUT); // connect to the participant
		
		// set up TCP stream reader and writer
		BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
		
		writer.println(HANDSHAKE_PROBE); // write probe message
		writer.flush(); // flush the message out
		String statusMsg = reader.readLine(); // wait and read the reply
		// check the replied message and set the probe result accordingly
		if(statusMsg.equals(IDLE_MSG))
		{
			result = PHONE_STATUS_IDLE;
		}
		else if(statusMsg.equals(SESSION_MSG))
		{
			result = PHONE_STATUS_SESSION;
		}
		else
		{
			result = PHONE_STATUS_ERROR;
		}
		
		// close streams
		reader.close();
		writer.close();
		// close socket
		s.close();
		return result;
	}
	
	/**
	 * the call initiator transmits the participant list to each participant
	 * @param list the guys who are invited into this session
	 * @param ports 
	 */
	public static void sendParticipantList(Vector<String> list)
	{
		
		
		for(int i = 0; i < list.size(); i++) // iterate each guy
		{
			if(i == 0) continue;
			String remoteAddr = list.get(i);
			try
			{
				
				Socket s = new Socket(); // create a client socket
				s.connect(new InetSocketAddress(remoteAddr, PHONE_STATUS_PORT)); // connect to a guy
				
				// write out the list
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
				// send the "start" message
				writer.println(HANDSHAKE_START);
				writer.flush();
				
				for(int j = 0; j < list.size(); j++)
				{
					writer.println(list.elementAt(j));
					writer.flush();
				}
				
				
				// the transmission of participant list completes
				writer.println(STARTED);
				writer.flush();
				
				
				writer.close();
				s.close();
			}
			catch(Exception e)
			{
				System.err.println("Errors occur when sending participant list to " + remoteAddr);
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	/**
	 * quit a session
	 * @param list the guys to be notified
	 */
	public static void quitSession(Vector<String> list)
	{
		// notify each guy
		for(String remoteAddr : list)
		{
			try
			{
				Socket s = new Socket(); // create a client socket
				s.connect(new InetSocketAddress(remoteAddr, PHONE_STATUS_PORT)); // connect to the guy
				PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
				writer.println(HANDSHAKE_QUIT); // tell him "I'm quitting!"
				writer.flush();
				writer.close();
				s.close();
			}
			catch(Exception e)
			{
				System.err.println("Errors occur when sending quitting the session with " + remoteAddr);
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	/**
	 * the constructor
	 * @param phone the main window
	 * @param callPanel the call window
	 */
	public JPhoneStatus(JPhone phone, JPhoneCall callPanel)
	{
		super(); // init the super class Thread
		m_phone = phone;
		m_callPanel = callPanel;
		status = PHONE_STATUS_IDLE; // set idle
	}

	/**
	 * set a new status
	 * @param newStatus the new status
	 */
	public void setStatus(int newStatus)
	{
		status = newStatus;
	}

	/**
	 * IMPORTANT: this class runs as a thread.
	 * @see java.lang.Thread#run()
	 */
	public void run()
	{		
		try
		{
			// open a server TCP socket receiving the probes and incoming call requests
			ServerSocket ss = new ServerSocket(PHONE_STATUS_PORT);
			for(;;) // this method runs until the whole program is closed
			{
				Socket client = ss.accept(); // accept a connect from another guy
				// set up read and write streams
				BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
				String handshakeMsg = in.readLine(); // read incoming message
				if(handshakeMsg.equals(HANDSHAKE_PROBE)) // if the message is a probe
				{
					/*****************************************************************
					 * Task 1                                                        *
					 * TODO: 														 *
					 * Check the current status and respond to the sender's probe    *
					 * Handle three kinds of status (respectively):                  *
					 * 1) PHONE_STATUS_IDLE                                          *
					 * 2) PHONE_STATUS_SESSION                                       *
					 * 3) PHONE_STATUS_ERROR                                         *
					 *****************************************************************/
					switch (status) {
					case PHONE_STATUS_IDLE: 
						 System.out.println(IDLE_MSG);
						 out.write(IDLE_MSG);
						 break;
					case PHONE_STATUS_SESSION: 
						System.out.println(SESSION_MSG);
						out.write(SESSION_MSG);
						break;
					case PHONE_STATUS_ERROR: 
						System.out.println(ERROR_MSG);
						out.write(SESSION_MSG);					
						break;
					default:
						System.out.println("Error");
						System.exit(1);							
					}
					 
					
				}
				else if(handshakeMsg.equals(HANDSHAKE_START)) // if the message is an incoming call request
				{
					// first receive the participant list and then start the call
					
					Vector<String> participantList = new Vector<String>();
										
					for(;;)
					{
						String line = in.readLine();
						if(line.equals(STARTED)) break;
						participantList.add(line);
					}

					this.status = PHONE_STATUS_SESSION;
					m_phone.setVisible(false); // set the main window invisible
					m_callPanel.startCall(participantList); // start call
				}
				else if(handshakeMsg.equals(HANDSHAKE_QUIT))  // if the message is "quit"
				{
					// check if the quitting client is in my current participant list
					Vector<String> participantList = m_callPanel.getCurrentParticipantList();
					String quittingClient = client.getInetAddress().getHostAddress(); // get who is quitting
					
					if(participantList.contains(quittingClient)) // check if the quitting person is in my list
					{
						m_callPanel.stopCall();
					}
				}
				in.close();
				out.close();
			} ss.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

}
