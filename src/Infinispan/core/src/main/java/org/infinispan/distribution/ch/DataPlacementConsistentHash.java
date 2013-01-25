package org.infinispan.distribution.ch;

import org.infinispan.dataplacement.ClusterSnapshot;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.remoting.transport.Address;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * The consistent hash function implementation that the Object Lookup implementations from the Data Placement
 * optimization
 *
 * @author Zhongmiao Li
 * @author João Paiva
 * @author Pedro Ruivo
 * @since 5.2
 */
public class DataPlacementConsistentHash extends AbstractConsistentHash {

   private final ObjectLookup[] objectsLookup;
   private final ClusterSnapshot clusterSnapshot;
   private ConsistentHash defaultConsistentHash;

   public DataPlacementConsistentHash(ClusterSnapshot clusterSnapshot) {
      this.clusterSnapshot = clusterSnapshot;
      objectsLookup = new ObjectLookup[clusterSnapshot.size()];
   }

   public void addObjectLookup(Address address, ObjectLookup objectLookup) {
      if (objectLookup == null) {
         return;
      }
      int index = clusterSnapshot.indexOf(address);
      if (index == -1) {
         return;
      }
      objectsLookup[index] = objectLookup;
   }

   public void setDefault(ConsistentHash defaultHash) {
      defaultConsistentHash = defaultHash;
   }

   @Override
   public void setCaches(Set<Address> caches) {
      defaultConsistentHash.setCaches(caches);
   }

   public ConsistentHash getDefaultHash() {
      return defaultConsistentHash;
   }

   @Override
   public Set<Address> getCaches() {
      return defaultConsistentHash.getCaches();
   }

   @Override
   public List<Address> locate(Object key, int replCount) {
      List<Address> defaultOwners = defaultConsistentHash.locate(key, replCount);
      int primaryOwnerIndex = clusterSnapshot.indexOf(defaultOwners.get(0));

      if (primaryOwnerIndex == -1) {
         return defaultOwners;
      }

      ObjectLookup lookup = objectsLookup[primaryOwnerIndex];

      if (lookup == null) {
         return defaultOwners;
      }

      List<Integer> newOwners = lookup.query(key);

      if (newOwners == null) {
         return defaultOwners;
      }

      LinkedList<Address> ownersAddress = new LinkedList<Address>();
      for (int index : newOwners) {
         Address owner = clusterSnapshot.get(index);
         if (owner == null) {
            continue;
         }
         ownersAddress.add(owner);
      }

      if (ownersAddress.size() > replCount) {
         while (ownersAddress.size() > replCount) {
            ownersAddress.removeLast();
         }
      } else if (ownersAddress.size() < replCount) {
         Iterator<Address> iterator = defaultOwners.iterator();
         while (ownersAddress.size() < replCount && iterator.hasNext()) {
            Address address = iterator.next();
            if (ownersAddress.contains(address)) {
               continue;
            }
            ownersAddress.add(address);
         }
      }

      return ownersAddress;
   }

   @Override
   public List<Integer> getHashIds(Address a) {
      return Collections.emptyList();
   }


}
