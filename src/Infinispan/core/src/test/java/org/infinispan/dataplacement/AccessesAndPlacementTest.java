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

import org.infinispan.commons.hash.Hash;
import org.infinispan.commons.hash.MurmurHash3;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.TestAddress;
import org.infinispan.distribution.ch.DefaultConsistentHash;
import org.infinispan.remoting.transport.Address;
import org.infinispan.stats.topK.StreamLibContainer;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.infinispan.dataplacement.AccessesManager.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test the functionality of the Remote Accesses Manager and the Object placement manager 
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
@Test(groups = "functional", testName = "dataplacement.AccessesAndPlacementTest")
public class AccessesAndPlacementTest {

   private static final Hash HASH = new MurmurHash3();
   private final AtomicInteger KEY_NEXT_ID = new AtomicInteger(0);

   public void testNoMovement() {
      ClusterSnapshot clusterSnapshot = createClusterSnapshot(4);
      ObjectPlacementManager manager = createObjectPlacementManager();
      manager.resetState(clusterSnapshot);
      Map<?, ?> newOwners = manager.calculateObjectsToMove();
      assert newOwners.isEmpty();
   }

   @SuppressWarnings("AssertWithSideEffects")
   public void testReturnValue() {
      ClusterSnapshot clusterSnapshot = createClusterSnapshot(2);
      ObjectPlacementManager manager = createObjectPlacementManager();
      manager.resetState(clusterSnapshot);

      assert !manager.aggregateRequest(clusterSnapshot.get(0), new ObjectRequest(null, null));
      assert manager.aggregateRequest(clusterSnapshot.get(1), new ObjectRequest(null, null));
      assert !manager.aggregateRequest(clusterSnapshot.get(0), new ObjectRequest(null, null));
      assert !manager.aggregateRequest(clusterSnapshot.get(1), new ObjectRequest(null, null));
   }

   public void testObjectPlacement() {
      ClusterSnapshot clusterSnapshot = createClusterSnapshot(4);
      ObjectPlacementManager manager = createObjectPlacementManager();
      manager.resetState(clusterSnapshot);

      Map<Object, Long> request = new HashMap<Object, Long>();

      TestKey key1 = new TestKey(1, clusterSnapshot.get(0), clusterSnapshot.get(1));
      TestKey key2 = new TestKey(2, clusterSnapshot.get(2), clusterSnapshot.get(3));

      request.put(key1, 1L);

      manager.aggregateRequest(clusterSnapshot.get(2), new ObjectRequest(request, null));

      request = new HashMap<Object, Long>();
      request.put(key1, 1L);

      manager.aggregateRequest(clusterSnapshot.get(3), new ObjectRequest(request, null));

      request = new HashMap<Object, Long>();
      request.put(key2, 1L);

      manager.aggregateRequest(clusterSnapshot.get(0), new ObjectRequest(request, null));

      request = new HashMap<Object, Long>();
      request.put(key2, 1L);

      manager.aggregateRequest(clusterSnapshot.get(1), new ObjectRequest(request, null));

      Map<Object, OwnersInfo> newOwners = manager.calculateObjectsToMove();

      assert newOwners.size() == 2;

      assertOwner(newOwners.get(key1), 2, 3);
      assertOwner(newOwners.get(key2), 0, 1);
   }

