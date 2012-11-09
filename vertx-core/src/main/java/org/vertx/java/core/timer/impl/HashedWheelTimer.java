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

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.internal.SharedResourceMisuseDetector;
import org.vertx.java.core.impl.NioWorkerWithTimer;

public class HashedWheelTimer extends NioWorkerWithTimer {

  private static final SharedResourceMisuseDetector misuseDetector =
      new SharedResourceMisuseDetector(HashedWheelTimer.class);

  /**
   * Creates a new timer with the default thread factory
   * ({@link Executors#defaultThreadFactory()}), default tick duration, and
   * default number of ticks per wheel.
   */
  public HashedWheelTimer() {
      this(Executors.defaultThreadFactory());
  }

  /**
   * Creates a new timer with the default thread factory
   * ({@link Executors#defaultThreadFactory()}) and default number of ticks
   * per wheel.
   *
   * @param tickDuration   the duration between tick
   * @param unit           the time unit of the {@code tickDuration}
   */
  public HashedWheelTimer(long tickDuration, TimeUnit unit) {
      this(Executors.defaultThreadFactory(), tickDuration, unit);
  }

  /**
   * Creates a new timer with the default thread factory
   * ({@link Executors#defaultThreadFactory()}).
   *
   * @param tickDuration   the duration between tick
   * @param unit           the time unit of the {@code tickDuration}
   * @param ticksPerWheel  the size of the wheel
   */
  public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
      this(Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel);
  }

  /**
   * Creates a new timer with the default tick duration and default number of
   * ticks per wheel.
   *
   * @param threadFactory  a {@link ThreadFactory} that creates a
   *                       background {@link Thread} which is dedicated to
   *                       {@link TimerTask} execution.
   */
  public HashedWheelTimer(ThreadFactory threadFactory) {
      this(threadFactory, 100, TimeUnit.MILLISECONDS);
  }

  /**
   * Creates a new timer with the default number of ticks per wheel.
   *
   * @param threadFactory  a {@link ThreadFactory} that creates a
   *                       background {@link Thread} which is dedicated to
   *                       {@link TimerTask} execution.
   * @param tickDuration   the duration between tick
   * @param unit           the time unit of the {@code tickDuration}
   */
  public HashedWheelTimer(
          ThreadFactory threadFactory, long tickDuration, TimeUnit unit) {
      this(threadFactory, tickDuration, unit, 512);
  }

  /**
   * Creates a new timer.
   *
   * @param threadFactory  a {@link ThreadFactory} that creates a
   *                       background {@link Thread} which is dedicated to
   *                       {@link TimerTask} execution.
   * @param tickDuration   the duration between tick
   * @param unit           the time unit of the {@code tickDuration}
   * @param ticksPerWheel  the size of the wheel
   */
  public HashedWheelTimer(
          ThreadFactory threadFactory,
          long tickDuration, TimeUnit unit, int ticksPerWheel) {

  		super(Executors.newSingleThreadExecutor(threadFactory), unit.toMillis(tickDuration));

      // Misuse check
      misuseDetector.increase();
  }
}
