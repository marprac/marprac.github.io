// Place this class in isp.signatures; its source directory must match this Java package.
package isp.signatures;

// Import the participant API used to send, receive, and print messages inside each agent.
import fri.isp.Agent;
// Import the simulation environment that registers agents and connects their message queues.
import fri.isp.Environment;

// Import the cryptographic APIs used below, including Cipher, KeyAgreement, Mac, and SecretKeyFactory.
import javax.crypto.*;
// Import the DH public-key interface so received keys expose their shared group parameters.
import javax.crypto.interfaces.DHPublicKey;
// Import the container for the DH prime modulus and generator used by both participants.
import javax.crypto.spec.DHParameterSpec;
// Import the GCM settings object that supplies the authentication-tag size and received IV during decryption.
import javax.crypto.spec.GCMParameterSpec;
// Import the password-based key specification containing the password, salt, iteration count, and key length.
import javax.crypto.spec.PBEKeySpec;
// Import the wrapper that labels raw key bytes for AES or HMAC without generating new key material.
import javax.crypto.spec.SecretKeySpec;
// Retain the original UTF-8 charset import; this particular implementation does not use it.
import java.nio.charset.StandardCharsets;
// Import security APIs for key generation, signatures, secure randomness, and SHA-256 hashing.
import java.security.*;
// Import the generic key-specification interface used to hold the PBKDF2 parameters.
import java.security.spec.KeySpec;
// Import the X.509 public-key encoding description used when reconstructing received DH keys.
import java.security.spec.X509EncodedKeySpec;

/*
 * MIDTERM A – POTEK KRIPTOGRAFSKEGA PROTOKOLA
 *
 * 1. ZAČETNE NASTAVITVE
 *    - Alice ima svoj RSA par ključev.
 *    - Server ima svoj RSA par ključev.
 *    - Oba poznata javni RSA ključ druge strani.
 *    - Server in Lock poznata isto geslo.
 *
 * 2. ALICE ZAČNE DH
 *    - Ustvari DH par ključev (zasebni a, javni A).
 *    - S svojim zasebnim RSA ključem podpiše javni DH ključ A
 *      z algoritmom SHA256withRSA.
 *    - Serverju pošlje A in RSA podpis.
 *
 * 3. SERVER NADALJUJE DH
 *    - Iz Alicinega javnega DH ključa pridobi DH parametre.
 *    - Ustvari svoj DH par ključev z enakimi parametri.
 *    - S svojim zasebnim RSA ključem podpiše javni DH ključ B.
 *    - Alice pošlje B in svoj RSA podpis.
 *
 * 4. MEDSEBOJNA AVTENTIKACIJA IN SKUPNA SKRIVNOST
 *    - Alice s Serverjevim javnim RSA ključem preveri podpis B.
 *    - Server z Alicinim javnim RSA ključem preveri podpis A.
 *    - Oba izvedeta DH KeyAgreement:
 *        Alice: zasebni a + javni B
 *        Server: zasebni b + javni A
 *    - Oba dobita enak sharedSecret.
 *
 * 5. VARNA KOMUNIKACIJA ALICE–SERVER
 *    - Iz sharedSecret vzameta prvih 16 bajtov za AES-128 ključ.
 *    - Alice z AES-128-GCM šifrira podatke.
 *    - Serverju pošlje ciphertext in IV.
 *    - Server z istim AES ključem podatke dešifrira.
 *
 * 6. AVTENTICIRAN PRENOS SERVER–LOCK
 *    - Server in Lock uporabljata skupno geslo.
 *    - Server ustvari naključen salt.
 *    - S PBKDF2WithHmacSHA256 iz gesla in salta izpelje
 *      128-bitni MAC ključ (1000 iteracij).
 *    - S ključem izračuna HMAC-SHA256 nad podatki.
 *    - Locku pošlje podatke, MAC tag in salt.
 *
 * 7. LOCK PREVERI PREJETE PODATKE
 *    - Iz gesla in prejetega salta z PBKDF2 izpelje isti ključ.
 *    - S HMAC-SHA256 preveri prejeti MAC tag.
 *    - Podatkom zaupa samo, če je preverjanje uspešno.
 *
 * 8. NADALJNJE PREVERJANJE
 *    - Lock uporablja SHA-256 za preverjanje prejetih vrednosti.
 *    - Pri tem ne potrebuje novega DH, RSA ali AES postopka.
 */

