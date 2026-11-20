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

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                /*
                 * - Create an RSA cipher and encrypt a message using Bob's PK;
                 * - Send the CT to Bob;
                 * - Reference the keys using the variables aliceKP and bobKP from the enclosing
                 * main() method.
                 */
                final String text = "Hi, Bob. Kind regards, Alice.";
                final byte[] pt = text.getBytes(StandardCharsets.UTF_8);

                final Cipher rsaEnc = Cipher.getInstance("RSA/ECB/OAEPPadding");
                rsaEnc.init(Cipher.ENCRYPT_MODE, bobKP.getPublic());
                final byte[] ct = rsaEnc.doFinal(pt);

                send("bob", ct);

            }
        });

        env.add(new Agent("bob") {
            @Override
            public void task() throws Exception {
                /*
                 * - Receive the incoming message;
                 * - Create an RSA cipher and decrypt incoming CT using Bob's SK;
                 * - Print the message;
                 * - Reference the keys using the variables aliceKP and bobKP from the enclosing
                 * main() method.
                 */
                final byte[] ct = receive("alice");

                final Cipher rsaDec = Cipher.getInstance("RSA/ECB/OAEPPadding");
                rsaDec.init(Cipher.DECRYPT_MODE, bobKP.getPrivate());
                final byte[] pt = rsaDec.doFinal(ct);

                System.out.println("Bob received: " + new String(pt, StandardCharsets.UTF_8));
            }
        });

        env.connect("alice", "bob");
        env.start();
    }
}
