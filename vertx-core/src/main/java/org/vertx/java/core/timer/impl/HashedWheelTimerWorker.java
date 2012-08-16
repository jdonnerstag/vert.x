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
import java.util.concurrent.TimeUnit;

import org.vertx.java.core.timer.Timeout;
import org.vertx.java.core.utils.lang.Args;

/**
 * A HashedWheel based timer worker. This class implements the actual logic.
 */
class HashedWheelTimerWorker {

	// Duration between ticks. E.g. 100ms
	private final long tickDuration;

	// The wheel: Essentially a List of List with HashedWheelTimeout objects
	private final WheelWithBuckets<HashedWheelTimeout> wheel;

	// The last bucket visited
	private int tick;

	// == startTime + tick * tickDuration
	private long lastDeadline;

	/**
	 * Constructor
	 */
	public HashedWheelTimerWorker() {
		this(100, TimeUnit.MILLISECONDS);
	}

	/**
	 * Constructor
	 */
	public HashedWheelTimerWorker(final long tickDuration, final TimeUnit unit) {
		this(tickDuration, unit, 1024);
	}

	/**
	 * Constructor
	 */
	public HashedWheelTimerWorker(final long tickDuration, final TimeUnit unit,
			final int ticksPerWheel) {

		Args.notNull(unit, "unit");

		Args.isTrue(tickDuration > 0, "tickDuration must be > 0: %s",
				tickDuration);

		Args.withinRange(1, 0x4000_0000, ticksPerWheel, "ticksPerWheel");

		// Initialize the wheel.
		this.wheel = new WheelWithBuckets<HashedWheelTimeout>(ticksPerWheel);

		// Convert tickDuration to milliseconds.
		this.tickDuration = unit.toMillis(tickDuration);

		// Prevent overflow.
		long maxSize = Long.MAX_VALUE / wheel.size();
		if (this.tickDuration >= maxSize) {
			long maxTicks = unit.convert(maxSize, TimeUnit.MILLISECONDS);
			throw new IllegalArgumentException("tickDuration is too long: "
					+ tickDuration + ' ' + unit + " >= " + maxTicks + ' '
					+ unit);
		}

		this.lastDeadline = currentTimeMillis();
		this.tick = 0;
	}

	/**
	 * Subclass for easy testing
	 * 
	 * @return
	 */
	protected long currentTimeMillis() {
		return System.currentTimeMillis();
	}

	/**
	 * @return mills to sleep before next tick processing
	 */
	public final long getSleepTime() {
		return lastDeadline + tickDuration - currentTimeMillis();
	}

	/**
	 * Get expired timeouts and notify them
	 */
	public final void nextTick() {
		notifyExpiredTimeouts(getExpiredTimeouts());
	}

	/**
	 * @return Gets the expired timeouts
	 */
	public final List<HashedWheelTimeout> getExpiredTimeouts() {
		List<HashedWheelTimeout> expiredTimeouts = null;

		long current = currentTimeMillis();
		while (this.lastDeadline <= current) {

			// "Add" expired timeouts to the list
			expiredTimeouts = fetchExpiredTimeouts(tick, current,
					expiredTimeouts);

			long next = this.lastDeadline + tickDuration;
			if (next > current) {
				break;
			}
			this.lastDeadline = next;
			tick += 1;
		}

		return expiredTimeouts;
	}

	/**
	 * @return Get all unprocessed timeouts
	 */
	public final List<? extends Timeout> getUnprocessedTimeouts() {
		return wheel.getAllEntries();
	}

	/**
	 * Schedule a new timeout
	 * 
	 * @param timeout
	 * @param delay
	 */
	final void scheduleTimeout(final HashedWheelTimeout timeout) {
		Args.notNull(timeout, "timeout");

		final long diff = timeout.deadline - this.lastDeadline;

		timeout.stopIndex = (int) (tick + diff / tickDuration);

		wheel.add(timeout.stopIndex, timeout);
	}

	/**
	 * Remove a timeout and don't fire it.
	 * 
	 * @param timeout
	 */
	public final void remove(final HashedWheelTimeout timeout) {
		Args.notNull(timeout, "timeout");
		this.wheel.remove(timeout.stopIndex, timeout);
	}

	/**
	 * Get the list of expired timeouts
	 * 
	 * @param tick
	 * @param deadline
	 * @param expiredTimeouts
	 */
	private final List<HashedWheelTimeout> fetchExpiredTimeouts(final int tick,
			final long deadline, List<HashedWheelTimeout> expiredTimeouts) {

		Iterator<HashedWheelTimeout> iter = wheel.iterator(tick);
		while (iter.hasNext()) {
			HashedWheelTimeout timeout = iter.next();
			if (timeout != null) {
				if (timeout.deadline <= deadline) {
					if (expiredTimeouts == null) {
						expiredTimeouts = new ArrayList<>();
					}
					expiredTimeouts.add(timeout);
					iter.remove();

					// Re-schedule if periodic
					if (timeout.isPeriodic()) {
						reschedulePeriodic(timeout);
					}

				} else if (timeout.isExpired() || timeout.isCancelled()) {
					// Cleanup: Remove already expired or canceled elements
					iter.remove();
				}
			}
		}
		return expiredTimeouts;
	}

	/**
	 * One can re-schedule with the same delay after the task was executed,
	 * before the task was executed or exactly "last planned" + delay. The
	 * latter is what we are doing, ignoring any processing delays.
	 * 
	 * @param timeout
	 */
	private void reschedulePeriodic(HashedWheelTimeout timeout) {
		timeout.deadline += timeout.delay;
		scheduleTimeout(timeout);
	}

	/**
	 * Notify the timeout that they have expired.
	 * 
	 * @param expiredTimeouts
	 *            May be null, in which case nothing happens
	 */
	public final void notifyExpiredTimeouts(
			final List<HashedWheelTimeout> expiredTimeouts) {

		if (expiredTimeouts != null) {

			// Notify the expired timeouts.
			for (HashedWheelTimeout timeout : expiredTimeouts) {
				timeout.expire();
			}
		}
	}

	@Override
	public String toString() {
		return "lastDeadline: " + lastDeadline + "; tick: " + tick
				+ "; tickDuration: " + tickDuration;
	}
}
