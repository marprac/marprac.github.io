package isp;

import fri.isp.Agent;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * Assignments:
 * - Find out how to manually change the RSA modulus size
 * - Set padding to NoPadding. Encrypt a message and decrypt it. Is the
 * decrypted text the same as the original plaint text? Why?
 */
public class RSAExample {

    public static void main(String[] args) throws Exception {
        // Set RSA cipher specs:
        //  - Set mode to ECB: each block is encrypted independently
        //  - Set padding to OAEP (preferred mode);
        //    alternatives are PKCS1Padding (the default) and NoPadding ("textbook" RSA)
        final String algorithm = "RSA/ECB/OAEPPadding";
        final String message = "I would like to keep this text confidential, Bob. Kind regards, Alice.";
        final byte[] pt = message.getBytes(StandardCharsets.UTF_8);

        System.out.println("Message: " + message);
        System.out.println("PT: " + Agent.hex(pt));

        // STEP 1: Bob creates his public and private key pair.
        // Alice receives Bob's public key.
        // Select an RSA generator to create Bob's matching public and private keys.
        final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        // Generate Bob's key pair using this provider's default RSA key size.
        final KeyPair bobKP = kpg.generateKeyPair();

        // STEP 2: Alice creates Cipher object defining cipher algorithm.
        // She then encrypts the clear-text and sends it to Bob.
        // Alice creates an RSA cipher using the OAEP transformation chosen above.
        final Cipher rsaEnc = Cipher.getInstance(algorithm);
        // Alice initializes encryption with Bob's public key; only the matching private key decrypts.
        rsaEnc.init(Cipher.ENCRYPT_MODE, bobKP.getPublic());
        // Alice encrypts the short plaintext message into an RSA-OAEP ciphertext.
        final byte[] ct = rsaEnc.doFinal(pt);

        // STEP 3: Display cipher text in hex. This is what an attacker would see,
        // if she intercepted the message.
        // Show the encrypted bytes in hexadecimal form to illustrate the transmitted ciphertext.
        System.out.println("CT: " + Agent.hex(ct));

        // STEP 4: Bob decrypts the cipher text using the same algorithm and his private key.
        // Bob creates an RSA cipher with the same transformation Alice used.
        final Cipher rsaDec = Cipher.getInstance(algorithm);
        // Bob initializes decryption using his private RSA key.
        rsaDec.init(Cipher.DECRYPT_MODE, bobKP.getPrivate());
        // Decrypt Alice's ciphertext and validate its OAEP encoding.
        final byte[] decryptedText = rsaDec.doFinal(ct);

        // STEP 5: Bob displays the clear text
        // Display the decrypted bytes in hexadecimal form for comparison with the original.
        System.out.println("PT: " + Agent.hex(decryptedText));
        // Decode the decrypted bytes with the same UTF-8 encoding used by Alice.
        final String message2 = new String(decryptedText, StandardCharsets.UTF_8);
        // Display the recovered readable message on Bob's side.
        System.out.println("Message: " + message2);
    }
}
