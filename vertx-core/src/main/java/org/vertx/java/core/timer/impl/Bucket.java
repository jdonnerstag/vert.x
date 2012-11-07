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

/**
 * Buckets have simple a simple interface
 * 
 * @author Juergen Donnerstag
 */
public interface Bucket<T> extends Iterable<T> {

	/**
	 * @return true, if empty
	 */
	boolean isEmpty();

	/**
	 * Add an entry
	 * 
	 * @param timeout
	 */
	void add(final T value);

	/**
	 * Remove an entry from it's bucket. To avoid array copy, the array entry
	 * will be set to null.
	 * 
	 * @param value
	 * @return True, if entry was removed
	 */
	boolean remove(final T value);

	/**
	 * Remove all entries
	 */
	void clear();
}
