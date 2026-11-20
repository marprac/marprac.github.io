package isp.secrecy;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * TASK:
 * Assuming Alice and Bob know a shared secret key in advance, encrypt their
 * messages
 * using AES in counter mode to provide confidentiality. Perform ten
 * request-reply rounds:
 * Alice sends one encrypted message and Bob sends one encrypted reply in each
 * round
 * (twenty encrypted messages in total). Both agents must decrypt and display
 * received messages.
 * This exercise does not provide message authentication.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/IvParameterSpec.html
 */
public class A2AESInCTRMode {
    public static void main(String[] args) throws Exception {
        // STEP 1: Alice and Bob beforehand agree upon a cipher algorithm and a shared
        // secret key
        // Both agents can access this shared key from the enclosing method
        final Key key = KeyGenerator.getInstance("AES").generateKey();

        // STEP 2: Setup communication
        final Environment env = new Environment();

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                final String message = "I love you Bob. Kisses, Alice.";
                /*
                 * TODO STEP 3:
                 * Alice creates, encrypts and sends a message to Bob. Bob replies to the
                 * message.
                 * Repeat for ten request-reply rounds.
                 *
                 * Use a fresh IV for every encryption, including Bob's replies.
                 * Counter blocks must never repeat under this shared key, in either direction;
                 * the counter ranges used by different messages must not overlap.
                 * Send the IV along with the ciphertext; it need not be secret.
                 * A cipher-generated IV can be accessed via cipher.getIV().
                 */
                for (int i = 0; i < 10; i++) {
                    Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
                    aes.init(Cipher.ENCRYPT_MODE, key);
                    byte[] ciphertext = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));
                    byte[] iv = aes.getIV();

                    send("bob", iv);
                    send("bob", ciphertext);
                    byte[] bobIv = receive("bob");
                    byte[] bobCiphertext = receive("bob");

                    aes.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(bobIv));
                    byte[] plaintext = aes.doFinal(bobCiphertext);
                    System.out.println("Alice received: " + new String(plaintext, StandardCharsets.UTF_8));
                }
            }
        });

        env.add(new Agent("bob") {
            @Override
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

                for (int i = 0; i < 10; i++) {
                    byte[] iv = receive("alice");
                    byte[] ciphertext = receive("alice");

                    Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
                    aes.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                    byte[] plaintext = aes.doFinal(ciphertext);

                    System.out.println("Bob received: " + new String(plaintext, StandardCharsets.UTF_8));

                    String message = "I love you Alice. Kisses, Bob.";
                    aes.init(Cipher.ENCRYPT_MODE, key);
                    byte[] replyCiphertext = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));
                    byte[] replyIv = aes.getIV();

                    send("alice", replyIv);
                    send("alice", replyCiphertext);

                }

            }
        });

        env.connect("alice", "bob");
        env.start();
    }
}
