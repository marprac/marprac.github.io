package isp;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;

import fri.isp.Agent;
import fri.isp.Environment;

/**
 * TASK:
 * Alice sends a confidential message to Bob over an insecure channel using
 * RSA. Alice encrypts her message with Bob's public key and sends the
 * ciphertext to Bob; Bob decrypts it with his private key and prints the
 * message. Both agents access the key pairs through the variables from the
 * enclosing main() method.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 */
public class A3AgentCommunicationRSA {
    public static void main(String[] args) throws Exception {

        final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        final KeyPair aliceKP = kpg.generateKeyPair();
        final KeyPair bobKP = kpg.generateKeyPair();

        final Environment env = new Environment();

        // Register Alice as the sender of the confidential RSA message.
        env.add(new Agent("alice") {
            // Implement the task method inherited from Agent.
            @Override
            // Run Alice's encryption and sending steps, propagating failures to the framework.
            public void task() throws Exception {
                /*
                 * - Create an RSA cipher and encrypt a message using Bob's PK;
                 * - Send the CT to Bob;
                 * - Reference the keys using the variables aliceKP and bobKP from the enclosing
                 * main() method.
                 */
                // Prepare the short plaintext message addressed to Bob.
                final String text = "Hi, Bob. Kind regards, Alice.";
                // Encode the message as UTF-8 bytes before encrypting it.
                final byte[] pt = text.getBytes(StandardCharsets.UTF_8);

                // Select RSA with OAEP padding; ECB is part of Java's RSA transformation name.
                final Cipher rsaEnc = Cipher.getInstance("RSA/ECB/OAEPPadding");
                // Encrypt with Bob's public key so the matching private key can decrypt.
                rsaEnc.init(Cipher.ENCRYPT_MODE, bobKP.getPublic());
                // Encrypt the short message into one RSA-OAEP ciphertext.
                final byte[] ct = rsaEnc.doFinal(pt);

                // Send only the ciphertext to Bob over the communication channel.
                send("bob", ct);

            } // Finish Alice's task after sending the encrypted message.
        }); // Finish defining and registering Alice's agent.

        // Register Bob as the recipient that owns the required RSA private key.
        env.add(new Agent("bob") {
            // Implement the task method inherited from Agent.
            @Override
            // Run Bob's receiving and decryption steps, propagating failures to the framework.
            public void task() throws Exception {
                /*
                 * - Receive the incoming message;
                 * - Create an RSA cipher and decrypt incoming CT using Bob's SK;
                 * - Print the message;
                 * - Reference the keys using the variables aliceKP and bobKP from the enclosing
                 * main() method.
                 */
                // Wait for the RSA ciphertext sent by Alice.
                final byte[] ct = receive("alice");

                // Select the same RSA-OAEP transformation Alice used for encryption.
                final Cipher rsaDec = Cipher.getInstance("RSA/ECB/OAEPPadding");
                // Initialize decryption with Bob's private RSA key.
                rsaDec.init(Cipher.DECRYPT_MODE, bobKP.getPrivate());
                // Decrypt the ciphertext and validate its OAEP encoding.
                final byte[] pt = rsaDec.doFinal(ct);

                // Decode the plaintext as UTF-8 and display Alice's message for Bob.
                System.out.println("Bob received: " + new String(pt, StandardCharsets.UTF_8));
            } // Finish Bob's task after displaying the decrypted message.
        }); // Finish defining and registering Bob's agent.

        env.connect("alice", "bob");
        env.start();
    }
}
