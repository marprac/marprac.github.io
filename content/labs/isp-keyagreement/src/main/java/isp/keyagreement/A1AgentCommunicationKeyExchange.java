package isp.keyagreement;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;

/*
 * Implement the unauthenticated key exchange from the slides between Alice and Bob using
 * public-key encryption to establish a shared symmetric secret (not DH or ECDH alone).
 * Then Alice sends Bob a message encrypted with AES-GCM. Bob decrypts and displays it.
 * The public key is not authenticated, so the exchange is vulnerable to active attackers.
 */
public class A1AgentCommunicationKeyExchange {
    public static void main(String[] args) {
        final Environment env = new Environment();

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair keyPair = kpg.generateKeyPair();

                send("bob", keyPair.getPublic().getEncoded());

                byte[] encryptedKey = receive("bob");

                Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
                byte[] sharedSecret = rsa.doFinal(encryptedKey);

                SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, "AES");

                Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                aes.init(Cipher.ENCRYPT_MODE, aesKey);

                byte[] ciphertext = aes.doFinal(
                        "Hey Bob!".getBytes(StandardCharsets.UTF_8));

                send("bob", aes.getIV());
                send("bob", ciphertext);

            }
        });

        env.add(new Agent("bob") {
            @Override
            public void task() throws Exception {
                byte[] publicKeyBytes = receive("alice");

                PublicKey alicePublicKey = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(publicKeyBytes));

                byte[] sharedSecret = new byte[16];
                new SecureRandom().nextBytes(sharedSecret);

                Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                rsa.init(Cipher.ENCRYPT_MODE, alicePublicKey);

                byte[] encryptedKey = rsa.doFinal(sharedSecret);
                send("alice", encryptedKey);

                SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, "AES");

                byte[] iv = receive("alice");
                byte[] ciphertext = receive("alice");

                Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));

                byte[] plaintext = aes.doFinal(ciphertext);

                print("I got: %s", new String(plaintext, StandardCharsets.UTF_8));

            }
        });

        env.connect("alice", "bob");
        env.start();
    }
}
