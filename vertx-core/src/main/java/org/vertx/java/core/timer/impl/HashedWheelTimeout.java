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

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.timer.Timeout;
import org.vertx.java.core.timer.Timer;
import org.vertx.java.core.timer.TimerTask;

/**
 * Maintain the info related to a single Timeout
 */
class HashedWheelTimeout implements Timeout {

	private static final Logger log = LoggerFactory
			.getLogger(HashedWheelTimeout.class);

	// The worker which created the Timeout
	private final HashedWheelTimer timer;

	// State of the timeout
	private STATE state = STATE.INIT;

	// The task to be executed upon timeout
	// TODO replace with vertx handler
	private final TimerTask task;

	// Must pass the deadline before invoking task
	long deadline;

	// wheel[stopIndex] = ...
	int stopIndex;

	// Only != 0, if periodic
	final long delay;

	private Handler<?> handler;

	private Context context;

	/**
	 * Constructor
	 * 
	 * @param worker
	 * @param task
	 * @param currentMillis
	 * @param delay
	 */
	public HashedWheelTimeout(final HashedWheelTimer timer,
			final TimerTask task, final long currentMillis, final long delay,
			final boolean periodic) {

		this.timer = timer;
		this.task = task;
		this.deadline = currentMillis + delay;
		this.delay = (periodic ? delay : 0);
	}

	@Override
	public final Timer getTimer() {
		return timer;
	}

	@Override
	public final TimerTask getTask() {
		return task;
	}

	@Override
	public void cancel() {
		// Don't change EXPIRED state
		if (state != STATE.INIT) {
			return;
		}

		state = STATE.CANCELLED;

		timer.remove(this);
	}

	@Override
	public boolean isCancelled() {
		return state == STATE.CANCELLED;
	}

	@Override
	public boolean isExpired() {
		return state == STATE.EXPIRED;
	}

	void expire() {
		// Don't change CANCELLED state
		if (state != STATE.INIT) {
			return;
		}

		state = STATE.EXPIRED;

		execute();
	}

	protected void execute() {
		// replace with vertx handler invocation
		try {
			task.run(this);
		} catch (Exception ex) {
			log.error(
					"TimerTask has reached Timeout and was fired. Exception while executing TimerTask: ",
					ex);
		}
	}

	/**
	 * 
	 * @return True if periodic
	 */
	public final boolean isPeriodic() {
		return delay != 0;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder(192);
		buf.append("deadline: ");
		buf.append(deadline);
		buf.append(" ms");

		if (isCancelled()) {
			buf.append(", cancelled");
		} else if (isExpired()) {
			buf.append(", expired");
		}

		buf.append(", stopIndex: ").append(stopIndex);

		if (isPeriodic()) {
			buf.append(", periodic");
		}

		return buf.toString();
	}
}
