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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.vertx.java.core.AsyncResult;

/**
 * Action Future
 */
public class ActionFuture<T> implements Future<AsyncResult<T>> {
	
  private final VertxCountDownLatch latch = new VertxCountDownLatch(1);
  private volatile AsyncResult<T> result;
  
  /**
   * Constructor
   */
  public ActionFuture() {
  }

  /**
   * The job has finished and the result is provided.
   * 
   * @param res
   */
  public void countDown(final AsyncResult<T> res) {
  	// Don't override the result, if already set
  	if (this.result == null) {
	  	this.result = res;
	  	this.latch.countDown();
  	}
  }

  /**
   * Not implemented
   */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

  /**
   * Not implemented
   */
	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return this.result != null;
	}

	/**
	 * The async result data.
	 * 
	 * @return null, if the job has not yet finished
	 */
	public AsyncResult<T> result() {
		return this.result;
	}
	
	/**
	 * Wait until the background action completed.
	 * 
	 * @return null, if the action has not yet completed
	 */
	@Override
	public AsyncResult<T> get() throws InterruptedException {
		if (result == null) {
			latch.await();
		}
		return this.result;
	}

	/**
	 * Wait until the background action completed. 
	 * 
	 * @return null, if the action has not yet completed
	 */
	@Override
	public AsyncResult<T> get(long timeout, TimeUnit unit) {
		latch.await(timeout, unit);
		
		// result will be null, if the action has not completed in time
    return this.result;
	}
}
