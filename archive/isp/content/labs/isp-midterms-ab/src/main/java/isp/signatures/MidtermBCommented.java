// Place this class in the same package as the original midterms so their project layout stays consistent.
package isp.signatures;

// Import the communication-model participant type; each anonymous Agent below runs one side of the handshake.
import fri.isp.Agent;
// Import the environment that registers participants, connects their message channels, and starts their tasks.
import fri.isp.Environment;

// Import the cryptographic cipher API used here for authenticated AES-GCM encryption and decryption.
import javax.crypto.Cipher;
// Import KeyAgreement, which computes the ECDH shared secret from one private key and the peer's public key.
import javax.crypto.KeyAgreement;
// Retain the original finite-field DH public-key import; this ECDH implementation does not use it.
import javax.crypto.interfaces.DHPublicKey;
// Import the object that supplies GCM's authentication-tag length and the received IV during decryption.
import javax.crypto.spec.GCMParameterSpec;
// Import the wrapper that labels a selected range of secret bytes as an AES key.
import javax.crypto.spec.SecretKeySpec;
// Import an in-memory byte buffer used to concatenate public keys and password/challenge bytes in order.
import java.io.ByteArrayOutputStream;
// Import the UTF-8 charset constant so both participants encode their shared password identically.
import java.nio.charset.StandardCharsets;
// Import Java security types for key generation, public-key decoding, SHA-256, signatures, and secure randomness.
import java.security.*;
// Import the EC public-key type required to obtain curve parameters and perform elliptic-curve agreement.
import java.security.interfaces.ECPublicKey;
// Import the curve-parameter container used to generate a server key compatible with Alice's EC key.
import java.security.spec.ECParameterSpec;
// Import the specification describing the X.509 encoding used to transmit and reconstruct public keys.
import java.security.spec.X509EncodedKeySpec;
// Import array utilities; the server uses Arrays.equals to compare the received and expected response digests.
import java.util.Arrays;

/*
 * MIDTERM B – POTEK KRIPTOGRAFSKEGA PROTOKOLA
 *
 * 1. ZAČETNE NASTAVITVE
 *    - Server ima RSA par ključev (javni + zasebni).
 *    - Alice vnaprej pozna Serverjev javni RSA ključ.
 *    - Alice in Server poznata isto geslo.
 *
 * 2. ALICE ZAČNE ECDH
 *    - Ustvari EC par ključev (zasebni a, javni A).
 *    - Serverju pošlje svoj javni ECDH ključ A.
 *
 * 3. SERVER IZVEDE ECDH
 *    - Ustvari svoj EC par ključev (zasebni b, javni B).
 *    - Iz svojega zasebnega ključa b in Alicinega javnega
 *      ključa A z ECDH izračuna sharedSecret.
 *    - sharedSecret hasha s SHA-256 in prvih 16 bajtov
 *      rezultata uporabi kot AES-128 ključ.
 *
 * 4. SERVER DOKAŽE SVOJO IDENTITETO
 *    - Z zasebnim RSA ključem podpiše A || B
 *      (algoritem SHA256withRSA).
 *    - Z AES-128-GCM šifrira sporočilo za Alice.
 *    - Alice pošlje B, RSA podpis, ciphertext in IV.
 *
 * 5. ALICE PREVERI SERVER IN USTVARI AES KLJUČ
 *    - S Serverjevim javnim RSA ključem preveri podpis A || B.
 *    - Iz svojega zasebnega ključa a in Serverjevega
 *      javnega ključa B izračuna isti sharedSecret (ECDH).
 *    - Enako kot Server izpelje AES-128 ključ s SHA-256.
 *    - Z AES-GCM dešifrira prejeto sporočilo.
 *
 * 6. ALICE DOKAŽE SVOJO IDENTITETO
 *    - Za avtentikacijo uporabi skupno geslo in SHA-256.
 *    - Odgovor šifrira z istim AES-128 ključem (AES-GCM).
 *    - Serverju pošlje ciphertext in novi IV.
 *
 * 7. SERVER PREVERI ALICE
 *    - Z AES-GCM dešifrira odgovor.
 *    - Z geslom preveri njegovo pravilnost.
 *    - Če je pravilen, sta obe strani avtenticirani
 *      in imata skupni AES ključ za nadaljnjo komunikacijo.
 */

