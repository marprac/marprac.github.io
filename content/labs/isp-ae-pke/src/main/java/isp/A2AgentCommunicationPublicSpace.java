package isp;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import fri.isp.Agent;
import fri.isp.Environment;

/**
 * TASK:
 * We want to send a large chunk of data from Alice to Bob while maintaining its
 * integrity and considering
 * the limitations of communication channels -- we have three such channels:
 * - Alice to Bob: an insecure channel, but has high bandwidth and can thus
 * transfer large files
 * - Alice to Public Space: a secure channel, but has low bandwidth and can only
 * transfer small amounts of data
 * - Bob to Public Space: a secure channel, but has low bandwidth and can only
 * transfer small amounts of data
 * <p>
 * The plan is to make use of the public-space technique:
 * - Alice creates the data and computes its digest
 * - Alice sends the data to Bob, and sends the encrypted digest to Public Space
 * - Channel between Alice and Public space is secured with ChaCha20-Poly1305
 * (Alice and Public space share
 * a ChaCha20 key)
 * - Public space forwards the digest to Bob
 * - The channel between Public Space and Bob is secured with AES in GCM mode
 * (Bob and Public space share
 * an AES key)
 * - Bob receives the data from Alice and the digest from Public space
 * - Bob computes the digest over the received data and compares it to the
 * received digest
 * <p>
 * Both secure channels use authenticated encryption, so every encryption needs
 * a fresh IV (nonce). Send the IV together with the ciphertext over the same
 * channel and use it for decryption.
 * <p>
 * Further instructions are given below.
 * <p>
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
 */
public class A2AgentCommunicationPublicSpace {
    public static void main(String[] args) throws Exception {
        final Environment env = new Environment();

        // Create a ChaCha20 key that is used by Alice and the public-space
        final SecretKey chachaKey = KeyGenerator.getInstance("ChaCha20").generateKey();
        // Create an AES key that is used by Bob and the public-space
        final SecretKey aesKey = KeyGenerator.getInstance("AES").generateKey();
        // Register Alice, the sender of the bulk data and its authenticated digest.
        env.add(new Agent("alice") {
            // Implement the task method supplied by the Agent framework.
            @Override
            // Run Alice's sending steps and propagate cryptographic or communication failures.
            public void task() throws Exception {
                // a payload of 200 MB
                // Allocate 200 MiB of bytes for the high-bandwidth transfer.
                final byte[] data = new byte[200 * 1024 * 1024];
                // Fill every payload byte using a cryptographically secure random generator.
                new SecureRandom().nextBytes(data);

                // Alice sends the data directly to Bob
                // The channel between Alice and Bob is not secured
                // Alice then computes the digest of the data and sends the digest to
                // public-space
                // The channel between Alice and the public-space is secured with
                // ChaCha20-Poly1305
                // Use the key that you have created above.

                // Send the bulk payload directly to Bob over the insecure channel.
                send("bob", data);
                // Create a SHA-256 calculator for the payload's compact integrity reference.
                final MessageDigest md = MessageDigest.getInstance("SHA-256");
                // Hash all payload bytes into a 32-byte digest.
                final byte[] digest = md.digest(data);

                // Create the authenticated cipher used by Alice's channel to Public Space.
                final Cipher alice = Cipher.getInstance("ChaCha20-Poly1305");
                // Use the Alice/Public Space key and let the provider generate a fresh nonce.
                alice.init(Cipher.ENCRYPT_MODE, chachaKey);
                // Encrypt the reference digest and append its Poly1305 authentication tag.
                final byte[] ct = alice.doFinal(digest);

                // Retrieve the generated nonce required to decrypt this encrypted digest.
                final byte[] iv = alice.getIV();
                // Send Public Space the nonsecret nonce before the matching ciphertext.
                send("public-space", iv);
                // Send the encrypted digest and its authentication tag to Public Space.
                send("public-space", ct);

            } // Finish Alice's task after sending both payload and protected digest.
        }); // Finish defining and registering Alice's agent.

        env.add(new Agent("public-space") {
            @Override
            public void task() throws Exception {
                // Receive the encrypted digest from Alice and decrypt ChaCha20 and
                // the key that you share with Alice
                // Encrypt the digest with AES-GCM and the key that you share with Bob and
                // send the encrypted digest to Bob

                final byte[] aliceIV = receive("alice");
                final byte[] aliceCT = receive("alice");

                final Cipher publicDec = Cipher.getInstance("ChaCha20-Poly1305");
                final IvParameterSpec specs = new IvParameterSpec(aliceIV);
                publicDec.init(Cipher.DECRYPT_MODE, chachaKey, specs);
                final byte[] digest = publicDec.doFinal(aliceCT);

                final Cipher publicEnc = Cipher.getInstance("AES/GCM/NoPadding");
                publicEnc.init(Cipher.ENCRYPT_MODE, aesKey);
                final byte[] ct = publicEnc.doFinal(digest);

                final byte[] iv = publicEnc.getIV();
                send("bob", iv);
                send("bob", ct);

            }
        });

        // Register Bob, who compares his received data against the protected digest.
        env.add(new Agent("bob") {
            // Implement the task method supplied by the Agent framework.
            @Override
            // Run Bob's verification and propagate cryptographic or communication failures.
            public void task() throws Exception {
                // Receive the data from Alice and compute the digest over it using SHA-256
                // Receive the encrypted digest from the public-space, decrypt it using AES-GCM
                // and the key that Bob shares with the public-space
                // Compare the computed digest and the received digest and print the string
                // "data valid" if the verification succeeds, otherwise print "data invalid"

                // Receive the bulk payload over the insecure high-bandwidth channel.
                final byte[] data = receive("alice");

                // Select the same hash algorithm Alice used for the original payload.
                final MessageDigest md = MessageDigest.getInstance("SHA-256");
                // Compute a digest of the bytes Bob actually received.
                final byte[] digest = md.digest(data);

                // Receive the AES-GCM IV from Public Space before its ciphertext.
                final byte[] iv = receive("public-space");
                // Receive the encrypted reference digest and its authentication tag.
                final byte[] ct = receive("public-space");

                // Create the AES-GCM cipher used by Bob's channel to Public Space.
                final Cipher bob = Cipher.getInstance("AES/GCM/NoPadding");
                // Supply the received IV and expected 128-bit GCM tag length.
                final GCMParameterSpec specs = new GCMParameterSpec(128, iv);
                // Use the Bob/Public Space key and received parameters for decryption.
                bob.init(Cipher.DECRYPT_MODE, aesKey, specs);
                // Authenticate and decrypt the reference digest; reject an invalid GCM tag.
                final byte[] receivedDigest = bob.doFinal(ct);

                // Check whether the received payload hashes to the authenticated reference.
                if (Arrays.equals(digest, receivedDigest)) {
                    // Report that the payload matches Alice's protected digest.
                    System.out.println("data valid");
                } else { // Handle a payload whose digest differs from the protected reference.
                    // Report that the received payload fails the integrity comparison.
                    System.out.println("data invalid");
                } // Finish the digest-comparison branch.
            } // Finish Bob's verification task.
        }); // Finish defining and registering Bob's agent.

        env.connect("alice", "bob");
        env.connect("alice", "public-space");
        env.connect("public-space", "bob");
        env.start();
    }
}
