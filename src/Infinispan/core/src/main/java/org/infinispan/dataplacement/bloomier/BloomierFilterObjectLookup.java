package org.infinispan.dataplacement.bloomier;

import edu.utexas.ece.mpc.bloomier.ImmutableBloomierFilter;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.dataplacement.stats.IncrementableLong;

import java.util.LinkedList;
import java.util.List;

/**
 * An Object Lookup implementation based on a Bloomier Filter
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class BloomierFilterObjectLookup implements ObjectLookup {

   private final ImmutableBloomierFilter<Object, Integer>[] bloomierFilters;

   public BloomierFilterObjectLookup(ImmutableBloomierFilter<Object, Integer>[] bloomierFilters) {
      this.bloomierFilters = bloomierFilters;
   }

   @Override
   public List<Integer> query(Object key) {
      List<Integer> newOwners = new LinkedList<Integer>();
      for (ImmutableBloomierFilter<Object, Integer> bloomierFilter : bloomierFilters) {
         newOwners.add(bloomierFilter.get(key));
      }
      return newOwners;
   }

   @Override
   public List<Integer> queryWithProfiling(Object key, IncrementableLong[] phaseDurations) {
      if (phaseDurations == null || phaseDurations.length < 1) {
         return query(key);
      }
      List<Integer> newOwners = new LinkedList<Integer>();
      long start = System.nanoTime();
      for (ImmutableBloomierFilter<Object, Integer> bloomierFilter : bloomierFilters) {
         newOwners.add(bloomierFilter.get(key));
      }
      phaseDurations[0].add(System.nanoTime() - start);
      return newOwners;
   }
}
