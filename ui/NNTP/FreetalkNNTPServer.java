/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import plugins.Freetalk.Freetalk;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import freenet.io.NetworkInterface;
import freenet.node.Node;
import freenet.support.Logger;

/**
 * NNTP server.
 *
 * The server runs in a background thread so it can wait for
 * connections from clients.  Each handler runs in its own thread as
 * well.  Use terminate() to shut everything down.
 *
 * @author Benjamin Moody
 */
public class FreetalkNNTPServer implements Runnable {

	private Node node;
	private Freetalk freetalk;

	/** Port to listen on for connections. */
	private int port;
	/** Comma-separated list of addresses to bind to. */
	private String bindTo;
	/** Comma-separated list of hosts to accept connections from. */
	private String allowedHosts;

	private NetworkInterface iface;
	private volatile boolean shutdown;
	private Thread serverThread;

	private ArrayList<FreetalkNNTPHandler> clientHandlers;

	public FreetalkNNTPServer(Node myNode, Freetalk ft, int port, String bindTo, String allowedHosts) {
		node = myNode;
		freetalk = ft;
		this.port = port; /* TODO: As soon as Freetalk has a configuration class, read it from there */
		this.bindTo = bindTo; /* TODO: As soon as Freetalk has a configuration class, read it from there */
		this.allowedHosts = allowedHosts; /* TODO: As soon as Freetalk has a configuration class, read it from there */
		shutdown = false;
		clientHandlers = new ArrayList<FreetalkNNTPHandler>();
		node.executor.execute(this, "Freetalk NNTP Server");
	}

	/**
	 * Shut down the server and disconnect any currently-connected
	 * clients.
	 */
	public void terminate() {
		shutdown = true;
		try {
			iface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Error shutting down NNTP server", e);
		}
		
		try {
			serverThread.join();
		}
		catch(InterruptedException e) {
			Thread.currentThread().interrupt(); /* Some HOWTO on the web said that we have to do this here */
		}
	}

	/**
	 * Main server connection loop
	 */
	public void run() {
		try {
			serverThread = Thread.currentThread();
			iface = NetworkInterface.create(port, bindTo, allowedHosts,
											node.executor, true);
			/* FIXME: NetworkInterface.accept() currently does not support being interrupted by Thread.interrupt(),
			 * shutdown works by timeout. This sucks and should be changed. As long as it is still like that,
			 * we have to use a low timeout. */
			iface.setSoTimeout(1000);
			while (!shutdown) {
				try {
					Socket clientSocket = iface.accept();
					Logger.debug(this, "Accepted an NNTP connection from " + clientSocket.getInetAddress());

					FreetalkNNTPHandler handler = new FreetalkNNTPHandler(freetalk, clientSocket);
					node.executor.execute(handler, "Freetalk NNTP Client " + clientSocket.getInetAddress());

					clientHandlers.add(handler);
				}
				catch (SocketTimeoutException e) {
					// ignore
				}

				// Remove disconnected clients from the list
				for (Iterator<FreetalkNNTPHandler> i = clientHandlers.iterator(); i.hasNext(); ) {
					FreetalkNNTPHandler handler = i.next();
					if (!handler.isAlive()) {
						i.remove();
					}
				}
			}
		}
		catch (IOException e) {
			Logger.error(this, "Unable to start NNTP server", e);
		}
		
		finally {
			terminateHandlers();
		}
	}
	
	private void terminateHandlers() {
		synchronized(clientHandlers) {
			// Close client sockets
			for (Iterator<FreetalkNNTPHandler> i = clientHandlers.iterator(); i.hasNext(); ) {
				FreetalkNNTPHandler handler = (FreetalkNNTPHandler) i.next();
				handler.terminate();
			}
		}
	}
}
