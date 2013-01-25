package org.jgroups.blocks;

public interface RequestHandler {
   public static final Object DO_NOT_REPLY = new Object();

   Object handle(MessageRequest request) throws Exception;
}
