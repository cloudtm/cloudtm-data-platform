package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.View;

/**
 * Extends the ACK_SEQUENCER to support uniformity properties
 *
 * @author Pedro Ruivo
 * @since 3.2
 */
//TODO: add test cases!
public class UNIFORM extends ACK_SEQUENCER {

   @Override
   protected Object handleMessage(Address originalSender, long seqNo, Message message) {
      sendAck(originalSender, seqNo, null);
      awaitUntilReadyToDeliver(originalSender, seqNo);
      return up_prot.up(new Event(Event.MSG, message));
   }

   @Override
   protected void handleViewChange(View view) {
      synchronized (origMbrAckMap) {
         updateState(view);

         //TODO this will originate a memory leak when a member leaves the view...

         for (Address address : view.getMembers()) {
            if (!origMbrAckMap.containsKey(address)) {
               origMbrAckMap.put(address, new MessageWindow());
            }
         }

         logViewChange();
      }
   }   
}
