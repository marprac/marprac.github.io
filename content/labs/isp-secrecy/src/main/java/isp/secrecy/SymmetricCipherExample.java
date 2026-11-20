package isp.secrecy;

import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

import java.security.SecureRandom;
import fri.isp.Agent;

/**
 * EXERCISE:
 * - Study the example
 * - Test different ciphers
 *
 * RC4 is used here to keep the introductory example simple: initialization
 * requires
 * only a key, with no IV or nonce. RC4 is obsolete and insecure and must not be
 * used
 * in new applications; this example demonstrates API usage, not recommended
 * cryptography.
 * When testing other ciphers, adapt the key type and cipher parameters as
 * needed.
 *
 * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
 */
public class SymmetricCipherExample {
    // Run the sender and receiver demonstration in one method so both sides can use the setup values.
    public static void main(String[] args) throws Exception {
        // Set the plaintext used to demonstrate Alice encrypting a message for Bob.
        final String message = "Hi Bob, this is Alice.";
        // Show Alice's original message before encryption.
        System.out.println("[MESSAGE] " + message);

        // STEP 1: Alice and Bob agree upon a cipher and a shared secret key
        // Generate a shared ChaCha20 key for this in-process demonstration; the key itself is not transmitted.
        final Key key = KeyGenerator.getInstance("ChaCha20").generateKey();

        // Encode Alice's plaintext message as UTF-8 bytes for the cipher API.
        final byte[] pt = message.getBytes(StandardCharsets.UTF_8);
        // Display the original plaintext bytes in hexadecimal.
        System.out.println("[PT] " + Agent.hex(pt));

        // STEP 2: Create a cipher, encrypt the PT and, optionally, extract cipher
        // parameters (such as IV)
        // Create a separate ChaCha20 cipher instance for this encryption or decryption operation.
        final Cipher encrypt = Cipher.getInstance("ChaCha20");
        // Allocate the 12-byte nonce required by ChaCha20.
        final byte[] nonce = new byte[12];
        // Fill the nonce with cryptographically strong random bytes to make reuse unlikely.
        SecureRandom.getInstanceStrong().nextBytes(nonce);
        // Start encryption at ChaCha20 block zero for this nonce.
        final int counter = 0;
        // Initialize Alice's ChaCha20 encryptor using the shared key, public nonce, and initial counter.
        encrypt.init(Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.ChaCha20ParameterSpec(nonce, counter));
        // Encrypt the plaintext bytes; plain ChaCha20 provides no authentication tag.
        final byte[] cipherText = encrypt.doFinal(pt);
        // final byte[] iv = encrypt.getIV();
        // System.out.println("[IV] " + Agent.hex(iv));
        // Display the public nonce that Bob needs along with the ciphertext and counter.
        System.out.println("[NONCE] " + Agent.hex(nonce));
        // STEP 3: Print out cipher text (in HEX) [this is what an attacker would see]
        // Display the ciphertext bytes in hexadecimal, as an observer of the channel could see them.
        System.out.println("[CT] " + Agent.hex(cipherText));

        /*
         * STEP 4.
         * The receiver creates a Cipher object, defines the algorithm, the secret key
         * and
         * possibly additional parameters (such as IV), and then decrypts the cipher
         * text
         */
        // Create a separate ChaCha20 cipher instance for this encryption or decryption operation.
        final Cipher decrypt = Cipher.getInstance("ChaCha20");
        // Initialize Bob's decryptor with the same key, nonce, and initial counter used by Alice.
        decrypt.init(Cipher.DECRYPT_MODE, key, new javax.crypto.spec.ChaCha20ParameterSpec(nonce, counter));
        // Decrypt the ciphertext into the original message bytes using the reproduced keystream.
        final byte[] dt = decrypt.doFinal(cipherText);
        // Display the recovered plaintext bytes so they can be compared with the original bytes.
        System.out.println("[PT] " + Agent.hex(dt));

        // Todo: What happens if the key is incorrect? (Try with RC4 or AES in CTR mode)

        // STEP 5: Create a string from a byte array
        // Decode Bob's recovered bytes as UTF-8 and display the original message.
        System.out.println("[MESSAGE] " + new String(dt, StandardCharsets.UTF_8));
    // Finish the sender and receiver demonstration.
    }
}
