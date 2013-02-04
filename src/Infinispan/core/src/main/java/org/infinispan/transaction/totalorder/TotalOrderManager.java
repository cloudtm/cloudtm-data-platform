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
package org.infinispan.transaction.totalorder;

import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.container.versioning.EntryVersionsMap;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.transaction.LocalTransaction;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.transaction.TxDependencyLatch;
import org.infinispan.transaction.xa.GlobalTransaction;

import java.util.Set;

/**
 * This class is responsible to validate transactions in the total order based protocol. It ensures the delivered order
 * and will validate multiple transactions in parallel if they are non conflicting transaction.
 *
 * @author Pedro Ruivo
 * @author mircea.markus@jboss.com
 * @since 5.2.0
 */
public interface TotalOrderManager {

   /**
    * Processes the transaction as received from the sequencer.
    *
    * @return the result of the transaction
    */
   Object processTransactionFromSequencer(PrepareCommand prepareCommand, TxInvocationContext ctx, CommandInterceptor invoker) throws Throwable;

   /**
    * This will mark a global transaction as finished. It will be invoked in the processing of the commit command in
    * repeatable read with write skew (not implemented yet!)
    */
   void finishTransaction(GlobalTransaction gtx, boolean ignoreNullTxInfo, RemoteTransaction transaction);

   /**
    * This ensures the order between the commit/rollback commands and the prepare command.
    * <p/>
    * However, if the commit/rollback command is deliver first, then they don't need to wait until the prepare is
    * deliver. The mark the remote transaction for commit or rollback and when the prepare arrives, it adapts its
    * behaviour: -> if it must rollback, the prepare is discarded (no needing for processing) -> if it must commit, then
    * it sets the one phase flag and wait for this turn, committing the modifications and it skips the write skew check
    * (note: the commit command saves the new versions in remote transaction)
    * <p/>
    * If the prepare is already in process, then the commit/rollback is blocked until the validation is finished.
    *
    * @param commit            true if it is a commit command, false if it is a rollback command
    * @return true if the command needs to be processed, false otherwise
    */
   boolean waitForTxPrepared(RemoteTransaction remoteTransaction, boolean commit, EntryVersionsMap newVersions);

   /**
    * Adds a local transaction to the map. Later, it will be notified when the modifications are applied in the data
    * container
    */
   void addLocalTransaction(GlobalTransaction globalTransaction, LocalTransaction localTransaction);

   /**
    * returns a set of the transaction dependency latch that are actually committing
    *
    * @return  a set of the transaction dependency latch that are actually committing
    */
   Set<TxDependencyLatch> getPendingCommittingTransaction();

   /**
    * @param globalTransaction   the global transaction
    * @return                    returns true if the transaction represented by {@param globalTransaction} is
    *                            originated locally
    */
   boolean isCoordinatedLocally(GlobalTransaction globalTransaction);
}
