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
package org.infinispan.configuration.cache;

import org.infinispan.config.ConfigurationException;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ConditionalExecutorServiceConfigurationBuilder
      extends AbstractConfigurationChildBuilder<ConditionalExecutorServiceConfiguration> {

   private int corePoolSize = 2;
   private int maxPoolSize = 16;
   private long keepAliveTime = 60000;

   protected ConditionalExecutorServiceConfigurationBuilder(ConfigurationBuilder builder) {
      super(builder);
   }

   public ConditionalExecutorServiceConfigurationBuilder corePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
      return this;
   }

   public ConditionalExecutorServiceConfigurationBuilder maxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
      return this;
   }

   public ConditionalExecutorServiceConfigurationBuilder keepAliveTime(long keepAliveTime) {
      this.keepAliveTime = keepAliveTime;
      return this;
   }

   @Override
   public ConfigurationChildBuilder read(ConditionalExecutorServiceConfiguration template) {
      this.corePoolSize = template.corePoolSize();
      this.maxPoolSize = template.maxPoolSize();
      this.keepAliveTime = template.keepAliveTime();
      return this;
   }

   @Override
   public String toString() {
      return "ConditionalExecutorServiceConfigurationBuilder{" +
            "corePoolSize=" + corePoolSize +
            ", maxPoolSize=" + maxPoolSize +
            ", keepAliveTime=" + keepAliveTime +
            '}';
   }

   @Override
   void validate() {
      if (corePoolSize <= 0) {
         throw new ConfigurationException("Core Pool Size should be greater than zero");
      }
      if (maxPoolSize < corePoolSize) {
         throw new ConfigurationException("Max Pool Size should be greater or equals than Core Pool Size");
      }
      if (keepAliveTime <= 0) {
         throw new ConfigurationException("Keep Alive Time should be greated than zero");
      }
   }

   @Override
   ConditionalExecutorServiceConfiguration create() {
      return new ConditionalExecutorServiceConfiguration(corePoolSize, maxPoolSize, keepAliveTime);
   }
}
