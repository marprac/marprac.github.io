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

        // Register Alice as a participant and define that agent's task.
        env.add(new Agent("alice") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Alice's communication steps; propagate cryptographic failures to the environment.
            public void task() throws Exception {
                // TODO: Send ten UTF-8 messages with their tags. Receive and
                // verify Bob's reply in each round before displaying it.
                // Create Alice's own HMAC-SHA256 instance for authenticating message bytes.
                final Mac alice = Mac.getInstance("HmacSHA256");
                // Initialize Alice's HMAC instance with the secret key shared by both participants.
                alice.init(key);

                // Perform one request-reply round, repeating for indices 0 through 9.
                for (int i = 0; i < 10; i++) {
                    // Include the current round number in Alice's message to Bob.
                    final String message = "Hello Bob " + i;
                    // Encode Alice's message as UTF-8 so the transmitted and authenticated bytes agree.
                    final byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                    // Compute Alice's HMAC tag; doFinal also resets this Mac for the next message with the same key.
                    final byte[] tag = alice.doFinal(messageBytes);

                    // Send Bob Alice's plaintext message bytes; HMAC authenticates them without encrypting them.
                    send("bob", messageBytes);
                    // Send Bob the HMAC tag immediately after the message it authenticates.
                    send("bob", tag);

                    // Receive Bob's reply bytes; verify the following tag before displaying this reply.
                    final byte[] reply = receive("bob");
                    // Receive the HMAC tag Bob sent immediately after his reply.
                    final byte[] receivedTag = receive("bob");

                    // Compute the tag Alice expects for the exact reply bytes using the shared secret key.
                    final byte[] expectedTag = alice.doFinal(reply);

                    // Reject a received message if its supplied tag differs from the locally computed tag.
                    if (!MessageDigest.isEqual(receivedTag, expectedTag)) {
                        // Stop the agent task so an unauthenticated message is not displayed or processed.
                        throw new SecurityException("Invalid HMAC tag");
                    // Finish the conditional block before continuing with the next step.
                    }

                    // Display Bob's UTF-8 reply only after its HMAC tag passed verification.
                    System.out.println("Alice received: " + new String(reply, StandardCharsets.UTF_8));
                // Finish this request-reply round before the loop advances.
                }

            // Finish Alice's task after its communication steps complete.
            }
        // Finish defining Alice and add this agent to the environment.
        });
        // Register Bob as a participant and define that agent's task.
        env.add(new Agent("bob") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Bob's communication steps; propagate cryptographic failures to the environment.
            public void task() throws Exception {
                // TODO: Receive and verify each message before displaying it.
                // Send an authenticated reply in each of the ten rounds.

                // Create Bob's own HMAC-SHA256 instance for authenticating message bytes.
                final Mac bob = Mac.getInstance("HmacSHA256");
                // Initialize Bob's HMAC instance with the secret key shared by both participants.
                bob.init(key);

                // Perform one request-reply round, repeating for indices 0 through 9.
                for (int i = 0; i < 10; i++) {
                    // Receive Alice's message bytes and wait to use them until the tag has been verified.
                    final byte[] message = receive("alice");
                    // Receive the HMAC tag associated with Alice's immediately preceding message.
                    final byte[] receivedTag = receive("alice");

                    // Compute Bob's expected HMAC over the received message using the shared key.
                    final byte[] expectedTag = bob.doFinal(message);

                    // Reject a received message if its supplied tag differs from the locally computed tag.
                    if (!MessageDigest.isEqual(receivedTag, expectedTag)) {
                        // Stop the agent task so an unauthenticated message is not displayed or processed.
                        throw new SecurityException("Invalid HMAC tag");
                    // Finish the conditional block before continuing with the next step.
                    }

                    // Display Alice's UTF-8 message only after its HMAC has passed verification.
                    System.out.println("Bob received: " + new String(message, StandardCharsets.UTF_8));

                    // Include the current round number in Bob's reply to Alice.
                    final String reply = "Hello Alice " + i;
                    // Encode Bob's reply as UTF-8 before authenticating and sending these exact bytes.
                    final byte[] replyBytes = reply.getBytes(StandardCharsets.UTF_8);
                    // Compute Bob's reply HMAC; this Mac instance is independent of Alice's instance.
                    final byte[] tag = bob.doFinal(replyBytes);

                    // Send Alice Bob's plaintext reply bytes, followed separately by its HMAC tag.
                    send("alice", replyBytes);
                    // Send Alice the HMAC tag immediately after the message it authenticates.
                    send("alice", tag);
                // Finish this request-reply round before the loop advances.
                }
            // Finish Bob's task after its communication steps complete.
            }
        // Finish defining Bob and add this agent to the environment.
        });

        env.connect("alice", "bob");
        env.start();
    }
}
