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

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                for (int i = 0; i < 10; i++) {
                    final String text = "I hope you get this message intact and in secret. Kisses, Alice.";
                    final byte[] pt = text.getBytes(StandardCharsets.UTF_8);

                    final Cipher alice = Cipher.getInstance("AES/GCM/NoPadding");
                    alice.init(Cipher.ENCRYPT_MODE, key);
                    final byte[] ct = alice.doFinal(pt);

                    final byte[] iv = alice.getIV();
                    send("bob", iv);
                    send("bob", ct);

                    final byte[] bobIV = receive("bob");
                    final byte[] bobCT = receive("bob");

                    final Cipher aliceDec = Cipher.getInstance("AES/GCM/NoPadding");
                    final GCMParameterSpec specs = new GCMParameterSpec(128, bobIV);
                    aliceDec.init(Cipher.DECRYPT_MODE, key, specs);
                    final byte[] pt2 = aliceDec.doFinal(bobCT);

                    System.out.println("Alice received: " + new String(pt2, StandardCharsets.UTF_8));
                }

            }
        });

        env.add(new Agent("bob") {
            @Override
            public void task() throws Exception {
                for (int i = 0; i < 10; i++) {
                    final byte[] aliceIV = receive("alice");
                    final byte[] aliceCT = receive("alice");

                    final Cipher bob = Cipher.getInstance("AES/GCM/NoPadding");
                    final GCMParameterSpec specs = new GCMParameterSpec(128, aliceIV);
                    bob.init(Cipher.DECRYPT_MODE, key, specs);
                    final byte[] pt = bob.doFinal(aliceCT);

                    System.out.println("Bob received: " + new String(pt, StandardCharsets.UTF_8));

                    final String text = "I hope you get this message intact and in secret. Kisses, Bob.";
                    final byte[] reply = text.getBytes(StandardCharsets.UTF_8);

                    final Cipher bobEnc = Cipher.getInstance("AES/GCM/NoPadding");
                    bobEnc.init(Cipher.ENCRYPT_MODE, key);
                    final byte[] ct = bobEnc.doFinal(reply);

                    final byte[] iv = bobEnc.getIV();
                    send("alice", iv);
                    send("alice", ct);
                }

            }
        });

        env.connect("alice", "bob");
        env.start();
    }
}
