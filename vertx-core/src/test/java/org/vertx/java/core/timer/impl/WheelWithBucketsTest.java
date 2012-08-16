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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;

public class WheelWithBucketsTest {

	@Test
	public void testCreate() {
		WheelWithBuckets<String> wheel = new WheelWithBuckets<String>(10);
		assertNotNull(wheel);

		assertEquals(0, wheel.getCount());
		assertFalse(wheel.iterator(0).hasNext());
		assertFalse(wheel.iterator(9).hasNext());
		assertFalse(wheel.iterator(10).hasNext());
		assertFalse(wheel.iterator(99).hasNext());
		assertFalse(wheel.iterator(9999).hasNext());

		assertTrue(wheel.getAllEntries().isEmpty());
	}

	/**
	 * Helper, since wheel or bucket have no size()
	 * 
	 * @param wheel
	 * @param index
	 * @return
	 */
	private int getBucketSize(WheelWithBuckets<String> wheel, int index) {
		int count = 0;
		Iterator<String> iter = wheel.iterator(index);
		while (iter.hasNext()) {
			iter.next();
			count += 1;
		}
		return count;
	}

	/**
	 * Helper since wheel oder bucket have no get(i)
	 * 
	 * @param wheel
	 * @param index
	 * @param i
	 * @return
	 */
	private String getBucketEntry(WheelWithBuckets<String> wheel, int index,
			int i) {
		int count = 0;
		Iterator<String> iter = wheel.iterator(index);
		while (iter.hasNext()) {
			String str = iter.next();
			if (count++ == i) {
				return str;
			}
		}
		return null;
	}

	@Test
	public void testAdd() {
		WheelWithBuckets<String> wheel = new WheelWithBuckets<String>(10);

		wheel.add(1, "111");
		assertEquals(1, wheel.getCount());
		assertEquals(1, getBucketSize(wheel, 1));
		assertEquals("111", getBucketEntry(wheel, 1, 0));
		assertEquals(1, wheel.getAllEntries().size());
		assertTrue(wheel.getAllEntries().contains("111"));

		wheel.add(1, "122");
		assertEquals(2, wheel.getCount());
		assertEquals(2, getBucketSize(wheel, 1));
		assertEquals(2, wheel.getAllEntries().size());
		assertEquals("111", getBucketEntry(wheel, 1, 0));
		assertEquals("122", getBucketEntry(wheel, 1, 1));
		assertTrue(wheel.getAllEntries().contains("111"));
		assertTrue(wheel.getAllEntries().contains("122"));

		wheel.add(11, "133");
		assertEquals(3, wheel.getCount());
		assertEquals(3, getBucketSize(wheel, 1));
		assertEquals(3, wheel.getAllEntries().size());
		assertEquals("111", getBucketEntry(wheel, 1, 0));
		assertEquals("122", getBucketEntry(wheel, 1, 1));
		assertEquals("133", getBucketEntry(wheel, 1, 2));
		assertTrue(wheel.getAllEntries().contains("111"));
		assertTrue(wheel.getAllEntries().contains("122"));
		assertTrue(wheel.getAllEntries().contains("133"));

		wheel.add(5, "511");
		assertEquals(4, wheel.getCount());
		assertEquals(3, getBucketSize(wheel, 1));
		assertEquals(1, getBucketSize(wheel, 5));
		assertEquals(4, wheel.getAllEntries().size());
		assertEquals("111", getBucketEntry(wheel, 1, 0));
		assertEquals("122", getBucketEntry(wheel, 1, 1));
		assertEquals("133", getBucketEntry(wheel, 1, 2));
		assertEquals("511", getBucketEntry(wheel, 5, 0));
		assertTrue(wheel.getAllEntries().contains("111"));
		assertTrue(wheel.getAllEntries().contains("122"));
		assertTrue(wheel.getAllEntries().contains("133"));
		assertTrue(wheel.getAllEntries().contains("511"));
	}

	@Test
	public void testRemove() {
		WheelWithBuckets<String> wheel = new WheelWithBuckets<String>(10);

		wheel.add(1, "111");
		assertEquals(1, wheel.getCount());
		assertEquals(1, getBucketSize(wheel, 1));
		assertEquals(1, wheel.getAllEntries().size());
		assertEquals("111", getBucketEntry(wheel, 1, 0));
		assertTrue(wheel.getAllEntries().contains("111"));

		assertFalse(wheel.remove(0, "111"));
		assertFalse(wheel.remove(1, "none"));
		assertTrue(wheel.remove(1, "111"));
		assertEquals(0, wheel.getCount());
		assertEquals(0, getBucketSize(wheel, 1));
		assertNull(getBucketEntry(wheel, 1, 0));
		assertFalse(wheel.getAllEntries().contains("111"));
	}
}
