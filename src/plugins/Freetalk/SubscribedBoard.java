package plugins.Freetalk;

import java.util.Arrays;
import java.util.Date;

import plugins.Freetalk.exceptions.DuplicateMessageException;
import plugins.Freetalk.exceptions.InvalidParameterException;
import plugins.Freetalk.exceptions.MessageNotFetchedException;
import plugins.Freetalk.exceptions.NoSuchMessageException;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.query.Query;

import freenet.support.Logger;

/**
 * A SubscribedBoard is a {@link Board} which only stores messages which the subscriber (a {@link FTOwnIdentity}) wants to read,
 * according to the implementation of {@link FTOwnIdentity.wantsMessagesFrom}.
 */
public final class SubscribedBoard extends Board {

	private final FTOwnIdentity mSubscriber;
	
	private Board mParentBoard;
	
	/**
	 * The description which the subscriber has specified for this Board. Null if he has not specified any.
	 */
	private String mDescription = null;
	
	/**
	 * Index of the latest message which this board has pulled from it's parent board. 
	 */
	private int	mHighestSynchronizedParentMessageIndex = 0;

	
	public SubscribedBoard(Board myParentBoard, FTOwnIdentity mySubscriber) throws InvalidParameterException {
		super(myParentBoard.getName());
		
		if(myParentBoard == null) throw new NullPointerException();
		if(mySubscriber == null) throw new NullPointerException();
		
		mParentBoard = myParentBoard;
		mSubscriber = mySubscriber;
	}
	
    public void initializeTransient(Freetalk myFreetalk) {
    	super.initializeTransient(myFreetalk);
    	mParentBoard.initializeTransient(myFreetalk);
    	if(mSubscriber instanceof Persistent) {
    		Persistent subscriber = (Persistent)mSubscriber;
    		subscriber.initializeTransient(myFreetalk);
    	}
    }
    
    protected void storeWithoutCommit() {
    	throwIfNotStored(mSubscriber);
    	throwIfNotStored(mParentBoard);
    	super.storeWithoutCommit();
    }

	
	protected void deleteWithoutCommit() {
		// TODO: When deleting a subscribed board, check whether the objects of class Message are being used by a subscribed board of another own identity.
		// If not, delete the messages.
		try {
			checkedActivate(3); // TODO: Figure out a suitable depth.
			
			for(MessageReference ref : getAllMessages(false)) {
				ref.initializeTransient(mFreetalk);
				ref.deleteWithoutCommit();
			}

			checkedDelete();
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}

	}
	
	public Board getParentBoard() {
		return mParentBoard;
	}

    public synchronized String getDescription() {
        return mDescription != null ? mDescription : super.getDescription(mSubscriber);
    }
    
    /**
     * Gets the reference to the latest message. Does not return ghost thread references - therefore, the returned MessageReference will always
     * point to a valid Message object.
     * 
     * TODO: Make this function return class Message and not class MessageReference because it won't return MessageReference objects whose Message
     * is not downloaded yet anyway.
     * 
     * @throws NoSuchMessageException If the board is empty.
     */
    @SuppressWarnings("unchecked")
	public synchronized MessageReference getLatestMessage() throws NoSuchMessageException {
    	// TODO: We can probably cache the latest message date in this SubscribedBoard object.
    	
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this);
        q.descend("mMessageDate").orderDescending();
        ObjectSet<MessageReference> allMessages = q.execute();

        // Do not use a constrain() because the case where the latest message has no message object should not happen very often.
        for(MessageReference ref : allMessages) {
        	try {
        		ref.initializeTransient(mFreetalk);
        		ref.getMessage(); // Check whether the message was downloaded
        		return ref;
        	}
        	catch(MessageNotFetchedException e)  {
        		// Continue to next MessageReference
        	}
        }
        
