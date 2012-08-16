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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;

public class BucketTest {

	@Test
	public void testDefault() {
		BucketArrayList<String> bucket = new BucketArrayList<String>();
		assertEquals(0, bucket.getCount());
		assertEquals(0, bucket.size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddNull() {
		Bucket<String> bucket = new BucketArrayList<String>();
		bucket.add(null);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetOOB() {
		BucketArrayList<String> bucket = new BucketArrayList<String>();
		bucket.get(0);
	}

	@Test
	public void testRemoveEmpty() {
		Bucket<String> bucket = new BucketArrayList<String>();
		assertFalse(bucket.remove(null));
		assertFalse(bucket.remove("whatever"));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testSetNoneExisting() {
		BucketArrayList<String> bucket = new BucketArrayList<String>();
		bucket.set(0, "Test");
	}

	@Test
	public void testEmptyIterator() {
		Bucket<String> bucket = new BucketArrayList<String>();
		assertFalse(bucket.iterator().hasNext());
	}

	@Test
	public void testAdd() {
		BucketArrayList<String> bucket = new BucketArrayList<String>();

		bucket.add("test");
		assertEquals(1, bucket.getCount());
		assertEquals(1, bucket.size());
		assertEquals("test", bucket.get(0));

		bucket.set(0, "2222");
		assertEquals(1, bucket.getCount());
		assertEquals(1, bucket.size());
		assertEquals("2222", bucket.get(0));

		Iterator<String> iter = bucket.iterator();
		assertTrue(iter.hasNext());
		assertNotNull(iter.next());
		assertFalse(iter.hasNext());

		assertFalse(bucket.remove("not known"));
		assertEquals(1, bucket.getCount());
		assertEquals(1, bucket.size());
		assertEquals("2222", bucket.get(0));

		assertTrue(bucket.remove("2222"));
		assertEquals(0, bucket.getCount());
		assertEquals(1, bucket.size());
		assertFalse(bucket.iterator().hasNext());
	}

	@Test
	public void testReAdd() {
		BucketArrayList<String> bucket = new BucketArrayList<String>();

		bucket.add("test");
		assertEquals(1, bucket.getCount());
		assertEquals(1, bucket.size());
		assertEquals("test", bucket.get(0));

		assertTrue(bucket.remove("test"));
		assertEquals(0, bucket.getCount());
		assertEquals(1, bucket.size());
		assertFalse(bucket.iterator().hasNext());

		bucket.add("2222");
		assertEquals(1, bucket.getCount());
		assertEquals(1, bucket.size());
		assertEquals("2222", bucket.get(0));

		bucket.add("3333");
		assertEquals(2, bucket.getCount());
		assertEquals(2, bucket.size());
		assertEquals("2222", bucket.get(0));
		assertEquals("3333", bucket.get(1));

		assertTrue(bucket.remove("2222"));
		assertEquals(1, bucket.getCount());
		assertEquals(2, bucket.size());

		bucket.add("4444");
		assertEquals(2, bucket.getCount());
		assertEquals(2, bucket.size());
		assertEquals("4444", bucket.get(0));
		assertEquals("3333", bucket.get(1));

		bucket.add("5555");
		assertEquals(3, bucket.getCount());
		assertEquals(3, bucket.size());
		assertEquals("4444", bucket.get(0));
		assertEquals("3333", bucket.get(1));
		assertEquals("5555", bucket.get(2));
	}
}
