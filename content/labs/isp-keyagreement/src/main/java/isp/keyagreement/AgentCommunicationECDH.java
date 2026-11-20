package isp.keyagreement;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;

public class AgentCommunicationECDH {
    public static void main(String[] args) {

        final Environment env = new Environment();

        // Create Alice's participant and register its task with the environment.
        env.add(new Agent("alice") {
            // Use the Agent task hook called when this participant starts.
            @Override
            // Run the protocol steps; failures propagate as exceptions from this task.
            public void task() throws Exception {
                // Create an elliptic-curve key-pair generator for ECDH.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                // Select the provider's elliptic-curve parameters for a 256-bit key size.
                kpg.initialize(256);

                // Generate key pair
                // Generate a private agreement key and its corresponding public contribution.
                final KeyPair keyPair = kpg.generateKeyPair();

                // send "PK" to bob ("PK": A = g^a, "SK": a)
                // Send Alice's encoded public key to Bob; the private key stays local.
                send("bob", keyPair.getPublic().getEncoded());
                // Display this participant's encoded public EC key as diagnostic hexadecimal text.
                print("My contribution to ECDH: %s", hex(keyPair.getPublic().getEncoded()));

                // get PK from bob
                // Receive Bob's public-key bytes and describe their X.509 encoding.
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(receive("bob"));
                // Decode Bob's X.509 key bytes as an elliptic-curve public key.
                final ECPublicKey bobPK = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);

                // Run the agreement protocol
                // Create the elliptic-curve Diffie-Hellman shared-secret calculation.
                final KeyAgreement dh = KeyAgreement.getInstance("ECDH");
                // Initialize agreement with this participant's private key; never send that key.
                dh.init(keyPair.getPrivate());
                // Combine Alice's private key with Bob's public key in the final agreement phase.
                dh.doPhase(bobPK, true);

                // generate a shared AES key
                // Extract the shared-secret bytes; both participants should compute the same value.
                final byte[] sharedSecret = dh.generateSecret();
                // Display the ECDH secret for teaching; keep this value private in real applications.
                print("Shared secret: %s", hex(sharedSecret));

                // By default the shared secret will be 32 bytes long,
                // our cipher requires keys of length 16 bytes
                // Use the first 16 shared-secret bytes for AES-128; this demonstration omits a proper KDF.
                final SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");

                // Create AES-GCM for encryption and ciphertext authentication under the shared key.
                final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                // Start encryption with the shared key and let the provider generate a fresh GCM IV.
                aes.init(Cipher.ENCRYPT_MODE, aesKey);

                // Encode the greeting as UTF-8, encrypt it, and append its GCM authentication tag.
                final byte[] ct = aes.doFinal("Hey Bob!".getBytes(StandardCharsets.UTF_8));
                // Read the fresh public IV generated when GCM encryption was initialized.
                final byte[] iv = aes.getIV();

                // Send the public IV before the ciphertext, matching Bob's receive order.
                send("bob", iv);
                // Send the GCM ciphertext and authentication tag to Bob.
                send("bob", ct);

                // Report that Alice has sent both items and completed her task.
                print("I'm, done!");
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
                // get PK from alice
                // Receive Alice's encoded public key and wrap it in an X.509 key specification.
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(receive("alice"));
                // Reconstruct Alice's elliptic-curve public key from the received bytes.
                final ECPublicKey alicePK = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);

                // Read Alice's curve parameters so Bob uses the same elliptic curve.
                final ECParameterSpec dhParamSpec = alicePK.getParams();

                // create your own DH key pair
                // Create an elliptic-curve key-pair generator for ECDH.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                // Configure Bob's generator with the peer's agreement parameters.
                kpg.initialize(dhParamSpec);
                // Generate a private agreement key and its corresponding public contribution.
                final KeyPair keyPair = kpg.generateKeyPair();
                // Send Bob's encoded public key to Alice; the private key stays local.
                send("alice", keyPair.getPublic().getEncoded());
                // Display this participant's encoded public EC key as diagnostic hexadecimal text.
                print("My contribution to ECDH: %s", hex(keyPair.getPublic().getEncoded()));

                // Create the elliptic-curve Diffie-Hellman shared-secret calculation.
                final KeyAgreement dh = KeyAgreement.getInstance("ECDH");
                // Initialize agreement with this participant's private key; never send that key.
                dh.init(keyPair.getPrivate());
                // Combine Bob's private key with Alice's public key in the final agreement phase.
                dh.doPhase(alicePK, true);

                // Extract the shared-secret bytes; both participants should compute the same value.
                final byte[] sharedSecret = dh.generateSecret();
                // Display the ECDH secret for teaching; keep this value private in real applications.
                print("Shared secret: %s", hex(sharedSecret));
                // Use the first 16 shared-secret bytes for AES-128; this demonstration omits a proper KDF.
                final SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");

                // Create AES-GCM for encryption and ciphertext authentication under the shared key.
                final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                // Receive the public GCM IV sent before Alice's ciphertext.
                final byte[] iv = receive("alice");
                // Receive the GCM ciphertext and appended authentication tag.
                final byte[] ct = receive("alice");
                // Initialize GCM decryption with the shared key, received IV, and 128-bit tag.
                aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
                // Verify the GCM tag and decrypt; tampered data causes a cryptographic exception.
                final byte[] pt = aes.doFinal(ct);

                // Convert the authenticated plaintext to UTF-8 and display the message.
                print("I got: %s", new String(pt, StandardCharsets.UTF_8));
            // End Bob's task method.
            }
        // Finish Bob's anonymous Agent definition and its registration call.
        });

        env.connect("alice", "bob");
        env.start();
    }
}
