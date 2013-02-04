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
package org.infinispan.dataplacement;

import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.DistributedStateTransferManagerImpl;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.BitSet;

/**
 * Collects all the Object Lookup from all the members. In the coordinator side, it collects all the acks before
 * triggering the state transfer
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ObjectLookupManager {

   private static final Log log = LogFactory.getLog(ObjectLookupManager.class);

   private ClusterSnapshot clusterSnapshot;

   //the state transfer manager
   private final DistributedStateTransferManagerImpl stateTransfer;

   private final BitSet objectLookupReceived;

   private final BitSet acksReceived;

   public ObjectLookupManager(DistributedStateTransferManagerImpl stateTransfer) {
      this.stateTransfer = stateTransfer;
      objectLookupReceived = new BitSet();
      acksReceived = new BitSet();
   }

   /**
    * reset the state (before each round)
    *
    * @param roundClusterSnapshot the current cluster members                    
    */
   public final synchronized void resetState(ClusterSnapshot roundClusterSnapshot) {
      clusterSnapshot = roundClusterSnapshot;
      objectLookupReceived.clear();
      acksReceived.clear();
      stateTransfer.createDataPlacementConsistentHashing(clusterSnapshot);
   }

   /**
    * add a new Object Lookup from a member
    *
    * Note: it only returns true on the first time that it is ready to the stat transfer. the following
    *       invocations return false
    *
    * @param from          the creator member
    * @param objectLookup  the Object Lookup instance
    * @return              true if it has all the object lookup, false otherwise (see Note)
    */
   public final synchronized boolean addObjectLookup(Address from, ObjectLookup objectLookup) {
      if (hasAllObjectLookup()) {
         return false;
      }

      int senderId = clusterSnapshot.indexOf(from);

      if (senderId < 0) {
         log.warnf("Receive an object lookup from %s but it is not in members list %s", from, clusterSnapshot);
         return false;
      }

      stateTransfer.addObjectLookup(from, objectLookup);
      objectLookupReceived.set(senderId);

      logObjectLookupReceived(from, objectLookup);

      return hasAllObjectLookup();
   }

   /**
    * add an ack from a member
    *
    * Note: it only returns true once, when it has all the acks for the first time
    *
    * @param from the sender
    * @return     true if it is has all the acks, false otherwise (see Note)    
    */
   public final synchronized boolean addAck(Address from) {
      if (hasAllAcks()) {
         return false;
      }

      int senderId = clusterSnapshot.indexOf(from);

      if (senderId < 0) {
         log.warnf("Receive an ack from %s but it is not in members list %s", from, clusterSnapshot);
         return false;
      }

      acksReceived.set(senderId);

      logAckReceived(from);

      return hasAllAcks();
   }

   /**
    * returns true if it has all the Object Lookup from all members
    *
    * @return  true if it has all the Object Lookup from all members
    */
   private boolean hasAllObjectLookup() {
      return clusterSnapshot.size() == objectLookupReceived.cardinality();
   }

   /**
    * returns true if it has all the acks from all members
    *
    * @return  true if it has all the acks from all members
    */
   private boolean hasAllAcks() {
      return clusterSnapshot.size() == acksReceived.cardinality();
   }

   private void logObjectLookupReceived(Address from, ObjectLookup objectLookup) {
      if (log.isTraceEnabled()) {
         StringBuilder missingMembers = new StringBuilder();

         for (int i = 0; i < clusterSnapshot.size(); ++i) {
            if (!objectLookupReceived.get(i)) {
               missingMembers.append(clusterSnapshot.get(i)).append(" ");
            }
         }
         log.debugf("Objects lookup received from %s. Missing objects lookup are %s. Objects lookup received is %s",
                    from, missingMembers, objectLookup);
      } else if (log.isDebugEnabled()) {
         log.debugf("Objects lookup received from %s. Missing objects lookup are %s",
                    from, (clusterSnapshot.size() - objectLookupReceived.cardinality()));
      }
   }

   private void logAckReceived(Address from) {
      if (log.isDebugEnabled()) {
         StringBuilder missingMembers = new StringBuilder();

         for (int i = 0; i < clusterSnapshot.size(); ++i) {
            if (!acksReceived.get(i)) {
               missingMembers.append(clusterSnapshot.get(i)).append(" ");
            }
         }
         log.debugf("Ack received from %s. Missing ack are %s", from, missingMembers);
      }
   }
}
