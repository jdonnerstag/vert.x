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
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.vertx.java.core.timer.impl.Wheel;

public class WheelTest {

	@Test
	public void testIndex() {
		Wheel<String> wheel = new Wheel<String>(10);
		assertEquals(0, wheel.getIndex(0));
		assertEquals(1, wheel.getIndex(1));
		assertEquals(9, wheel.getIndex(9));
		assertEquals(0, wheel.getIndex(10));
		assertEquals(1, wheel.getIndex(11));
	}

	@Test
	public void testGetEmpty() {
		Wheel<String> wheel = new Wheel<String>(10);
		assertNull(wheel.get(0));
		assertNull(wheel.get(5));
		assertNull(wheel.get(10));
		assertNull(wheel.get(99999));
	}

	@Test
	public void testSet() {
		Wheel<String> wheel = new Wheel<String>(10);
		wheel.set(0, "1");
		assertEquals("1", wheel.get(0));
		assertNull(wheel.get(1));
		assertNull(wheel.get(9));
		assertEquals("1", wheel.get(10));
		assertNull(wheel.get(99999));

		wheel.set(99, "9");
		assertEquals("1", wheel.get(0));
		assertNull(wheel.get(1));
		assertEquals("9", wheel.get(9));
		assertEquals("1", wheel.get(10));
		assertEquals("9", wheel.get(9999));
	}
}
