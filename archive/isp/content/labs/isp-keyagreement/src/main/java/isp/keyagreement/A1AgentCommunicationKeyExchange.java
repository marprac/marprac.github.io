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

        // Create Alice's participant and register its task with the environment.
        env.add(new Agent("alice") {
            // Use the Agent task hook called when this participant starts.
            @Override
            // Run the protocol steps; failures propagate as exceptions from this task.
            public void task() throws Exception {
                // Create an RSA key-pair generator for Alice to receive an encrypted session key.
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                // Select a 2048-bit key size for this key-pair generator.
                kpg.initialize(2048);
                // Generate Alice's RSA public key and matching private decryption key.
                KeyPair keyPair = kpg.generateKeyPair();

                // Send Alice's encoded public key to Bob; the private key stays local.
                send("bob", keyPair.getPublic().getEncoded());

                // Wait for Bob's RSA-encrypted session secret.
                byte[] encryptedKey = receive("bob");

                // Create RSA-OAEP with the named padding transformation.
                Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                // Configure RSA decryption with Alice's private key, which stays local.
                rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
                // Recover the AES session-key bytes that Bob encrypted for Alice.
                byte[] sharedSecret = rsa.doFinal(encryptedKey);

                // Wrap the shared 16-byte secret as an AES-128 key object.
                SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, "AES");

                // Create AES-GCM to encrypt data and detect ciphertext modification.
                Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                // Start encryption with the shared key and let the provider generate a fresh GCM IV.
                aes.init(Cipher.ENCRYPT_MODE, aesKey);

                // Encrypt and authenticate the message bytes supplied on the next line.
                byte[] ciphertext = aes.doFinal(
                        // Encode Alice's greeting as UTF-8 and finish the encryption call.
                        "Hey Bob!".getBytes(StandardCharsets.UTF_8));

                // Send the public GCM IV so Bob can initialize decryption.
                send("bob", aes.getIV());
                // Send the encrypted greeting and the authentication tag produced by GCM.
                send("bob", ciphertext);

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
                // Receive Alice's encoded RSA public key; this exchange does not authenticate that key.
                byte[] publicKeyBytes = receive("alice");

                // Choose an RSA key factory to reconstruct the received public key.
                PublicKey alicePublicKey = KeyFactory.getInstance("RSA")
                        // Decode Alice's X.509 public-key bytes into a Java PublicKey object.
                        .generatePublic(new X509EncodedKeySpec(publicKeyBytes));

                // Allocate 16 bytes for the AES-128 session secret Bob will share with Alice.
                byte[] sharedSecret = new byte[16];
                // Fill the session secret with cryptographically secure random bytes.
                new SecureRandom().nextBytes(sharedSecret);

                // Create RSA-OAEP with the named padding transformation.
                Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                // Configure RSA-OAEP encryption with the received public key.
                rsa.init(Cipher.ENCRYPT_MODE, alicePublicKey);

                // Encrypt the session secret for the holder of the matching RSA private key.
                byte[] encryptedKey = rsa.doFinal(sharedSecret);
                // Send the RSA-encrypted session secret to Alice.
                send("alice", encryptedKey);

                // Wrap the shared 16-byte secret as an AES-128 key object.
                SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, "AES");

                // Receive the public IV Alice used for this GCM ciphertext.
                byte[] iv = receive("alice");
                // Receive Alice's ciphertext and its GCM authentication tag.
                byte[] ciphertext = receive("alice");

                // Create AES-GCM to encrypt data and detect ciphertext modification.
                Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                // Initialize GCM decryption with the shared key, received IV, and 128-bit tag.
                aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));

                // Verify the GCM tag and recover plaintext; invalid ciphertext raises an exception.
                byte[] plaintext = aes.doFinal(ciphertext);

                // Decode the verified plaintext as UTF-8 and display the greeting.
                print("I got: %s", new String(plaintext, StandardCharsets.UTF_8));

            // End Bob's task method.
            }
        // Finish Bob's anonymous Agent definition and its registration call.
        });

        env.connect("alice", "bob");
        env.start();
    }
}
