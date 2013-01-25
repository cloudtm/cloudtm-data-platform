package org.infinispan.tx.gmu;

import org.infinispan.Cache;
import org.infinispan.remoting.transport.Address;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.infinispan.distribution.DistributionTestHelper.addressOf;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class GMUMagicKey implements Serializable {
   private final String name;
   private final Collection<Address> mapTo;
   private final Collection<Address> notMapTo;

   public GMUMagicKey(Collection<Cache> toMapTo, Collection<Cache> notToMapTo, String name) {
      if (toMapTo == null && notToMapTo == null) {
         throw new NullPointerException("Both map to and not map to cannot be null");
      }

      mapTo = getAddresses(toMapTo);
      notMapTo = getAddresses(notToMapTo);
      this.name = name;
   }

   public final Collection<Address> getMapTo() {
      return mapTo == null ? Collections.<Address>emptyList() : mapTo;
   }

   public final Collection<Address> getNotMapTo() {
      return notMapTo == null ? Collections.<Address>emptyList() : notMapTo;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GMUMagicKey magicKey = (GMUMagicKey) o;

      return !(mapTo != null ? !mapTo.equals(magicKey.mapTo) : magicKey.mapTo != null) &&
            !(name != null ? !name.equals(magicKey.name) : magicKey.name != null) &&
            !(notMapTo != null ? !notMapTo.equals(magicKey.notMapTo) : magicKey.notMapTo != null);

   }

   @Override
   public int hashCode() {
      int result = name != null ? name.hashCode() : 0;
      result = 31 * result + (mapTo != null ? mapTo.hashCode() : 0);
      result = 31 * result + (notMapTo != null ? notMapTo.hashCode() : 0);
      return result;
   }

   @Override
   public String toString() {
      return "GMUMagicKey{" +
            "name='" + name + '\'' +
            ", mapTo=" + mapTo +
            ", notMapTo=" + notMapTo +
            '}';
   }

   private static Collection<Address> getAddresses(Collection<Cache> caches) {
      if (caches == null) {
         return null;
      }
      List<Address> list = new LinkedList<Address>();
      for (Cache cache : caches) {
         list.add(addressOf(cache));
      }
      return list;
   }
}
