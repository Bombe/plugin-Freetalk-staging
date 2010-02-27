/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk.ui.NNTP;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;

import plugins.Freetalk.Freetalk;
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
 * @author xor (xor@freenetproject.org)
 */
public final class FreetalkNNTPServer implements Runnable {

	private final Freetalk mFreetalk;

	/** Comma-separated list of addresses to bind to. */
	private final String mBindTo;
	/** Port to listen on for connections. */
	private final int mPort;
	/** Comma-separated list of hosts to accept connections from. */
	private final String mAllowedHosts;

	private NetworkInterface mInterface;
	private volatile boolean mIsShuttingDown;
	private boolean mIsShutdownFinished;

	private final ArrayList<FreetalkNNTPHandler> clientHandlers;

	public FreetalkNNTPServer(Freetalk ft, int port, String bindTo, String allowedHosts) {
		mFreetalk = ft;
		mBindTo = bindTo;
		mPort = port;
		mAllowedHosts = allowedHosts;
		mIsShuttingDown = mIsShutdownFinished = false;
		clientHandlers = new ArrayList<FreetalkNNTPHandler>();
	}
	
	public void start() {
		mFreetalk.getPluginRespirator().getNode().executor.execute(this, "Freetalk " + this.getClass().getName());
		Logger.debug(this, "Started.");
	}

	/**
	 * Shut down the server and disconnect any currently-connected clients.
	 */
	public void terminate() {
		mIsShuttingDown = true;
		try {
			mInterface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Error shutting down NNTP server", e);
		}
		
		synchronized (this) {
			while (!mIsShutdownFinished) {
				try {
					wait();
				}
				catch (InterruptedException e) {
					// ignore
				}
			}
		}
	}

	/**
	 * Main server connection loop
	 */
	public void run() {
		try {
			mInterface = NetworkInterface.create(mPort, mBindTo, mAllowedHosts, mFreetalk.getPluginRespirator().getNode().executor, true);
			/* FIXME: NetworkInterface.accept() currently does not support being interrupted by Thread.interrupt(),
			 * shutdown works by timeout. This sucks and should be changed. As long as it is still like that,
			 * we have to use a low timeout. */
			mInterface.setSoTimeout(1000);
			while (!mIsShuttingDown) {
				final Socket clientSocket = mInterface.accept();
				if(clientSocket != null) { /* null is returned on timeout */
					try {
						acceptConnection(clientSocket);
					} catch(SocketException e) {
						Logger.error(this, "Accepting connection failed.", e);
					}
				}
				
				garbageCollectDisconnectedHandlers();
			}

			Logger.debug(this, "NNTP Server exiting...");
			mInterface.close();
		}
		catch (IOException e) {
			Logger.error(this, "Unable to start NNTP server", e);
		}
		
		finally {
			terminateHandlers();

			synchronized (this) {
				mIsShutdownFinished = true;
				notify();
			}
		}
	}
	
	private void acceptConnection(Socket clientSocket) throws SocketException {
		final FreetalkNNTPHandler handler = new FreetalkNNTPHandler(mFreetalk, clientSocket);

		synchronized(clientHandlers) {
			clientHandlers.add(handler);
		}
		
		mFreetalk.getPluginRespirator().getNode().executor.execute(handler, "Freetalk NNTP Client " + clientSocket.getInetAddress());
		Logger.debug(this, "Accepted an NNTP connection from " + clientSocket.getInetAddress());
	}
	
	private void garbageCollectDisconnectedHandlers() {
		synchronized(clientHandlers) {
			for (final Iterator<FreetalkNNTPHandler> i = clientHandlers.iterator(); i.hasNext(); ) {
				final FreetalkNNTPHandler handler = i.next();
				if (!handler.isAlive()) {
					i.remove();
				}
			}
		}
	}
	
	private void terminateHandlers() {
		Logger.debug(this, "Closing client handlers...");
		synchronized(clientHandlers) {
			// Close client sockets
			for (final FreetalkNNTPHandler handler : clientHandlers) {
				handler.terminate();
			}
			
			clientHandlers.clear();
		}
	}
}
