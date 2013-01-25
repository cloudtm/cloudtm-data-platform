package org.infinispan.executors;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public interface ConditionalRunnable extends Runnable {

   boolean isReady();

}
