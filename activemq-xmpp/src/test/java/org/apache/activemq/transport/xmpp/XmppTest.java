/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.xmpp;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.apache.activemq.util.Wait;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppTest extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(XmppTest.class);

    protected static boolean block;

    private final XmppBroker broker = new XmppBroker();

    private final long sleepTime = 5000;

    public static void main(String[] args) {
        block = true;
        TestRunner.run(XmppTest.class);
    }

    public void testConnect() throws Exception {
        ConnectionConfiguration config = new
            ConnectionConfiguration("localhost", 61222);
        // config.setDebuggerEnabled(true);

        try {
            // SmackConfiguration.setPacketReplyTimeout(1000);
            XMPPConnection con = new XMPPConnection(config);
            con.connect();
            con.login("amq-user", "amq-pwd");
            ChatManager chatManager = con.getChatManager();
            Chat chat = chatManager.createChat("test@localhost", new MessageListener() {
                public void processMessage(Chat chat, Message message) {
                    LOG.info("Got XMPP message from chat " + chat.getParticipant() + " message - " + message.getBody());
                }
            });
            for (int i = 0; i < 10; i++) {
                LOG.info("Sending message: " + i);
                chat.sendMessage("Hello from Message: " + i);
            }
            LOG.info("Sent all messages!");
            con.disconnect();
        } catch (XMPPException e) {
            if (block) {
                LOG.info("Caught: " + e);
                e.printStackTrace();
            } else {
                throw e;
            }
        }
        if (block) {
            Thread.sleep(20000);
            LOG.info("Press any key to quit!: ");
            System.in.read();
        }
        LOG.info("Done!");
    }

    public void testChat() throws Exception {
        ConnectionConfiguration config = new ConnectionConfiguration("localhost", 61222);
        //config.setDebuggerEnabled(true);

        XMPPConnection consumerCon = new XMPPConnection(config);
        consumerCon.connect();
        consumerCon.login("consumer", "consumer");
        consumerCon.addPacketListener(new XmppLogger("CONSUMER INBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });
        consumerCon.addPacketWriterListener(new XmppLogger("CONSUMER OUTBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });
        final ConsumerMessageListener listener = new ConsumerMessageListener();

        consumerCon.getChatManager().addChatListener(new ChatManagerListener() {
            public void chatCreated(Chat chat, boolean createdLocally) {
                chat.addMessageListener(listener);
            }
        });

        XMPPConnection producerCon = new XMPPConnection(config);
        producerCon.connect();
        producerCon.login("producer", "producer");
        producerCon.addPacketListener(new XmppLogger("PRODUCER INBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });

        producerCon.addPacketWriterListener(new XmppLogger("PRODUCER OUTBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });

        Chat chat = producerCon.getChatManager().createChat("consumer", new MessageListener() {
            public void processMessage(Chat chat, Message message) {
                LOG.info("Got XMPP message from chat " + chat.getParticipant() + " message - " + message.getBody());
            }
        });

        for (int i = 0; i < 10; i++) {
            LOG.info("Sending message: " + i);
            Message message = new Message("consumer");
            message.setType(Message.Type.chat);
            message.setBody("Hello from producer, message # " + i);
            chat.sendMessage(message);
        }
        LOG.info("Sent all messages!");

        assertTrue("Consumer received - " + listener.getMessageCount(), Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return listener.getMessageCount() == 10;
            }
        }));

        LOG.info("Consumer received - " + listener.getMessageCount());
    }

    public void testMultiUserChat() throws Exception {
        LOG.info("\n\n\n\n\n\n");
        ConnectionConfiguration config = new ConnectionConfiguration("localhost", 61222);
        //config.setDebuggerEnabled(true);
        //
        XMPPConnection consumerCon = new XMPPConnection(config);
        consumerCon.connect();
        consumerCon.login("consumer", "consumer");
        MultiUserChat consumerMuc = new MultiUserChat(consumerCon, "muc-test");
        consumerMuc.join("consumer");

        final ConsumerMUCMessageListener listener = new ConsumerMUCMessageListener();
        consumerMuc.addMessageListener(listener);

        XMPPConnection producerCon = new XMPPConnection(config);
        producerCon.connect();
        producerCon.login("producer", "producer");
        MultiUserChat producerMuc = new MultiUserChat(producerCon, "muc-test");
        producerMuc.join("producer");

        for (int i = 0; i < 10; i++) {
            LOG.info("Sending message: " + i);
            Message message = producerMuc.createMessage();
            message.setBody("Hello from producer, message # " + i);
            producerMuc.sendMessage(message);
        }
        LOG.info("Sent all messages!");

        assertTrue("Consumer received - " + listener.getMessageCount(), Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return listener.getMessageCount() == 10;
            }
        }));

        LOG.info("Consumer received - " + listener.getMessageCount());
    }

    public void addLoggingListeners(String name, XMPPConnection connection) {
        connection.addPacketListener(new XmppLogger(name + " INBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });
        connection.addPacketWriterListener(new XmppLogger(name + " OUTBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });
    }

    public void testTwoConnections() throws Exception {
        LOG.info("\n\n\n\n\n\n");
        ConnectionConfiguration config = new ConnectionConfiguration("localhost", 61222);
        //config.setDebuggerEnabled(true);

        //create the consumer first...
        XMPPConnection consumerCon = new XMPPConnection(config);
        consumerCon.connect();
        addLoggingListeners("CONSUMER", consumerCon);
        consumerCon.login("consumer", "consumer");

        final ConsumerMessageListener listener1 = new ConsumerMessageListener();
        consumerCon.getChatManager().addChatListener(new ChatManagerListener() {
            public void chatCreated(Chat chat, boolean createdLocally) {
                chat.addMessageListener(listener1);
            }
        });

        //now create the producer
        XMPPConnection producerCon = new XMPPConnection(config);
        LOG.info("Connecting producer and consumer");
        producerCon.connect();
        addLoggingListeners("PRODUCER", producerCon);
        producerCon.login("producer", "producer");

        //create the chat and send some messages
        Chat chat = producerCon.getChatManager().createChat("consumer", new MessageListener() {
            public void processMessage(Chat chat, Message message) {
                LOG.info("Got XMPP message from chat " + chat.getParticipant() + " message - " + message.getBody());
            }
        });

        for (int i = 0; i < 10; i++) {
            LOG.info("Sending message: " + i);
            Message message = new Message("consumer");
            message.setType(Message.Type.chat);
            message.setBody("Hello from producer, message # " + i);
            chat.sendMessage(message);
        }

        //make sure the consumer has time to receive all the messages...
        Thread.sleep(sleepTime);

        //create an identical 2nd consumer
        XMPPConnection lastguyCon = new XMPPConnection(config);
        lastguyCon.connect();
        addLoggingListeners("LASTGUY", consumerCon);
        lastguyCon.login("consumer", "consumer");
        final ConsumerMessageListener listener2 = new ConsumerMessageListener();
        lastguyCon.getChatManager().addChatListener(new ChatManagerListener() {
            public void chatCreated(Chat chat, boolean createdLocally) {
                chat.addMessageListener(listener2);
            }
        });

        for (int i = 0; i < 10; i++) {
            LOG.info("Sending message: " + i);
            Message message = new Message("consumer");
            message.setType(Message.Type.chat);
            message.setBody("Hello from producer, message # " + i);
            chat.sendMessage(message);
        }

        LOG.info("Sent all messages!");

        assertTrue("Consumer received - " + listener1.getMessageCount(), Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return listener1.getMessageCount() == 20;
            }
        }));

        assertTrue("Consumer received - " + listener2.getMessageCount(), Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return listener2.getMessageCount() == 10;
            }
        }));
    }

    class XmppLogger implements PacketListener {

        private final String direction;

        public XmppLogger(String direction) {
            this.direction = direction;
        }

        public void processPacket(Packet packet) {
            LOG.info(direction + " : " + packet.toXML());
        }
    }

    class ConsumerMUCMessageListener implements PacketListener {
        private int messageCount=0;

        public void processPacket(Packet packet) {
            if ( packet instanceof Message) {
                LOG.info("Received message number : " + (messageCount++));
            }
        }
        public int getMessageCount() {
            return messageCount;
        }
    }

    class ConsumerMessageListener implements MessageListener {
        private int messageCount=0;

        public void processMessage(Chat chat, Message message) {
            LOG.info("Received message number : " + (messageCount++));
        }

        public int getMessageCount() {
            return messageCount;
        }
    }

    @Override
    protected void setUp() throws Exception {
        broker.start();
    }

    @Override
    protected void tearDown() throws Exception {
        broker.stop();
    }
}
