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
