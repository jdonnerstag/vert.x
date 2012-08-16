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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.timer.Timeout;
import org.vertx.java.core.timer.TimerTask;

public class HashedWheelTimerWorkerTest {

	private static final Logger log = LoggerFactory
			.getLogger(HashedWheelTimerWorkerTest.class);

	@Test
	public void testDefaultConstructor() {
		HashedWheelTimerWorker worker = new HashedWheelTimerWorker();
		assertNotNull(worker);
		assertNull(worker.getExpiredTimeouts());
		assertTrue(worker.getUnprocessedTimeouts().isEmpty());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRemoveNull() {
		HashedWheelTimerWorker worker = new HashedWheelTimerWorker();
		worker.remove(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testScheduleNull() {
		HashedWheelTimerWorker worker = new HashedWheelTimerWorker();
		worker.scheduleTimeout(null);
	}

	@Test
	public void testEmptyNotifier() {
		HashedWheelTimerWorker worker = new HashedWheelTimerWorker();
		assertNotNull(worker);
		worker.notifyExpiredTimeouts(null);

		List<HashedWheelTimeout> expired = worker.getExpiredTimeouts();
		worker.notifyExpiredTimeouts(expired);
	}

	class TestHashedWheelTimerWorker extends HashedWheelTimerWorker {
		long timeMillis = 0;

		@Override
		protected long currentTimeMillis() {
			return timeMillis;
		}

		@Override
		public String toString() {
			return "timeMillis: " + timeMillis + "; " + super.toString();
		}
	}

	private static TimerTask task = new TimerTask() {
		@Override
		public void run(Timeout timeout) {
		}
	};

	private HashedWheelTimeout createDummyTimeout(long currentMillis, long delay) {
		return new HashedWheelTimeout(null, task, currentMillis, delay, false);
	}

	@Test
	public void testScheduleEntryWithinCurrentTimeslot() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis, 50));

		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 49;
		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 1;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(0, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 50;
		assertNull(worker.getExpiredTimeouts());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Test
	public void testScheduleEntryInCurrentTimeslotButComeBackInNextCycle() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis, 50));

		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 100;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Test
	public void testScheduleEntryInNextNextNextSlot() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis, 220));

		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		// Validate that we properly check all buckets since last time
		worker.timeMillis += 300;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Test
	public void testRemove() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		HashedWheelTimeout t = createDummyTimeout(worker.timeMillis, 50);
		worker.scheduleTimeout(t);

		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.remove(t);
		assertNull(worker.getExpiredTimeouts());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Test
	public void testRemoveAfterExpire() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		HashedWheelTimeout t = createDummyTimeout(worker.timeMillis, 50);
		worker.scheduleTimeout(t);

		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 100;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(0, worker.getUnprocessedTimeouts().size());

		worker.remove(t);
		assertNull(worker.getExpiredTimeouts());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Test
	public void testScheduleMultiple() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis, 50));
		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis, 120));

		assertNull(worker.getExpiredTimeouts());
		assertEquals(2, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 50;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 100;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Test
	public void testLongTerm() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis, 1024 * 100));
		worker.scheduleTimeout(createDummyTimeout(worker.timeMillis,
				1024 * 100 + 200));

		assertNull(worker.getExpiredTimeouts());
		assertEquals(2, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 1024 * 100 - 1;
		assertNull(worker.getExpiredTimeouts());
		assertEquals(2, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 1;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 200;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}

	@Ignore
	@Test
	public void testBruteForce() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		Random gen = new Random();
		int countCancel = 0;
		int countAdd = 0;
		int countProcess = 0;
		int countProcessed = 0;

		long start = System.currentTimeMillis();
		for (int i = 0; i < 100000000; i++) {

			int a = gen.nextInt(100);
			if (a < 10) {
				// cancel
				countCancel++;
			} else if (a < 70) {
				// add
				countAdd++;
				worker.scheduleTimeout(createDummyTimeout(worker.timeMillis,
						a + 100));
			} else {
				// process
				countProcess++;
				worker.timeMillis += a;
				// worker.nextTick();
				List<HashedWheelTimeout> expired = worker.getExpiredTimeouts();
				worker.notifyExpiredTimeouts(expired);
				countProcessed += expired.size();
			}
		}

		assertNotNull(worker.getUnprocessedTimeouts());

		long diff = System.currentTimeMillis() - start;
		log.error("Finish: " + diff + "ms");
		log.error("cancel: " + countCancel + "; add: " + countAdd
				+ "; processed: " + countProcessed + "; remaining: "
				+ worker.getUnprocessedTimeouts().size() + "; process: "
				+ countProcess + "; timer: " + worker.timeMillis);
	}

	@Test
	public void testPeriodic() {
		TestHashedWheelTimerWorker worker = new TestHashedWheelTimerWorker();
		assertNotNull(worker);

		HashedWheelTimeout timeout = new HashedWheelTimeout(null, task,
				worker.timeMillis, 50, true);
		worker.scheduleTimeout(timeout);

		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 49;
		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 1;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 49;
		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 1;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 49;
		assertNull(worker.getExpiredTimeouts());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.timeMillis += 1;
		assertEquals(1, worker.getExpiredTimeouts().size());
		assertEquals(1, worker.getUnprocessedTimeouts().size());

		worker.remove(timeout);
		assertEquals(0, worker.getUnprocessedTimeouts().size());
	}
}
