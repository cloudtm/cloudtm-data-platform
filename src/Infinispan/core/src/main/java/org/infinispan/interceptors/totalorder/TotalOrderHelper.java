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

import org.infinispan.CacheException;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.remoting.responses.AllResponsesFilter;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.KeyValidationFilter;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.responses.SelfDeliverFilter;
import org.infinispan.remoting.rpc.ResponseFilter;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.StateTransferException;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.Collection;
import java.util.Map;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 4.0
 */
public class TotalOrderHelper {

   private static final Log log = LogFactory.getLog(TotalOrderHelper.class);

   public static Object prepare(TxInvocationContext txInvocationContext, PrepareCommand prepareCommand,
                                TotalOrderRpcInterceptor interceptor) throws Throwable {
      if (log.isTraceEnabled()) {
         log.tracef("Preparing transaction %s", prepareCommand.getGlobalTransaction().prettyPrint());
      }
      try {
         Object result = interceptor.visitPrepare(txInvocationContext, prepareCommand);
         if (log.isTraceEnabled()) {
            log.tracef("Successful prepare of transaction %s", prepareCommand.getGlobalTransaction().prettyPrint());
         }
         return result;
      } catch (CacheException cacheException) {
         Throwable cause = cacheException.getCause();
         if (cause instanceof StateTransferException) {
            if (log.isTraceEnabled()) {
               log.tracef("Transaction %s should be re-prepare", prepareCommand.getGlobalTransaction().prettyPrint());
            }
            throw (StateTransferException) cause;
         } else {
            if (log.isTraceEnabled()) {
               log.tracef("Error preparing transaction %s: %s", prepareCommand.getGlobalTransaction().prettyPrint(),
                          cacheException.getCause());
            }
            throw cacheException;
         }
      }
   }

   public static Collection<Response> totalOrderBroadcastPrepare(PrepareCommand prepareCommand,
                                                                 Collection<Address> target,
                                                                 Collection<Object> keysToValidate,
                                                                 RpcManager rpcManager, boolean waitOnlySelfDeliver,
                                                                 boolean syncCommitPhase, long timeout) {
      ResponseFilter filter;
      if (waitOnlySelfDeliver) {
         filter = new SelfDeliverFilter(rpcManager.getAddress());
      } else if (keysToValidate != null && !syncCommitPhase) {
         filter = new KeyValidationFilter(keysToValidate, rpcManager.getAddress());
      } else {
         filter = new AllResponsesFilter();
      }

      if (log.isTraceEnabled()) {
         log.tracef("Filter used by transaction %s is %s", prepareCommand.getGlobalTransaction().prettyPrint(), filter);
      }

      Map<Address, Response> responses = rpcManager.invokeRemotely(target, prepareCommand, ResponseMode.SYNCHRONOUS,
                                                                   timeout, true, filter, true);
      for (Response  response : responses.values()) {
         if (response instanceof ExceptionResponse) {
            Exception e = ((ExceptionResponse) response).getException();
            if (e instanceof CacheException) {
               throw (CacheException) e;
            } else {
               throw new CacheException(e);
            }
         }
      }
      return responses.values();
   }

   public static interface TotalOrderRpcInterceptor {

      Object visitPrepare(TxInvocationContext context, PrepareCommand command) throws Throwable;

   }

}
