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
import org.vertx.java.core.timer.TimerTask;

/**
 * Maintain the info related to a single Timeout
 */
class VertxTimeout extends HashedWheelTimeout {

	private static final Logger log = LoggerFactory
			.getLogger(VertxTimeout.class);

	private final Handler<Long> handler;
	private final long id;
	private final Context context;
	private final long delay;
	private final boolean periodic;

	/**
	 * Constructor
	 * 
	 * @param worker
	 * @param task
	 * @param currentMillis
	 * @param delay
	 */
	public VertxTimeout(final HashedWheelTimer timer, final TimerTask task,
			final long currentMillis, final long delay, long id,
			Context context, boolean periodic, Handler<Long> handler) {

		super(timer, task, currentMillis, delay, false);

		this.id = id;
		this.context = context;
		this.periodic = periodic;
		this.delay = delay;
		this.handler = handler;
	}

	@Override
	protected void execute() {
		context.execute(new Runnable() {

			@Override
			public void run() {
				handler.handle(id);
			}
		});

		if (periodic) {
			// getTimer().newTimeout(task, delay, unit)
		}
	}
}
