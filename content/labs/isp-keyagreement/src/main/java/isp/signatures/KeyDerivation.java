package isp.signatures;

import fri.isp.Agent;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;

public class KeyDerivation {
    public static void main(String[] args) throws Exception {
        // password from which the key will be derived
        // Set the fixed demonstration password used as input to key derivation.
        final String password = "hunter2";

        // a random, public and fixed string
        // Encode the fixed demonstration salt as UTF-8; this line does not generate randomness.
        final byte[] salt = "89fjh3409fdj390fk".getBytes(StandardCharsets.UTF_8);

        // use PBKDF2 with the password, salt, and number of iterations and required bits
        // Create the example's PBKDF2 implementation using HMAC-SHA1 internally.
        final SecretKeyFactory pbkdf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        // Supply the password characters and salt as the first PBKDF2 parameters.
        final KeySpec specs = new PBEKeySpec(password.toCharArray(), salt,
                // Request 10,000 iterations and a derived key length of 128 bits.
                10000, 128);
        // Derive the key from the complete PBKDF2 parameter specification.
        final SecretKey generatedKey = pbkdf.generateSecret(specs);

        // Print the derived key for teaching; real applications must keep key bytes secret.
        System.out.printf("key = %s%n", Agent.hex(generatedKey.getEncoded()));
        // Print the encoded key's length in bytes to show the requested 128-bit result.
        System.out.printf("len(key) = %d bytes%n", generatedKey.getEncoded().length);

        // Choose the example message to authenticate with the derived key.
        final String message = "Hello World!";

        // for example, use the derived key as the HMAC key
        // Create an HMAC-SHA256 message-authentication calculation.
        final Mac hmac = Mac.getInstance("HmacSHA256");
        // Wrap the derived bytes as an HMAC key and initialize the MAC calculation.
        hmac.init(new SecretKeySpec(generatedKey.getEncoded(), "HmacSHA256"));
        // MAC the message using the platform-default text encoding and print the tag as hexadecimal.
        System.out.printf("HMAC[%s] = %s%n", message, Agent.hex(hmac.doFinal(message.getBytes())));

    // End the password-derived key and HMAC demonstration.
    }
}
