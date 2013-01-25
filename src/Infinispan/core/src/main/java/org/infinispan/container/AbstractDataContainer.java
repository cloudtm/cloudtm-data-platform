package org.infinispan.container;

import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.versioning.EntryVersion;
import org.infinispan.eviction.EvictionManager;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.eviction.EvictionThreadPolicy;
import org.infinispan.eviction.PassivationManager;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.util.Immutables;
import org.infinispan.util.concurrent.BoundedConcurrentHashMap;
import org.infinispan.util.concurrent.ConcurrentMapFactory;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public abstract class AbstractDataContainer<T> implements DataContainer {

   protected final ConcurrentMap<Object, T> entries;
   protected InternalEntryFactory entryFactory;
   private EvictionManager evictionManager;
   private PassivationManager passivator;

   protected AbstractDataContainer(int concurrencyLevel) {
      entries = ConcurrentMapFactory.makeConcurrentMap(128, concurrencyLevel);
   }

   protected AbstractDataContainer(int concurrencyLevel, int maxEntries, EvictionStrategy strategy, EvictionThreadPolicy policy) {
      // translate eviction policy and strategy
      BoundedConcurrentHashMap.EvictionListener<Object, T> evictionListener;
      switch (policy) {
         case PIGGYBACK:
         case DEFAULT:
            evictionListener = new DefaultEvictionListener();
            break;
         default:
            throw new IllegalArgumentException("No such eviction thread policy " + strategy);
      }

      BoundedConcurrentHashMap.Eviction eviction;
      switch (strategy) {
         case FIFO:
         case UNORDERED:
         case LRU:
            eviction = BoundedConcurrentHashMap.Eviction.LRU;
            break;
         case LIRS:
            eviction = BoundedConcurrentHashMap.Eviction.LIRS;
            break;
         default:
            throw new IllegalArgumentException("No such eviction strategy " + strategy);
      }
      entries = new BoundedConcurrentHashMap<Object, T>(maxEntries, concurrencyLevel, eviction, evictionListener);
   }

   @Inject
   public void initialize(EvictionManager evictionManager, PassivationManager passivator,
                          InternalEntryFactory entryFactory) {
      this.evictionManager = evictionManager;
      this.passivator = passivator;
      this.entryFactory = entryFactory;
   }

   @Override
   public Set<Object> keySet(EntryVersion version) {
      return Collections.unmodifiableSet(entries.keySet());
   }

   @Override
   public Collection<Object> values(EntryVersion version) {
      return new Values(version);
   }

   @Override
   public Set<InternalCacheEntry> entrySet(EntryVersion version) {
      return new EntrySet(version);
   }

   @Override
   public Iterator<InternalCacheEntry> iterator() {
      return createEntryIterator(null);
   }

   protected abstract Map<Object, InternalCacheEntry> getCacheEntries(Map<Object, T> evicted);

   protected abstract InternalCacheEntry getCacheEntry(T evicted);

   protected abstract InternalCacheEntry getCacheEntry(T entry, EntryVersion version);

   protected abstract EntryIterator createEntryIterator(EntryVersion version);

   private final class DefaultEvictionListener implements BoundedConcurrentHashMap.EvictionListener<Object, T> {

      @Override
      public void onEntryEviction(Map<Object, T> evicted) {
         evictionManager.onEntryEviction(getCacheEntries(evicted));
      }

      @Override
      public void onEntryChosenForEviction(T entry) {
         passivator.passivate(getCacheEntry(entry));
      }
   }

   protected abstract static class EntryIterator implements Iterator<InternalCacheEntry> {}
   
   private class ImmutableEntryIterator implements Iterator<InternalCacheEntry> {

      private final EntryIterator entryIterator;

      ImmutableEntryIterator(EntryIterator entryIterator){
         this.entryIterator = entryIterator;
      }

      @Override
      public boolean hasNext() {
         return entryIterator.hasNext();
      }

      @Override
      public InternalCacheEntry next() {
         return Immutables.immutableInternalCacheEntry(entryIterator.next());
      }

      @Override
      public void remove() {
         entryIterator.remove();
      }
   }   

   /**
    * Minimal implementation needed for unmodifiable Set
    *
    */
   public class EntrySet extends AbstractSet<InternalCacheEntry> {

      private final EntryVersion version;

      public EntrySet(EntryVersion version) {
         this.version = version;
      }

      @Override
      public boolean contains(Object o) {
         if (!(o instanceof Map.Entry)) {
            return false;
         }

         Map.Entry e = (Map.Entry) o;
         InternalCacheEntry ice = getCacheEntry(entries.get(e.getKey()), version);
         return ice != null && ice.getValue().equals(e.getValue());
      }

      @Override
      public Iterator<InternalCacheEntry> iterator() {
         return new ImmutableEntryIterator(createEntryIterator(version));
      }

      @Override
      public int size() {
         return AbstractDataContainer.this.size(version);
      }
   }

   /**
    * Minimal implementation needed for unmodifiable Collection
    *
    */
   private class Values extends AbstractCollection<Object> {

      private final EntryVersion version;

      private Values(EntryVersion version) {
         this.version = version;
      }

      @Override
      public Iterator<Object> iterator() {
         return new ValueIterator(createEntryIterator(version));
      }

      @Override
      public int size() {
         return AbstractDataContainer.this.size(version);
      }
   }

   private class ValueIterator implements Iterator<Object> {
      private final EntryIterator currentIterator;

      private ValueIterator(EntryIterator it) {
         currentIterator = it;
      }

      @Override
      public boolean hasNext() {
         return currentIterator.hasNext();
      }

      @Override
      public void remove() {
         throw new UnsupportedOperationException();
      }

      @Override
      public Object next() {
         return currentIterator.next().getValue();
      }
   }
}
