/*
 * INESC-ID, Instituto de Engenharia de Sistemas e Computadores Investigação e Desevolvimento em Lisboa
 * Copyright 2013 INESC-ID and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.util.executor;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.executors.ConditionalExecutorService;
import org.infinispan.executors.ConditionalRunnable;
import org.infinispan.test.AbstractInfinispanTest;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
@Test(groups = "functional", testName = "util.executor.ConditionalExecutorServiceTest")
public class ConditionalExecutorServiceTest extends AbstractInfinispanTest {

   public void simpleTest() throws Exception {
      ConditionalExecutorService executorService = createExecutorService();
      try {
         executorService.start();
         final DoSomething doSomething = new DoSomething();
         executorService.execute(doSomething);

         Thread.sleep(100);

         assert !doSomething.isReady();
         assert !doSomething.isExecuted();

         doSomething.markReady();

         assert doSomething.isReady();

         eventually(new Condition() {
            @Override
            public boolean isSatisfied() throws Exception {
               return doSomething.isExecuted();
            }
         });
      } finally {
         executorService.stop();
      }
   }

   public void simpleTest2() throws Exception {
      ConditionalExecutorService executorService = createExecutorService();
      try {
         executorService.start();
         List<DoSomething> tasks = new LinkedList<DoSomething>();

         for (int i = 0; i < 30; ++i) {
            tasks.add(new DoSomething());
         }

         for (DoSomething doSomething : tasks) {
            executorService.execute(doSomething);
         }

         for (DoSomething doSomething : tasks) {
            assert !doSomething.isReady();
            assert !doSomething.isExecuted();
         }

         for (DoSomething doSomething : tasks) {
            doSomething.markReady();
         }

         for (final DoSomething doSomething : tasks) {
            eventually(new Condition() {
               @Override
               public boolean isSatisfied() throws Exception {
                  return doSomething.isExecuted();
               }
            });
         }

      } finally {
         executorService.stop();
      }
   }

   private Configuration createConfiguration() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder.conditionalExecutorService().corePoolSize(1)
            .maxPoolSize(8)
            .keepAliveTime(10000);
      return builder.build();
   }

   private ConditionalExecutorService createExecutorService() {
      ConditionalExecutorService executorService = new ConditionalExecutorService();
      executorService.inject(createConfiguration(), null);
      return executorService;
   }

   public class DoSomething implements ConditionalRunnable {

      private volatile boolean ready = false;
      private volatile boolean executed = false;

      @Override
      public synchronized final boolean isReady() {
         return ready;
      }

      @Override
      public synchronized final void run() {
         executed = true;
      }

      public synchronized final void markReady() {
         ready = true;
      }

      public synchronized final boolean isExecuted() {
         return executed;
      }
   }
}
