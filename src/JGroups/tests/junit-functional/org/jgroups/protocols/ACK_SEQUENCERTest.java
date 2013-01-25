package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Global;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.stack.Protocol;
import org.jgroups.util.Util;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test the behavior of the ACK_SEQUENCER protocol layer
 *
 * @author Pedro Ruivo
 * @since 3.1
 */
@Test(groups = Global.FUNCTIONAL, sequential = true)
public class ACK_SEQUENCERTest {
   private static final int NUMBER_OF_MEMBERS = 3;
   private static final short SEQUENCER_ID = 1;
   private static final short ACK_SEQUENCER_ID = 2;

   private ACK_SEQUENCER[] ackSequencers;
   private UpProtocol[] upProtocols;
   private DownProtocol[] downProtocols;
   private Address[] members;

   @BeforeClass
   public void setUpClass() {
      ackSequencers = new ACK_SEQUENCER[NUMBER_OF_MEMBERS];
      upProtocols = new UpProtocol[NUMBER_OF_MEMBERS];
      downProtocols = new DownProtocol[NUMBER_OF_MEMBERS];
      members = new Address[NUMBER_OF_MEMBERS];
   }

   @BeforeMethod
   public void setUpProtocols() {
      for (int i = 0; i < NUMBER_OF_MEMBERS; ++i) {
         members[i] = Util.createRandomAddress("A" + i);
         upProtocols[i] = new UpProtocol();
         downProtocols[i] = new DownProtocol();
         ackSequencers[i] = new ACK_SEQUENCER();

         ackSequencers[i].setUpProtocol(upProtocols[i]);
         upProtocols[i].setDownProtocol(ackSequencers[i]);

         SEQUENCER sequencer = new SEQUENCER();
         sequencer.setDownProtocol(downProtocols[i]);
         downProtocols[i].setUpProtocol(sequencer);

         sequencer.setUpProtocol(ackSequencers[i]);
         ackSequencers[i].setDownProtocol(sequencer);

         sequencer.setId(SEQUENCER_ID);
         ackSequencers[i].setId(ACK_SEQUENCER_ID);

         upProtocols[i].down(new Event(Event.SET_LOCAL_ADDRESS, members[i]));
      }
   }

   @AfterMethod
   public void cleanProtocols() {
      for (int i = 0; i < NUMBER_OF_MEMBERS; ++i) {
         members[i] = null;
         upProtocols[i] = null;
         downProtocols[i] = null;
         ackSequencers[i] = null;
         System.gc();
      }
   }

   public void testNonSequencerInStack() {
      ACK_SEQUENCER ack_sequencer = ackSequencers[0];
      Protocol p = new UNICAST();
      p.setDownProtocol(new FRAG2());
      ack_sequencer.setDownProtocol(p);

      try {
         ack_sequencer.start();
      } catch (Exception e) {
         assert false : "Exception while starting the ACK_SEQUENCER. " + e.getLocalizedMessage();
      }

      checkSequencerHeaderID((short) -1, ack_sequencer);

      Message ack = createAckMessage(members[0], 1);
      String data = randomData();
      ack.setObject(data);

      ack_sequencer.up(new Event(Event.MSG, ack));

      assert upProtocols[0].lastMessageDeliver != null : "Expected a delivered message";
      assert upProtocols[0].lastMessageDeliver.getObject().equals(data) : "Wrong delivered message";
   }

   public void testStaticNumberOfExpectedFailedMembers() {
      ACK_SEQUENCER ack_sequencer = ackSequencers[0];
      ack_sequencer.setExpectedNumberOfFailedMembers(5);
      ack_sequencer.setPercentageOfFailedMembers(-1);

      try {
         ack_sequencer.start();
      } catch (Exception e) {
         assert false : "Exception while starting the ACK_SEQUENCER. " + e.getLocalizedMessage();
      }

      checkSequencerHeaderID(SEQUENCER_ID, ack_sequencer);
      checkActualNumberOfMembers(0, ack_sequencer);
      checkExpectedFailedMembers(5, ack_sequencer);

      List<Address> viewMember = new LinkedList<Address>();
      viewMember.add(members[0]);
      View view = new View(members[0], 1, viewMember);

      ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

      checkActualNumberOfMembers(1, ack_sequencer);
      checkExpectedFailedMembers(5, ack_sequencer);
      checkCoordinator(ack_sequencer);

      for (int i = 2; i <= 10; ++i) {
         viewMember.add(Util.createRandomAddress("B" + i));
         view = new View(members[0], i, viewMember);
         ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

         checkActualNumberOfMembers(i, ack_sequencer);
         checkExpectedFailedMembers(5, ack_sequencer);
         checkCoordinator(ack_sequencer);
      }
   }

