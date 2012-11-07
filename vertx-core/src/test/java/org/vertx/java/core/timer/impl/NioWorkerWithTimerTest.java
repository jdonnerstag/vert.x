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

import static org.junit.Assert.assertNotNull;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.impl.NioWorkerWithTimer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.timer.Timeout;
import org.vertx.java.core.timer.TimerTask;

public class NioWorkerWithTimerTest {

	private static final Logger log = LoggerFactory.getLogger(NioWorkerWithTimerTest.class);

	private static TimerTask task = new TimerTask() {
		@Override
		public void run(Timeout timeout) {
			log.warn("TimerTask: " + timeout);
		}
	};

	@Ignore
	@Test
	public void testBruteForce() {
		ExecutorService service = Executors.newFixedThreadPool(1);
		NioWorkerWithTimer timer = new NioWorkerWithTimer(service);
		assertNotNull(timer);

		Random gen = new Random();
		int countAdd = 0;

		long start = System.currentTimeMillis();
		while (countAdd < 1000) {

			int a = gen.nextInt(1000);
			if (a <= 970) {
				countAdd++;
				timer.newTimeout(task, a + 100, TimeUnit.MILLISECONDS, false);
			} else {
				try {
					Thread.sleep(a);
				} catch (InterruptedException e) {
					// ignore
				}
			}
		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// ignore
		}

		service.shutdown();
		try {
			service.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			// ignore
		}
		
		// assertNotNull(timer.getUnprocessedTimeouts());

		long diff = System.currentTimeMillis() - start;
		log.error("Finish: " + diff + "ms");
		// log.error("cancel: " + countCancel + "; add: " + countAdd
		// + "; processed: " + countProcessed + "; remaining: "
		// + worker.getUnprocessedTimeouts().size() + "; process: "
		// + countProcess + "; timer: " + worker.timeMillis);
	}
}
