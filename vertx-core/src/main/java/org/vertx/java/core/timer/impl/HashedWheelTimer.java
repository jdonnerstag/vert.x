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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.internal.SharedResourceMisuseDetector;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.timer.Timeout;
import org.vertx.java.core.timer.Timer;
import org.vertx.java.core.timer.TimerTask;
import org.vertx.java.core.utils.lang.Args;

/**
 * A {@link Timer} optimized for approximated I/O timeout scheduling.
 * 
 * <h3>Tick Duration</h3>
 * 
 * As described with 'approximated', this timer does not execute the scheduled
 * {@link TimerTask} on time. {@link HashedWheelTimer}, on every tick, will
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
 * {@link HashedWheelTimer} maintains a data structure called 'wheel'. To put
 * simply, a wheel is a hash table of {@link TimerTask}s whose hash function is
 * 'dead line of the task'. The default number of ticks per wheel (i.e. the size
 * of the wheel) is 512. You could specify a larger value if you are going to
 * schedule a lot of timeouts.
 * 
 * <h3>Do not create many instances.</h3>
 * 
 * {@link HashedWheelTimer} creates a new thread whenever it is instantiated and
 * started. Therefore, you should make sure to create only one instance and
 * share it across your application. One of the common mistakes, that makes your
 * application unresponsive, is to create a new instance for every connection.
 * 
 * <h3>Implementation Details</h3>
 * 
 * {@link HashedWheelTimer} is based on <a
 * href="http://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and Tony
 * Lauck's paper, <a
 * href="http://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed and
 * Hierarchical Timing Wheels: data structures to efficiently implement a timer
 * facility'</a>. More comprehensive slides are located <a
 * href="http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt"
 * >here</a>.
 */
public class HashedWheelTimer implements Timer {

	private static final Logger log = LoggerFactory
			.getLogger(HashedWheelTimer.class);

	private static final SharedResourceMisuseDetector misuseDetector = new SharedResourceMisuseDetector(
			HashedWheelTimer.class);

	// Doing the actual work
	private Worker worker;

	// A dedicated thread for the worker
	private final Thread workerThread;

	// Events to the worker
	private final BlockingQueue<TimerEvent> events;

	/**
	 * Creates a new timer with the default thread factory (
	 * {@link Executors#defaultThreadFactory()}), default tick duration, and
	 * default number of ticks per wheel.
	 */
	public HashedWheelTimer() {
		this(Executors.defaultThreadFactory());
	}

	/**
	 * Creates a new timer with the default thread factory (
	 * {@link Executors#defaultThreadFactory()}) and default number of ticks per
	 * wheel.
	 * 
	 * @param tickDuration
	 *            the duration between ticks
	 * @param unit
	 *            the time unit of the {@code tickDuration}
	 */
	public HashedWheelTimer(long tickDuration, TimeUnit unit) {
		this(Executors.defaultThreadFactory(), tickDuration, unit);
	}