   public void testDynamicNumberOfExpectedFailedMembers() {
      ACK_SEQUENCER ack_sequencer = ackSequencers[0];
      ack_sequencer.setExpectedNumberOfFailedMembers(0);
      ack_sequencer.setPercentageOfFailedMembers(0.5); //50% + 1

      try {
         ack_sequencer.start();
      } catch (Exception e) {
         assert false : "Exception while starting the ACK_SEQUENCER. " + e.getLocalizedMessage();
      }

      checkSequencerHeaderID(SEQUENCER_ID, ack_sequencer);
      checkActualNumberOfMembers(0, ack_sequencer);
      checkExpectedFailedMembers(1, ack_sequencer);

      List<Address> viewMember = new LinkedList<Address>();
      viewMember.add(members[0]);
      View view = new View(members[0], 1, viewMember);

      ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

      checkActualNumberOfMembers(1, ack_sequencer);
      checkExpectedFailedMembers(1, ack_sequencer);
      checkCoordinator(ack_sequencer);

      for (int i = 2; i <= 10; ++i) {
         viewMember.add(Util.createRandomAddress("B" + i));
         view = new View(members[0], i, viewMember);

         ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

         checkActualNumberOfMembers(i, ack_sequencer);
         checkExpectedFailedMembers(i / 2 + 1, ack_sequencer);
         checkCoordinator(ack_sequencer);
      }
   }

   public void testDynamicNumberOfExpectedFailedMembers2() {
      ACK_SEQUENCER ack_sequencer = ackSequencers[0];
      ack_sequencer.setExpectedNumberOfFailedMembers(0);
      ack_sequencer.setPercentageOfFailedMembers(0); //0% + 1

      try {
         ack_sequencer.start();
      } catch (Exception e) {
         assert false : "Exception while starting the ACK_SEQUENCER. " + e.getLocalizedMessage();
      }

      checkSequencerHeaderID(SEQUENCER_ID, ack_sequencer);
      checkActualNumberOfMembers(0, ack_sequencer);
      checkExpectedFailedMembers(1, ack_sequencer);

      List<Address> viewMember = new LinkedList<Address>();
      viewMember.add(members[0]);
      View view = new View(members[0], 1, viewMember);

      ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

      checkActualNumberOfMembers(1, ack_sequencer);
      checkExpectedFailedMembers(1, ack_sequencer);
      checkCoordinator(ack_sequencer);

      for (int i = 2; i <= 10; ++i) {
         viewMember.add(Util.createRandomAddress("B" + i));
         view = new View(members[0], i, viewMember);

         ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

         checkActualNumberOfMembers(i, ack_sequencer);
         checkExpectedFailedMembers(1, ack_sequencer);
         checkCoordinator(ack_sequencer);
      }
   }

   public void testDynamicNumberOfExpectedFailedMembers3() {
      ACK_SEQUENCER ack_sequencer = ackSequencers[0];
      ack_sequencer.setExpectedNumberOfFailedMembers(0);
      ack_sequencer.setPercentageOfFailedMembers(1); //100% + 1

      try {
         ack_sequencer.start();
      } catch (Exception e) {
         assert false : "Exception while starting the ACK_SEQUENCER. " + e.getLocalizedMessage();
      }

      checkSequencerHeaderID(SEQUENCER_ID, ack_sequencer);
      checkActualNumberOfMembers(0, ack_sequencer);
      checkExpectedFailedMembers(1, ack_sequencer);

      List<Address> viewMember = new LinkedList<Address>();
      viewMember.add(members[0]);
      View view = new View(members[0], 1, viewMember);

      ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

      checkActualNumberOfMembers(1, ack_sequencer);
      checkExpectedFailedMembers(2, ack_sequencer);
      checkCoordinator(ack_sequencer);

      for (int i = 2; i <= 10; ++i) {
         viewMember.add(Util.createRandomAddress("B" + i));
         view = new View(members[0], i, viewMember);

         ack_sequencer.up(new Event(Event.VIEW_CHANGE, view));

         checkActualNumberOfMembers(i, ack_sequencer);
         checkExpectedFailedMembers(i+1, ack_sequencer);
         checkCoordinator(ack_sequencer);
      }
   }

