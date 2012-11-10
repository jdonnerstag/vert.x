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
import org.vertx.java.core.timer.Timer;
import org.vertx.java.core.utils.lang.Args;

/**
 * A {@link Timer} optimized for approximated I/O timeout scheduling.
 * 
 * <h3>Tick Duration</h3>
 * 
 * As described with 'approximated', this timer does not execute the scheduled
 * {@link TimerTask} on time. {@link HashedWheelTimerImpl}, on every tick, will
 * check if there are any {@link TimerTask}s behind the schedule and execute
 * them.
 * <p>
 * You can increase or decrease the accuracy of the execution timing by
 * specifying smaller or larger tick duration in the constructor. In most
 * network applications, I/O timeout does not need to be accurate. Therefore,
 * the default tick duration is 100 milliseconds and you will not need to try
 * different configurations in most cases.
 * 
 * <h3>Ticks per Wheel (Wheel Size)</h3>
 * 
 * {@link HashedWheelTimerImpl} maintains a data structure called 'wheel'. To put
 * simply, a wheel is a hash table of {@link TimerTask}s whose hash function is
 * 'dead line of the task'. The default number of ticks per wheel (i.e. the size
 * of the wheel) is 512. You could specify a larger value if you are going to
 * schedule a lot of timeouts.
 * 
 * <h3>Do not create many instances.</h3>
 * 
 * {@link HashedWheelTimerImpl} creates a new thread whenever it is instantiated and
 * started. Therefore, you should make sure to create only one instance and
 * share it across your application. One of the common mistakes, that makes your
 * application unresponsive, is to create a new instance for every connection.
 * 
 * <h3>Implementation Details</h3>
 * 
 * {@link HashedWheelTimerImpl} is based on <a
 * href="http://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and Tony
 * Lauck's paper, <a
 * href="http://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed and
 * Hierarchical Timing Wheels: data structures to efficiently implement a timer
 * facility'</a>. More comprehensive slides are located <a
 * href="http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt"
 * >here</a>.
 */
public class HashedWheelTimerImpl {

	// Duration between ticks. E.g. 100ms
	private final long tickDuration;

	// The wheel: Essentially a List of List with HashedWheelTimeout objects
	private final WheelWithBuckets<HashedWheelTimeout> wheel;

	// The index of last bucket visited
	private int tick;

	// == startTime + tick * tickDuration
	private long lastDeadline;

	// Every timeout gets an ID assigned
	private long idCounter;
	
	// Helper for ID generator
	private int mask;
	
	/**
	 * Constructor
	 */
	public HashedWheelTimerImpl() {
		this(100, TimeUnit.MILLISECONDS);
	}

	/**
	 * Constructor
	 */
	public HashedWheelTimerImpl(final long tickDuration, final TimeUnit unit) {
		this(tickDuration, unit, 1024);
	}

	/**
	 * Constructor
	 */
	public HashedWheelTimerImpl(final long tickDuration, final TimeUnit unit,
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
		
		// use the actual wheel size
		int size = this.wheel.size();
		this.mask = Integer.SIZE - Integer.numberOfLeadingZeros(size);
		if ((1 << (this.mask - 1)) >= size) {
			this.mask -= 1;
		}
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
	public final void scheduleTimeout(final HashedWheelTimeout timeout) {
		Args.notNull(timeout, "timeout");

		final long diff = timeout.deadline - this.lastDeadline;

		int stopIndex = (int) (tick + diff / tickDuration) % wheel.size();

		// An ID with a hint to efficiently find the timeout by ID
		if (++this.idCounter < 0) {
			this.idCounter = 0;
		}
		
		// New timeout or rescheduled entry (periodic)
		if (timeout.id == 0) {
			timeout.id = (this.idCounter << this.mask) + stopIndex;
		} else {
			// do not change the "counter" 
			long m = -(1L << this.mask);
			timeout.id = (timeout.id & m) + stopIndex;
		}
		
		wheel.add(stopIndex, timeout);
	}

	/**
	 * Remove a timeout and don't fire it.
	 * 
	 * @param timeout
	 */
	public final void remove(final HashedWheelTimeout timeout) {
		Args.notNull(timeout, "timeout");
		remove(timeout.id, timeout.delay != 0);
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
				if (timeout.isExpired() || timeout.isCancelled()) {
					// Cleanup: Remove already expired or canceled elements
					iter.remove();
				} else if (timeout.deadline <= deadline) {
					if (expiredTimeouts == null) {
						expiredTimeouts = new ArrayList<>();
					}
					expiredTimeouts.add(timeout);
					iter.remove();

					// Re-schedule if periodic
					if (timeout.isPeriodic()) {
						reschedulePeriodic(timeout);
					}
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

	/**
	 * Find timeout by ID
	 * 
	 * @param id
	 * @return
	 */
	public final HashedWheelTimeout find(final long id) {
		return findAndRemove(id, false);
	}
	
	private HashedWheelTimeout findAndRemove(final long id, boolean remove) {
		int index = (int)(id & ((1 << this.mask) - 1));
		Iterator<HashedWheelTimeout> iter = wheel.iterator(index);
		while (iter.hasNext()) {
			HashedWheelTimeout timeout = iter.next();
			if (timeout != null) {
				if (timeout.id == id) {
					if (remove) {
						iter.remove();
					}
					return timeout;
				}
			}
		}
		
		return null;
	}

	/**
	 * Remove timeout by ID
	 * 
	 * @param id
	 * @return
	 */
	public final HashedWheelTimeout remove(long id, final boolean periodic) {
		if (periodic == false) {
			return findAndRemove(id, true);
		}
		
		long m = -(1L << this.mask);
		id &= m;
		Iterator<HashedWheelTimeout> iter = wheel.getAllEntries().iterator();
		while(iter.hasNext()) {
			HashedWheelTimeout entry = iter.next();
			if ((entry.id & m) == id) {
				remove(entry.id, false);
				return entry;
			}
		}
		return null;
	}
	
	@Override
	public String toString() {
		return "lastDeadline: " + lastDeadline + "; tick: " + tick
				+ "; tickDuration: " + tickDuration;
	}
}
