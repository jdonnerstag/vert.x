/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.vertx.java.core.timer.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vertx.java.core.utils.lang.Args;

/**
 * Very similar to BucketArrayList, but the underlying list will only be lazily
 * created and removed when empty.
 */
class BucketLazyArrayList<T> implements Bucket<T> {

	// The underlying ArrayList implementation
	private List<T> entries;

	// Number of entries. Might be != size(), since remove will set value to
	// null and not remove the entry from the array.
	private int count;

	/**
	 * Constructor
	 */
	public BucketLazyArrayList() {
	}

	private List<T> createList() {
		return new ArrayList<T>();
	}

	/**
	 * @return Get the number of entries, excluding null entries
	 */
	public final int getCount() {
		return count;
	}

	/**
	 * @return Get the size of the underlying ArrayList
	 */
	public final int size() {
		return (entries != null ? entries.size() : 0);
	}

	/**
	 * @param index
	 * @return Get element at index; entry might be null
	 */
	public final T get(int index) {
		if (entries == null) {
			throw new IndexOutOfBoundsException("List is empty. 'index': "
					+ index);
		}
		return entries.get(index);
	}

	/**
	 * Set value at index. Value might be null.
	 * 
	 * @param index
	 * @param value
	 */
	public final void set(int index, T value) {
		if (entries == null) {
			throw new IndexOutOfBoundsException("List is empty. 'index': "
					+ index);
		}

		T old = entries.set(index, value);

		if ((old == null) && (value != null)) {
			count += 1;
		} else if ((old != null) && (value == null)) {
			count -= 1;
			if ((count == 0) && (entries.size() > 100)) {
				clear();
			}
		}
	}

	/**
	 * Add an entry
	 * 
	 * @param timeout
	 */
	@Override
	public final void add(final T value) {
		Args.notNull(value, "value");

		if (entries == null) {
			entries = createList();
		}

		// Find the first non-null entry
		for (int i = 0; i < entries.size(); i++) {
			T entry = entries.get(i);
			if (entry == null) {
				// replace null with new value
				entries.set(i, value);
				count += 1;
				return;
			}
		}

		// Possibly increase array to add value
		entries.add(value);
		count += 1;
	}

	/**
	 * Remove an entry from it's bucket. To avoid array copy, the array entry
	 * will be set to null.
	 * 
	 * @param value
	 * @return True, if entry was removed
	 */
	@Override
	public final boolean remove(final T value) {
		if ((value == null) || (entries == null)) {
			return false;
		}

		Iterator<T> iter = iterator();
		while (iter.hasNext()) {
			T entry = iter.next();
			if (entry == value) {
				iter.remove();
				if ((count == 0) && (entries.size() > 100)) {
					clear();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isEmpty() {
		return entries == null || count == 0;
	}

	@Override
	public void clear() {
		entries = null;
	}

	/**
	 * First create a list of all non-null entries. Than create the iterator.
	 */
	@Override
	public final Iterator<T> iterator() {
		return new Iterator<T>() {

			private int i = 0;

			@Override
			public boolean hasNext() {
				while ((entries != null) && (i < entries.size())) {
					if (entries.get(i) != null) {
						return true;
					}
					i += 1;
				}
				return false;
			}

			@Override
			public T next() {
				return entries.get(i++);
			}

			@Override
			public void remove() {
				entries.set(i, null);
				count--;
			}
		};
	}

	@Override
	public String toString() {
		return "Count: " + count + "; " + entries.toString();
	}
}
