package isp.signatures;

import fri.isp.Agent;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

public class SignatureExampleRSA {
    public static void main(String[] args) throws Exception {

        // https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature
        // Select the signature transformation used by both sender and receiver.
        final String signingAlgorithm =
                // Use SHA-256 hashing with the RSA signature scheme.
                "SHA256withRSA";
//         "SHA256withDSA";
//        "SHA256withECDSA";
        // Select the key-pair algorithm compatible with the signature scheme.
        final String keyAlgorithm =
                // Use RSA public and private keys for this example.
                "RSA";
//         "DSA";
//         "EC";


        // The message we want to sign
        // Define the document the sender signs and the receiver later verifies.
        final String document = "We would like to sign this.";

        /*
         * STEP 1.
         * We create a public-private key pair using standard algorithm names
         * http://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
         */
        // Generate the sender's private signing key and matching public verification key.
        final KeyPair key = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair();

        /*
         * Alice creates Signature object defining Signature algorithm.
         */
        // Create the sender's signing object with the selected algorithm.
        final Signature signer = Signature.getInstance(signingAlgorithm);

        /*
         * We initialize the signature object with
         * - Operation modes (SIGN) and
         * - provides appropriate ***Private*** Key
         */
        // Initialize signing with the private key, which belongs only to the sender.
        signer.initSign(key.getPrivate());

        // Finally, we load the document into the signature object and sign it
        // Feed the document's exact UTF-8 bytes into the signing calculation.
        signer.update(document.getBytes(StandardCharsets.UTF_8));
        // Complete the signature and obtain the bytes the receiver will check.
        final byte[] signature = signer.sign();
        // Display the public signature bytes as hexadecimal for inspection.
        System.out.println("Signature: " + Agent.hex(signature));

        /*
         * To verify the signature, we create another signature object
         * and specify its algorithm
         */
        // Create the receiver's verification object with the matching algorithm.
        final Signature verifier = Signature.getInstance(signingAlgorithm);

        /*
         * We have to initialize in the verification mode. We only need
         * to know public key of the signer.
         */
        // Initialize verification with the sender's public key, assumed authentic in this example.
        verifier.initVerify(key.getPublic());

        // Check whether the signature is valid
        // Feed the same UTF-8 document bytes into the receiver's verification calculation.
        verifier.update(document.getBytes(StandardCharsets.UTF_8));

        // Check whether the signature matches these document bytes and this public key.
        if (verifier.verify(signature))
            // Report success only when the signature verification returns true.
            System.out.println("Valid signature.");
        // Handle the case where signature verification returns false.
        else
            // Report verification failure on the error output stream.
            System.err.println("Invalid signature.");
    // End the standalone sender/receiver demonstration.
    }
}
