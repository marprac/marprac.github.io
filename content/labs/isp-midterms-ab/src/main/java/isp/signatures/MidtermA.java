package isp.signatures;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.*;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
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

public class MidtermA {
    public static void main(String[] args) throws Exception {
        Environment env = new Environment();
        final String signingAlgorithm = "SHA256withRSA";
        final String keyAlgorithm = "RSA";
        // Create key pairs
        final KeyPair AliceKey = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair();
        final KeyPair ServerKey = KeyPairGenerator.getInstance(keyAlgorithm).generateKeyPair();
        final String password = "RadImamTaKolokvij2026";

        env.add(new Agent("alice") {
            public void task() throws Exception {
                final String signingAlgorithm =
                        "SHA256withRSA";

                final String keyAlgorithm =
                        "RSA";

                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
                kpg.initialize(2048);
                // Generate key pair
                final KeyPair keyPair = kpg.generateKeyPair();
                // The message we want to sign
                final byte[] DHpk = keyPair.getPublic().getEncoded();
                //Alice creates Signature object defining Signature algorithm.
                final Signature signer = Signature.getInstance(signingAlgorithm);
                /*
                 * We initialize the signature object with
                 * - Operation modes (SIGN) and
                 * - provides appropriate ***Private*** Key
                 */
                signer.initSign(AliceKey.getPrivate());
                // Finally, we load the document into the signature object and sign it
                signer.update(DHpk);
                final byte[] signature = signer.sign();
                send("server", DHpk);
                send("server", signature);
                /*
                 * To verify the signature, we create another signature object
                 * and specify its algorithm
                 */
                final Signature verifier = Signature.getInstance(signingAlgorithm);
                /*
                 * We have to initialize in the verification mode. We only need
                 * to know public key of the signer.
                 */
                verifier.initVerify(ServerKey.getPublic());
                // Check whether the signature is valid
                final byte[] DHpkServer = receive("server");//dobimo podpisan DH ključ
                verifier.update(DHpkServer);

                if (verifier.verify(receive("server"))){
                    System.out.println("Valid signature.");
                    //Let's generate a sharedSecret from their public DH key and our private DH key
                    final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(DHpkServer);
                    final DHPublicKey DHSerPK = (DHPublicKey) KeyFactory.getInstance("DH")
                            .generatePublic(keySpec);
                    // Run the agreement protocol
                    final KeyAgreement dh = KeyAgreement.getInstance("DH");
                    dh.init(keyPair.getPrivate());
                    dh.doPhase(DHSerPK, true);
                    // generate a sharedSecret
                    final byte[] sharedSecret = dh.generateSecret();
                    print("Shared secret: g^ab = A^b = %s", hex(sharedSecret));

                    //create the token
                    final int counter = 1000;
                    final SecureRandom random = new SecureRandom();
                    final byte[] s = new byte[32]; //ustvarim nov prazen nonce
                    random.nextBytes(s);
                    final byte[] token=hash(1,s);
                    byte[][] TokenArray = new byte[1000][];
                    TokenArray[0]=token;
                    for(int i=1;i<counter;i++){
                        TokenArray[i]=hash(1,TokenArray[i-1]);
                    }
                    print("Alice 1000th token = %s", hex(TokenArray[counter-1]));

                    //let's send the token using a AES key which is derived from our Shared Secret
                    final SecretKeySpec aesKey = new SecretKeySpec(sharedSecret,
                            0, 16, "AES");

                    final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                    aes.init(Cipher.ENCRYPT_MODE, aesKey);

                    final byte[] ct = aes.doFinal(TokenArray[counter-1]);
                    final byte[] iv = aes.getIV();

                    send("server", ct);
                    send("server", iv);


                }
                else
                    System.err.println("Invalid signature.");

            }
        });
        env.add(new Agent("server") {
            public void task() throws Exception {
                final byte[] DHpkAlice = receive("alice");
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(DHpkAlice);
                final DHPublicKey DHAliPK = (DHPublicKey) KeyFactory.getInstance("DH")
                        .generatePublic(keySpec);

                final DHParameterSpec dhParamSpec = DHAliPK.getParams();

                // create your own DH key pair
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
                kpg.initialize(dhParamSpec);
                final KeyPair keyPair = kpg.generateKeyPair();

                final String signingAlgorithm =
                        "SHA256withRSA";

                final String keyAlgorithm =
                        "RSA";
                // The message we want to sign
                final byte[] DHpk= keyPair.getPublic().getEncoded();
                /*
                 * Alice creates Signature object defining Signature algorithm.
                 */
                final Signature signer = Signature.getInstance(signingAlgorithm);
                /*
                 * We initialize the signature object with
                 * - Operation modes (SIGN) and
                 * - provides appropriate ***Private*** Key
                 */
                signer.initSign(ServerKey.getPrivate());

                // Finally, we load the document into the signature object and sign it
                signer.update(DHpk);
                final byte[] signature = signer.sign();
                send("alice", DHpk);
                send("alice", signature);
                /*
                 * To verify the signature, we create another signature object
                 * and specify its algorithm
                 */
                final Signature verifier = Signature.getInstance(signingAlgorithm);
                /*
                 * We have to initialize in the verification mode. We only need
                 * to know public key of the signer.
                 */
                verifier.initVerify(AliceKey.getPublic());

                // Check whether the signature is valid
                verifier.update(DHpkAlice);//dobimo podpisan DH ključ

                if (verifier.verify(receive("alice"))){
                    System.out.println("Valid signature.");

                    // Run the agreement protocol
                    final KeyAgreement dh = KeyAgreement.getInstance("DH");
                    dh.init(keyPair.getPrivate());
                    dh.doPhase(DHAliPK, true);
                    // generate a shared AES key
                    final byte[] sharedSecret = dh.generateSecret();
                    print("Shared secret: g^ab = A^b = %s", hex(sharedSecret));

                    final byte[] ciphertext = receive("alice");
                    final byte[] iv = receive("alice");

                    //System.out.println("[IV] prejet " + Agent.hex(iv));//sprintam IV
                    final SecretKeySpec aesKey = new SecretKeySpec(sharedSecret,
                            0, 16, "AES");
                    final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                    final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv);
                    decrypt.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                    final byte[] dt = decrypt.doFinal(ciphertext);
                    final byte[] aliceToken=dt;

                    final SecureRandom random = new SecureRandom();
                    final byte[] salt = new byte[32];
                    random.nextBytes(salt);
                    final byte[] tag1 = mac(aliceToken,password,salt);
                    // use PBKDF2 with the password, salt, and number of iterations and required bit

                    send("lock", aliceToken);
                    send("lock",tag1);
                    send("lock",salt);
                }
                else{
                    System.err.println("Invalid signature.");
                }

            }
        });
        env.add(new Agent("lock") {
            public void task() throws Exception {
                final byte[] aliceToken = receive("server");
                final byte[] tag1 = receive("server");
                final byte[] salt = receive("server");

                byte[] AliceValidToken=new byte[32];
                if(verify(aliceToken,tag1,password,salt)){
                    AliceValidToken =  aliceToken;
                }
                System.out.println(verify(aliceToken,tag1,password,salt));
            }
        });

