package org.jgroups.blocks;

import org.jgroups.Message;

/**
 * // TODO: Document this
 *
 * @author Pedro Ruivo
 * @since 3.3
 */
public interface MessageRequest {

   void sendReply(Object reply, boolean exceptionThrown);

   Message getMessage();
}
