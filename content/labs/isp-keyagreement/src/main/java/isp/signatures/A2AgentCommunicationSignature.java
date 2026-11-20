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

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                // create a message, sign it,
                // and send the message, signature pair to bob
                // receive the message-signature pair, verify the signature
                // repeat 10 times
                for (int i = 0; i < 10; i++) {
                    String message = "Hello Bob! Round " + (i + 1);
                    byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

                    Signature signer = Signature.getInstance("SHA256withECDSA");
                    signer.initSign(aliceKeys.getPrivate());
                    signer.update(messageBytes);
                    byte[] signature = signer.sign();

                    send("bob", messageBytes);
                    send("bob", signature);

                    print("Sent: %s", message);

                    byte[] reply = receive("bob");
                    byte[] replySignature = receive("bob");

                    Signature verifier = Signature.getInstance("SHA256withECDSA");
                    verifier.initVerify(bobKeys.getPublic());
                    verifier.update(reply);

                    if (verifier.verify(replySignature)) {
                        print("Valid signature from Bob: %s",
                                new String(reply, StandardCharsets.UTF_8));
                    } else {
                        print("Invalid signature from Bob!");
                        throw new SecurityException("Invalid signature");
                    }
                }
            }
        });

        env.add(new Agent("bob") {
            @Override
            public void task() throws Exception {
                for (int i = 0; i < 10; i++) {
                    byte[] message = receive("alice");
                    byte[] signature = receive("alice");

                    Signature verifier = Signature.getInstance("SHA256withECDSA");
                    verifier.initVerify(aliceKeys.getPublic());
                    verifier.update(message);

                    if (verifier.verify(signature)) {
                        print("Valid signature from Alice: %s",
                                new String(message, StandardCharsets.UTF_8));
                    } else {
                        print("Invalid signature from Alice!");
                        throw new SecurityException("Invalid signature");
                    }

                    String reply = "Hello Alice! Reply " + (i + 1);
                    byte[] replyBytes = reply.getBytes(StandardCharsets.UTF_8);

                    Signature signer = Signature.getInstance("SHA256withECDSA");
                    signer.initSign(bobKeys.getPrivate());
                    signer.update(replyBytes);
                    byte[] replySignature = signer.sign();

                    send("alice", replyBytes);
                    send("alice", replySignature);

                    print("Sent: %s", reply);
                }

            }
        });

        env.connect("alice", "bob");
        env.start();
    }
}