   public void testObjectPlacement2() {
      ClusterSnapshot clusterSnapshot = createClusterSnapshot(4);
      ObjectPlacementManager manager = createObjectPlacementManager();
      manager.resetState(clusterSnapshot);

      Map<Object, Long> remote = new HashMap<Object, Long>();
      Map<Object, Long> local = new HashMap<Object, Long>();

      TestKey key1 = new TestKey(1, clusterSnapshot.get(0), clusterSnapshot.get(1));
      TestKey key2 = new TestKey(2, clusterSnapshot.get(2), clusterSnapshot.get(3));

      remote.put(key2, 1L);
      local.put(key1, 4L);

      manager.aggregateRequest(clusterSnapshot.get(0), new ObjectRequest(remote, local));

      remote = new HashMap<Object, Long>();
      local = new HashMap<Object, Long>();

      remote.put(key2, 3L);
      local.put(key1, 1L);

      manager.aggregateRequest(clusterSnapshot.get(1), new ObjectRequest(remote, local));

      remote = new HashMap<Object, Long>();
      local = new HashMap<Object, Long>();

      remote.put(key1, 2L);
      local.put(key2, 4L);

      manager.aggregateRequest(clusterSnapshot.get(2), new ObjectRequest(remote, local));

      remote = new HashMap<Object, Long>();
      local = new HashMap<Object, Long>();

      remote.put(key1, 3L);
      local.put(key2, 2L);

      manager.aggregateRequest(clusterSnapshot.get(3), new ObjectRequest(remote, null));

      Map<Object, OwnersInfo> newOwners = manager.calculateObjectsToMove();

      assert newOwners.size() == 2;

      assertOwner(newOwners.get(key1), 0, 3);
      assertOwner(newOwners.get(key2), 1, 2);
   }

   public void testObjectPlacement3() {
      ClusterSnapshot clusterSnapshot = createClusterSnapshot(4);
      ObjectPlacementManager manager = createObjectPlacementManager();
      manager.resetState(clusterSnapshot);

      Map<Object, Long> request = new HashMap<Object, Long>();

      TestKey key = new TestKey(2, clusterSnapshot.get(2), clusterSnapshot.get(3));

      request.put(key, 2L);

      manager.aggregateRequest(clusterSnapshot.get(0), new ObjectRequest(request, null));

      request = new HashMap<Object, Long>();

      request.put(key, 3L);

      manager.aggregateRequest(clusterSnapshot.get(1), new ObjectRequest(request, null));

      request = new HashMap<Object, Long>();

      request.put(key, 5L);

      manager.aggregateRequest(clusterSnapshot.get(2), new ObjectRequest(null, request));

      request = new HashMap<Object, Long>();

      request.put(key, 6L);

      manager.aggregateRequest(clusterSnapshot.get(3), new ObjectRequest(null, request));

      Map<Object, OwnersInfo> newOwners = manager.calculateObjectsToMove();

      assert newOwners.isEmpty();
   }

   public void testRemoteAccesses() {
      ClusterSnapshot clusterSnapshot = createClusterSnapshot(4);
      AccessesManager manager = createRemoteAccessManager();
      manager.resetState(clusterSnapshot);
      StreamLibContainer container = StreamLibContainer.getInstance();
      container.setActive(true);
      container.setCapacity(2);
      container.resetAll();

      TestKey key1 = new TestKey(1, clusterSnapshot.get(0), clusterSnapshot.get(1));
      TestKey key2 = new TestKey(2, clusterSnapshot.get(1), clusterSnapshot.get(2));
      TestKey key3 = new TestKey(3, clusterSnapshot.get(2), clusterSnapshot.get(3));
      TestKey key4 = new TestKey(4, clusterSnapshot.get(3), clusterSnapshot.get(0));

      addKey(key1, false, 10, container);
      addKey(key2, true, 5, container);
      addKey(key3, true, 15, container);
      addKey(key4, false, 2, container);

      manager.calculateAccesses();

      Map<Object, Long> remote = new HashMap<Object, Long>();
      Map<Object, Long> local = new HashMap<Object, Long>();

      local.put(key1, 10L);

      assertAccesses(manager.getObjectRequestForAddress(clusterSnapshot.get(0)), remote, local);

      remote.clear();
      local.clear();

      remote.put(key2, 5L);

      assertAccesses(manager.getObjectRequestForAddress(clusterSnapshot.get(1)), remote, local);

      remote.clear();
      local.clear();

      remote.put(key3, 15L);

      assertAccesses(manager.getObjectRequestForAddress(clusterSnapshot.get(2)), remote, local);

      remote.clear();
      local.clear();

      local.put(key4, 2L);

      assertAccesses(manager.getObjectRequestForAddress(clusterSnapshot.get(3)), remote, local);
   }

