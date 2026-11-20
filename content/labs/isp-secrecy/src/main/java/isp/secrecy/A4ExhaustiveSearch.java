package isp.secrecy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implement a brute force key search (exhaustive key search) if you know that
 * the
 * message is:
 * "I would like to keep this text confidential Bob. Kind regards, Alice."
 * <p>
 * Assume the message was encrypted with "DES/ECB/PKCS5Padding".
 * Also assume that the key was poorly chosen. In particular, as an attacker,
 * you are certain that all bytes in the key, with the exception of the last
 * three bytes,
 * have been set to 0.
 * <p>
 * A DES key is represented by 8 bytes, but only 56 bits affect encryption; the
 * remaining
 * bits are parity bits. Varying the last three bytes produces 2^24 byte
 * representations
 * but only 2^21 effective keys. Any equivalent key that correctly decrypts the
 * ciphertext
 * is acceptable; it need not match the original key byte for byte.
 * <p>
 * In main(), generate a demonstration key with the stated restriction, encrypt
 * the supplied
 * message with DES/ECB/PKCS5Padding, and call bruteForceKey(). Verify that the
 * returned key
 * decrypts the ciphertext to the complete original message. Use UTF-8 for text
 * conversion.
 * The search must use only its arguments and the stated key restriction, not
 * the original key.
 * DES and ECB are used only for this teaching exercise, not as recommended
 * encryption.
 * <p>
 * To manually specify a key, use {@link javax.crypto.spec.SecretKeySpec}.
 */
public class A4ExhaustiveSearch {
    public static void main(String[] args) throws Exception {
        final String message = "I would like to keep this text confidential Bob. Kind regards, Alice.";
        System.out.println("[MESSAGE] " + message);

        // TODO
        byte[] keyBytes = new byte[8];
        byte[] randomBytes = new byte[3];
        new SecureRandom().nextBytes(randomBytes);

        System.arraycopy(randomBytes, 0, keyBytes, 5, 3);

        SecretKeySpec key = new SecretKeySpec(keyBytes, "DES");

        Cipher encrypt = Cipher.getInstance("DES/ECB/PKCS5Padding");
        encrypt.init(Cipher.ENCRYPT_MODE, key);

        byte[] ciphertext = encrypt.doFinal(message.getBytes(StandardCharsets.UTF_8));
        byte[] bfKey = bruteForceKey(ciphertext, message);

        Cipher decrypt = Cipher.getInstance("DES/ECB/PKCS5Padding");
        decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(bfKey, "DES"));

        byte[] plaintext = decrypt.doFinal(ciphertext);
        String decryptedMessage = new String(plaintext, StandardCharsets.UTF_8);

        System.out.println("[DECRYPTED] " + decryptedMessage);
        System.out.println("[MATCH] " + message.equals(decryptedMessage));

    }

    /**
     * Recover an 8-byte representation of a key that decrypts ct to the known
     * message.
     * Return null if no matching key exists within the restricted search space.
     */
    public static byte[] bruteForceKey(byte[] ct, String message) throws Exception {
        // TODO
        byte[] expected = message.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = new byte[8];

        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");

        for (int i = 0; i < (1 << 24); i++) {
            keyBytes[5] = (byte) (i >>> 16);
            keyBytes[6] = (byte) (i >>> 8);
            keyBytes[7] = (byte) i;

            SecretKeySpec key = new SecretKeySpec(keyBytes, "DES");

            try {
                cipher.init(Cipher.DECRYPT_MODE, key);
                byte[] plaintext = cipher.doFinal(ct);

                if (Arrays.equals(plaintext, expected)) {
                    return keyBytes.clone();
                }
            } catch (javax.crypto.BadPaddingException e) {
            }
        }

        return null;
    }
}