	/**
	 * Creates a new timer with the default thread factory (
	 * {@link Executors#defaultThreadFactory()}).
	 * 
	 * @param tickDuration
	 *            the duration between tick
	 * @param unit
	 *            the time unit of the {@code tickDuration}
	 * @param ticksPerWheel
	 *            the size of the wheel
	 */
	public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
		this(Executors.defaultThreadFactory(), tickDuration, unit,
				ticksPerWheel);
	}

	/**
	 * Creates a new timer with the default tick duration and default number of
	 * ticks per wheel.
	 * 
	 * @param threadFactory
	 *            a {@link ThreadFactory} that creates a background
	 *            {@link Thread} which is dedicated to {@link TimerTask}
	 *            execution.
	 */
	public HashedWheelTimer(ThreadFactory threadFactory) {
		this(threadFactory, 100, TimeUnit.MILLISECONDS);
	}

	/**
	 * Creates a new timer with the default number of ticks per wheel.
	 * 
	 * @param threadFactory
	 *            a {@link ThreadFactory} that creates a background
	 *            {@link Thread} which is dedicated to {@link TimerTask}
	 *            execution.
	 * @param tickDuration
	 *            the duration between tick
	 * @param unit
	 *            the time unit of the {@code tickDuration}
	 */
	public HashedWheelTimer(ThreadFactory threadFactory, long tickDuration,
			TimeUnit unit) {
		this(threadFactory, tickDuration, unit, 512);
	}

	/**
	 * Creates a new timer.
	 * 
	 * @param threadFactory
	 *            a {@link ThreadFactory} that creates a background
	 *            {@link Thread} which is dedicated to {@link TimerTask}
	 *            execution.
	 * @param tickDuration
	 *            the duration between tick
	 * @param unit
	 *            the time unit of the {@code tickDuration}
	 * @param ticksPerWheel
	 *            the size of the wheel (adjusted to the next higher value which
	 *            is a power of two)
	 */
	public HashedWheelTimer(final ThreadFactory threadFactory,
			final long tickDuration, final TimeUnit unit,
			final int ticksPerWheel) {

		Args.notNull(threadFactory, "threadFactory");
		Args.notNull(unit, "unit");
		Args.isTrue(tickDuration > 0, "tickDuration must be > 0: %s",
				tickDuration);
		Args.isTrue(ticksPerWheel > 0, "ticksPerWheel must be > 0: %s",
				ticksPerWheel);

		events = createEventQueue();
		if (events == null) {
			throw new NullPointerException(
					"createEventQueue() must not return null");
		}

		this.worker = new Worker(tickDuration, unit, ticksPerWheel);

		this.workerThread = threadFactory.newThread(worker);

		misuseDetector.increase();
	}

	/**
	 * Subclass to replace the event queue being used
	 * 
	 * @return
	 */
	protected BlockingQueue<TimerEvent> createEventQueue() {
		// See testBruteForce. The test essentially confirms that common wisdom
		// is true: either event queues are empty or full. Thus they can be
		// rather small.
		return new LinkedBlockingDeque<TimerEvent>(16);
	}

	/**
	 * Starts the background thread explicitly. The background thread will start
	 * automatically on demand even if you did not call this method.
	 */
	public final void start() {
		if ((worker != null) && !workerThread.isAlive()) {
			try {
				workerThread.start();
			} catch (IllegalThreadStateException ex) {
				// ignore. Thread already started.
			}
		}
	}

	@Override
	public final List<? extends Timeout> stop() {

		if (Thread.currentThread() == workerThread) {
			throw new IllegalStateException(
					HashedWheelTimer.class.getSimpleName()
							+ ".stop() cannot be called from "
							+ TimerTask.class.getSimpleName());
		}

		Worker worker = this.worker;
		if (worker == null) {
			return Collections.emptyList();
		}

		this.worker = null;

		sendEvent(TimerEvent.createShutdownEvent());

		boolean interrupted = false;
		while (workerThread.isAlive()) {
			try {
				workerThread.join(100);
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}

		if (interrupted) {
			Thread.currentThread().interrupt();
		}

		misuseDetector.decrease();

		return worker.getUnprocessedTimeouts();
	}

	private final void sendEvent(final TimerEvent event) {
		try {
			if (events.offer(event) == false) {
				log.warn("EventQueue full. Requires waiting");
				events.put(event);
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Error while queueing event", e);
		}
	}

	@Override
	public final Timeout newTimeout(final TimerTask task, final long delay,
			final TimeUnit unit) {

		long currentTime = System.currentTimeMillis();

		Args.notNull(task, "task");
		Args.notNull(unit, "unit");

		// Make sure the worker thread is up and running
		start();

		HashedWheelTimeout timeout = new HashedWheelTimeout(this, task,
				currentTime, unit.toMillis(delay), false);

		sendEvent(TimerEvent.createTimeoutEvent(timeout));
		return timeout;
	}

	/**
	 * Remove a timeout from the scheduler
	 * 
	 * @param timeout
	 */
	public final void remove(final HashedWheelTimeout timeout) {
		if (timeout == null) {
			return;
		}

		sendEvent(TimerEvent.createCancelEvent(timeout));
	}

	/**
	 * A thread worker that listens to the queue (events) to than execute the
	 * "commands". If while waiting the next timeslot is reached, the wait gets
	 * interrupted and already waiting tasks are executed.
	 */
	private final class Worker implements Runnable {

		private final HashedWheelTimerWorker worker;

		/**
		 * Constructor
		 * 
		 * @param tickDuration
		 * @param unit
		 * @param ticksPerWheel
		 */
		public Worker(final long tickDuration, final TimeUnit unit,
				final int ticksPerWheel) {

			this.worker = new HashedWheelTimerWorker(tickDuration, unit,
					ticksPerWheel);
		}

		/**
		 * @return A list of unprocessed timeouts.
		 */
		public final List<? extends Timeout> getUnprocessedTimeouts() {
			return worker.getUnprocessedTimeouts();
		}

		@Override
		public void run() {

			try {
				while (true) {
					long sleepTime = worker.getSleepTime();
					if (sleepTime <= 0) {
						sleepTime = 0;
					}

					TimerEvent event = events.poll(sleepTime,
							TimeUnit.MILLISECONDS);
					if (event == null) {
						worker.nextTick();
					} else {
						do {
							if (event.type == 0) {
								// shutdown
								return;
							} else if (event.type == 1) {
								// new timeout
								worker.scheduleTimeout(event.timeout);
							} else if (event.type == 2) {
								// Cancel
								remove(event.timeout);
							}
							event = events.poll();
						} while (event != null);
					}
				}
			} catch (InterruptedException e) {
				// ignore
				log.error("Timer stopped via Interrupt", e);
			}
		}
	}

	/**
	 * A simple event class (immutable and final). This is the only information
	 * that gets passed between threads.
	 */
	private final static class TimerEvent {

		public static TimerEvent createShutdownEvent() {
			return new TimerEvent(0, null);
		}

		public static TimerEvent createTimeoutEvent(
				final HashedWheelTimeout timeout) {
			return new TimerEvent(1, timeout);
		}

		public static TimerEvent createCancelEvent(
				final HashedWheelTimeout timeout) {
			return new TimerEvent(2, timeout);
		}

		private final int type;
		private final HashedWheelTimeout timeout;

		private TimerEvent(int type, HashedWheelTimeout timeout) {
			this.type = type;
			this.timeout = timeout;
		}
	}
}
