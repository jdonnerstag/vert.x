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

import java.util.concurrent.Executor;

import org.jboss.netty.channel.socket.nio.NioWorker;

/**
 * Netty's NioWorker implements something like an event loop. Either after a selector has fired 
 * or after a timeout
 * 
 * @author Juergen Donnerstag
 */
public class NioWorkerWithTimer extends NioWorker {

	// milli secs; replace Netty's default (500ms)
	private long timeout = 200;
	
	public NioWorkerWithTimer(final Executor executor) {
		super(executor);
	}

	@Override
	protected long getWaitTimeout(long defaultTimeout) {
  	return this.timeout;
	}
	
	@Override
	protected void processUserTask() {
	}
}