// Declare a separately named runnable teaching copy so it can coexist with the unchanged MidtermB class.
public class MidtermBCommented {
    // Start the program; propagate checked cryptographic and communication errors instead of suppressing them.
    public static void main(String[] args) throws Exception {
        // Create the simulation environment before registering Alice and the server.
        Environment env = new Environment();

        // Serverjev RSA par ključev
        // Declare the generator that will create the server's long-term RSA identity key pair.
        final KeyPairGenerator rsaGenerator =
                // Select RSA for identity signatures; the separate temporary key pairs below use EC for agreement.
                KeyPairGenerator.getInstance("RSA");
        // Configure the server's RSA modulus size to 2048 bits before generating its identity keys.
        rsaGenerator.initialize(2048);

        // Generate and retain the server's matching RSA public and private keys; Alice verifies with the public half.
        final KeyPair serverKeyPair = rsaGenerator.generateKeyPair();

        // Skupno geslo Alice in Serverja
        // Set the demonstration password shared in advance; this hardcoded value is part of the original exercise.
        final String password = "MojeGeslo123";

        // Register Alice and define her protocol as an anonymous Agent implementation.
        env.add(new Agent("alice") {
            // Mark the following method as the implementation of the task method inherited from Agent.
            @Override
            // Run Alice's side of the handshake and allow failures to propagate through the simulation.
            public void task() throws Exception {
                // Declare the algorithm name used to verify the server's signature over the two public contributions.
                final String signingAlgorithm =
                        // Choose SHA-256 with an RSA signature; the Signature API performs the hash operation itself.
                        "SHA256withRSA";
//         "SHA256withDSA";
//        "SHA256withECDSA";
                // Retain the original key-algorithm label; this local variable is unused by the actual EC generator below.
                final String keyAlgorithm =
                        // Store the string RSA in that unused label without changing the original program's behavior.
                        "RSA";
//         "DSA";
//         "EC";
                // Create an EC key-pair generator for Alice's temporary ECDH contribution, independent of the server's RSA keys.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC"); //EC pomeni eliptic curves
                // Request a supported 256-bit elliptic curve for Alice's temporary public/private key pair.
                kpg.initialize(256);

                // Generate key pair
                // Generate Alice's private EC key and its corresponding public key A; only the public key is transmitted.
                final KeyPair keyPair = kpg.generateKeyPair();

                // send "PK" to bob ("PK": A = g^a, "SK": a)
                // Encode public contribution A in X.509 format and send those bytes as Alice's first message to the server.
                send("server", keyPair.getPublic().getEncoded());
                //print("My contribution to ECDH: %s", hex(keyPair.getPublic().getEncoded()));
                // Wait for the server's encoded temporary EC public contribution B, which is its first reply.
                final byte[] B = receive("server");
                // Receive the RSA signature that the server created over the ordered concatenation A followed by B.
                final byte[] ServerSignature = receive("server");
                // Receive the AES-GCM encrypted challenge, including the appended authentication tag, before receiving its IV.
                final byte[] challCT = receive("server");
                // Receive the public GCM IV associated specifically with the challenge ciphertext just received.
                final byte[] iv = receive("server");

                // Create a byte buffer to build exactly the same signed transcript that the server used.
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                // Append Alice's own encoded public contribution A as the first portion of the signed transcript.
                outputStream.write(keyPair.getPublic().getEncoded());
                // Append the received server contribution B second; reversing the order would change the signed bytes.
                outputStream.write(B);

                // Extract the concatenated A || B bytes for signature verification; neither public contribution is secret.
                final byte[] combinedAB = outputStream.toByteArray( );

                // Create the RSA/SHA-256 verifier selected by signingAlgorithm for this transcript.
                final Signature verifier = Signature.getInstance(signingAlgorithm);

                /*
                 * We have to initialize in the verification mode. We only need
                 * to know public key of the signer.
                 */
                // Initialize verification using the server's already trusted RSA public key; Alice does not sign with its private key.
                verifier.initVerify(serverKeyPair.getPublic());

                // Check whether the signature is valid
                // Supply the complete ordered public-key transcript to the verifier before checking the received signature.
                verifier.update(combinedAB);

                // Continue with key derivation only if the server's signature matches this transcript and trusted public key.
                if (verifier.verify(ServerSignature)){
                    // Report that the server signature passed verification; this message alone does not authenticate Alice to the server.
                    System.out.println("Valid signature.");

                    //Let's generate a sharedSecret
                    // Describe B as an X.509-encoded public key so Java can reconstruct the received EC key object.
                    final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(B);
                    // Create an EC KeyFactory and declare the decoded server key using the ECPublicKey interface.
                    final ECPublicKey DHSerPK = (ECPublicKey) KeyFactory.getInstance("EC")
                            // Finish decoding B from its key specification and cast the resulting public key to its EC-specific type.
                            .generatePublic(keySpec);
                    // Run the agreement protocol
                    // Create Alice's ECDH agreement object, which combines her private EC key with the server's public EC key.
                    final KeyAgreement dh = KeyAgreement.getInstance("ECDH");
                    // Initialize the agreement with Alice's private key; this private value is never sent through the channel.
                    dh.init(keyPair.getPrivate());
                    // Process the server's decoded public key; true marks the only and final phase of this two-party agreement.
                    dh.doPhase(DHSerPK, true);
                    // generate a sharedSecret
                    // Obtain the raw shared-secret bytes; the server computes the same value using the complementary key combination.
                    final byte[] sharedSecret = dh.generateSecret();
                    // Print the secret in hexadecimal for this demonstration; such logging exposes key material and is unsuitable for a deployed protocol.
                    print("Shared secret: %s", hex(sharedSecret));
                    // Create a SHA-256 digest instance for the exercise's shared-secret-to-AES-key derivation.
                    final MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-256");
                    // Hash the raw ECDH secret into 32 bytes; the original protocol then truncates this digest rather than using a full KDF.
                    final byte[] hashed = digestAlgorithm.digest(sharedSecret);

                    // Copy bytes 0 through 15 of the hash into an AES-128 key object, matching the server's extraction rule.
                    final SecretKeySpec aesKey = new SecretKeySpec(hashed, 0, 16, "AES");
                    // Create an AES-GCM decryptor; GCM provides authentication as well as encryption and requires no padding.
                    final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                    // Specify a 128-bit GCM authentication tag and the exact IV that arrived with the challenge ciphertext.
                    final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv);
                    // Initialize decryption with the derived AES key and received challenge parameters.
                    decrypt.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                    // Authenticate and decrypt the challenge; a tag failure throws instead of yielding a successfully authenticated plaintext.
                    final byte[] dt = decrypt.doFinal(challCT);

                    // Create a second byte buffer for the password-based challenge response.
                    ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream( );
                    // Append the UTF-8 password bytes first; the server must use the same encoding and ordering.
                    outputStream2.write(password.getBytes(StandardCharsets.UTF_8));
                    // Append the decrypted challenge bytes after the password; dt is the challenge plaintext on Alice's side.
                    outputStream2.write(dt);
                    // Extract password || challenge as one byte array for the response hash.
                    final byte[] combinedpwddt = outputStream2.toByteArray( );

                    // Create a separate SHA-256 instance to compute the password/challenge response, independent of the key-derivation digest.
                    final MessageDigest digestAlgorithm2 = MessageDigest.getInstance("SHA-256");
                    // Calculate the 32-byte response SHA-256(password || challenge) required by this teaching protocol.
                    final byte[] hashedchall = digestAlgorithm2.digest(combinedpwddt);

                    // Create a fresh AES-GCM cipher instance for encrypting the response under the already derived session key.
                    final Cipher aes2 = Cipher.getInstance("AES/GCM/NoPadding");
                    // Initialize response encryption and let the provider generate a fresh IV; reusing an IV with this AES key would be unsafe.
                    aes2.init(Cipher.ENCRYPT_MODE, aesKey);
                    // Encrypt the response digest and append its GCM authentication tag to the returned ciphertext bytes.
                    final byte[] ct2 = aes2.doFinal(hashedchall);
                    // Obtain the new response IV so the server can reproduce the parameters for this specific decryption.
                    final byte[] iv2 = aes2.getIV();
                    // Send the encrypted response digest first; the password itself is not sent as a plaintext message.
                    send("server", ct2);//encrypted chall
                    // Send the response IV second, matching the order in which the server receives these two messages.
                    send("server", iv2);


                // Finish Alice's successful signature-verification branch after transmitting her password response.
                }
                // Select the failure branch when the server signature is invalid; no challenge response is sent in this branch.
                else{
                    // Report the invalid signature; the original code then ends Alice's task, so the server may remain waiting for a response.
                    System.err.println("Invalid signature.");
                // End the signature-failure branch without deriving or using a session key from an unverified server contribution.
                }
            // Finish Alice's task after either sending a response or reporting the signature failure.
            }
        // Finish defining Alice's anonymous Agent and add that participant to the environment.
        });

