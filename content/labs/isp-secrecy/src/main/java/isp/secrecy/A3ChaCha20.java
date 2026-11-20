package isp.secrecy;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.ChaCha20ParameterSpec;

import fri.isp.Agent;
import fri.isp.Environment;

/**
 * TASK:
 * Assuming Alice and Bob know a shared secret key in advance, encrypt their
 * messages
 * using the ChaCha20 stream cipher to provide confidentiality. Perform ten
 * request-reply rounds:
 * Alice sends one encrypted message and Bob sends one encrypted reply in each
 * round
 * (twenty encrypted messages in total). Both agents must decrypt and display
 * received messages.
 * This exercise does not provide message authentication.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/ChaCha20ParameterSpec.html
 */
public class A3ChaCha20 {
    public static void main(String[] args) throws Exception {
        // STEP 1: Alice and Bob beforehand agree upon a cipher algorithm and a shared
        // secret key
        // Both agents can access this shared key from the enclosing method
        final Key key = KeyGenerator.getInstance("ChaCha20").generateKey();

        // STEP 2: Setup communication
        final Environment env = new Environment();

        // Register Alice as a participant and define that agent's task.
        env.add(new Agent("alice") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Alice's communication steps; propagate cryptographic failures to the environment.
            public void task() throws Exception {
                // Store the plaintext Alice will send to Bob in each round.
                final String message = "I love you Bob. Kisses, Alice.";
                /*
                 * TODO STEP 3:
                 * Alice creates, encrypts and sends a message to Bob. Bob replies to the
                 * message.
                 * Repeat for ten request-reply rounds.
                 *
                 * For this exercise, explicitly specify a 12-byte nonce and an initial block
                 * counter using ChaCha20ParameterSpec. Use a fresh nonce for every encryption,
                 * including Bob's replies: nonces must not repeat under the shared key.
                 * Make the nonce and initial counter available to the receiver; neither is
                 * secret.
                 */

                // Perform one request-reply round, repeating for indices 0 through 9.
                for (int i = 0; i < 10; i++) {
                    // Create a separate ChaCha20 cipher instance for this encryption or decryption operation.
                    Cipher cipher = Cipher.getInstance("ChaCha20");
                    // Allocate the 12-byte nonce required by this ChaCha20 parameter format.
                    byte[] nonce = new byte[12];
                    // Fill the nonce with cryptographically strong random bytes to make reuse unlikely.
                    SecureRandom.getInstanceStrong().nextBytes(nonce);
                    // Start this message at ChaCha20 block counter zero; the nonce distinguishes messages.
                    int counter = 0;

                    // Initialize ChaCha20 encryption with the shared key, this nonce, and the initial block counter.
                    cipher.init(Cipher.ENCRYPT_MODE, key, new ChaCha20ParameterSpec(nonce, counter));
                    // Encode the message as UTF-8 and encrypt it with the chosen stream-cipher parameters.
                    byte[] ciphertext = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));

                    // Send Bob the public nonce used for this ChaCha20 ciphertext.
                    send("bob", nonce);
                    // Send Bob the initial counter as one byte; the value zero used here fits this encoding.
                    send("bob", new byte[] { (byte) counter });
                    // Send Bob the ciphertext after its public decryption parameters.
                    send("bob", ciphertext);

                    // Receive the fresh nonce Bob selected for his reply.
                    byte[] bobNonce = receive("bob");
                    // Receive Bob's initial counter, encoded as one byte in this exercise.
                    byte[] bobCounterBytes = receive("bob");
                    // Receive Bob's encrypted reply after its IV or nonce parameters.
                    byte[] bobCiphertext = receive("bob");

                    // Interpret Bob's counter byte as unsigned; this one-byte encoding suffices for the counter zero used here.
                    int bobCounter = bobCounterBytes[0] & 0xFF;

                    // Create a separate ChaCha20 cipher instance for this encryption or decryption operation.
                    Cipher decrypt = Cipher.getInstance("ChaCha20");
                    // Start configuring the reply decryptor with the shared ChaCha20 key.
                    decrypt.init(Cipher.DECRYPT_MODE, key,
                            // Supply Bob's nonce and initial counter to reproduce his reply's keystream.
                            new ChaCha20ParameterSpec(bobNonce, bobCounter));

                    // Apply Bob's ChaCha20 parameters to recover his original reply bytes.
                    byte[] plaintext = decrypt.doFinal(bobCiphertext);

                    // Begin displaying Bob's decrypted reply with a label identifying Alice as the recipient.
                    System.out.println("Alice received: " +
                            // Finish the print call by decoding the recovered plaintext bytes as UTF-8.
                            new String(plaintext, StandardCharsets.UTF_8));

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
                // TODO: Receive, decrypt and display Alice's message, then encrypt and send
                // a reply with a fresh nonce. Repeat for ten request-reply rounds.

                // Perform one request-reply round, repeating for indices 0 through 9.
                for (int i = 0; i < 10; i++) {
                    // Receive the public nonce used for Alice's current ChaCha20 message.
                    byte[] nonce = receive("alice");
                    // Receive Alice's initial counter in the exercise's one-byte representation.
                    byte[] counterBytes = receive("alice");
                    // Receive Alice's ciphertext in the same order in which she sent the parameters and data.
                    byte[] ciphertext = receive("alice");

                    // Recover the unsigned counter value; the example sends only zero, which fits in one byte.
                    int counter = counterBytes[0] & 0xFF;

                    // Create a separate ChaCha20 cipher instance for this encryption or decryption operation.
                    Cipher cipher = Cipher.getInstance("ChaCha20");
                    // Start configuring ChaCha20 decryption with the shared key.
                    cipher.init(Cipher.DECRYPT_MODE, key,
                            // Complete the setup with Alice's nonce and initial counter so the keystream matches.
                            new ChaCha20ParameterSpec(nonce, counter));

                    // Apply the ChaCha20 keystream to recover Alice's original plaintext bytes.
                    byte[] plaintext = cipher.doFinal(ciphertext);

                    // Begin displaying Alice's decrypted message with a label identifying Bob as the recipient.
                    System.out.println("Bob received: " +
                            // Finish the print call by decoding the recovered plaintext bytes as UTF-8.
                            new String(plaintext, StandardCharsets.UTF_8));

                    // Store Bob's plaintext reply for this round.
                    String message = "I love you Alice. Kisses, Bob.";

                    // Allocate a separate 12-byte nonce for Bob's reply.
                    byte[] replyNonce = new byte[12];
                    // Randomize Bob's reply nonce; the shared key must never be used with a repeated nonce.
                    SecureRandom.getInstanceStrong().nextBytes(replyNonce);
                    // Start Bob's reply at block zero under its new nonce.
                    int replyCounter = 0;

                    // Create a separate ChaCha20 cipher instance for this encryption or decryption operation.
                    Cipher encrypt = Cipher.getInstance("ChaCha20");
                    // Begin configuring ChaCha20 encryption for Bob's reply using the shared key.
                    encrypt.init(Cipher.ENCRYPT_MODE, key,
                            // Complete the reply setup with Bob's fresh nonce and initial block counter.
                            new ChaCha20ParameterSpec(replyNonce, replyCounter));

                    // Convert Bob's reply to UTF-8 and encrypt it with the new reply nonce and counter.
                    byte[] replyCiphertext = encrypt.doFinal(message.getBytes(StandardCharsets.UTF_8));

                    // Send Alice the fresh public nonce used for Bob's reply.
                    send("alice", replyNonce);
                    // Send Alice the reply counter as one byte; the value zero used here fits this encoding.
                    send("alice", new byte[] { (byte) replyCounter });
                    // Send Alice the encrypted reply after its public decryption parameters.
                    send("alice", replyCiphertext);

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
