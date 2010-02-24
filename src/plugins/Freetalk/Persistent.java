package plugins.Freetalk;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.ExtObjectSet;

import freenet.support.Logger;

/**
 * This is the base class for all classes which are stored in the Freetalk database.<br /><br />
 * 
 * It provides common functions which are needed for storing, updating, retrieving and deleting objects.
 * 
 * @author xor (xor@freenetproject.org)
 */
public abstract class Persistent {
	
	/**
	 * A reference to the Freetalk object with which this Persistent object is associated.
	 */
	protected transient Freetalk mFreetalk;
	
	/**
	 * A reference to the database in which this Persistent object resists.
	 */
	protected transient ExtObjectContainer mDB;
	
	
	/**
	 * Holds a per-class list of indexed member variables. Indexed member variables are such where db4o is 
	 * told to create an index on for fast queries.
	 */
	protected transient static final Hashtable<Class<? extends Persistent>, String[]> mIndexedFields = new Hashtable<Class<? extends Persistent>, String[]>();

	/**
	 * Function for registering the indexed fields of a class.
	 * Has to be called in the "static{}" code block of the class - calling it after the static code block was executed will not have any effect!
	 * 
	 * @param clazz The class of which the fields are to be registered
	 * @param fields The names of the fields in the Java source code.
	 */
	protected static final void registerIndexedFields(Class<? extends Persistent> clazz, String[] fields) {
		mIndexedFields.put(clazz, fields);
	}
	
	/**
	 * Gets all indexed fields which were registered yet.
	 * Must be called after the "static{}" code blocks were executed as the indexed fields are registered during that phase.
	 * @return
	 */
	protected synchronized static final Hashtable<Class<? extends Persistent>, String[]> getIndexedFields() {
		return mIndexedFields;
	}
	
	/**
	 * Must be called once after obtaining this object from the database before using any getter or setter member functions
	 * and before calling storeWithoutCommit / deleteWithoutCommit.
	 * Transient fields are NOT stored in the database. They are references to objects such as the IdentityManager.
	 */
	public void initializeTransient(Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		mDB = mFreetalk.getDatabase();
	}
	