   public void testRemoteTopKeyRequest() {
      RemoteTopKeyRequest request = new RemoteTopKeyRequest(10);

      TestKey key1 = createRandomKey();
      TestKey key2 = createRandomKey();
      TestKey key3 = createRandomKey();
      TestKey key4 = createRandomKey();

      Map<Object, Long> counter = new HashMap<Object, Long>();
      counter.put(key1, 1L);
      counter.put(key2, 1L);
      counter.put(key3, 1L);
      counter.put(key4, 1L);

      request.merge(counter, 1);

      assertKeyAccess(key1, request, 1);
      assertKeyAccess(key2, request, 1);
      assertKeyAccess(key3, request, 1);
      assertKeyAccess(key4, request, 1);

      counter.put(key1, 1L);
      counter.put(key2, 2L);
      counter.put(key3, 3L);
      counter.put(key4, 4L);

      request.merge(counter, 2);

      assertKeyAccess(key1, request, 3);
      assertKeyAccess(key2, request, 5);
      assertKeyAccess(key3, request, 7);
      assertKeyAccess(key4, request, 9);

      assertSortedKeyAccess(key1, request, 3, 3);
      assertSortedKeyAccess(key2, request, 5, 2);
      assertSortedKeyAccess(key3, request, 7, 1);
      assertSortedKeyAccess(key4, request, 9, 0);

      assertSortedKeyAccess(request);

      counter.clear();

      TestKey[] keys = new TestKey[11];

      for (int i = 0; i < keys.length; ++i) {
         TestKey key = createRandomKey();
         keys[i] = key;
         counter.put(key, (long) i);
      }

      request.merge(counter, 1);

      assertKeyAccess(key1, request, 3);
      assertKeyAccess(key2, request, 5);
      assertKeyAccess(key3, request, 7);
      assertKeyAccess(key4, request, 9);

      for (int i = 1; i < keys.length; ++i) {
         assertKeyAccess(keys[i], request, i);
      }

      int idx = 0;
      assertSortedKeyAccess(keys[10], request, 10, idx++);
      assertSortedKeyAccess(keys[9], request, 9, idx++);
      assertSortedKeyAccess(key4, request, 9, idx++);
      assertSortedKeyAccess(keys[8], request, 8, idx++);
      assertSortedKeyAccess(keys[7], request, 7, idx++);
      assertSortedKeyAccess(key3, request, 7, idx++);
      assertSortedKeyAccess(keys[6], request, 6, idx++);
      assertSortedKeyAccess(keys[5], request, 5, idx++);
      assertSortedKeyAccess(key2, request, 5, idx++);
      assertSortedKeyAccess(keys[4], request, 4, idx++);
      assertSortedKeyAccess(keys[3], request, 3, idx++);
      assertSortedKeyAccess(key1, request, 3, idx++);
      assertSortedKeyAccess(keys[2], request, 2, idx++);
      assertSortedKeyAccess(keys[1], request, 1, idx);

      assertSortedKeyAccess(request);

      assertNoKeyAccess(keys[0], request);
   }

