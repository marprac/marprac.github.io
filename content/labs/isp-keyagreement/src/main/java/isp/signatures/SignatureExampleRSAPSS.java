package isp.signatures;

import fri.isp.Agent;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public class SignatureExampleRSAPSS {
    public static void main(String[] args) throws Exception {
        // Define the document the sender signs and the receiver later verifies.
        final String document = "We would like to sign this.";

        // Generate the sender's RSA private signing key and public verification key.
        final KeyPair key = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        // Create the sender's RSA-PSS signing object.
        final Signature signer = Signature.getInstance("RSASSA-PSS");
        // Use SHA-256 for PSS and MGF1, a 32-byte salt, and the standard trailer field.
        signer.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        // Initialize signing with the private key, which belongs only to the sender.
        signer.initSign(key.getPrivate());
        // Feed the document's exact UTF-8 bytes into the signing calculation.
        signer.update(document.getBytes(StandardCharsets.UTF_8));
        // Complete the signature and obtain the bytes the receiver will check.
        final byte[] signature = signer.sign();

        // Display the public signature bytes as hexadecimal for inspection.
        System.out.println("Signature: " + Agent.hex(signature));

        // Create the receiver's RSA-PSS verification object.
        final Signature verifier = Signature.getInstance("RSASSA-PSS");
        // Match the sender's PSS hash, mask-generation hash, salt length, and trailer field.
        verifier.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        // Initialize verification with the sender's public key, assumed authentic in this example.
        verifier.initVerify(key.getPublic());
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
