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
package org.infinispan.interceptors.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.VersionedPrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.VersionedReplicationInterceptor;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import static org.infinispan.interceptors.totalorder.TotalOrderHelper.TotalOrderRpcInterceptor;
import static org.infinispan.interceptors.totalorder.TotalOrderHelper.prepare;
import static org.infinispan.interceptors.totalorder.TotalOrderHelper.totalOrderBroadcastPrepare;
import static org.infinispan.transaction.WriteSkewHelper.setVersionsSeenOnPrepareCommand;

/**
 * Replication Interceptor for Total Order protocol with versioning.
 *
 * @author Pedro Ruivo
 * @author Mircea.Markus@jboss.com
 * @since 5.2
 */
public class TotalOrderVersionedReplicationInterceptor extends VersionedReplicationInterceptor implements TotalOrderRpcInterceptor {

   private static final Log log = LogFactory.getLog(TotalOrderVersionedReplicationInterceptor.class);

   @Override
   public final Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      return prepare(ctx, command, this);
   }

   @Override
   public Object visitPrepare(TxInvocationContext context, PrepareCommand command) throws Throwable {
      return super.visitPrepareCommand(context, command);
   }

   @Override
   protected void broadcastPrepare(TxInvocationContext ctx, PrepareCommand command) {

      if (!(command instanceof VersionedPrepareCommand)) {
         throw new IllegalStateException("Expected a Versioned Prepare Command in version aware component");
      }

      if (log.isTraceEnabled())
         log.tracef("Broadcasting prepare for transaction %s with total order", command.getGlobalTransaction());

      setVersionsSeenOnPrepareCommand((VersionedPrepareCommand) command, ctx);
      boolean waitOnlySelfDeliver =!configuration.isSyncCommitPhase();
      totalOrderBroadcastPrepare(command, null, null, rpcManager, waitOnlySelfDeliver,
                                 configuration.isSyncCommitPhase(), configuration.getSyncReplTimeout());
   }
}