   public void testNumberOfExpectedFailedMembers() {
      initAckSequencers();

      ACK_SEQUENCER ack_sequencer = ackSequencers[0];
      ack_sequencer.setExpectedNumberOfFailedMembers(0);
      checkExpectedFailedMembers(0, ack_sequencer);

      ack_sequencer.setExpectedNumberOfFailedMembers(10);
      checkExpectedFailedMembers(10, ack_sequencer);

      ack_sequencer.setPercentageOfFailedMembers(0);
      checkExpectedFailedMembers(1, ack_sequencer);

      ack_sequencer.setPercentageOfFailedMembers(0.34);
      checkExpectedFailedMembers(2, ack_sequencer);

      ack_sequencer.setPercentageOfFailedMembers(0.5);
      checkExpectedFailedMembers(2, ack_sequencer);

      ack_sequencer.setPercentageOfFailedMembers(0.67);
      checkExpectedFailedMembers(3, ack_sequencer);

      ack_sequencer.setPercentageOfFailedMembers(1);
      checkExpectedFailedMembers(4, ack_sequencer);

      ack_sequencer.setExpectedNumberOfFailedMembers(5);
      checkExpectedFailedMembers(5, ack_sequencer);
   }

   public void testNoAcksNeeded() {
      initAckSequencers();
      setNumberOfExpectedFailedMembers(0);
      Message sentMsg = sendBroadcast(0);
      checkMessageSent(0);
      final Message broadcast = getAndRemoveMessage(downProtocols[0]);
      final CountDownLatch countDownLatch = new CountDownLatch(NUMBER_OF_MEMBERS);

      for (final DownProtocol downProtocol : downProtocols) {
         new Thread() {
            @Override
            public void run() {
               downProtocol.up(new Event(Event.MSG, broadcast));
               countDownLatch.countDown();
            }
         }.start();
      }

      try {
         countDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in all members";
      }

      for (int i = 0; i < NUMBER_OF_MEMBERS; ++i) {
         checkMessageDelivered(i);
         Message deliveredMsg = getAndRemoveMessage(upProtocols[i]);
         assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
      }

      checkNoMessageSent(0);

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         checkMessageSent(i);
         Message msg = getAndRemoveMessage(downProtocols[i]);
         assert msg.getHeader(ACK_SEQUENCER_ID) != null : "Expected an ack for *no* coordinators";
      }
   }

   public void testOneAcksNeeded() {
      initAckSequencers();
      setNumberOfExpectedFailedMembers(1);
      Message sentMsg = sendBroadcast(0);
      checkMessageSent(0);
      final Message broadcast = getAndRemoveMessage(downProtocols[0]);
      final CountDownLatch noCoordinatorCountDownLatch = new CountDownLatch(NUMBER_OF_MEMBERS - 1);
      final CountDownLatch coordinatorCountDownLatch = new CountDownLatch(1);

      new Thread() {
         @Override
         public void run() {
            downProtocols[0].up(new Event(Event.MSG, broadcast));
            coordinatorCountDownLatch.countDown();
         }
      }.start();

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         final int finalI = i;
         new Thread() {
            @Override
            public void run() {
               downProtocols[finalI].up(new Event(Event.MSG, broadcast));
               noCoordinatorCountDownLatch.countDown();
            }
         }.start();
      }

      try {
         noCoordinatorCountDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in *no* coordinator members";
      }

      final Message[] acks = new Message[NUMBER_OF_MEMBERS - 1];

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         checkMessageDelivered(i);
         Message deliveredMsg = getAndRemoveMessage(upProtocols[i]);
         assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
         checkMessageSent(i);
         Message msg = getAndRemoveMessage(downProtocols[i]);
         assert msg.getHeader(ACK_SEQUENCER_ID) != null : "Expected an ack for *no* coordinator member";
         acks[i-1] = msg;
      }

      //message must be blocked in coordinator
      checkNoMessageDelivered(0);
      assert coordinatorCountDownLatch.getCount() == 1 : "Expected a blocked coordinator";
      downProtocols[0].up(new Event(Event.MSG, acks[0]));


      try {
         coordinatorCountDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in coordinator members";
      }

      checkMessageDelivered(0);
      Message deliveredMsg = getAndRemoveMessage(upProtocols[0]);
      assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
      checkNoMessageSent(0);
   }

   public void testSameNumberOfAcksNeededAsMembers() {
      initAckSequencers();
      setNumberOfExpectedFailedMembers(NUMBER_OF_MEMBERS);
      Message sentMsg = sendBroadcast(0);
      checkMessageSent(0);
      final Message broadcast = getAndRemoveMessage(downProtocols[0]);
      final CountDownLatch noCoordinatorCountDownLatch = new CountDownLatch(NUMBER_OF_MEMBERS - 1);
      final CountDownLatch coordinatorCountDownLatch = new CountDownLatch(1);

      new Thread() {
         @Override
         public void run() {
            downProtocols[0].up(new Event(Event.MSG, broadcast));
            coordinatorCountDownLatch.countDown();
         }
      }.start();

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         final int finalI = i;
         new Thread() {
            @Override
            public void run() {
               downProtocols[finalI].up(new Event(Event.MSG, broadcast));
               noCoordinatorCountDownLatch.countDown();
            }
         }.start();
      }

      try {
         noCoordinatorCountDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in *no* coordinator members";
      }

      Message[] acks = new Message[NUMBER_OF_MEMBERS - 1];

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         checkMessageDelivered(i);
         Message deliveredMsg = getAndRemoveMessage(upProtocols[i]);
         assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
         checkMessageSent(i);
         Message msg = getAndRemoveMessage(downProtocols[i]);
         assert msg.getHeader(ACK_SEQUENCER_ID) != null : "Expected an ack for *no* coordinator member";
         acks[i-1] = msg;
      }

      for (final Message ack : acks) {
         //message must be blocked in coordinator
         checkNoMessageDelivered(0);
         assert coordinatorCountDownLatch.getCount() == 1 : "Expected a blocked coordinator";
         downProtocols[0].up(new Event(Event.MSG, ack));
      }

      try {
         coordinatorCountDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in coordinator members";
      }

      checkMessageDelivered(0);
      Message deliveredMsg = getAndRemoveMessage(upProtocols[0]);
      assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
      checkNoMessageSent(0);
   }

   public void testHighNumberOfAcksNeededThanMembers() {
      initAckSequencers();
      setNumberOfExpectedFailedMembers(NUMBER_OF_MEMBERS + 2);
      Message sentMsg = sendBroadcast(0);
      checkMessageSent(0);
      final Message broadcast = getAndRemoveMessage(downProtocols[0]);
      final CountDownLatch noCoordinatorCountDownLatch = new CountDownLatch(NUMBER_OF_MEMBERS - 1);
      final CountDownLatch coordinatorCountDownLatch = new CountDownLatch(1);

      new Thread() {
         @Override
         public void run() {
            downProtocols[0].up(new Event(Event.MSG, broadcast));
            coordinatorCountDownLatch.countDown();
         }
      }.start();

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         final int finalI = i;
         new Thread() {
            @Override
            public void run() {
               downProtocols[finalI].up(new Event(Event.MSG, broadcast));
               noCoordinatorCountDownLatch.countDown();
            }
         }.start();
      }

      try {
         noCoordinatorCountDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in *no* coordinator members";
      }

      Message[] acks = new Message[NUMBER_OF_MEMBERS - 1];

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         checkMessageDelivered(i);
         Message deliveredMsg = getAndRemoveMessage(upProtocols[i]);
         assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
         checkMessageSent(i);
         Message msg = getAndRemoveMessage(downProtocols[i]);
         assert msg.getHeader(ACK_SEQUENCER_ID) != null : "Expected an ack for *no* coordinator member";
         acks[i-1] = msg;
      }

      for (final Message ack : acks) {
         //message must be blocked in coordinator
         checkNoMessageDelivered(0);
         assert coordinatorCountDownLatch.getCount() == 1 : "Expected a blocked coordinator";
         downProtocols[0].up(new Event(Event.MSG, ack));
      }

      try {
         coordinatorCountDownLatch.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         assert false : "Delivered message expected in coordinator members";
      }

      checkMessageDelivered(0);
      Message deliveredMsg = getAndRemoveMessage(upProtocols[0]);
      assert deliveredMsg.equals(sentMsg) : "Delivered message is different from the sent message";
      checkNoMessageSent(0);
   }

   private Message getAndRemoveMessage(DownProtocol downProtocol) {
      Message msg = downProtocol.lastMessageSent;
      downProtocol.lastMessageSent = null;
      return msg;
   }

   private Message getAndRemoveMessage(UpProtocol upProtocol) {
      Message msg = upProtocol.lastMessageDeliver;
      upProtocol.lastMessageDeliver = null;
      return msg;
   }

   private Message sendBroadcast(int idx) {
      Message msg = new Message();
      msg.setSrc(members[idx]);
      msg.setDest(null);
      msg.setObject(randomData());
      upProtocols[idx].down(new Event(Event.MSG, msg));
      return msg;
   }

   private void setNumberOfExpectedFailedMembers(int numberOfExpectedFailedMembers) {
      for (ACK_SEQUENCER ack_sequencer : ackSequencers) {
         ack_sequencer.setExpectedNumberOfFailedMembers(numberOfExpectedFailedMembers);
      }
   }

   private void initAckSequencers() {
      for (ACK_SEQUENCER ack_sequencer : ackSequencers) {
         try {
            ack_sequencer.start();
         } catch (Exception e) {
            assert false : "Exception while starting the ACK_SEQUENCER. " + e.getLocalizedMessage();
         }
      }
      List<Address> viewMembers = Arrays.asList(members);
      View view = new View(members[0], 1, viewMembers);
      for (Protocol p : upProtocols) {
         p.down(new Event(Event.VIEW_CHANGE, view));
      }
      checkSequencerHeaderID(SEQUENCER_ID, ackSequencers[0]);
      checkCoordinator(ackSequencers[0]);

      for (ACK_SEQUENCER ack_sequencer : ackSequencers) {
         checkActualNumberOfMembers(viewMembers.size(), ack_sequencer);
         ack_sequencer.setPercentageOfFailedMembers(-1);
      }

      for (int i = 1; i < NUMBER_OF_MEMBERS; ++i) {
         checkNoCoordinator(ackSequencers[i]);
         checkCoordinatorAddress(ackSequencers[i]);
      }
   }

   private void checkMessageSent(int idx) {
      assert downProtocols[idx].lastMessageSent != null : "Expected a sent message for index " + idx;
   }

   private void checkMessageDelivered(int idx) {
      assert upProtocols[idx].lastMessageDeliver != null : "Expected a delivered message for index " + idx;
   }

   private void checkNoMessageSent(int idx) {
      assert downProtocols[idx].lastMessageSent == null : "Expected *no* sent message for index " + idx +
            ". Message sent is " + downProtocols[idx].lastMessageSent;
   }

   private void checkNoMessageDelivered(int idx) {
      assert upProtocols[idx].lastMessageDeliver == null : "Expected *no* delivered message for index " + idx +
            ". Message delivered is " + upProtocols[idx].lastMessageDeliver;
   }

   private void checkSequencerHeaderID(short expected, ACK_SEQUENCER ack_sequencer) {
      assert expected == ack_sequencer.getSequencerHeaderID() : "Wrong SEQUENCER header ID. " + expected +
            "!=" + ack_sequencer.getSequencerHeaderID();
   }

   private void checkCoordinator(ACK_SEQUENCER ack_sequencer) {
      assert ack_sequencer.isCoordinator() : "It is expected to be the coordinator";
   }

   private void checkNoCoordinator(ACK_SEQUENCER ack_sequencer) {
      assert !ack_sequencer.isCoordinator() : "It is expected *not* to be the coordinator";
   }

   private void checkCoordinatorAddress(ACK_SEQUENCER ack_sequencer) {
      assert members[0].equals(ack_sequencer.getCoordinatorAddress()) : "Wrong coordinator address. " + members[0] +
            "!=" + ack_sequencer.getCoordinatorAddress();
   }

   private void checkActualNumberOfMembers(int expected, ACK_SEQUENCER ack_sequencer) {
      assert expected == ack_sequencer.getActualNumberOfMembers() : "Wrong number of actual members. " + expected +
            "!=" + ack_sequencer.getActualNumberOfMembers();
   }

   private void checkExpectedFailedMembers(int expected, ACK_SEQUENCER ack_sequencer) {
      assert expected == ack_sequencer.getExpectedNumberOfFailedMembers() : "Wrong number of expected failed members " +
            expected + "!=" + ack_sequencer.getExpectedNumberOfFailedMembers();
   }

   private Message createAckMessage(Address originalSender, long seqNo) {
      Message msg = new Message();
      ACK_SEQUENCER.AckSequencerHeader header = new ACK_SEQUENCER.AckSequencerHeader(originalSender, seqNo);
      msg.putHeader(ACK_SEQUENCER_ID, header);
      return msg;
   }

   private String randomData() {
      return Integer.toOctalString(new Random().nextInt());
   }

   private class DownProtocol extends Protocol {
      volatile Message lastMessageSent;

      @Override
      public Object down(Event evt) {
         if (evt.getType() == Event.MSG) {
            lastMessageSent = (Message) evt.getArg();
         }
         return null;
      }
   }

   private class UpProtocol extends Protocol {
      volatile Message lastMessageDeliver;

      @Override
      public Object up(Event evt) {
         if (evt.getType() == Event.MSG) {
            lastMessageDeliver = (Message) evt.getArg();
         }
         return null;
      }
   }

}