        throw new NoSuchMessageException();
    }
    
    /**
     * Called by the {@link MessageManager} when the parent board has received new messages.
     * Does not delete messages, only adds new messages.
     * 
     * @throws Exception If one of the addMessage calls fails. 
     */
    protected synchronized final void synchronizeWithoutCommit() throws Exception {
    	for(Board.BoardMessageLink messageLink : mParentBoard.getMessagesAfterIndex(mHighestSynchronizedParentMessageIndex)) {
    		addMessage(messageLink.getMessage());
    		mHighestSynchronizedParentMessageIndex = messageLink.getMessageIndex();
    	}
    	
    	storeWithoutCommit();
    }
    
    /**
     * The job for this function is to find the right place in the thread-tree for the new message and to move around older messages
     * if a parent message of them is received.
     * 
     * Does not store the message, you have to do this before!
     * 
     * Only to be used by the SubscribedBoard itself, the MessageManager should use {@link synchronizeWithoutCommit}. 
     * 
     * @throws Exception If wantsMessagesFrom(author of newMessage) fails. 
     */
    protected synchronized final void addMessage(Message newMessage) throws Exception {
    	if(!mSubscriber.wantsMessagesFrom(newMessage.getAuthor())) {
    		// FIXME: Store a UnwantedMessageLink object for the message and periodically check whether the trust value of the author changed to positive
    		// - then we need to add the unwanted messages of that author.
    		Logger.error(this, "Ignoring message from " + newMessage.getAuthor().getNickname() + " because " + mSubscriber.getNickname() + " does not his messages.");
    		return;
    	}
    	
    	if(newMessage instanceof OwnMessage) {
    		/* We do not add the message to the boards it is posted to because the user should only see the message if it has been downloaded
    		 * successfully. This helps the user to spot problems: If he does not see his own messages we can hope that he reports a bug */
    		throw new IllegalArgumentException("Adding OwnMessages to a board is not allowed.");
    	}
    	
    	if(super.contains(newMessage) == false)
    		throw new IllegalArgumentException("addMessage called with a message which was not posted to this board (" + getName() + "): " + newMessage);
    	
    	BoardThreadLink ghostRef = null;
    	
    	try {
    		// If there was a ghost thread reference for the new message, we associate the message with it - even if it is no thread:
    		// People are allowed to reply to non-threads as if they were threads, which results in a 'forked' thread.
    		ghostRef = getThreadLink(newMessage.getID());
    		ghostRef.setMessage(newMessage);
    		ghostRef.storeWithoutCommit();
    		
    		linkThreadRepliesToNewParent(newMessage.getID(), newMessage);
    	}
    	catch(NoSuchMessageException e) {
    		// If there was no ghost reference, we must store a BoardThreadLink if the new message is a thread 
			if(newMessage.isThread()) {
	    		BoardThreadLink threadRef = new BoardThreadLink(this, newMessage, takeFreeMessageIndexWithoutCommit());
	    		threadRef.initializeTransient(mFreetalk);
	    		threadRef.storeWithoutCommit();
	    		
	    		// We do not call linkThreadRepliesToNewParent() here because if there was no ghost reference for the new message this means that no replies to
	    		// it were received yet.
			}
    	}
    
    	if(newMessage.isThread() == false) {
    		// The new message is no thread. We must:
    		
    		// 1. Find it's parent thread, create a ghost reference for it if it does not exist.
    		BoardThreadLink parentThreadRef = findOrCreateParentThread(newMessage);
    		
    		// 2. Tell it about it's parent thread if it exists.
    		try {
    			newMessage.setThread(parentThreadRef.getMessage());
    		} catch(MessageNotFetchedException e) {
    			// Can happen if the parent thread was not downloaded yet, then it's a ghost reference.
    		}
    		
    		// 3. Tell the parent thread that a new message was added. This updates the last reply date and the "was read"-flag of the thread.
    		parentThreadRef.onMessageAdded(newMessage);
    		parentThreadRef.storeWithoutCommit();
    		
    		// 4. Store a BoardReplyLink for the new message
    		BoardReplyLink messageRef;
    		try {
    			// If addMessage() was called already for the given message (this might happen due to transaction management of the message manager), we must
    			// use the already stored reply link for the message.
    			messageRef = getReplyLink(newMessage);
    		}
    		catch(NoSuchMessageException e) {
    			messageRef = new BoardReplyLink(this, newMessage, takeFreeMessageIndexWithoutCommit());
    			messageRef.initializeTransient(mFreetalk);
    			messageRef.storeWithoutCommit();
    		}
    		
    		// 5. Try to find the new message's parent message and tell it about it's parent message if it exists.
    		try {
    			// Try to find the parent message of the message
    			
    			// FIXME: This allows crossposting. Figure out whether we need to handle it specially:
    			// What happens if the message has specified a parent thread which belongs to this board BUT a parent message which is in a different board
    			// and does not belong to the parent thread
    			newMessage.setParent(mFreetalk.getMessageManager().get(newMessage.getParentID()));
    		}
    		catch(NoSuchMessageException e) {
    			// The parent message of the message was not downloaded yet
    			// TODO: The MessageManager should try to download the parent message if it's poster has enough trust.
    		}

    		linkThreadRepliesToNewParent(parentThreadRef.getThreadID(), newMessage);
    	}

    	storeWithoutCommit();
    }

    
    /**
     * Called by the {@link MessageManager} before a {@link Message} object is deleted from the database.
     * This usually happens when an {@link FTIdentity} is being deleted.
     * 
     * Does not delete the Message object itself, this is to be done by the callee.
     * 
     * @param message The message which is about to be deleted. It must still be stored within the database so that queries on it work.
     * @throws NoSuchMessageException If the message does not exist in this Board.
     */
    protected synchronized void deleteMessage(Message message) throws NoSuchMessageException {
    	
    	try {
    		// Check whether the message was listed as a thread.
    		BoardThreadLink threadLink = getThreadLink(message.getID());
    		
    		// If it was listed as a thread and had no replies, we can delete it's ThreadLink.
	    	if(threadReplyCount(message.getID()) == 0) {
	    		threadLink.deleteWithoutCommit();
	    	} else {
	    		// We do not delete the ThreadLink if it has replies already: We want the replies to stay visible and therefore the ThreadLink has to be kept,
	    		// so we mark it as a ghost thread.
	    		threadLink.removeThreadMessage();
	    	}
    	}
    	catch(NoSuchMessageException e) { // getThreadReference failed
    		if(message.isThread()) {
				Logger.error(this, "Should not happen: deleteMessage() called for a thread which does not exist in this Board.", e);
				throw e;
    		}
    	}
    	
    	if(message.isThread() == false) {
			try {
				final String parentThreadID;
				
				{ // Delete the reply itself.
					BoardReplyLink replyLink = getReplyLink(message);
					parentThreadID = replyLink.getThreadID();
					replyLink.deleteWithoutCommit();
				}
				
				// Update the parent thread of the reply
				
				BoardThreadLink threadLink = getThreadLink(parentThreadID);
				
				try {
					threadLink.getMessage();
				}
				catch(MessageNotFetchedException e) {
					// If the thread itself is a ghost thread and it has no more replies, we must delete it:
					// It might happen that the caller first calls deleteMessage(thread) and then deleteMessage(all replies). The call to
					// deleteMessage(thread) did not delete the thread because it still had replies. Now it has no more replies and we must delete it.
					if(threadReplyCount(parentThreadID) == 0) {
						threadLink.deleteWithoutCommit();
						threadLink = null;
					} 
				}
				
				if(threadLink != null)
					threadLink.onMessageRemoved(message);
				
			} catch (NoSuchMessageException e) {
				Logger.error(this, "Should not happen: deleteMessage() called for a reply message which does not exist in this Board.", e);
				throw e;
			}
    	}
    }

    /**
     * For a new thread, calls setParent() for all messages which are a reply to it and setThread() for all messages which belong to the new thread.
     * For a new message, i.e. reply to a thread, calls setParent() for all messages which are a reply to it.
     *      
     * Assumes that the transient fields of the newMessage are initialized already.
     */
    private synchronized void linkThreadRepliesToNewParent(String parentThreadID, Message newMessage) {
    	
    	boolean newMessageIsThread = (newMessage.getID().equals(parentThreadID));
 
    	for(BoardReplyLink ref : getAllThreadReplies(parentThreadID, false)) {
    		Message threadReply;
			try {
				threadReply = ref.getMessage();
			} catch (MessageNotFetchedException e1) {
				throw new RuntimeException(e1); // Should not happen: BoardReplyLink objects are only created if a message was fetched already.
			}
    		
    		try {
    			threadReply.getParent();
    		}
    		catch(NoSuchMessageException e) {
    			try {
    				if(threadReply.getParentID().equals(newMessage.getID()))
    					threadReply.setParent(newMessage);
    			}
    			catch(NoSuchMessageException ex) {
    				Logger.debug(this, "SHOULD NOT HAPPEN: getParentID() failed for a thread reply: " + threadReply, ex);
    			}
    		}
    		
    		if(newMessageIsThread) {
	    		try {
	    			threadReply.getThread();
	    		}
	    		catch(NoSuchMessageException e) {
	    			threadReply.setThread(newMessage);
	    		}
    		}
    	}
    }
    
    @SuppressWarnings("unchecked")
	public synchronized BoardReplyLink getReplyLink(final Message message) throws NoSuchMessageException {
        final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessage").constrain(message).identity();
        ObjectSet<BoardReplyLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				final BoardReplyLink messageRef = results.next();
				messageRef.initializeTransient(mFreetalk);
				assert(message.equals(messageRef.mMessage)); // The query works
				return messageRef;
	        case 0:
	        	throw new NoSuchMessageException(message.getID());
	        default:
	        	throw new DuplicateMessageException(message.getID());
        }
    }
    
    @SuppressWarnings("unchecked")
	public synchronized BoardThreadLink getThreadLink(final String threadID) throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(BoardThreadLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        ObjectSet<BoardThreadLink> results = q.execute();
        
        switch(results.size()) {
	        case 1:
				final BoardThreadLink threadRef = results.next();
				threadRef.initializeTransient(mFreetalk);
				assert(threadID.equals(threadRef.mThreadID)); // The query works
				return threadRef;
	        case 0:
	        	throw new NoSuchMessageException(threadID);
	        default:
	        	throw new DuplicateMessageException(threadID);
        }
    }

    /**
     * Returns the {@link BoardThreadLink} of the parent thread of the given message.
     * If the parent thread was not downloaded yet, a ghost BoardThreadLink is created and stored for it, without committing the transaction. 
     * You have to lock the board and the database before calling this function.
     * 
     * If the parent thread was downloaded but is no thread actually, a new thread is 'forked' off, making the parent thread message of the given message
     * both appear as a reply to the original thread where it belonged AND as a thread on it's own to which the given message belong.
     * 
     * The transient fields of the returned message will be initialized already.
     * @throws NoSuchMessageException
     */
    private synchronized BoardThreadLink findOrCreateParentThread(final Message newMessage) {
    	String parentThreadID;
    	
    	try {
    		parentThreadID = newMessage.getThreadID();
    	}
    	catch(NoSuchMessageException e) {
    		Logger.error(this, "SHOULD NOT HAPPEN: findOrCreateParentThread called for a message where getThreadID failed: " + e);
    		throw new IllegalArgumentException(e);
    	}

    	try {
    		// The parent thread was downloaded and marked as a thread already, we return its BoardThreadLink
    		return getThreadLink(parentThreadID);
    	}
    	catch(NoSuchMessageException e) {
    		// There is no thread reference for the parent thread yet. Either it was not downloaded yet or it was downloaded but is no thread.
    		try {
    			final Message parentThread = mFreetalk.getMessageManager().get(parentThreadID);
    			
    			if(Arrays.binarySearch(parentThread.getBoards(), mParentBoard) < 0) {
    				// The parent thread is not a message in this board.
    				// TODO: Decide whether we should maybe store a flag in the BoardThreadLink which marks it.
    				// IMHO it is part of the UI's job to read the board list of the actual Message object and display something if the thread is not
    				// really a message to this board.
    			}

    			// The parent thread was downloaded and is no thread actually, we create a BoardThreadLink for it and therefore 'fork' a new thread off
    			// that message. The parent thread message will still be displayed as a reply to it's original thread, but it will also appear as a new thread
    			// which is the parent of the message which was passed to this function.

    			BoardThreadLink parentThreadRef = new BoardThreadLink(this, parentThread, takeFreeMessageIndexWithoutCommit());
    			parentThreadRef.initializeTransient(mFreetalk);
    			parentThreadRef.storeWithoutCommit();
    			return parentThreadRef;
    		}
    		catch(NoSuchMessageException ex) { 
    			// The message manager did not find the parentThreadID, so the parent thread was not downloaded yet, we create a ghost thread reference for it.
    			BoardThreadLink ghostThreadRef = new BoardThreadLink(this, parentThreadID, newMessage.getDate(), takeFreeMessageIndexWithoutCommit());
    			ghostThreadRef.initializeTransient(mFreetalk);
    			ghostThreadRef.storeWithoutCommit();
    			return ghostThreadRef;
    		}		
    	}
    }


    /**
     * Get all threads in the board. The view is specified to the FTOwnIdentity who has subscribed to this board.
     * The transient fields of the returned messages will be initialized already.
     * @param identity The identity viewing the board.
     * @return An iterator of the message which the identity will see (based on its trust levels).
     */
    @SuppressWarnings("unchecked")
    public synchronized ObjectSet<BoardThreadLink> getThreads() {
    	final Query q = mDB.query();
    	q.constrain(BoardThreadLink.class);
    	q.descend("mBoard").constrain(SubscribedBoard.this).identity(); // FIXME: Benchmark whether switching the order of those two constrains makes it faster.
    	q.descend("mLastReplyDate").orderDescending();
    	return new Persistent.InitializingObjectSet<BoardThreadLink>(mFreetalk, q.execute());
    }

    @SuppressWarnings("unchecked")
    public synchronized ObjectSet<MessageReference> getAllMessages(final boolean sortByMessageIndexAscending) {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending(); /* Needed for NNTP */
        }
        return new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q.execute());
    }

    @SuppressWarnings("unchecked")
	public synchronized int getLastMessageIndex() throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageIndex").orderDescending();
        ObjectSet<MessageReference> result = q.execute();
        
        if(result.size() == 0)
        	throw new NoSuchMessageException();
        
        return result.next().getIndex();
    }
    
	public synchronized int getUnreadMessageCount() {
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mWasRead").constrain(false);
        
        return q.execute().size();
    }

    /**
     * Gets a reference to the message with the given index number.
     * 
     * Index numbers are local to each subscribed board. Attention: If a subscription to a board is removed and re-created, different index numbers might
     * be assigned to each message. This can be detected by a changed ID of the subscribed board.
     * 
     * @param index The index number of the demanded message.
     * @return A reference to the demanded message.
     * @throws NoSuchMessageException If there is no such message index.
     */
    @SuppressWarnings("unchecked")
    public synchronized MessageReference getMessageByIndex(int index) throws NoSuchMessageException {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mMessageIndex").constrain(index);
        final ObjectSet<MessageReference> result = q.execute();
        
        switch(result.size()) {
	        case 1:
	        	final MessageReference ref = result.next();
	        	ref.initializeTransient(mFreetalk);
	        	return ref;
	        case 0:
	            throw new NoSuchMessageException();
	        default:
	        	throw new DuplicateMessageException("index " + Integer.toString(index));
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized ObjectSet<MessageReference> getMessagesByMinimumIndex(
            int minimumIndex,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumIndex > 0) {
            q.descend("mMessageIndex").constrain(minimumIndex).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        return new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q.execute());
    }

    @SuppressWarnings("unchecked")
    public synchronized ObjectSet<MessageReference> getMessagesByMinimumDate(
            long minimumDate,
            final boolean sortByMessageIndexAscending,
            final boolean sortByMessageDateAscending)
    {
        final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        if (minimumDate > 0) {
            q.descend("mMessageDate").constrain(minimumDate).smaller().not();
        }
        if (sortByMessageIndexAscending) {
            q.descend("mMessageIndex").orderAscending();
        }
        if (sortByMessageDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        return new Persistent.InitializingObjectSet<MessageReference>(mFreetalk, q.execute());
    }

    /**
     * Get the number of messages in this board.
     */
    public synchronized int messageCount() {
    	final Query q = mDB.query();
        q.constrain(MessageReference.class);
        q.descend("mBoard").constrain(this).identity();
        return q.execute().size();
    }

    /**
     * Get the number of replies to the given thread.
     */
    public synchronized int threadReplyCount(String threadID) {
    	final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        return q.execute().size();
    }
    
    /**
     * Get the number of unread replies to the given thread.
     */
    public synchronized int threadUnreadReplyCount(String threadID) {
    	final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        q.descend("mWasRead").constrain(false);
        
        return q.execute().size();
    }

    /**
     * Get all replies to the given thread, sorted ascending by date if requested
     */
    @SuppressWarnings("unchecked")
    public synchronized ObjectSet<BoardReplyLink> getAllThreadReplies(final String threadID, final boolean sortByDateAscending) {
    	final Query q = mDB.query();
        q.constrain(BoardReplyLink.class);
        q.descend("mBoard").constrain(this).identity();
        q.descend("mThreadID").constrain(threadID);
        
        if (sortByDateAscending) {
            q.descend("mMessageDate").orderAscending();
        }
        
		return new Persistent.InitializingObjectSet(mFreetalk, q.execute());
    }
    
//    public static final class UnwantedMessageLink {
//    	
//    	protected final SubscribedBoard mBoard;
//    	
//    	protected final Message mMessage;
//    	
//    	protected final FTIdentity mAuthor;
//    
//    	
//    	private UnwantedMessageLink(SubscribedBoard myBoard, Message myMessage) {
//    		if(myBoard == null) throw new NullPointerException();
//    		if(myMessage == null) throw new NullPointerException();
//    		
//    		mBoard = myBoard;
//    		mMessage = myMessage;
//    		mAuthor = mMessage.getAuthor();
//    	}
//    	
//    }

    public static abstract class MessageReference extends Persistent {
    	
    	protected final SubscribedBoard mBoard;
    	
    	protected Message mMessage;
    	
    	protected Date mMessageDate;
    	
    	protected final int mMessageIndex;

    	private boolean mWasRead = false;
    	
    	static {
    		Persistent.registerIndexedFields(MessageReference.class, 
    			new String[] { "mBoard", "mMessage", "mMessageIndex", "mMessageDate" });
    	}
    	
    	
    	private MessageReference(SubscribedBoard myBoard, int myMessageIndex) {
        	if(myBoard == null)
        		throw new NullPointerException();
        	
    		mBoard = myBoard;
    		mMessage = null;
    		mMessageDate = null;
    		mMessageIndex = myMessageIndex;
    		
    		try {
				assert(mMessageIndex > mBoard.getLastMessageIndex());
			} catch (NoSuchMessageException e) {
			}
    	}

		private MessageReference(SubscribedBoard myBoard, Message myMessage, int myMessageIndex) {
    		this(myBoard, myMessageIndex);
    		
    		if(myMessage == null)
    			throw new NullPointerException();
    		
    		mMessage = myMessage;
    		mMessageDate = mMessage.getDate();
    	}
    	
        /**
         * Get the message to which this reference points.
         * @throws MessageNotFetchedException If the message belonging to this reference was not fetched yet.
         */
        public Message getMessage() throws MessageNotFetchedException {
        	activate(3); // FIXME: Figure out a reasonable depth
        	mMessage.initializeTransient(mFreetalk);
            return mMessage;
        }
        
        public Date getMessageDate() {
        	return mMessageDate;
        }
        
        /** Get an unique index number of this message in the board where which the query for the message was executed.
         * This index number is needed for NNTP and for synchronization with client-applications: They can check whether they have all messages by querying
         * for the highest available index number. */
        public int getIndex() {
        	return mMessageIndex;
        }
        
		public boolean wasRead() {
			return mWasRead;
		}
		
		public void markAsRead() {
			mWasRead = true;
		}
		
		public void markAsUnread() { 
			mWasRead = false;
		}
        
        /**
         * Does not provide synchronization, you have to lock the MessageManager, this Board and then the database before calling this function.
         */
        protected void storeWithoutCommit(ExtObjectContainer db) {
        	try {
        		checkedActivate(3); // TODO: Figure out a suitable depth.
        		throwIfNotStored(mBoard);
        		if(mMessage != null) throwIfNotStored(mMessage);

        		checkedStore();
        	}
        	catch(RuntimeException e) {
        		rollbackAndThrow(e);
        	}
        }
        
        /**
         * Does not provide synchronization, you have to lock this Board before calling this function.
         */
        public void storeAndCommit() {
        	synchronized(mDB.lock()) {
        		try {
	        		storeWithoutCommit();
	        		commit(this);
        		}
        		catch(RuntimeException e) {
        			rollbackAndThrow(e);
        		}
        	}
        }
        
        
    	protected void deleteWithoutCommit(ExtObjectContainer db) {
    		deleteWithoutCommit(3); // TODO: Figure out a suitable depth.
		}
    }
    
    /**
     * Helper class to associate messages with boards in the database
     */
    public static class BoardReplyLink extends MessageReference { /* TODO: This is only public for configuring db4o. Find a better way */
        
        private final String mThreadID;
        
        static {
        	Persistent.registerIndexedFields(BoardReplyLink.class, new String[] { "mThreadID" });
        }

        protected BoardReplyLink(SubscribedBoard myBoard, Message myMessage, int myIndex) {
        	super(myBoard, myMessage, myIndex);
            
            try {
            	mThreadID = mMessage.getThreadID();
            }
            catch(NoSuchMessageException e) {
            	throw new IllegalArgumentException("Trying to create a BoardReplyLink for a thread, should be a BoardThreadLink.");
            }
            
        }
        /**
         * @throws MessageNotFetchedException For BoardReplyLink objects, this should never throw a MessageNotFetchedException in the current implementation.
         */
        public Message getMessage() throws MessageNotFetchedException {
        	return super.getMessage();
        }
        
        public String getThreadID() {
        	return mThreadID;
        }
        
		public Date getDate() {
			return mMessageDate;
		}

    }
    
    public final static class BoardThreadLink  extends MessageReference {
        
        private final String mThreadID;
        
    	private Date mLastReplyDate;
    	
    	private boolean mWasThreadRead = false;
    	
        static {
        	Persistent.registerIndexedFields(BoardReplyLink.class, new String[] { "mThreadID" });
        }
    	
    	protected BoardThreadLink(SubscribedBoard myBoard, Message myThread, int myMessageIndex) {
    		super(myBoard, myThread, myMessageIndex);
    		
    		if(myThread == null)
    			throw new NullPointerException();
    		
    		mThreadID = mMessage.getID();
    		mLastReplyDate = myThread.getDate();
    	}

		/**
    	 * @param myLastReplyDate The date of the last reply to this thread. This parameter must be specified at creation to prevent threads from being hidden if
    	 * 							the user of this constructor forgot to call updateLastReplyDate() - thread display is sorted descending by reply date!
    	 */
    	protected BoardThreadLink(SubscribedBoard myBoard, String myThreadID, Date myLastReplyDate, int myMessageIndex) {
    		super(myBoard, myMessageIndex);
    		
    		if(myThreadID == null)
    			throw new NullPointerException();
    		
    		// TODO: We might validate the thread id here. Should be safe not to do so because it is taken from class Message which validates it.
    		
    		mThreadID = myThreadID;
    		mLastReplyDate = myLastReplyDate;
    	}
    	
    	protected void onMessageAdded(Message newMessage) {
    		mWasThreadRead = false;
    		
    		Date newDate = newMessage.getDate();
			if(newDate.after(mLastReplyDate))
				mLastReplyDate = newDate;
		}
    	
    	protected void onMessageRemoved(Message removedMessage) {
    		if(removedMessage.getDate().before(mLastReplyDate))
    			return;
    		
    		synchronized(mBoard) {
    	    		// TODO: This assumes that getAllThreadReplies() obtains the sorted order using an index. This is not the case right now. If we do not
    	    		// optimize getAllThreadReplies() we should just iterate over the unsorted replies list and do maximum search.
    				
    				mLastReplyDate = mMessageDate;
    				
    				for(BoardReplyLink reply : mBoard.getAllThreadReplies(mThreadID, true)) {
    					mLastReplyDate = reply.getDate();
    				}
    		}

    		// TODO: I decided not to change the "therad was read flag:" If the thread was unread before, then it is probably still unread now.
    		// If it was read before, removing a message won't change that.
    	}
    	
    	
    	public void removeThreadMessage() {
    		mMessage = null;
    		
    		// TODO: This assumes that getAllThreadReplies() obtains the sorted order using an index. This is not the case right now. If we do not
    		// optimize getAllThreadReplies() we should just iterate over the unsorted replies list and do minimum search.
    		for(BoardReplyLink reply : mBoard.getAllThreadReplies(mThreadID, true)) {
    			mLastReplyDate = reply.getDate();
    			return;
    		}
		}
		
		public Date getLastReplyDate() {
			return mLastReplyDate;
		}
    	
		public String getThreadID() {
			return mThreadID;
		}
		
		/**
		 * Gets the actual message of this thread.
		 * 
		 * @throws MessageNotFetchedException If the message was not downloaded yet! This happens when a reply to a thread is downloaded before the actual
		 * thread was downloaded.
		 */
		public Message getMessage() throws MessageNotFetchedException {
			if(mMessage == null)
				throw new MessageNotFetchedException(mThreadID);
			
			return super.getMessage();
		}
		
		public void setMessage(Message myThread) {
			if(myThread == null)
				throw new NullPointerException();
			
			if(myThread.getID().equals(mThreadID) == false)
				throw new IllegalArgumentException();
			
			mMessage = myThread;
			
			markAsUnread(); // Mark the thread message itself as unread (not the whole thread).
			
			onMessageAdded(myThread); // This also marks the whole thread as unread.
		}
		
		public boolean wasThreadRead() {
			return mWasThreadRead;
		}
		
		public void markThreadAsRead() {
			mWasThreadRead = true;
		}
		
		public void markThreadAsUnread() {
			markAsUnread();
			mWasThreadRead = false;
		}
		
    }

}