// Define the commented copy under a distinct class name so it can compile alongside the unchanged MidtermA.
public class MidtermACommented {
    // Enter the program through main; cryptographic and communication failures may propagate as exceptions.
    public static void main(String[] args) throws Exception {
        // Create the environment that will run Alice, Server, and Lock as separate communicating agents.
        Environment env = new Environment();
        // Choose SHA-256 with RSA signatures for the identity keys; the agents also declare the same algorithm locally.
        final String signingAlgorithm = "SHA256withRSA";
        // Select RSA as the identity-key algorithm, separate from the DH session-key agreement below.
        final String keyAlgorithm = "RSA";
        // Create key pairs
        // Generate Alice's RSA identity key pair; the enclosing main method makes its trusted public key available to Server.
        final KeyPair AliceKey = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair();
        // Generate Server's RSA identity key pair; Alice uses its trusted public key for signature verification.
        final KeyPair ServerKey = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair();
        // Set the exercise password shared by Server and Lock; this hardcoded value is demonstration data.
        final String password = "RadImamTaKolokvij2026";

        // Register Alice and start the anonymous Agent definition containing her side of the protocol.
        env.add(new Agent("alice") {
            // Define the task executed by Alice's agent, allowing checked failures to reach the environment.
            public void task() throws Exception {
                // Begin Alice's local declaration of the signature algorithm; its value continues on the next line.
                final String signingAlgorithm =
                        // Use SHA256withRSA to sign and verify the encoded DH public-key bytes.
                        "SHA256withRSA";

                // Begin the original local key-algorithm declaration, which is retained even though Alice does not use it later.
                final String keyAlgorithm =
                        // Assign the RSA algorithm name to that unused local constant without changing the original implementation.
                        "RSA";

                // Obtain a generator for finite-field Diffie-Hellman key pairs, rather than an elliptic-curve generator.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
                // Request a 2048-bit DH group/key configuration from the selected Java provider.
                kpg.initialize(2048);
                // Generate key pair
                // Generate Alice's private DH exponent and matching public DH contribution; only the public part will be sent.
                final KeyPair keyPair = kpg.generateKeyPair();
                // The message we want to sign
                // Encode Alice's DH public key as X.509 bytes, including the group parameters required by Server.
                final byte[] DHpk = keyPair.getPublic().getEncoded();
                //Alice creates Signature object defining Signature algorithm.
                // Create an RSA signature object configured to hash its input with SHA-256 before signing.
                final Signature signer = Signature.getInstance(signingAlgorithm);
                /*
                 * We initialize the signature object with
                 * - Operation modes (SIGN) and
                 * - provides appropriate ***Private*** Key
                 */
                // Initialize signing with Alice's private RSA identity key, which must not be sent to the other agents.
                signer.initSign(AliceKey.getPrivate());
                // Finally, we load the document into the signature object and sign it
                // Supply Alice's encoded DH public key as the signed data; this signature covers this contribution, not a full handshake transcript.
                signer.update(DHpk);
                // Complete the RSA signature and store its encoded signature bytes for transmission.
                final byte[] signature = signer.sign();
                // Send Alice's DH public key first so Server can reconstruct the key and its group parameters.
                send("server", DHpk);
                // Send the corresponding RSA signature second; Server must receive these two messages in the same order.
                send("server", signature);
                /*
                 * To verify the signature, we create another signature object
                 * and specify its algorithm
                 */
                // Create a separate signature object for checking Server's signed DH public key.
                final Signature verifier = Signature.getInstance(signingAlgorithm);
                /*
                 * We have to initialize in the verification mode. We only need
                 * to know public key of the signer.
                 */
                // Initialize verification with Server's already trusted RSA public key from the enclosing setup.
                verifier.initVerify(ServerKey.getPublic());
                // Check whether the signature is valid
                // Wait for Server's encoded DH public key; receive blocks until the next Server message arrives.
                final byte[] DHpkServer = receive("server");//dobimo podpisan DH ključ
                // Supply the exact received public-key bytes as the data whose signature Alice will verify.
                verifier.update(DHpkServer);

                // Receive Server's next message as the signature and proceed only if it verifies against those bytes and Server's public key.
                if (verifier.verify(receive("server"))){
                    // Report that Server's public-key signature verified; this message alone does not describe later protocol outcomes.
                    System.out.println("Valid signature.");
                    //Let's generate a sharedSecret from their public DH key and our private DH key
                    // Describe the received bytes as an X.509-encoded public key so a KeyFactory can parse them.
                    final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(DHpkServer);
                    // Obtain a DH KeyFactory and start reconstruction of Server's public key as the DH-specific key type.
                    final DHPublicKey DHSerPK = (DHPublicKey) KeyFactory.getInstance("DH")
                            // Finish decoding the key using the received X.509 specification; the reconstructed public key is used by DH.
                            .generatePublic(keySpec);
                    // Run the agreement protocol
                    // Create the DH agreement engine that combines Alice's private contribution with Server's public contribution.
                    final KeyAgreement dh = KeyAgreement.getInstance("DH");
                    // Initialize the agreement using Alice's private DH key, which remains local to Alice.
                    dh.init(keyPair.getPrivate());
                    // Process Server's public key; true marks the last and only peer phase of this two-party agreement.
                    dh.doPhase(DHSerPK, true);
                    // generate a sharedSecret
                    // Extract the shared-secret bytes that Server independently computes from its private key and Alice's public key.
                    final byte[] sharedSecret = dh.generateSecret();
                    // Print the shared secret in hexadecimal for this demonstration; such output exposes the session secret to log readers.
                    print("Shared secret: g^ab = A^b = %s", hex(sharedSecret));

                    //create the token
                    // Set the hash-chain length to 1000 entries, with the terminal entry later sent to Server.
                    final int counter = 1000;
                    // Create a cryptographically strong random-byte generator for Alice's secret hash-chain seed.
                    final SecureRandom random = new SecureRandom();
                    // Allocate the 32-byte seed buffer; allocation alone leaves zeroes until the next line fills it.
                    final byte[] s = new byte[32]; //ustvarim nov prazen nonce
                    // Replace the seed buffer's zeroes with unpredictable random bytes.
                    random.nextBytes(s);
                    // Apply SHA-256 once to the random seed using the hash helper to obtain the first chain token.
                    final byte[] token=hash(1,s);
                    // Allocate 1000 references for byte-array tokens; each entry will hold one successive hash value.
                    byte[][] TokenArray = new byte[1000][];
                    // Store the initial hashed seed at index zero, the first entry in the chain.
                    TokenArray[0]=token;
                    // Iterate from the second chain entry up to index 999, using the fixed counter as the exclusive upper bound.
                    for(int i=1;i<counter;i++){
                        // Hash the preceding token once and store the result as the next chain entry.
                        TokenArray[i]=hash(1,TokenArray[i-1]);
                    // End the loop after all remaining token entries have been generated.
                    }
                    // Print the terminal token at index counter minus one, rather than the original seed or earlier chain entries.
                    print("Alice 1000th token = %s", hex(TokenArray[counter-1]));

                    //let's send the token using a AES key which is derived from our Shared Secret
                    // Begin wrapping the raw DH shared-secret bytes as the AES key material used to encrypt the terminal token.
                    final SecretKeySpec aesKey = new SecretKeySpec(sharedSecret,
                            // Select bytes zero through fifteen for AES-128; this direct truncation is the original teaching construction, not a separate KDF.
                            0, 16, "AES");

                    // Create an AES cipher in authenticated GCM mode; GCM needs no block padding.
                    final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                    // Initialize encryption with the derived AES key and let the provider generate the encryption IV.
                    aes.init(Cipher.ENCRYPT_MODE, aesKey);

                    // Encrypt the terminal hash-chain token and append its GCM authentication tag to the returned ciphertext bytes.
                    final byte[] ct = aes.doFinal(TokenArray[counter-1]);
                    // Retrieve the generated IV so Server can reproduce the same GCM parameters for decryption.
                    final byte[] iv = aes.getIV();

                    // Send the encrypted token and its GCM tag to Server as the next protocol message.
                    send("server", ct);
                    // Send its public IV after the ciphertext, matching the order used by Server's receive calls.
                    send("server", iv);


                // End the successful-signature branch after Alice has sent the encrypted token and its IV.
                }
                // Select the failure branch if Server's signature did not verify; this original branch has a single statement and no braces.
                else
                    // Print the signature failure; the original program does not send a failure notification to the other agents.
                    System.err.println("Invalid signature.");

            // End Alice's task; on failure, peers may still be waiting for messages that were never sent.
            }
        // Finish Alice's anonymous Agent definition and add that participant to the environment.
        });
        // Register Server and open the anonymous Agent containing its DH, decryption, and forwarding steps.
        env.add(new Agent("server") {
            // Define the Server task, with checked failures propagated to the environment.
            public void task() throws Exception {
                // Receive Alice's encoded DH public key as the first message from Alice.
                final byte[] DHpkAlice = receive("alice");
                // Describe Alice's received public-key bytes using their X.509 encoding.
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(DHpkAlice);
                // Create the DH KeyFactory and begin reconstructing Alice's public key with a DH-specific type.
                final DHPublicKey DHAliPK = (DHPublicKey) KeyFactory.getInstance("DH")
                        // Complete decoding so Server can read the DH parameters and use Alice's public key in the agreement.
                        .generatePublic(keySpec);

                // Read the DH modulus and generator from Alice's key so Server uses the same group.
                final DHParameterSpec dhParamSpec = DHAliPK.getParams();

                // create your own DH key pair
                // Obtain a DH key-pair generator for Server's private and public session contributions.
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
                // Initialize Server's generator with Alice's received DH group parameters rather than selecting an unrelated group.
                kpg.initialize(dhParamSpec);
                // Generate Server's private DH key and the matching public contribution for this exchange.
                final KeyPair keyPair = kpg.generateKeyPair();

                // Begin Server's local signature-algorithm declaration, whose value is on the following line.
                final String signingAlgorithm =
                        // Configure Server's signatures and verifications to use SHA-256 with RSA.
                        "SHA256withRSA";

                // Begin the original local identity-key algorithm constant; it remains unused within Server's task.
                final String keyAlgorithm =
                        // Retain RSA as that unused local constant's value, preserving the original code.
                        "RSA";
                // The message we want to sign
                // Encode Server's public DH key into bytes for signing and transmission to Alice.
                final byte[] DHpk= keyPair.getPublic().getEncoded();
                /*
                 * Alice creates Signature object defining Signature algorithm.
                 */
                // Create Server's RSA signature engine; despite the nearby original comment, this signer belongs to Server.
                final Signature signer = Signature.getInstance(signingAlgorithm);
                /*
                 * We initialize the signature object with
                 * - Operation modes (SIGN) and
                 * - provides appropriate ***Private*** Key
                 */
                // Initialize signing with Server's private RSA identity key, distinct from its private DH session key.
                signer.initSign(ServerKey.getPrivate());

                // Finally, we load the document into the signature object and sign it
                // Feed Server's encoded DH public contribution into the signature engine; only this contribution is covered here.
                signer.update(DHpk);
                // Produce the RSA signature that Alice checks with Server's trusted public RSA key.
                final byte[] signature = signer.sign();
                // Send Server's encoded DH public key as Alice's first expected Server message.
                send("alice", DHpk);
                // Send its RSA signature next so Alice can verify that exact public-key encoding.
                send("alice", signature);
                /*
                 * To verify the signature, we create another signature object
                 * and specify its algorithm
                 */
                // Create another signature object for authenticating Alice's DH public-key message.
                final Signature verifier = Signature.getInstance(signingAlgorithm);
                /*
                 * We have to initialize in the verification mode. We only need
                 * to know public key of the signer.
                 */
                // Initialize verification with Alice's already trusted RSA identity public key.
                verifier.initVerify(AliceKey.getPublic());

                // Check whether the signature is valid
                // Load the previously received encoded DH contribution from Alice as the signature's message.
                verifier.update(DHpkAlice);//dobimo podpisan DH ključ

                // Receive Alice's signature and continue only if it verifies against her public key and original DH contribution.
                if (verifier.verify(receive("alice"))){
                    // Print that Alice's DH-public-key signature has verified successfully.
                    System.out.println("Valid signature.");

                    // Run the agreement protocol
                    // Obtain Server's finite-field DH agreement engine for this exchange.
                    final KeyAgreement dh = KeyAgreement.getInstance("DH");
                    // Initialize the agreement with Server's private DH key, which is never transmitted.
                    dh.init(keyPair.getPrivate());
                    // Process Alice's public DH key as the final agreement phase, matching Alice's computation with the roles reversed.
                    dh.doPhase(DHAliPK, true);
                    // generate a shared AES key
                    // Generate the shared-secret bytes; the following lines use part of this value as an AES key.
                    final byte[] sharedSecret = dh.generateSecret();
                    // Print the shared secret for demonstration; anyone who can read this output learns the session's key material.
                    print("Shared secret: g^ab = A^b = %s", hex(sharedSecret));

                    // Receive Alice's encrypted terminal token, including the appended GCM tag.
                    final byte[] ciphertext = receive("alice");
                    // Receive the IV belonging to that ciphertext, preserving Alice's ciphertext-then-IV message order.
                    final byte[] iv = receive("alice");

                    //System.out.println("[IV] prejet " + Agent.hex(iv));//sprintam IV
                    // Begin constructing Server's AES key from the same raw DH shared-secret bytes Alice used.
                    final SecretKeySpec aesKey = new SecretKeySpec(sharedSecret,
                            // Select the first 16 bytes for AES-128, exactly matching Alice's original direct-truncation rule.
                            0, 16, "AES");
                    // Create an AES-GCM cipher to decrypt and authenticate the received token.
                    final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                    // Specify a 128-bit GCM authentication tag and Alice's received IV for this ciphertext.
                    final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv);
                    // Initialize decryption using the reproduced AES key, tag size, and IV.
                    decrypt.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                    // Decrypt the ciphertext and verify the GCM tag; an authentication failure throws instead of returning trusted plaintext.
                    final byte[] dt = decrypt.doFinal(ciphertext);
                    // Give the decrypted bytes the aliceToken name; this assignment keeps a reference to the same byte array.
                    final byte[] aliceToken=dt;

                    // Create a secure random-byte generator for the password-derivation salt used on the Server-to-Lock channel.
                    final SecureRandom random = new SecureRandom();
                    // Allocate a 32-byte salt buffer to be filled with fresh random values.
                    final byte[] salt = new byte[32];
                    // Fill the salt with unpredictable bytes; Lock will receive this public salt to derive the same MAC key.
                    random.nextBytes(salt);
                    // Compute an HMAC over the token using the helper's password-derived key and this salt.
                    final byte[] tag1 = mac(aliceToken,password,salt);
                    // use PBKDF2 with the password, salt, and number of iterations and required bit

                    // Send the decrypted token to Lock in plaintext on this channel; the HMAC protects integrity, not confidentiality.
                    send("lock", aliceToken);
                    // Send the HMAC tag after the token so Lock can compare it with an independently computed tag.
                    send("lock",tag1);
                    // Send the PBKDF2 salt last; the salt is needed for key derivation and is not a secret.
                    send("lock",salt);
                // End the successful Alice-signature branch after Server forwards the token, tag, and salt.
                }
                // Begin the failure branch when Alice's RSA signature is invalid.
                else{
                    // Print the failure; the original branch does not send Lock a replacement message or failure notification.
                    System.err.println("Invalid signature.");
                // End the invalid-signature branch without adding any recovery behavior.
                }

            // Finish Server's task after the successful forwarding or failed-signature path.
            }
        // Close Server's anonymous Agent and register it with the environment.
        });
        // Register the Lock participant, which checks the password-authenticated token received from Server.
        env.add(new Agent("lock") {
            // Define Lock's verification task, allowing cryptographic and communication failures to propagate.
            public void task() throws Exception {
                // Receive the plaintext token as Lock's first message from Server.
                final byte[] aliceToken = receive("server");
                // Receive the accompanying HMAC tag as the second message from Server.
                final byte[] tag1 = receive("server");
                // Receive the salt as the third message, enabling Lock to reproduce the password-derived MAC key.
                final byte[] salt = receive("server");

                // Allocate a zero-filled placeholder for a validated token; it is only replaced if the following verification succeeds.
                byte[] AliceValidToken=new byte[32];
                // Recompute and verify the token's HMAC using the shared password and received salt before selecting the received token.
                if(verify(aliceToken,tag1,password,salt)){
                    // Store a reference to the received token as AliceValidToken after a successful MAC verification.
                    AliceValidToken =  aliceToken;
                // End the conditional assignment; an invalid tag leaves the zero-filled placeholder and does not throw in this original code.
                }
                // Perform MAC verification a second time and print its boolean result; AliceValidToken is not used afterward here.
                System.out.println(verify(aliceToken,tag1,password,salt));
            // End Lock's task after reporting the verification result.
            }
        // Close Lock's anonymous Agent definition and register that participant.
        });

