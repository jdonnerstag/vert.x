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
 * A Wheel with Bucket<T> entries
 */
class WheelWithBuckets<T> {

	// The underlying wheel
	private final Wheel<Bucket<T>> wheel;

	// Number of entries across all buckets
	private int count;

	/**
	 * Constructor
	 * 
	 * @param size
	 *            The (fixed) size of the underlying wheel
	 */
	public WheelWithBuckets(int size) {
		wheel = Wheel.newWheel(size);
		for (int i = 0; i < wheel.size(); i++) {
			wheel.set(i, createBucket());
		}
	}

	/**
	 * May be subclassed to provide you own Bucket implementation
	 * 
	 * @return
	 */
	protected Bucket<T> createBucket() {
		return new BucketArrayList<T>();
	}

	/**
	 * @return The size of underlying wheel
	 */
	public final int size() {
		return wheel.size();
	}

	/**
	 * Iterate over bucket at 'index'
	 * 
	 * @param index
	 * @return
	 */
	public final Iterator<T> iterator(int index) {
		return wheel.get(index).iterator();
	}

	/**
	 * @return Get the number of entries across all buckets
	 */
	public final int getCount() {
		return count;
	}

	/**
	 * Add an entry at wheel entry 'index'
	 * 
	 * @param index
	 * @param entry
	 */
	public final void add(int index, final T entry) {
		Args.notNull(entry, "timeout");
		count += 1;
		wheel.get(index).add(entry);
	}

	/**
	 * Remove an entry from it's bucket
	 * 
	 * @param index
	 *            wheel index of the bucket
	 * @param entry
	 * @return True, if entry was removed
	 */
	public final boolean remove(int index, final T entry) {
		if (entry == null) {
			return false;
		}

		Bucket<T> bucket = wheel.get(index);
		if (bucket.remove(entry)) {
			count -= 1;
			return true;
		}
		return false;
	}

	/**
	 * Get all entries from all buckets. This method is only really useful when
	 * the timer has gone down and you want to know which timeout are yet in the
	 * system.
	 * 
	 * @return Timeout currently in the system
	 */
	public final List<T> getAllEntries() {
		List<T> entries = new ArrayList<T>(count + 10);
		for (Bucket<T> bucket : wheel) {
			for (T entry : bucket) {
				entries.add(entry);
			}
		}

		return entries;
	}

	@Override
	public String toString() {
		return "Count: " + count;
	}
}
