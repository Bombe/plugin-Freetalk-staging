/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Freetalk;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.db4o.ObjectSet;
import com.db4o.ext.ExtObjectContainer;
import com.db4o.ext.ExtObjectSet;
import com.db4o.query.Query;

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
	public final void initializeTransient(final Freetalk myFreetalk) {
		mFreetalk = myFreetalk;
		mDB = mFreetalk.getDatabase();
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
	protected final void checkedActivate(final Object object, final int depth) {
		if(mDB.isStored(object)) {
			if(!mDB.isActive(object))
				Logger.error(this, "Trying to store a non-active object: " + object);
				
			mDB.activate(this, depth);
		}
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * 
	 * Same as a call to {@link checkedActivate(this, depth)}
	 */
	protected final void checkedActivate(final int depth) {
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
	protected final void checkedStore(final Object object) {
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
	protected final void checkedDelete(final Object object) {
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
	protected final void throwIfNotStored(final Object object) {
		if(object == null) {
			Logger.error(this, "Mandatory object is null!");
			throw new RuntimeException("Mandatory object is null!"); 
		}
		
		if(!mDB.isStored(object)) {
			Logger.error(this, "Mandatory object is not stored: " + object);
			throw new RuntimeException("Mandatory object is not stored: " + object);
		}
	}
	
	/**
	 * This is one of the only functions which outside classes should use.  Rolls back the current transaction, logs the passed exception and throws it.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) {<br />
	 * 	try { object.storeWithoutCommit(); object.checkedCommit(this); }<br />
	 * 	catch(RuntimeException e) { Persistent.checkedRollback(mDB, this, e); }<br />
	 * } 
	 */
	public static final void checkedRollback(final ExtObjectContainer db, final Object loggingObject, final Throwable error) {
		// As of db4o 7.4 it seems necessary to call gc(); to cause rollback() to work.
		System.gc();
		db.rollback();
		System.gc(); 
		Logger.error(loggingObject, "ROLLED BACK!", error);
	}

	/**
	 * This is one of the only functions which outside classes should use.  Rolls back the current transaction, logs the passed exception and throws it.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) {<br />
	 * 	try { object.storeWithoutCommit(); object.checkedCommit(this); }<br />
	 * 	catch(RuntimeException e) { Persistent.checkedRollbackAndThrow(mDB, this, e); }<br />
	 * } 
	 */
	public static final void checkedRollbackAndThrow(final ExtObjectContainer db, final Object loggingObject, final RuntimeException error) {
		checkedRollback(db, loggingObject, error);
		throw error;
	}
	
	/**
	 * Only to be used by the extending classes, not to be called from the outside.
	 * To be used when writing your own {@link storeWithoutCommit}, look at this function to see how it is used.
	 */
	protected final void checkedRollbackAndThrow(final RuntimeException error) {
		checkedRollbackAndThrow(mDB, this, error);
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
	protected void storeWithoutCommit(final int activationDepth) {
		try {		
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(activationDepth);
			checkedStore();
		}
		catch(final RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	/**
	 * This is one of the only functions which outside classes should use. It is used for storing the object.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) {<br />
	 * 	try { object.storeWithoutCommit(); object.checkedCommit(this); }<br />
	 * 	catch(RuntimeException e) { Persistent.checkedRollbackAndThrow(mDB, this, e); }<br />
	 * } 
	 */
	protected void storeWithoutCommit() {
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
	protected void deleteWithoutCommit(final int activationDepth) {
		try {
			// 1 is the maximal depth of all getter functions. You have to adjust this when introducing new member variables.
			checkedActivate(activationDepth);
			checkedDelete(this);
		}
		catch(final RuntimeException e) {
			checkedRollbackAndThrow(e);
		}
	}
	
	/**
	 * This is one of the only functions which outside classes should use. It is used for deleting the object.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) {<br />
	 * 	try { object.storeWithoutCommit(); object.checkedCommit(this); }<br />
	 * 	catch(RuntimeException e) { Persistent.checkedRollbackAndThrow(mDB, this, e); }<br />
	 * } 
	 */
	protected void deleteWithoutCommit() {
		deleteWithoutCommit(1);
	}
	

	/**
	 * This is one of the only functions which outside classes should use. It is used for committing the transaction. 
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) {<br />
	 * 	try { object.storeWithoutCommit(); Persistent.checkedCommit(mDB, this); }<br />
	 * 	catch(RuntimeException e) { Persistent.checkedRollbackAndThrow(mDB, this, e); }<br />
	 * } 
	 */
	protected static final void checkedCommit(final ExtObjectContainer db, final Object loggingObject) {
		db.commit();
		Logger.debug(loggingObject, "COMMITED.");
	}
	
	/**
	 * This is one of the only functions which outside classes should use. It is used for committing the transaction.
	 * The call to this function must be embedded in a transaction, that is a block of:<br />
	 * synchronized(mDB.lock()) {<br />
	 * 	try { object.storeWithoutCommit(); object.checkedCommit(this); }<br />
	 * 	catch(RuntimeException e) { Persistent.checkedRollbackAndThrow(mDB, this, e); }<br />
	 * } 
	 * 
	 * Notice that this function is not final to allow implementing classes to override it for making it visible in their package.
	 */
	protected void checkedCommit(final Object loggingObject) {
		checkedCommit(mDB, loggingObject);
	}
	
	/**
	 * An implementation of ObjectSet which encapsulates a given ObjectSet of objects which extend Persistent and calls initializeTransient() for each returned object
	 * automatically.
	 */
	public static final class InitializingObjectSet<Type extends Persistent> implements ObjectSet<Type> {
		
		private final Freetalk mFreetalk;
		private final ObjectSet<Type> mObjectSet;
		
		public InitializingObjectSet(final Freetalk myFreetalk, final ObjectSet<Type> myObjectSet) {
			mFreetalk = myFreetalk;
			mObjectSet = myObjectSet;
		}
		
		@SuppressWarnings("unchecked")
		public InitializingObjectSet(final Freetalk myFreetalk, final Query myQuery) {
			this(myFreetalk, myQuery.execute());
		}
	
		public ExtObjectSet ext() {
			throw new UnsupportedOperationException();
		}

		public boolean hasNext() {
			return mObjectSet.hasNext();
		}

		public Type next() {
			final Type next = mObjectSet.next();
			next.initializeTransient(mFreetalk);
			return next;
		}

		public void reset() {
			mObjectSet.reset();
		}

		public int size() {
			return mObjectSet.size();
		}

		public boolean add(final Type e) {
			throw new UnsupportedOperationException();
		}

		public void add(final int index, final Type element) {
			throw new UnsupportedOperationException();
		}

		public boolean addAll(final Collection<? extends Type> c) {
			throw new UnsupportedOperationException();
		}

		public boolean addAll(final int index, final Collection<? extends Type> c) {
			throw new UnsupportedOperationException();
		}

		public void clear() {
			throw new UnsupportedOperationException();
		}

		public boolean contains(final Object o) {
			return mObjectSet.contains(o);
		}

		public boolean containsAll(final Collection<?> c) {
			return mObjectSet.containsAll(c);
		}

		public Type get(final int index) {
			Type object = mObjectSet.get(index);
			object.initializeTransient(mFreetalk);
			return object;
		}

		public int indexOf(final Object o) {
			return mObjectSet.indexOf(o);
		}

		public boolean isEmpty() {
			return mObjectSet.isEmpty();
		}

		public final Iterator<Type> iterator() {
			return new Iterator<Type>() {
				final Iterator<Type> mIterator = mObjectSet.iterator(); 
				
				public boolean hasNext() {
					return mIterator.hasNext();
				}

				public Type next() {
					final Type next = mIterator.next();
					next.initializeTransient(mFreetalk);
					return next;
				}

				public void remove() {
					throw new UnsupportedOperationException();
				}
				
			};
		}

		public int lastIndexOf(final Object o) {
			return mObjectSet.lastIndexOf(o);
		}

		private final class InitializingListIterator<ListType extends Persistent> implements ListIterator<ListType> {
			private final ListIterator<ListType> mIterator;
			
			public InitializingListIterator(final ListIterator<ListType> myIterator) {
				 mIterator = myIterator;
			}

			public void add(final ListType e) {
				throw new UnsupportedOperationException();
			}

			public boolean hasNext() {
				return mIterator.hasNext();
			}

			public boolean hasPrevious() {
				return mIterator.hasPrevious();
			}

			public ListType next() {
				final ListType next = mIterator.next();
				next.initializeTransient(mFreetalk);
				return next;
			}

			public int nextIndex() {
				return mIterator.nextIndex();
			}

			public ListType previous() {
				final ListType previous = mIterator.previous();
				previous.initializeTransient(mFreetalk);
				return previous;
			}

			public int previousIndex() {
				return mIterator.previousIndex();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}

			public void set(final ListType e) {
				throw new UnsupportedOperationException();
			}
		}
		
		public ListIterator<Type> listIterator() {
			return new InitializingListIterator<Type>(mObjectSet.listIterator());
		}
		
		public ListIterator<Type> listIterator(final int index) {
			return new InitializingListIterator<Type>(mObjectSet.listIterator(index));
		}

		public boolean remove(final Object o) {
			throw new UnsupportedOperationException();
		}

		public Type remove(final int index) {
			throw new UnsupportedOperationException();
		}

		public boolean removeAll(final Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		public boolean retainAll(final Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		public Type set(final int index, final Type element) {
			throw new UnsupportedOperationException();
		}

		public List<Type> subList(final int fromIndex, final int toIndex) {
			throw new UnsupportedOperationException();
		}

		public Object[] toArray() {
			throw new UnsupportedOperationException("ObjectSet provides array functionality already.");
		}

		public <T> T[] toArray(final T[] a) {
			throw new UnsupportedOperationException("ObjectSet provides array functionality already.");
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

	}
	
}