        // Connect Alice and Server so their send and receive calls can exchange messages.
        env.connect("alice", "server");
        // Connect Alice and Lock as in the original topology, although this implementation sends no messages directly between them.
        env.connect("alice", "lock");
        // Connect Server and Lock for the token, HMAC tag, and salt transfer.
        env.connect("server", "lock");
        // Start the environment, which runs the registered agents and their communicating tasks.
        env.start();
    // End main after starting the agent environment.
    }

    /**
     * Verifies the MAC tag.
     *
     * @param payload  the message
     * @param tag      the MAC tag
     * @param password the password form which MAC key is derived
     * @param salt     the salt used to strengthen the password
     * @return true iff. the verification succeeds, false otherwise
     */
    // Define a reusable MAC verifier that receives the payload, supplied tag, shared password, and salt.
    public static boolean verify(byte[] payload, byte[] tag, String password, byte[] salt) throws Exception {
        // Independently compute the expected tag over this payload using the same password and salt.
        final byte[] tag2 = mac(payload,password,salt);
        // Compare the received and recomputed tags with MessageDigest's byte-array comparison intended for cryptographic digests.
        return MessageDigest.isEqual(tag, tag2);
    // End the verify helper after returning whether the tags match.
    }

    /**
     * Computes the MAC tag over the message.
     *
     * @param payload  the message
     * @param password the password form which MAC key is derived
     * @param salt     the salt used to strengthen the password
     * @return the computed tag
     */
    // Define the MAC helper that derives a key from a password and authenticates the supplied payload.
    public static byte[] mac(byte[] payload, String password, byte[] salt) throws Exception {
        // Obtain PBKDF2 using HMAC-SHA256 as its underlying pseudorandom function for password-based key derivation.
        final SecretKeyFactory pbkdf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        // Begin specifying the password characters and salt; the work factor and output-key length continue on the next line.
        final KeySpec specs = new PBEKeySpec(password.toCharArray(), salt,
                // Use the assignment's fixed 1000 PBKDF2 iterations and request a 128-bit derived key.
                1000, 128);
        // Perform PBKDF2 with those parameters and keep the resulting key object for use by HMAC.
        final SecretKey generatedKey = pbkdf.generateSecret(specs);

        // for example, use the derived key as the HMAC key
        // Obtain HMAC-SHA256, which computes a keyed authentication tag rather than encrypting the payload.
        final Mac hmac = Mac.getInstance("HmacSHA256");
        // Rewrap the PBKDF2 output bytes with the HmacSHA256 algorithm label and initialize the MAC engine with that key.
        hmac.init(new SecretKeySpec(generatedKey.getEncoded(), "HmacSHA256"));
        // Authenticate the entire payload and return the resulting 32-byte HMAC-SHA256 tag from doFinal.
        final byte[] tag1 = hmac.doFinal(payload);
        // Return the computed authentication-tag bytes to the caller.
        return tag1;
    // End the password-based MAC helper.
    }

    /**
     * Hashes the given payload multiple times.
     *
     * @param times   the number of times the value is hashed
     * @param payload the initial value to be hashed
     * @return the final hash value
     */
    // Define a helper that repeatedly applies SHA-256 to the payload the requested number of times.
    public static byte[] hash(int times, byte[] payload) throws Exception {
        // Start with a reference to the supplied payload; each iteration replaces this reference with a newly computed digest.
        byte[] token=payload;
        // Apply one hash per loop iteration up to the requested count; zero iterations return the original payload reference.
        for(int i =0;i<times;i++){
            // Obtain a fresh SHA-256 digest object for this iteration, avoiding any leftover digest state.
            final MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-256");
            // Hash the current token bytes and use the 32-byte result as the next iteration's input.
            token = digestAlgorithm.digest(token);
        // End the repeated-hashing loop after the requested number of SHA-256 applications.
        }
        // Return the final token bytes produced by the hash chain.
        return token;
    // Close the hash helper method.
    }
// Close the commented Midterm A class after its three helper methods.
}