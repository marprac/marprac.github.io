package isp;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.KeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * TASK:
 * Assuming Alice and Bob know a shared secret key, secure the channel using
 * AES in GCM mode. Complete ten request-reply rounds: Alice sends one
 * encrypted message and Bob sends one encrypted reply in each round (twenty
 * encrypted messages in total). Both agents must decrypt and display the
 * messages they receive. Use a fresh IV for every encryption and send it
 * together with the ciphertext; the IV need not be secret.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
 */
public class A1AgentCommunicationGCM {
    public static void main(String[] args) throws Exception {
        /*
         * Alice and Bob share a secret session key that will be
         * used for AES in GCM.
         */
        final Key key = KeyGenerator.getInstance("AES").generateKey();

        final Environment env = new Environment();

        // Register Alice as an independently executing participant.
        env.add(new Agent("alice") {
            // Implement the task method inherited from Agent.
            @Override
            // Run Alice's protocol and propagate any cryptographic or communication failure.
            public void task() throws Exception {
                // Complete ten rounds, sending one message and receiving one reply per round.
                for (int i = 0; i < 10; i++) {
                    // Prepare the plaintext Alice wants to send in this round.
                    final String text = "I hope you get this message intact and in secret. Kisses, Alice.";
                    // Encode the text consistently as UTF-8 bytes before encryption.
                    final byte[] pt = text.getBytes(StandardCharsets.UTF_8);

                    // Create Alice's AES-GCM cipher for encryption and authentication.
                    final Cipher alice = Cipher.getInstance("AES/GCM/NoPadding");
                    // Use the shared key and let the provider generate a fresh encryption IV.
                    alice.init(Cipher.ENCRYPT_MODE, key);
                    // Encrypt the plaintext and append its GCM authentication tag.
                    final byte[] ct = alice.doFinal(pt);

                    // Retrieve the nonsecret IV required to decrypt this ciphertext.
                    final byte[] iv = alice.getIV();
                    // Send the IV before its matching ciphertext so Bob can initialize decryption.
                    send("bob", iv);
                    // Send the ciphertext together with its appended authentication tag.
                    send("bob", ct);

                    // Wait for the IV Bob generated for his reply.
                    final byte[] bobIV = receive("bob");
                    // Receive Bob's encrypted reply and its authentication tag.
                    final byte[] bobCT = receive("bob");

                    // Create a separate AES-GCM cipher for receiving Bob's reply.
                    final Cipher aliceDec = Cipher.getInstance("AES/GCM/NoPadding");
                    // Supply Bob's IV and the expected 128-bit authentication-tag length.
                    final GCMParameterSpec specs = new GCMParameterSpec(128, bobIV);
                    // Initialize authenticated decryption using the shared key and received IV.
                    aliceDec.init(Cipher.DECRYPT_MODE, key, specs);
                    // Verify the tag and decrypt; modified ciphertext causes an exception.
                    final byte[] pt2 = aliceDec.doFinal(bobCT);

                    // Decode the authenticated reply as UTF-8 and display it for Alice.
                    System.out.println("Alice received: " + new String(pt2, StandardCharsets.UTF_8));
                } // Finish this request-reply round.

            } // Finish Alice's task after all ten rounds.
        }); // Finish defining and registering Alice's agent.

        // Register Bob as the participant that receives Alice's requests and replies.
        env.add(new Agent("bob") {
            // Implement the task method inherited from Agent.
            @Override
            // Run Bob's protocol and propagate any cryptographic or communication failure.
            public void task() throws Exception {
                // Receive and reply once per iteration, matching Alice's ten rounds.
                for (int i = 0; i < 10; i++) {
                    // Receive Alice's IV before reading its matching ciphertext.
                    final byte[] aliceIV = receive("alice");
                    // Receive Alice's ciphertext, including the GCM authentication tag.
                    final byte[] aliceCT = receive("alice");

                    // Create Bob's AES-GCM cipher for authenticated decryption.
                    final Cipher bob = Cipher.getInstance("AES/GCM/NoPadding");
                    // Use Alice's IV and expect a 128-bit authentication tag.
                    final GCMParameterSpec specs = new GCMParameterSpec(128, aliceIV);
                    // Initialize decryption using the shared key and Alice's GCM parameters.
                    bob.init(Cipher.DECRYPT_MODE, key, specs);
                    // Authenticate and decrypt Alice's message; an invalid tag raises an exception.
                    final byte[] pt = bob.doFinal(aliceCT);

                    // Decode Alice's authenticated plaintext using UTF-8 and display it.
                    System.out.println("Bob received: " + new String(pt, StandardCharsets.UTF_8));

                    // Prepare Bob's plaintext reply for this round.
                    final String text = "I hope you get this message intact and in secret. Kisses, Bob.";
                    // Encode the reply as UTF-8 bytes before encrypting it.
                    final byte[] reply = text.getBytes(StandardCharsets.UTF_8);

                    // Create a separate AES-GCM cipher for Bob's outgoing reply.
                    final Cipher bobEnc = Cipher.getInstance("AES/GCM/NoPadding");
                    // Initialize reply encryption with the shared key and a fresh generated IV.
                    bobEnc.init(Cipher.ENCRYPT_MODE, key);
                    // Encrypt the reply and append its GCM authentication tag.
                    final byte[] ct = bobEnc.doFinal(reply);

                    // Retrieve the fresh IV used for this encrypted reply.
                    final byte[] iv = bobEnc.getIV();
                    // Send Alice the reply IV before the ciphertext.
                    send("alice", iv);
                    // Send Alice the encrypted reply and its authentication tag.
                    send("alice", ct);
                } // Finish this receive-and-reply round.

            } // Finish Bob's task after all ten rounds.
        }); // Finish defining and registering Bob's agent.

        env.connect("alice", "bob");
        env.start();
    }
}
