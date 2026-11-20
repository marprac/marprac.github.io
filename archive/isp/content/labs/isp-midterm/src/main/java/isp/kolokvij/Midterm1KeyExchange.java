// This file belongs to the "isp.kolokvij" package.
// Keep it in the corresponding package/folder in your Java project.
package isp.kolokvij;

// Agent represents one participant (Alice or Server) in the ISP simulation.
import fri.isp.Agent;
// Environment stores the agents, connects them, and starts their tasks.
import fri.isp.Environment;

// Cipher performs encryption and decryption (here, AES-GCM).
import javax.crypto.Cipher;
// KeyAgreement performs the ECDH calculation using our private key and the peer's public key.
import javax.crypto.KeyAgreement;
// GCMParameterSpec provides the authentication-tag length and IV for GCM decryption.
import javax.crypto.spec.GCMParameterSpec;
// SecretKeySpec wraps raw AES key bytes in a Java key object.
import javax.crypto.spec.SecretKeySpec;
// UTF-8 converts the shared password String into bytes consistently on both sides.
import java.nio.charset.StandardCharsets;
// KeyFactory reconstructs a public-key object from received encoded bytes.
import java.security.KeyFactory;
// KeyPair stores a public key and its matching private key.
import java.security.KeyPair;
// KeyPairGenerator creates new RSA or elliptic-curve public/private key pairs.
import java.security.KeyPairGenerator;
// MessageDigest calculates SHA-256 hashes.
import java.security.MessageDigest;
// SecureRandom generates unpredictable bytes for the challenge.
import java.security.SecureRandom;
// Signature creates and verifies digital signatures.
import java.security.Signature;
// ECPublicKey is the Java type for an elliptic-curve public key.
import java.security.interfaces.ECPublicKey;
// ECParameterSpec holds the curve settings used by Alice's EC public key.
import java.security.spec.ECParameterSpec;
// X509EncodedKeySpec describes the standard byte encoding of a public key.
import java.security.spec.X509EncodedKeySpec;

/*
 * MIDTERM 1: AUTHENTICATED ECDH KEY EXCHANGE
 *
 * Protocol summary:
 *   1. Alice sends her temporary EC public key A to Server.
 *   2. Server derives an ECDH secret, signs A || B using its RSA private key,
 *      and sends its temporary EC public key B and signature.
 *   3. Server also sends a random 32-byte challenge encrypted with AES-GCM.
 *      Alice verifies the signature, derives the same AES key, decrypts the
 *      challenge, and sends an encrypted SHA-256(pwd || challenge) response.
 *   4. Server decrypts the response and checks that the hash matches.
 *
 * Important distinctions:
 *   - "EC" is used to GENERATE elliptic-curve key pairs.
 *   - "ECDH" is used to AGREE on a shared secret using those keys.
 *   - RSA is used only to AUTHENTICATE the server by signing A || B.
 *   - AES-GCM is used to ENCRYPT and authenticate challenge/response messages.
 *
 * A and B are encoded EC public keys (byte arrays), not secret/private keys.
 * Every send() in this lab's Agent API expects a byte[].
 */
public class Midterm1KeyExchange {

