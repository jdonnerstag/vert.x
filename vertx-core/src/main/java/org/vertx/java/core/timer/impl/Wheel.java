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

import java.io.Serializable;
import java.util.Iterator;

import org.vertx.java.core.utils.lang.Args;

/**
 * A simple Array based Wheel implementation. That is, you don't get
 * OutOfBoundExceptions but the index will go in circles (i = index % size).
 * 
 * Note that the index must be positive. A negative index is not supported.
 * 
 * Please also note that the size of a Wheel is fixed. You can not extend or
 * shrink it.
 */
class Wheel<T> implements Iterable<T>, Serializable {

	private static final long serialVersionUID = 1L;

	private final T[] wheel;

	/**
	 * A helper method (factory) to avoid some generics related typing
	 * 
	 * @param capacity
	 *            The fixed size of the wheel
	 * @return
	 */
	public static <E> Wheel<E> newWheel(final int capacity) {
		return new Wheel<E>(capacity);
	}

	/**
	 * Constructor
	 * 
	 * @param size
	 *            The fixed size of the wheel
	 */
	@SuppressWarnings("unchecked")
	public Wheel(final int size) {
		Args.isFalse(size <= 0, "Parameter 'size' must not be > 0");
		this.wheel = (T[]) new Object[size];
	}

	/**
	 * Calculate the real index
	 * 
	 * @param index
	 * @return the real index
	 */
	public final int getIndex(int index) {
		Args.isFalse(index < 0, "Parameter 'index' must not be < 0");
		return (index % wheel.length);
	}

	/**
	 * Get the entry at index
	 * 
	 * @param index
	 * @return Entry
	 */
	public final T get(int index) {
		return wheel[getIndex(index)];
	}

	/**
	 * Set the entry at index
	 * 
	 * @param index
	 * @param element
	 * @return the element previously at the specified position
	 */
	public final void set(int index, T element) {
		wheel[getIndex(index)] = element;
	}

	/**
	 * The (fixed) size of the wheel
	 * 
	 * @return
	 */
	public final int size() {
		return wheel.length;
	}

	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>()  {

			private int i = 0;
			
			@Override
			public boolean hasNext() {
				return i < wheel.length;
			}

			@Override
			public T next() {
				return wheel[i++];
			}

			@Override
			public void remove() {
				// ignore
			}
		};
	}
}