   public void testLocalTopKeyRequest() {
      LocalTopKeyRequest request = new LocalTopKeyRequest();

      TestKey key1 = createRandomKey();
      TestKey key2 = createRandomKey();
      TestKey key3 = createRandomKey();
      TestKey key4 = createRandomKey();

      Map<Object, Long> counter = new HashMap<Object, Long>();
      counter.put(key1, 1L);
      counter.put(key2, 1L);
      counter.put(key3, 1L);
      counter.put(key4, 1L);

      request.merge(counter, 1);

      assertKeyAccess(key1, request, 1);
      assertKeyAccess(key2, request, 1);
      assertKeyAccess(key3, request, 1);
      assertKeyAccess(key4, request, 1);

      counter.put(key1, 1L);
      counter.put(key2, 2L);
      counter.put(key3, 3L);
      counter.put(key4, 4L);

      request.merge(counter, 2);

      assertKeyAccess(key1, request, 3);
      assertKeyAccess(key2, request, 5);
      assertKeyAccess(key3, request, 7);
      assertKeyAccess(key4, request, 9);

      counter.clear();

      TestKey[] keys = new TestKey[11];

      for (int i = 0; i < keys.length; ++i) {
         TestKey key = createRandomKey();
         keys[i] = key;
         counter.put(key, (long) i);
      }

      request.merge(counter, 1);

      assertKeyAccess(key1, request, 3);
      assertKeyAccess(key2, request, 5);
      assertKeyAccess(key3, request, 7);
      assertKeyAccess(key4, request, 9);

      for (int i = 1; i < keys.length; ++i) {
         assertKeyAccess(keys[i], request, i);
      }

      assertNoKeyAccess(keys[0], request);
   }

   private void assertNoKeyAccess(Object key, RemoteTopKeyRequest request) {
      assert !request.contains(key) : "Key " + key + " has found in map";
      for (KeyAccess keyAccess : request.getSortedKeyAccess()) {
         assert !keyAccess.getKey().equals(key)  : "Key " + key + " has found in list";
      }
   }

   private void assertNoKeyAccess(Object key, LocalTopKeyRequest request) {
      assert !request.contains(key) : "Key " + key + " has found";
   }

   private void assertKeyAccess(Object key, RemoteTopKeyRequest request, long accesses) {
      assert request.contains(key) :  "Key " + key + " not found";
      KeyAccess keyAccess = request.get(key);
      assert keyAccess.getAccesses() == accesses : "Number of accesses: " + keyAccess.getAccesses() + " != " + accesses;
      assert keyAccess.getKey().equals(key) : "Key is different: " + keyAccess.getKey() + " != " + key;
   }

   private void assertKeyAccess(Object key, LocalTopKeyRequest request, long accesses) {
      assert request.contains(key) :  "Key " + key + " not found";
      KeyAccess keyAccess = request.get(key);
      assert keyAccess.getAccesses() == accesses : "Number of accesses: " + keyAccess.getAccesses() + " != " + accesses;
      assert keyAccess.getKey().equals(key) : "Key is different: " + keyAccess.getKey() + " != " + key;
   }

   private void assertSortedKeyAccess(Object key, RemoteTopKeyRequest request, long accesses, int pos) {
      List<KeyAccess> keyAccessList = request.getSortedKeyAccess();
      assert keyAccessList.size() > pos : "Size (" + keyAccessList.size() + ") is smaller than " + pos;
      assert keyAccessList.get(pos).getKey().equals(key) : "Key is different [" + pos + "]: " +
            keyAccessList.get(pos).getKey() + " != " + key;
      assert keyAccessList.get(pos).getAccesses() == accesses : "Number of accesses [" + pos + "]: " +
            keyAccessList.get(pos).getAccesses() + " != " + accesses;
   }

   private void assertSortedKeyAccess(RemoteTopKeyRequest request) {
      List<KeyAccess> keyAccessList = request.getSortedKeyAccess();
      if (keyAccessList.isEmpty()) {
         return;
      }

      long maxValue = keyAccessList.get(0).getAccesses();

      for (KeyAccess keyAccess : keyAccessList) {
         assert keyAccess.getAccesses() <= maxValue : "Different order: " + keyAccess.getAccesses()  + " > " + maxValue;
         maxValue = keyAccess.getAccesses();
      }
   }

   private TestKey createRandomKey() {
      return new TestKey(KEY_NEXT_ID.incrementAndGet(), new TestAddress(0), new TestAddress(1));
   }