	protected final void activate(int depth) { // FIXME: Change visibility to private and use checkedActivate in extending classes.
		mDB.activate(this, depth);
	}

	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Used by storeWithoutCommit/deleteWithoutCommit to check whether an object is active before storing it.<br /><br />
	 * 
	 * Logs an error if the object is not active.<br /><br />
	 * 
	 * Activates the object to the specified depth.<br /><br />
	 */
	protected final void checkedActivate(Object object, int depth) {
		if(mDB.isStored(object)) {
			if(!mDB.isActive(object))
				Logger.error(this, "Trying to store a non-active object: " + object);
				
			activate(depth);
		}
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedActivate(this, depth)}
	 */
	protected final void checkedActivate(int depth) {
		checkedActivate(this, depth);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Used by storeWithoutCommit for actually storing the object.<br /><br />
	 * 
	 * Currently does not any additional checks, it is used to 
	 * @param object
	 */
	protected final void checkedStore(Object object) {
		mDB.store(object);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedStore(this)}
	 */
	protected final void checkedStore() {
		mDB.store(this);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Checks whether an object is stored in the database and deletes it if it is.
	 * If it was not found in the database, an error is logged.<br /><br />
	 * 
	 * This is to be used as an integrity check in deleteWithoutCommit() implementations. 
	 */
	protected final void checkedDelete(Object object) {
		if(mDB.isStored(object))
			mDB.delete(object);
		else
			Logger.error(this, "Trying to delete a inexistent object: " + object);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedDelete(Object object)}
	 */
	protected final void checkedDelete() {
		checkedDelete(this);
	}
	
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Checks whether the given object is stored in the database already and throws a RuntimeException if it is not.<br /><br />
	 * 
	 * This function is to be used as an integrity check in storeWithoutCommit() implementations which require that objects to which
	 * this object references have been stored already.
	 */
	protected final void throwIfNotStored(Object object) {
		if(object == null) {
			Logger.error(this, "Mandatory object is null!");
			throw new RuntimeException("Mandatory object is null!"); 
		}
		
		if(!mDB.isStored(object)) {
			Logger.error(this, "Mandatory object is not stored: " + object);
			throw new RuntimeException("Mandatory object is not stored: " + object);
		}
	}
	
	public static final void checkedRollback(ExtObjectContainer db, Object loggingObject, Throwable error) {
		// As of db4o 7.4 it seems necessary to call gc(); to cause rollback() to work.
		System.gc();
		db.rollback();
		System.gc(); 
		Logger.error(loggingObject, "ROLLED BACK!", error);
	}

	/**
	 * Rolls back the current transaction, logs the passed exception and throws it.
	 * To be used in try/catch blocks in storeWithoutCommit/deleteWithoutCommit.
	 */
	public static final void rollbackAndThrow(ExtObjectContainer db, Object loggingObject, RuntimeException error) {
		checkedRollback(db, loggingObject, error);
		throw error;
	}
	
	protected final void rollbackAndThrow(RuntimeException error) {
		rollbackAndThrow(mDB, this, error);
	}
	

	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * When your extending class needs a different activation depth for store than 1, you have to override storeWithoutCommit() and make it call this function.
	 * If you need to store other objects than this object (that is member objects) then you might want to copy the body of this function so that 
	 * checkedActivate() is not called twice.
	 * 
	 * @param activationDepth The desired activation depth.
	 */
	protected void storeWithoutCommit(int activationDepth) {
		try {		
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(activationDepth);
			checkedStore(); // There is no checkedStore()
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}
	}
	
	/**
	 * This is one of the only public functions which outside classes should use. It is used for storing the object.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) { try { object.storeWithoutCommit(); mDB.commit(); } catch(RuntimeException e) { Persistent.rollbackAndThrow(mDB, this, e); } } 
	 */
	protected  void storeWithoutCommit() {
		storeWithoutCommit(1);
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * When your extending class needs a different activation depth for store than 1, you have to override storeWithoutCommit() and make it call this function.
	 * If you need to store other objects than this object (that is member objects) then you might want to copy the body of this function so that 
	 * checkedActivate() is not called twice.
	 * 
	 * @param activationDepth The desired activation depth.
	 */
	protected void deleteWithoutCommit(int activationDepth) {
		try {
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(activationDepth);
			checkedDelete(this);
		}
		catch(RuntimeException e) {
			rollbackAndThrow(e);
		}
	}
	
	/**
	 * This is one of the only public functions which outside classes should use. It is used for deleting the object.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) { try { object.deleteWithoutCommit(); mDB.commit(); } catch(RuntimeException e) { Persistent.rollbackAndThrow(mDB, this, e); } }
	 */
	protected void deleteWithoutCommit() {
		deleteWithoutCommit(1);
	}
	
	protected static final void commit(ExtObjectContainer db, Object loggingObject) {
		db.commit();
		Logger.debug(loggingObject, "COMMITED.");
	}
	
	protected void commit(Object loggingObject) {
		commit(mDB, loggingObject);
	}
	
	/**
	 * An implementation of ObjectSet which encapsulates a given ObjectSet of objects which extend Persistent and calls initializeTransient() for each returned object
	 * automatically.
	 */
	public static class InitializingObjectSet<Type extends Persistent> implements ObjectSet<Type> {
		
		final Freetalk mFreetalk;
		final ObjectSet<Type> mObjectSet;
		
		public InitializingObjectSet(Freetalk myFreetalk, ObjectSet<Type> myObjectSet) {
			mFreetalk = myFreetalk;
			mObjectSet = myObjectSet;
		}

		@Override
		public ExtObjectSet ext() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasNext() {
			return mObjectSet.hasNext();
		}

		@Override
		public Type next() {
			Type next = mObjectSet.next();
			next.initializeTransient(mFreetalk);
			return next;
		}

		@Override
		public void reset() {
			mObjectSet.reset();
		}

		@Override
		public int size() {
			return mObjectSet.size();
		}

		@Override
		public boolean add(Type e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(int index, Type element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends Type> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(int index, Collection<? extends Type> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean contains(Object o) {
			return mObjectSet.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return mObjectSet.containsAll(c);
		}

		@Override
		public Type get(int index) {
			Type object = mObjectSet.get(index);
			object.initializeTransient(mFreetalk);
			return object;
		}

		@Override
		public int indexOf(Object o) {
			return mObjectSet.indexOf(o);
		}

		@Override
		public boolean isEmpty() {
			return mObjectSet.isEmpty();
		}

		@Override
		public Iterator<Type> iterator() {
			return new Iterator<Type>() {
				final Iterator<Type> mIterator = mObjectSet.iterator(); 
				
				@Override
				public boolean hasNext() {
					return mIterator.hasNext();
				}

				@Override
				public Type next() {
					Type next = mIterator.next();
					next.initializeTransient(mFreetalk);
					return next;
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
				
			};
		}

		@Override
		public int lastIndexOf(Object o) {
			return mObjectSet.lastIndexOf(o);
		}

		private final class InitializingListIterator<ListType extends Persistent> implements ListIterator<ListType> {
			private final ListIterator<ListType> mIterator;
			
			public InitializingListIterator(ListIterator<ListType> myIterator) {
				 mIterator = myIterator;
			}

			@Override
			public void add(ListType e) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean hasNext() {
				return mIterator.hasNext();
			}

			@Override
			public boolean hasPrevious() {
				return mIterator.hasPrevious();
			}

			@Override
			public ListType next() {
				ListType next = mIterator.next();
				next.initializeTransient(mFreetalk);
				return next;
			}

			@Override
			public int nextIndex() {
				return mIterator.nextIndex();
			}

			@Override
			public ListType previous() {
				ListType previous = mIterator.previous();
				previous.initializeTransient(mFreetalk);
				return previous;
			}

			@Override
			public int previousIndex() {
				return mIterator.previousIndex();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public void set(ListType e) {
				throw new UnsupportedOperationException();
			}
		}
		
		@Override
		public ListIterator<Type> listIterator() {
			return new InitializingListIterator<Type>(mObjectSet.listIterator());
		}
		
		@Override
		public ListIterator<Type> listIterator(int index) {
			return new InitializingListIterator<Type>(mObjectSet.listIterator(index));
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Type remove(int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Type set(int index, Type element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Type> subList(int fromIndex, int toIndex) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object[] toArray() {
			throw new UnsupportedOperationException("ObjectSet provides array functionality already.");
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new UnsupportedOperationException("ObjectSet provides array functionality already.");
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}
	
}
