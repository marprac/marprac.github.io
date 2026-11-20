package isp.integrity;

import java.security.Key;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import fri.isp.Agent;
import fri.isp.Environment;

/**
 * TASK:
 * Alice and Bob share a secret key. Authenticate their messages using
 * HmacSHA256.
 * Complete ten request-reply rounds: Alice sends a message and Bob sends a
 * reply
 * in each round (twenty authenticated messages in total).
 * Both agents must verify the received tag before displaying or using the
 * message.
 * Reject an invalid tag; do not process the corresponding message.
 *
 * Send each message and its tag in the same agreed order. Use a separate Mac
 * instance for each agent and MessageDigest.isEqual to compare tags. For this
 * exercise, the shared key is generated here; do not send it over the channel.
 * HMAC does not encrypt messages or by itself prevent replay attacks.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html
 */
public class A1AgentCommunicationHMAC {
    public static void main(String[] args) throws Exception {
        /*
         * Alice and Bob share a secret session key that will be
         * used for hash based message authentication code.
         */
        final Key key = KeyGenerator.getInstance("HmacSHA256").generateKey();

        final Environment env = new Environment();

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                // TODO: Send ten UTF-8 messages with their tags. Receive and
                // verify Bob's reply in each round before displaying it.
                final Mac alice = Mac.getInstance("HmacSHA256");
                alice.init(key);

                for (int i = 0; i < 10; i++) {
                    final String message = "Hello Bob " + i;
                    final byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                    final byte[] tag = alice.doFinal(messageBytes);

                    send("bob", messageBytes);
                    send("bob", tag);

                    final byte[] reply = receive("bob");
                    final byte[] receivedTag = receive("bob");

                    final byte[] expectedTag = alice.doFinal(reply);

                    if (!MessageDigest.isEqual(receivedTag, expectedTag)) {
                        throw new SecurityException("Invalid HMAC tag");
                    }

                    System.out.println("Alice received: " + new String(reply, StandardCharsets.UTF_8));
                }

            }
        });
        env.add(new Agent("bob") {
            @Override
            public void task() throws Exception {
                // TODO: Receive and verify each message before displaying it.
                // Send an authenticated reply in each of the ten rounds.

                final Mac bob = Mac.getInstance("HmacSHA256");
                bob.init(key);

                for (int i = 0; i < 10; i++) {
                    final byte[] message = receive("alice");
                    final byte[] receivedTag = receive("alice");

                    final byte[] expectedTag = bob.doFinal(message);

                    if (!MessageDigest.isEqual(receivedTag, expectedTag)) {
                        throw new SecurityException("Invalid HMAC tag");
                    }

                    System.out.println("Bob received: " + new String(message, StandardCharsets.UTF_8));

                    final String reply = "Hello Alice " + i;
                    final byte[] replyBytes = reply.getBytes(StandardCharsets.UTF_8);
                    final byte[] tag = bob.doFinal(replyBytes);

                    send("alice", replyBytes);
                    send("alice", tag);
                }
            }
        });

        env.connect("alice", "bob");
        env.start();
    }
}
