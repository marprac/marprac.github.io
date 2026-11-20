package isp.secrecy;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.KeyGenerator;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;

/**
 * TASK:
 * Assuming Alice and Bob know a shared secret key in advance, encrypt their
 * messages
 * using AES in CBC mode to provide confidentiality. Perform ten request-reply
 * rounds:
 * Alice sends one encrypted message and Bob sends one encrypted reply in each
 * round
 * (twenty encrypted messages in total). Both agents must decrypt and display
 * received messages.
 * This exercise does not provide message authentication.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/IvParameterSpec.html
 */
public class A1AESInCBCMode {
    public static void main(String[] args) throws Exception {
        // STEP 1: Alice and Bob beforehand agree upon a cipher algorithm and a shared
        // secret key
        // Both agents can access this shared key from the enclosing method
        final Key key = KeyGenerator.getInstance("AES").generateKey();

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
                 * Use a fresh, unpredictable IV for every encryption, including Bob's replies.
                 * Send the IV along with the ciphertext; it need not be secret.
                 * A cipher-generated IV can be accessed via cipher.getIV().
                 */
                // Perform one request-reply round, repeating for indices 0 through 9.
                for (int i = 0; i < 10; i++) {
                    // Create AES in CBC mode with padding; this mode encrypts but does not authenticate messages.
                    Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    // Initialize AES encryption with the shared secret key; this call generates a fresh IV.
                    aes.init(Cipher.ENCRYPT_MODE, key);
                    // Encode the message as UTF-8 and encrypt it; CBC/CTR alone adds no authentication.
                    byte[] ciphertext = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));
                    // Read the public IV generated during this encryption so the receiver can reproduce the setup.
                    byte[] iv = aes.getIV();

                    // Send Bob the public IV needed to initialize decryption.
                    send("bob", iv);
                    // Send Bob the ciphertext after its public decryption parameters.
                    send("bob", ciphertext);
                    // Wait for the IV that Bob used to encrypt his reply.
                    byte[] bobIv = receive("bob");
                    // Receive Bob's encrypted reply after its IV or nonce parameters.
                    byte[] bobCiphertext = receive("bob");

                    // Prepare AES to decrypt Bob's reply with the shared key and Bob's IV.
                    aes.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(bobIv));
                    // Decrypt Bob's ciphertext into the reply's original bytes.
                    byte[] plaintext = aes.doFinal(bobCiphertext);
                    // Decode the recovered bytes as UTF-8 and display Bob's reply to Alice.
                    System.out.println("Alice received: " + new String(plaintext, StandardCharsets.UTF_8));
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
                /*
                 * TODO STEP 4
                 * Bob receives, decrypts and displays a message.
                 * Wrap the received IV bytes in IvParameterSpec for the init() call:
                 *
                 * IvParameterSpec ivSpec = new IvParameterSpec(iv);
                 * aes.init(Cipher.DECRYPT_MODE, key, ivSpec);
                 *
                 * Encrypt and send a reply using a fresh IV in each round.
                 */
                // Perform one request-reply round, repeating for indices 0 through 9.
                for (int i = 0; i < 10; i++) {
                    // Receive Alice's public IV before receiving its matching ciphertext.
                    byte[] iv = receive("alice");
                    // Receive Alice's ciphertext in the same order in which she sent the parameters and data.
                    byte[] ciphertext = receive("alice");

                    // Create AES in CBC mode with padding; this mode encrypts but does not authenticate messages.
                    Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    // Prepare AES decryption with the shared key and the received IV.
                    aes.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                    // Decrypt the received ciphertext into its original plaintext bytes.
                    byte[] plaintext = aes.doFinal(ciphertext);

                    // Decode the recovered bytes as UTF-8 and display Alice's message to Bob.
                    System.out.println("Bob received: " + new String(plaintext, StandardCharsets.UTF_8));

                    // Store Bob's plaintext reply for this round.
                    String message = "I love you Alice. Kisses, Bob.";
                    // Initialize AES encryption with the shared secret key; this call generates a fresh IV.
                    aes.init(Cipher.ENCRYPT_MODE, key);
                    // Convert Bob's reply to UTF-8 and encrypt it with the fresh reply IV.
                    byte[] replyCiphertext = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));
                    // Read the new IV generated for Bob's reply rather than reusing Alice's IV.
                    byte[] replyIv = aes.getIV();

                    // Send Alice the public IV generated for Bob's reply.
                    send("alice", replyIv);
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