        // Register the server and define the complementary receive/derive/sign/challenge/verify protocol.
        env.add(new Agent("server") {
            // Mark the server's following method as its implementation of the Agent task contract.
            @Override
            // Run the server task and propagate cryptographic or communication errors to the simulation.
            public void task() throws Exception {
                // get PK from alice and save it as a key
                // Wait for Alice's encoded public contribution A; the variable name says DH although this key uses EC.
                final byte[] AliceDHPk= receive("alice");
                // Wrap the received bytes in an X.509 public-key specification for decoding.
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(AliceDHPk);
                // Decode Alice's contribution using an EC KeyFactory and retain its EC public-key interface.
                final ECPublicKey alicePK = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);

                // Read Alice's curve parameters so the server generates its temporary EC keys on a compatible curve.
                final ECParameterSpec dhParamSpec = alicePK.getParams();

                // create your own DH key pair
                // Create an EC generator for the server's temporary ECDH key pair, separate from its RSA identity pair.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                // Initialize the generator with the same curve parameters carried by Alice's public key.
                kpg.initialize(dhParamSpec);
                // Generate the server's private EC key and corresponding public contribution B for this session.
                final KeyPair keyPair = kpg.generateKeyPair();
                //send("alice", keyPair.getPublic().getEncoded());
                // Print the server's public EC contribution in hexadecimal; public key bytes may be disclosed to peers.
                print("My contribution to ECDH: %s", hex(keyPair.getPublic().getEncoded()));

                // Create the server's ECDH agreement object for deriving the shared session secret.
                final KeyAgreement dh = KeyAgreement.getInstance("ECDH");
                // Initialize ECDH with the server's private EC key, which remains local to the server task.
                dh.init(keyPair.getPrivate());
                // Complete the agreement with Alice's public EC key; true indicates the final phase.
                dh.doPhase(alicePK, true);

                // Extract the raw shared secret that must equal the value Alice derives from her private key and B.
                final byte[] sharedSecret = dh.generateSecret();
                // Print the shared secret for inspection in the exercise; exposing this value would compromise session secrecy in a real system.
                print("Shared secret: %s", hex(sharedSecret));
                // Create SHA-256 for the same shared-secret hashing rule that Alice uses.
                final MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-256");
                // Hash the ECDH secret into 32 bytes before choosing the first 16 bytes as the session AES key.
                final byte[] hashed = digestAlgorithm.digest(sharedSecret);
                // Print the digest's byte count to demonstrate that SHA-256 returns 32 bytes.
                print("Hashed length: %s", hashed.length);

                // Create a byte buffer to assemble the exact public-key transcript authenticated by the server.
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                // Append the received encoded Alice contribution A first, retaining the received byte representation.
                outputStream.write(AliceDHPk);
                // Append the server's own encoded contribution B second, producing the agreed A || B ordering.
                outputStream.write(keyPair.getPublic().getEncoded());

                // Extract the full concatenated transcript as a byte array to pass into the signature API.
                final byte[] combinedAB = outputStream.toByteArray( );

                // Declare the signature algorithm name for proving the server's identity to Alice.
                final String signingAlgorithm =
                        // Choose SHA-256 with RSA; the signer hashes the supplied transcript internally before producing its signature.
                        "SHA256withRSA";
//         "SHA256withDSA";
//        "SHA256withECDSA";
                // Retain the original unused key-algorithm label; signing uses the already generated serverKeyPair instead.
                final String keyAlgorithm =
                        // Store RSA in that label without generating a new key or altering which signing key is used.
                        "RSA";
//         "DSA";
//         "EC";
                // Create a Signature object configured for the selected RSA/SHA-256 signing algorithm.
                final Signature signer = Signature.getInstance(signingAlgorithm);
                // Initialize signing with the server's long-term RSA private key, distinct from its temporary ECDH private key.
                signer.initSign(serverKeyPair.getPrivate());
                // Finally, we load the document into the signature object and sign it
                // Load the ordered A || B transcript into the signer so both public contributions are covered by the signature.
                signer.update(combinedAB);
                // Finish signing and obtain signature bytes that Alice can verify using the trusted server RSA public key.
                final byte[] signature = signer.sign();

                // Send the server's encoded EC public contribution B first so Alice can reconstruct its agreement key.
                send("alice", keyPair.getPublic().getEncoded());//my B
                // Send the RSA signature second; despite the original inline note, it authenticates the full A || B transcript.
                send("alice", signature);//signiture of B

                // Create a cryptographically strong random-number generator for the fresh challenge.
                final SecureRandom random = new SecureRandom();
                // Allocate 32 bytes, giving the challenge the protocol's required 256-bit length.
                byte[] chall = new byte[32]; //ustvarim nov prazen nonce
                // Fill every challenge byte with fresh random data so an old response does not simply answer this challenge.
                random.nextBytes(chall);

                // Construct the same AES-128 key Alice will obtain from the first 16 bytes of the SHA-256 shared-secret digest.
                final SecretKeySpec aesKey = new SecretKeySpec(hashed, 0, 16, "AES");
                // Create an AES-GCM cipher to encrypt and authenticate the random challenge under that session key.
                final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                // Initialize encryption and allow the provider to generate a new GCM IV for the challenge.
                aes.init(Cipher.ENCRYPT_MODE, aesKey);
                // Encrypt the challenge and append a GCM authentication tag; no manual block padding is needed.
                final byte[] ct = aes.doFinal(chall);
                // Retrieve the challenge IV, which is public but must accompany the corresponding ciphertext.
                final byte[] iv = aes.getIV();
                // Send the authenticated challenge ciphertext third in the server's response sequence.
                send("alice", ct);//encrypted chall
                // Send the challenge IV fourth, matching Alice's receive order after B, signature, and ciphertext.
                send("alice", iv);

                // Wait for Alice's encrypted password-response digest; this is the first message of her reply.
                final byte[] ct2 = receive("alice");
                // Receive the separate fresh IV Alice used for that encrypted response.
                final byte[] iv2 = receive("alice");
                // Create an AES-GCM cipher for decrypting and authenticating Alice's response.
                final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                // Specify the expected 128-bit tag length and the response IV received from Alice.
                final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv2);
                // Initialize response decryption with the same session AES key and these response-specific GCM parameters.
                decrypt.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                // Authenticate and decrypt the response into dt; here dt is a digest, whereas Alice used dt for the challenge plaintext.
                final byte[] dt = decrypt.doFinal(ct2);

                // Create a buffer to reconstruct the expected password/challenge input independently of Alice's claimed digest.
                ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream( );
                // Append the shared password encoded as UTF-8, exactly as Alice did.
                outputStream2.write(password.getBytes(StandardCharsets.UTF_8));
                // Append the server's original random challenge, ensuring the expected response belongs to this challenge.
                outputStream2.write(chall);
                // Extract the expected input password || challenge in the same ordering and encoding used by Alice.
                final byte[] combinedpwddt = outputStream2.toByteArray( );

                // Create a SHA-256 instance for the server's independent computation of the expected response.
                final MessageDigest digestAlgorithm2 = MessageDigest.getInstance("SHA-256");
                // Hash the reconstructed bytes to obtain the expected 32-byte password-response digest.
                final byte[] hashedchall = digestAlgorithm2.digest(combinedpwddt);
                // Print Alice's decrypted response digest for exercise debugging; password-derived responses should not be exposed in application logs.
                print("Alice hashed challange with pwd: %s", hex(dt));
                // Print the server's expected digest to make the comparison visible in the teaching example.
                print("Server hashed challange with pwd: %s", hex(hashedchall));
                // Compare the two digest arrays for equal contents; Arrays.equals is an ordinary comparison rather than a dedicated timing-resistant comparison.
                if(Arrays.equals(dt, hashedchall)){
                    // Report that Alice returned the expected password-based response for this session's challenge.
                    System.out.println("Alice is authenticated.");
                // Select the authentication-failure branch when the decrypted response differs from the expected digest.
                }else{
                    // Report authentication failure instead of treating a mismatched response as accepted.
                    System.out.println("Alice is NOT authenticated.");
                    // Stop the server task immediately on authentication failure; no further application communication follows here.
                    return;
                // Finish the server's decision about whether Alice supplied the expected password/challenge response.
                }

            // Finish the server task after accepting the response or returning from its failure branch.
            }
        // Finish defining the server's anonymous Agent and register it with the environment.
        });

        // Create the message channel between the two registered participants named alice and server.
        env.connect("alice", "server");
        // Start the environment so both Agent tasks execute and exchange messages through the connected channel.
        env.start();
    // Finish the main method after starting the simulation.
    }
// Close the separately named annotated MidtermBCommented class.
}