   private void assertAccesses(ObjectRequest request, Map<Object, Long> remote, Map<Object, Long> local) {
      Map<Object, Long> remoteAccesses = request.getRemoteAccesses();
      Map<Object, Long> localAccesses = request.getLocalAccesses();

      assert remoteAccesses.size() == remote.size();
      assert localAccesses.size() == local.size();

      for (Map.Entry<Object, Long> entry: remote.entrySet()) {
         long value1 = entry.getValue();
         long value2 = remoteAccesses.get(entry.getKey());

         assert value1 == value2;
      }

      for (Map.Entry<Object, Long> entry: local.entrySet()) {
         long value1 = entry.getValue();
         long value2 = localAccesses.get(entry.getKey());

         assert value1 == value2;
      }
   }

   private void addKey(Object key, boolean remote, int count, StreamLibContainer container) {
      for (int i = 0; i < count; ++i) {
         container.addGet(key, remote);
      }
   }

   private void assertOwner(OwnersInfo ownersInfo, Integer... newOwners) {
      assert ownersInfo != null;

      List<Integer> expectedOwners = Arrays.asList(newOwners);
      List<Integer> owners = ownersInfo.getNewOwnersIndexes();

      assert expectedOwners.size() == owners.size();
      assert expectedOwners.containsAll(owners);
   }

   private ClusterSnapshot createClusterSnapshot(int size) {
      List<Address> members = new ArrayList<Address>(size);
      for (int i = 0; i < size; ++i) {
         members.add(new TestAddress(i));
      }
      return new ClusterSnapshot(members.toArray(new Address[size]), HASH);
   }

   private ObjectPlacementManager createObjectPlacementManager() {
      return new ObjectPlacementManager(getMockDistributionManager(), new MurmurHash3(), 2);
   }

   private AccessesManager createRemoteAccessManager() {
      return new AccessesManager(getMockDistributionManager(), 1000);
   }

   private DistributionManager getMockDistributionManager() {
      DefaultConsistentHash consistentHash = mock(DefaultConsistentHash.class);
      when(consistentHash.locate(isA(TestKey.class), anyInt())).thenAnswer(new Answer<List<Address>>() {
         @Override
         public List<Address> answer(InvocationOnMock invocationOnMock) throws Throwable {
            return new LinkedList<Address>(((TestKey) invocationOnMock.getArguments()[0]).getOwners());
         }
      });

      when(consistentHash.locateAll(anyCollectionOf(Object.class), anyInt())).thenAnswer(new Answer<Object>() {
         @SuppressWarnings("unchecked")
         @Override
         public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
            Collection<Object> keys = (Collection<Object>) invocationOnMock.getArguments()[0];

            Map<Object, List<Address>> addresses = new HashMap<Object, List<Address>>();
            for (Object key : keys) {
               if (key instanceof TestKey) {
                  addresses.put(key, new LinkedList<Address>(((TestKey) key).getOwners()));
               }
            }

            return addresses;
         }
      });

      DistributionManager distributionManager = mock(DistributionManager.class);
      when(distributionManager.locate(isA(TestKey.class))).thenAnswer(new Answer<List<Address>>() {
         @Override
         public List<Address> answer(InvocationOnMock invocationOnMock) throws Throwable {
            return new LinkedList<Address>(((TestKey) invocationOnMock.getArguments()[0]).getOwners());
         }
      });
      when(distributionManager.getConsistentHash()).thenReturn(consistentHash);
      return distributionManager;
   }

   private class TestKey {

      private final Collection<Address> owners;
      private final int id;

      private TestKey(int id, Address... owners) {
         this.id = id;
         this.owners = Arrays.asList(owners);
      }

      public Collection<Address> getOwners() {
         return owners;
      }

      public int getId() {
         return id;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         TestKey testKey = (TestKey) o;

         return id == testKey.id;

      }

      @Override
      public int hashCode() {
         return id;
      }

      @Override
      public String toString() {
         return "TestKey{" +
               "id=" + id +
               '}';
      }
   }

}