        env.connect("alice", "server");
        env.connect("alice", "lock");
        env.connect("server", "lock");
        env.start();
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
    public static boolean verify(byte[] payload, byte[] tag, String password, byte[] salt) throws Exception {
        final byte[] tag2 = mac(payload,password,salt);
        return MessageDigest.isEqual(tag, tag2);
    }

    /**
     * Computes the MAC tag over the message.
     *
     * @param payload  the message
     * @param password the password form which MAC key is derived
     * @param salt     the salt used to strengthen the password
     * @return the computed tag
     */
    public static byte[] mac(byte[] payload, String password, byte[] salt) throws Exception {
        final SecretKeyFactory pbkdf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        final KeySpec specs = new PBEKeySpec(password.toCharArray(), salt,
                1000, 128);
        final SecretKey generatedKey = pbkdf.generateSecret(specs);

        // for example, use the derived key as the HMAC key
        final Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(generatedKey.getEncoded(), "HmacSHA256"));
        final byte[] tag1 = hmac.doFinal(payload);
        return tag1;
    }

    /**
     * Hashes the given payload multiple times.
     *
     * @param times   the number of times the value is hashed
     * @param payload the initial value to be hashed
     * @return the final hash value
     */
    public static byte[] hash(int times, byte[] payload) throws Exception {
        byte[] token=payload;
        for(int i =0;i<times;i++){
            final MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-256");
            token = digestAlgorithm.digest(token);
        }
        return token;
    }
}