    // main() is the entry point executed when the Java program starts.
    // throws Exception lets checked cryptographic and communication errors propagate.
    public static void main(String[] args) throws Exception {

        // Create the simulation environment in which the two agents will communicate.
        // "final" means the env variable will not be assigned a different object.
        final Environment env = new Environment();

        // SETUP: Long-term RSA identity of the Server.
        // This RSA key pair is DIFFERENT from the temporary EC key pairs used for ECDH.
        // The Server signs with the RSA private key; Alice verifies with the RSA public key.
        final KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");

        // Generate a 2048-bit RSA key pair (RSA key size, not AES key size).
        rsaKpg.initialize(2048);

        // Store both RSA keys. getPrivate() is used by Server; getPublic() by Alice.
        final KeyPair rsaKeys = rsaKpg.generateKeyPair();

        // Both agents know this same password before communication begins.
        // This is only an exercise password; a real application should not hardcode it.
        final String pwd = "password";

        // Add the first participant to the environment: an agent called "alice".
        // The anonymous Agent class overrides task(), which runs Alice's protocol.
        env.add(new Agent("alice") {
            // Replace the Agent base class's task with Alice's handshake steps.
            @Override
            // Run Alice's protocol; propagate failures to the simulation.
            public void task() throws Exception {

                // -------------------- STEP 1: ALICE SENDS A --------------------

                // Create a generator for elliptic-curve (EC) key pairs.
                // We say "EC" here, NOT "ECDH": ECDH is the agreement algorithm below.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");

                // Request a 256-bit elliptic-curve key pair.
                kpg.initialize(256);

                // Generate Alice's temporary private key a and public key A.
                // Alice must keep the private key; she only sends the public key.
                final KeyPair aliceKp = kpg.generateKeyPair();

                // Encode Alice's public key as a byte[], which send() can transmit.
                // getEncoded() uses the standard X.509 public-key encoding.
                final byte[] A = aliceKp.getPublic().getEncoded();

                // Send public key A to the agent named "server".
                send("server", A);

                // -------------------- STEP 2: ALICE RECEIVES SERVER DATA --------------------

                // receive("server") blocks until Server sends the next byte[] message.
                // Messages must be received in exactly the order Server sends them.

                // B is the Server's temporary EC public key, encoded as bytes.
                final byte[] B = receive("server");

                // RSA signature made by Server over A || B.
                final byte[] signature = receive("server");

                // IV (initialization vector / nonce) used for encrypting the challenge.
                // GCM needs the SAME IV for decrypting that particular ciphertext.
                final byte[] challIv = receive("server");

                // Encrypted challenge, including the GCM authentication tag.
                final byte[] cChall = receive("server");

                // -------------------- VERIFY SERVER'S IDENTITY --------------------

                // Verify Server's RSA signature BEFORE trusting public key B.
                // SHA256withRSA hashes the supplied bytes with SHA-256 and verifies
                // the RSA signature; we do NOT need to hash A || B ourselves.
                final Signature verifier = Signature.getInstance("SHA256withRSA");

                // Alice verifies using Server's known RSA PUBLIC key, never its private key.
                verifier.initVerify(rsaKeys.getPublic());

                // Supply Alice's public key A as the first portion of signed data.
                verifier.update(A);

                // Supply Server's public key B as the second portion of signed data.
                // update(A) followed by update(B) is equivalent to signing A || B.
                verifier.update(B);

                // verify() returns true only if the signature matches this data/key.
                if (!verifier.verify(signature)) {
                    // Abort: do not establish a session with an unauthenticated server.
                    throw new SecurityException("Invalid server signature!");
                // Continue only after the server signature has passed verification.
                }

                // Show confirmation in Alice's ISP agent output.
                print("Valid server signature.");

                // -------------------- ECDH: ALICE DERIVES SHARED KEY --------------------

                // B arrived as bytes; ECDH requires an actual ECPublicKey object.
                // Describe the received byte format as an X.509-encoded public key.
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(B);

                // Decode those bytes using an EC KeyFactory and cast to ECPublicKey.
                final ECPublicKey serverPK = (ECPublicKey) KeyFactory.getInstance("EC")
                        // Finish decoding the received X.509 public-key bytes.
                        .generatePublic(keySpec);

                // Obtain an Elliptic-Curve Diffie-Hellman key agreement object.
                final KeyAgreement dh = KeyAgreement.getInstance("ECDH");

                // Use Alice's PRIVATE EC key a to initialize ECDH.
                dh.init(aliceKp.getPrivate());

                // Combine a with Server's PUBLIC EC key B.
                // true means this is the last (and only) ECDH phase.
                dh.doPhase(serverPK, true);

                // Get ECDH's shared secret bytes. The Server obtains the same value
                // by combining its private EC key b with Alice's public key A.
                final byte[] sharedSecret = dh.generateSecret();

                // -------------------- DERIVE AN AES-128 KEY --------------------

                // Our assignment defines k = first 16 bytes of SHA-256(sharedSecret).
                // Create a SHA-256 message digest instance.
                final MessageDigest md = MessageDigest.getInstance("SHA-256");

                // Hash the shared secret; SHA-256 produces 32 bytes (256 bits).
                final byte[] hashSS = md.digest(sharedSecret);

                // Take the first 16 hash bytes (128 bits) and label them as an AES key.
                // SecretKeySpec(array, offset, length, algorithm) copies that byte range.
                final SecretKeySpec aesKey = new SecretKeySpec(hashSS, 0, 16, "AES");

                // -------------------- DECRYPT SERVER'S CHALLENGE --------------------

                // AES/GCM/NoPadding = AES encryption in authenticated GCM mode.
                // GCM does not need padding even when message length is not a multiple of 16.
                final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");

                // Configure this Cipher to DECRYPT using AES key k, the received IV,
                // and a 128-bit (16-byte) authentication tag expected by GCM.
                aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, challIv));

                // Decrypt cChall. GCM checks its authentication tag; if the data,
                // key, or IV is wrong, doFinal() throws an exception.
                final byte[] chall = aes.doFinal(cChall);

                // The assignment requires a challenge of exactly 32 bytes.
                if (chall.length != 32) {
                    // Reject malformed challenge data.
                    throw new SecurityException("Invalid challenge length!");
                // A valid challenge has the required 32-byte length.
                }

                // -------------------- CALCULATE PASSWORD RESPONSE --------------------

                // Required formula: resp = SHA-256(pwd || chall).
                // "||" means concatenate in THIS order: password bytes, then challenge.
                final MessageDigest responseHash = MessageDigest.getInstance("SHA-256");

                // Feed the UTF-8 representation of the password into the digest first.
                // update() adds data without finalizing the hash yet.
                responseHash.update(pwd.getBytes(StandardCharsets.UTF_8));

                // Append chall and finalize hashing. digest(chall) adds challenge bytes
                // after the password bytes, producing a 32-byte hash called resp.
                final byte[] resp = responseHash.digest(chall);

                // -------------------- ENCRYPT AND SEND RESPONSE --------------------

                // Use a NEW Cipher instance so GCM encryption uses a fresh IV.
                // Reusing the same (key, IV) pair with AES-GCM would be insecure.
                final Cipher responseAes = Cipher.getInstance("AES/GCM/NoPadding");

                // Start AES-GCM encryption with the shared AES key.
                // With this Java provider configuration, a fresh IV is generated
                // automatically because no IV was explicitly supplied.
                responseAes.init(Cipher.ENCRYPT_MODE, aesKey);

                // Encrypt resp. The returned bytes include the GCM authentication tag.
                final byte[] cResp = responseAes.doFinal(resp);

                // Send the new response IV so Server can decrypt cResp.
                send("server", responseAes.getIV());

                // Send the encrypted password response (NOT the plaintext password).
                send("server", cResp);

                // This only means Alice sent the response; Server still has to check it.
                print("Sent encrypted password response.");
            // End Alice's task after sending her encrypted response.
            }
        // Register the completed Alice agent with the environment.
        });

        // Add the second participant, whose agent name is "server".
        env.add(new Agent("server") {
            // Replace the Agent base class's task with the server's handshake steps.
            @Override
            // Run the server protocol; propagate cryptographic or authentication errors.
            public void task() throws Exception {

                // -------------------- STEP 2: SERVER RECEIVES A --------------------

                // Wait for Alice's first message: encoded EC public key A.
                final byte[] A = receive("alice");

                // The received bytes are an X.509-encoded public key.
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(A);

                // Reconstruct Alice's actual EC public-key object from those bytes.
                final ECPublicKey alicePK = (ECPublicKey) KeyFactory.getInstance("EC")
                        // Finish decoding Alice's X.509-encoded EC public key.
                        .generatePublic(keySpec);

                // -------------------- SERVER GENERATES B --------------------

                // Read the curve parameters from Alice's public key.
                // This keeps Alice's and Server's ephemeral EC keys on the same curve.
                final ECParameterSpec ecParams = alicePK.getParams();

                // Create an EC key-pair generator for Server's ephemeral key pair.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");

                // Use Alice's EC curve parameters to generate compatible EC keys.
                kpg.initialize(ecParams);

                // Generate Server's temporary private key b and public key B.
                final KeyPair serverKp = kpg.generateKeyPair();

                // Encode B as bytes to transmit to Alice and include in the signature.
                final byte[] B = serverKp.getPublic().getEncoded();

                // -------------------- ECDH: SERVER DERIVES SHARED KEY --------------------

                // Use ECDH (not RSA) to calculate a session shared secret.
                final KeyAgreement dh = KeyAgreement.getInstance("ECDH");

                // Initialize ECDH with Server's PRIVATE EC key b.
                dh.init(serverKp.getPrivate());

                // Combine b with Alice's PUBLIC EC key A.
                // The resulting secret must match Alice's sharedSecret.
                dh.doPhase(alicePK, true);

                // Finish the agreement and obtain the raw ECDH shared secret.
                final byte[] sharedSecret = dh.generateSecret();

                // -------------------- DERIVE THE SAME AES-128 KEY --------------------

                // Alice and Server must use the same hash and extraction rule.
                final MessageDigest md = MessageDigest.getInstance("SHA-256");

                // SHA-256 produces a 32-byte hash of the ECDH shared secret.
                final byte[] hashSS = md.digest(sharedSecret);

                // Copy bytes 0 through 15 into the AES-128 key representation.
                final SecretKeySpec aesKey = new SecretKeySpec(hashSS, 0, 16, "AES");

                // -------------------- SERVER SIGNS A || B --------------------

                // SHA256withRSA performs a SHA-256/RSA digital signature operation.
                final Signature signer = Signature.getInstance("SHA256withRSA");

                // Sign using Server's long-term RSA PRIVATE key, not its EC private key.
                signer.initSign(rsaKeys.getPrivate());

                // Feed Alice's public key first.
                signer.update(A);

                // Feed Server's public key second: together the signed bytes are A || B.
                signer.update(B);

                // Produce the RSA signature bytes that Alice will verify with the RSA
                // public key known before this key exchange began.
                final byte[] signature = signer.sign();

                // -------------------- STEP 3: PASSWORD CHALLENGE --------------------

                // Reserve 32 bytes (256 bits) for a fresh random challenge.
                final byte[] chall = new byte[32];

                // Fill all 32 bytes with cryptographically secure random values.
                // Fresh randomness prevents an old response from simply being reused.
                new SecureRandom().nextBytes(chall);

                // -------------------- ENCRYPT THE CHALLENGE --------------------

                // GCM encrypts the challenge and protects it against modification.
                final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");

                // Initialize for encryption using the AES key derived via ECDH.
                // Java generates a fresh GCM IV for this encryption.
                aes.init(Cipher.ENCRYPT_MODE, aesKey);

                // Encrypt the 32-byte challenge and attach an authentication tag.
                final byte[] cChall = aes.doFinal(chall);

                // -------------------- SEND SERVER'S FOUR MESSAGES --------------------

                // 1. Send public key B so Alice can perform ECDH.
                send("alice", B);

                // 2. Send RSA signature so Alice can authenticate A || B.
                send("alice", signature);

                // 3. Send the challenge encryption IV (nonce); it is not secret.
                send("alice", aes.getIV());

                // 4. Send the encrypted challenge and its GCM tag.
                send("alice", cChall);

                // -------------------- STEP 4: VERIFY ALICE --------------------

                // Receive Alice's newly generated GCM IV for her response.
                final byte[] respIv = receive("alice");

                // Receive Alice's encrypted SHA-256(pwd || chall) response.
                final byte[] cResp = receive("alice");

                // Create a new Cipher to decrypt Alice's response.
                final Cipher responseAes = Cipher.getInstance("AES/GCM/NoPadding");

                // Supply the shared AES key, received response IV, and 128-bit GCM tag size.
                responseAes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, respIv));

                // Decrypt and authenticate Alice's response ciphertext.
                final byte[] resp = responseAes.doFinal(cResp);

                // -------------------- COMPUTE EXPECTED RESPONSE --------------------

                // Server knows the original chall and the same shared password pwd.
                // It independently calculates expected = SHA-256(pwd || chall).
                final MessageDigest expectedHash = MessageDigest.getInstance("SHA-256");

                // First hash the same UTF-8 password bytes Alice used.
                expectedHash.update(pwd.getBytes(StandardCharsets.UTF_8));

                // Then hash the ORIGINAL server challenge bytes and finish the digest.
                // digest(chall) appends chall after pwd; the order is important.
                final byte[] expected = expectedHash.digest(chall);

                // Compare hashes with a comparison function designed to avoid
                // leaking the position of the first differing byte by early exit.
                if (MessageDigest.isEqual(resp, expected)) {

                    // A match means Alice returned the correct password-based hash
                    // of this session's fresh random challenge.
                    print("Alice authenticated successfully!");
                // Select the failure branch when the response hashes differ.
                } else {

                    // Reject Alice if the decrypted response does not match.
                    throw new SecurityException("Invalid password response!");
                // End the authentication decision.
                }
            // End the server's task after accepting or rejecting the response.
            }
        // Register the completed server agent with the environment.
        });

        // Allow the two named agents to exchange messages in the ISP environment.
        env.connect("alice", "server");

        // Start the environment: Alice and Server execute their task() methods.
        env.start();
    }
}
