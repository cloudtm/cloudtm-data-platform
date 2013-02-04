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
package org.infinispan.stats.topK;

import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.base.BaseCustomInterceptor;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;
import org.infinispan.transaction.WriteSkewException;
import org.rhq.helpers.pluginAnnotations.agent.Operation;

import java.util.Map;

/**
 * @author Pedro Ruivo
 * @since 5.2
 */
@MBean(objectName = "StreamLibStatistics", description = "Show analytics for workload monitor")
public class StreamLibInterceptor extends BaseCustomInterceptor {

   private static final StreamLibContainer streamLibContainer = StreamLibContainer.getInstance();
   private boolean statisticEnabled = false;

   @Override
   protected void start() {
      super.start();
      setStatisticsEnabled(true);
   }

   protected boolean isRemote(Object k){
      return false;
   }

   @Override
   public Object visitGetKeyValueCommand(InvocationContext ctx, GetKeyValueCommand command) throws Throwable {

      if(statisticEnabled && ctx.isOriginLocal() && ctx.isInTxScope()) {
         streamLibContainer.addGet(command.getKey(), isRemote(command.getKey()));
      }
      return invokeNextInterceptor(ctx, command);
   }

   @Override
   public Object visitPutKeyValueCommand(InvocationContext ctx, PutKeyValueCommand command) throws Throwable {
      try {
      if(statisticEnabled && ctx.isOriginLocal() && ctx.isInTxScope()) {
         streamLibContainer.addPut(command.getKey(), isRemote(command.getKey()));
      }
      return invokeNextInterceptor(ctx, command);
      } catch (WriteSkewException wse) {
         Object key = wse.getKey();
         if (key != null && ctx.isOriginLocal()) {
            streamLibContainer.addWriteSkewFailed(key);
         }
         throw wse;
      }
   }

   @Override
   public Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      try {
         return invokeNextInterceptor(ctx, command);
      } catch (WriteSkewException wse) {
         Object key = wse.getKey();
         if (key != null && ctx.isOriginLocal()) {
            streamLibContainer.addWriteSkewFailed(key);
         }
         throw wse;
      }
   }

   @ManagedOperation(description = "Resets statistics gathered by this component")
   @Operation(displayName = "Reset Statistics (Statistics)")
   public void resetStatistics() {
      streamLibContainer.resetAll();
   }

   @ManagedOperation(description = "Set K for the top-K values")
   @Operation(displayName = "Set K")
   public void setTopKValue(int value) {
      streamLibContainer.setCapacity(value);
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys most read remotely by this instance")
   @Operation(displayName = "Top Remote Read Keys")
   public Map<Object, Long> getRemoteTopGets() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.REMOTE_GET);
   }

   @ManagedOperation(description = "Show the top n keys most read remotely by this instance")
   @Operation(displayName = "Top Remote Read Keys")
   public Map<Object, Long> getNRemoteTopGets(int n) {
      Map<Object, Long> res = streamLibContainer.getTopKFrom(StreamLibContainer.Stat.REMOTE_GET, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.REMOTE_GET);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys most read locally by this instance")
   @Operation(displayName = "Top Local Read Keys")
   public Map<Object, Long> getLocalTopGets() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.LOCAL_GET);
   }

   @ManagedOperation(description = "Show the top n keys most read locally by this instance")
   @Operation(displayName = "Top Local Read Keys")
   public Map<Object, Long> getNLocalTopGets(int n) {
      Map<Object, Long> res =  streamLibContainer.getTopKFrom(StreamLibContainer.Stat.LOCAL_GET, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.LOCAL_GET);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys most write remotely by this instance")
   @Operation(displayName = "Top Remote Write Keys")
   public Map<Object, Long> getRemoteTopPuts() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.REMOTE_PUT);
   }

   @ManagedOperation(description = "Show the top n keys most write remotely by this instance")
   @Operation(displayName = "Top Remote Write Keys")
   public Map<Object, Long> getNRemoteTopPuts(int n) {
      Map<Object, Long> res =  streamLibContainer.getTopKFrom(StreamLibContainer.Stat.REMOTE_PUT, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.REMOTE_PUT);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys most write locally by this instance")
   @Operation(displayName = "Top Local Write Keys")
   public Map<Object, Long> getLocalTopPuts() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.LOCAL_PUT);
   }

   @ManagedOperation(description = "Show the top n keys most write locally by this instance")
   @Operation(displayName = "Top Local Write Keys")
   public Map<Object, Long> getNLocalTopPuts(int n) {
      Map<Object, Long> res  = streamLibContainer.getTopKFrom(StreamLibContainer.Stat.LOCAL_PUT, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.LOCAL_PUT);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys most locked")
   @Operation(displayName = "Top Locked Keys")
   public Map<Object, Long> getTopLockedKeys() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_LOCKED_KEYS);
   }

   @ManagedOperation(description = "Show the top n keys most locked")
   @Operation(displayName = "Top Locked Keys")
   public Map<Object, Long> getNTopLockedKeys(int n) {
      Map<Object, Long> res = streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_LOCKED_KEYS, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.MOST_LOCKED_KEYS);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys most contended")
   @Operation(displayName = "Top Contended Keys")
   public Map<Object, Long> getTopContendedKeys() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_CONTENDED_KEYS);
   }

   @ManagedOperation(description = "Show the top n keys most contended")
   @Operation(displayName = "Top Contended Keys")
   public Map<Object, Long> getNTopContendedKeys(int n) {
      Map<Object, Long> res = streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_CONTENDED_KEYS, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.MOST_CONTENDED_KEYS);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys whose lock acquisition failed by timeout")
   @Operation(displayName = "Top Keys whose Lock Acquisition Failed by Timeout")
   public Map<Object, Long> getTopLockFailedKeys() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_FAILED_KEYS);
   }

   @ManagedOperation(description = "Show the top n keys whose lock acquisition failed ")
   @Operation(displayName = "Top Keys whose Lock Acquisition Failed by Timeout")
   public Map<Object, Long> getNTopLockFailedKeys(int n) {
      Map<Object, Long> res = streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_FAILED_KEYS, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.MOST_FAILED_KEYS);
      return res;
   }

   @ManagedAttribute(description = "Show the top " + StreamLibContainer.MAX_CAPACITY + " keys whose write skew check was failed")
   @Operation(displayName = "Top Keys whose Write Skew Check was failed")
   public Map<Object, Long> getTopWriteSkewFailedKeys() {
      return streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_WRITE_SKEW_FAILED_KEYS);
   }

   @ManagedOperation(description = "Show the top n keys whose write skew check was failed")
   @Operation(displayName = "Top Keys whose Write Skew Check was failed")
   public Map<Object, Long> getNTopWriteSkewFailedKeys(int n) {
      Map<Object, Long> res = streamLibContainer.getTopKFrom(StreamLibContainer.Stat.MOST_WRITE_SKEW_FAILED_KEYS, n);
      streamLibContainer.resetStat(StreamLibContainer.Stat.MOST_WRITE_SKEW_FAILED_KEYS);
      return res;
   }

   @ManagedOperation(description = "Show the top n keys whose write skew check was failed")
   @Operation(displayName = "Top Keys whose Write Skew Check was failed")
   public void setStatisticsEnabled(boolean enabled) {
      statisticEnabled = true;
      streamLibContainer.setActive(enabled);
   }
}
