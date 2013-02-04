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

import org.infinispan.dataplacement.c50.lookup.BloomFilter;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
@Test(groups = "functional", testName = "dataplacement.BloomFilterTest")
public class BloomFilterTest {

   private static final Log log = LogFactory.getLog(BloomFilterTest.class);

   private static final int START = 10;
   private static final int END = 100000;
   private static final double PROB = 0.001;

   public void testBloomFilter() {

      for (int iteration = START; iteration <= END; iteration *= 10) {
         BloomFilter bloomFilter = new BloomFilter(PROB, iteration);

         for (int key = 0; key < iteration; ++key) {
            bloomFilter.add(getKey(key));
         }

         log.infof("=== Iterator with %s objects ===", iteration);
         log.infof("Bloom filter size = %s", bloomFilter.size());

         long begin = System.currentTimeMillis();
         for (int key = 0; key < iteration; ++key) {
            assert bloomFilter.contains(getKey(key)) : "False Negative should not happen!";
         }
         long end = System.currentTimeMillis();

         log.infof("Query duration:\n\ttotal=%s ms\n\tper-key=%s ms", end - begin, (end - begin) / iteration);

         log.infof("[%s] Bloom Filter serialized size (bytes) = %s", iteration, serializedSize(bloomFilter));

         bloomFilter.clear();

         LinkedList<Object> linkedList = new LinkedList<Object>();
         ArrayList<Object> arrayList = new ArrayList<Object>();
         HashSet<Object> hashSet = new HashSet<Object>();

         for (int key = 0; key < iteration; ++key) {
            linkedList.add(getKey(key));
         }
         log.infof("[%s] Linked List serialized size (bytes) = %s", iteration, serializedSize(linkedList));
         linkedList.clear();

         for (int key = 0; key < iteration; ++key) {
            arrayList.add(getKey(key));
         }
         log.infof("[%s] Array List serialized size (bytes) = %s", iteration, serializedSize(arrayList));
         arrayList.clear();

         for (int key = 0; key < iteration; ++key) {
            hashSet.add(getKey(key));
         }
         log.infof("[%s] Hash Set serialized size (bytes) = %s", iteration, serializedSize(hashSet));
         hashSet.clear();
      }

   }

   public void testSerializable() throws IOException, ClassNotFoundException {
      int numberOfKeys = 1000;
      BloomFilter bloomFilter = new BloomFilter(PROB, numberOfKeys);

      for (int i = 0; i < numberOfKeys; ++i) {
         bloomFilter.add(getKey(i));
      }

      ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(arrayOutputStream);

      oos.writeObject(bloomFilter);
      oos.flush();
      oos.close();

      byte[] bytes = arrayOutputStream.toByteArray();

      log.infof("Bloom filter size with %s keys is %s bytes", numberOfKeys, bytes.length);

      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));

      BloomFilter readBloomFilter = (BloomFilter) ois.readObject();
      ois.close();

      for (int i = 0; i < numberOfKeys; ++i) {
         assert bloomFilter.contains(getKey(i)) : "False negative should never happen. It happened in original " +
               "Bloom Filter";
         assert readBloomFilter.contains(getKey(i)) : "False negative should never happen. It happened in read " +
               "Bloom Filter";
      }
   }

   private String getKey(int index) {
      return "KEY_" + index + "_" + (index * 2) + "_" + (index * 3);
   }

   private int serializedSize(Object object) {
      ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();

      try {
         ObjectOutputStream oos = new ObjectOutputStream(arrayOutputStream);
         oos.writeObject(object);
         oos.flush();
         oos.close();
      } catch (IOException e) {
         return -1;
      }

      return arrayOutputStream.toByteArray().length;
   }

}
