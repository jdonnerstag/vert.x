/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.impl;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.timer.Timer;
import org.vertx.java.core.timer.TimerTask;
import org.vertx.java.core.timer.impl.HashedWheelTimeout;
import org.vertx.java.core.timer.impl.HashedWheelTimerImpl;
import org.vertx.java.core.utils.lang.Args;

/**
 * Netty's NioWorker implements an event loop. This implementation add timer 
 * functionalities and thus doesn't require yet another thread. The timer itself
 * is a modified (and improved) version of netty's HashedWheelTimer.
 * 
 * @author Juergen Donnerstag
 */
public class NioWorkerWithTimer extends NioWorker implements Timer<HashedWheelTimeout> {

  private static final Logger log = LoggerFactory.getLogger(NioWorkerWithTimer.class);

	// Implements the hashed wheel timer. 
	// Note: Access should ONLY be in the IO thread
	private HashedWheelTimerImpl timer;

	private final long tickDuration;
	
	/**
	 * Constructor
	 * 
	 * @param executor
	 */
	public NioWorkerWithTimer(final Executor executor) {
		this(executor, 100);
	}

	/**
	 * Constructor
	 * 
	 * @param executor
	 */
	public NioWorkerWithTimer(final Executor executor, final long tickDuration) {
		super(executor);
		
		this.tickDuration = tickDuration;
	}

	/**
	 * Subclasses may provide their own timer
	 * @return
	 */
	protected HashedWheelTimerImpl newTimer() {
		return new HashedWheelTimerImpl(tickDuration, TimeUnit.MILLISECONDS, 8192);
	}
	
	/**
	 * Replace Netty's default timeout (500ms) with something more suitable for the 
	 * timer: time resolution (e.g. 200ms)
	 * <p>
	 * Executed in the IO Thread!!
	 */
	@Override
	protected long getWaitTimeout(long defaultTimeout) {
		if (timer == null) {
			timer = newTimer();
		}
		long sleepTime = timer.getSleepTime();
  	return sleepTime < 0 ? 0 : Math.min(sleepTime, defaultTimeout);
	}

	/**
	 * Register
	 * @param task
	 * @param delay
	 * @param unit
	 * @param periodic
	 * @return
	 */
	@Override
	public final HashedWheelTimeout newTimeout(final TimerTask task, final long delay,
			final TimeUnit unit, boolean periodic) {

		long currentTime = System.currentTimeMillis();

		Args.notNull(task, "task");
		Args.notNull(unit, "unit");

		final HashedWheelTimeout timeout = new HashedWheelTimeout(task,
				currentTime, unit.toMillis(delay), periodic);

		executeInIoThread(new Runnable() {
			@Override
			public void run() {
				timer.scheduleTimeout(timeout);
			}
		});
		
		return timeout;
	}

	/**
	 * Remove a timeout from the scheduler
	 * 
	 * @param timeout
	 */
	@Override
	public final void remove(final HashedWheelTimeout timeout) {
		if (timeout == null) {
			return;
		}

		executeInIoThread(new Runnable() {
			@Override
			public void run() {
				timer.remove(timeout);
			}
		});
	}

	/**
	 * The underlying eventloop woke up. 
	 * <p>
	 * This method is always executed in the IO thread.
	 */
	@Override
	protected void processUserTask() {
		List<HashedWheelTimeout> expiredTimeouts = this.timer.getExpiredTimeouts();
		if (expiredTimeouts != null) {

			// Notify the expired timeouts.
			for (final HashedWheelTimeout timeout : expiredTimeouts) {
				// Change status
				timeout.expire();
				
				// execute
				executeInIoThread(new Runnable() {
					@Override
					public void run() {
						try {
							timeout.getTask().run(timeout);
						} catch (Exception ex) {
							log.error("Exception while executing timer task: " + ex.getMessage(), ex);
						}
					}
				});
			}
		}
	}
}
