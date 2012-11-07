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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.vertx.java.core.utils.lang.Args;

/**
 * This implementation uses a LinkedList. Nothing special, no lazy entries.
 */
class BucketLinkedList<T> implements Bucket<T> {

	// The underlying ArrayList implementation
	private final List<T> entries;

	/**
	 * Constructor
	 */
	public BucketLinkedList() {
		entries = createList();
	}

	private List<T> createList() {
		return new LinkedList<T>();
	}

	/**
	 * Add an entry
	 * 
	 * @param timeout
	 */
	@Override
	public final void add(final T value) {
		Args.notNull(value, "value");
		entries.add(value);
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
		return entries.remove(value);
	}

	/**
	 * First create a list of all non-null entries. Than create the iterator.
	 */
	@Override
	public final Iterator<T> iterator() {
		return entries.iterator();
	}

	@Override
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	@Override
	public void clear() {
		entries.clear();
	}

	@Override
	public String toString() {
		return "Count: " + entries.size() + "; " + entries.toString();
	}
}
