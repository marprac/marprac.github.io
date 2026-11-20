package isp.signatures;

import fri.isp.Agent;
import fri.isp.Environment;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

/*
 * Assuming Alice and Bob know each other's authentic public key, exchange signed messages
 * using ECDSA. Complete ten request-reply rounds: Alice sends a signed message and Bob
 * sends a signed reply in each round. Each recipient verifies the signature and rejects
 * invalid messages. Signatures provide integrity and evidence of the signer, not secrecy.
 */
public class A2AgentCommunicationSignature {
    public static void main(String[] args) {
        final Environment env = new Environment();

        // Create key pairs
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final KeyPair aliceKeys = kpg.generateKeyPair();
        final KeyPair bobKeys = kpg.generateKeyPair();

        // Create Alice's participant and register its task with the environment.
        env.add(new Agent("alice") {
            // Use the Agent task hook called when this participant starts.
            @Override
            // Run the protocol steps; failures propagate as exceptions from this task.
            public void task() throws Exception {
                // create a message, sign it,
                // and send the message, signature pair to bob
                // receive the message-signature pair, verify the signature
                // repeat 10 times
                // Perform ten request-reply rounds, using i as the zero-based round counter.
                for (int i = 0; i < 10; i++) {
                    // Build Alice's greeting with a human-readable round number.
                    String message = "Hello Bob! Round " + (i + 1);
                    // Encode the message as UTF-8 before signing and sending those same bytes.
                    byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

                    // Create an ECDSA signer that hashes the message with SHA-256.
                    Signature signer = Signature.getInstance("SHA256withECDSA");
                    // Initialize signing with Alice's private EC key.
                    signer.initSign(aliceKeys.getPrivate());
                    // Feed Alice's exact outgoing message bytes into the signature calculation.
                    signer.update(messageBytes);
                    // Complete the ECDSA signature and store its encoded bytes.
                    byte[] signature = signer.sign();

                    // Send the message bytes in the clear; signatures do not encrypt them.
                    send("bob", messageBytes);
                    // Send the signature after the message so Bob can verify that pair.
                    send("bob", signature);

                    // Display the greeting Alice just sent in this round.
                    print("Sent: %s", message);

                    // Wait for Bob's reply message bytes for this round.
                    byte[] reply = receive("bob");
                    // Receive the signature corresponding to Bob's reply.
                    byte[] replySignature = receive("bob");

                    // Create an ECDSA verifier using the same SHA-256 signature algorithm.
                    Signature verifier = Signature.getInstance("SHA256withECDSA");
                    // Use Bob's already trusted public key to verify the reply.
                    verifier.initVerify(bobKeys.getPublic());
                    // Provide the exact received reply bytes to the verifier.
                    verifier.update(reply);

                    // Verify Bob's signature and enter this branch only if it succeeds.
                    if (verifier.verify(replySignature)) {
                        // Begin reporting a reply verified with Bob's trusted public key.
                        print("Valid signature from Bob: %s",
                                // Decode the verified reply as UTF-8 and finish printing it.
                                new String(reply, StandardCharsets.UTF_8));
                    // Handle the alternative case where the received signature does not verify.
                    } else {
                        // Report the failed verification before rejecting this exchange.
                        print("Invalid signature from Bob!");
                        // Abort this protocol task rather than accepting an invalid message.
                        throw new SecurityException("Invalid signature");
                    // End the current verification branch or request-reply loop.
                    }
                // End the current verification branch or request-reply loop.
                }
            // End Alice's task method.
            }
        // Finish Alice's anonymous Agent definition and its registration call.
        });

        // Create Bob's participant and register its task with the environment.
        env.add(new Agent("bob") {
            // Use the Agent task hook called when this participant starts.
            @Override
            // Run the protocol steps; failures propagate as exceptions from this task.
            public void task() throws Exception {
                // Perform ten request-reply rounds, using i as the zero-based round counter.
                for (int i = 0; i < 10; i++) {
                    // Receive Alice's unencrypted message bytes for the current round.
                    byte[] message = receive("alice");
                    // Receive the ECDSA signature sent immediately after Alice's message.
                    byte[] signature = receive("alice");

                    // Create an ECDSA verifier using the same SHA-256 signature algorithm.
                    Signature verifier = Signature.getInstance("SHA256withECDSA");
                    // Use Alice's already trusted public EC key to verify her message.
                    verifier.initVerify(aliceKeys.getPublic());
                    // Feed the exact received message bytes into signature verification.
                    verifier.update(message);

                    // Check Alice's signature and accept the message only in the successful branch.
                    if (verifier.verify(signature)) {
                        // Begin displaying Alice's message after its signature has verified.
                        print("Valid signature from Alice: %s",
                                // Decode the verified message bytes as UTF-8 and finish printing them.
                                new String(message, StandardCharsets.UTF_8));
                    // Handle the alternative case where the received signature does not verify.
                    } else {
                        // Report that Alice's message failed the signature check.
                        print("Invalid signature from Alice!");
                        // Abort this protocol task rather than accepting an invalid message.
                        throw new SecurityException("Invalid signature");
                    // End the current verification branch or request-reply loop.
                    }

                    // Create Bob's reply with the current one-based round number.
                    String reply = "Hello Alice! Reply " + (i + 1);
                    // Encode the reply as UTF-8 so transmitted and signed bytes match.
                    byte[] replyBytes = reply.getBytes(StandardCharsets.UTF_8);

                    // Create an ECDSA signer that hashes the message with SHA-256.
                    Signature signer = Signature.getInstance("SHA256withECDSA");
                    // Initialize reply signing with Bob's private EC key.
                    signer.initSign(bobKeys.getPrivate());
                    // Feed the exact outgoing reply bytes into the ECDSA signer.
                    signer.update(replyBytes);
                    // Finish signing Bob's reply and obtain its signature bytes.
                    byte[] replySignature = signer.sign();

                    // Send Bob's reply bytes in the clear for Alice to verify.
                    send("alice", replyBytes);
                    // Send the matching ECDSA reply signature as the next message.
                    send("alice", replySignature);

                    // Display the reply Bob sent in this round.
                    print("Sent: %s", reply);
                // End the current verification branch or request-reply loop.
                }

            // End Bob's task method.
            }
        // Finish Bob's anonymous Agent definition and its registration call.
        });

        env.connect("alice", "bob");
        env.start();
    }
}
