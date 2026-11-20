package isp;

import fri.isp.Agent;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/**
 * An example of using the authenticated encryption cipher.
 * <p>
 * During the encryption, the Galois-Counter mode automatically
 * creates a MAC and verifies it during the decryption.
 * <p>
 * What happens, if the cipher text gets modified?
 * What happens, if the IV gets modified?
 * What happens, if the key is incorrect?
 */
public class GCMExample {
    public static void main(String[] args) throws Exception {
        // shared key
        final SecretKey sharedKey = KeyGenerator.getInstance("AES").generateKey();

        // the payload
        final String message = "this is my message";
        final byte[] pt = message.getBytes(StandardCharsets.UTF_8);
        System.out.printf("MSG: %s%n", message);
        System.out.printf("PT:  %s%n", Agent.hex(pt));

        // encrypt
        // Alice selects AES-GCM to encrypt the message and produce an authentication tag.
        final Cipher alice = Cipher.getInstance("AES/GCM/NoPadding");
        // Alice uses the shared AES key and lets the provider generate a fresh IV.
        alice.init(Cipher.ENCRYPT_MODE, sharedKey);
        // Encrypt all plaintext bytes; the returned ciphertext includes the GCM tag.
        final byte[] ct = alice.doFinal(pt);
        // Display the ciphertext and tag as hexadecimal bytes for inspection.
        System.out.printf("CT:  %s%n", Agent.hex(ct));

        // send IV
        // Alice obtains the nonsecret IV Bob needs to decrypt this ciphertext.
        final byte[] iv = alice.getIV();
        // Display the IV to illustrate the parameters that accompany the ciphertext.
        System.out.printf("IV:  %s%n", Agent.hex(iv));

        // decrypt
        // Bob creates a matching AES-GCM cipher for authenticated decryption.
        final Cipher bob = Cipher.getInstance("AES/GCM/NoPadding");
        // the length of the MAC tag is either 128, 120, 112, 104 or 96 bits
        // the default is 128 bits
        // Bob supplies Alice's IV and expects the default 128-bit authentication tag.
        final GCMParameterSpec specs = new GCMParameterSpec(128, iv);
        // Bob initializes decryption with the shared key and the received GCM parameters.
        bob.init(Cipher.DECRYPT_MODE, sharedKey, specs);
        // Verify the authentication tag and decrypt; an invalid tag raises an exception.
        final byte[] pt2 = bob.doFinal(ct);
        // Display the recovered plaintext bytes in hexadecimal form.
        System.out.printf("PT:  %s%n", Agent.hex(pt2));
        // Decode the recovered bytes using UTF-8 to display the original message.
        System.out.printf("MSG: %s%n", new String(pt2, StandardCharsets.UTF_8));
    }
}
