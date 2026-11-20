package isp.signatures;

import fri.isp.Agent;
import fri.isp.Environment;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
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

public class MidtermB {
    public static void main(String[] args) throws Exception {
        Environment env = new Environment();

        // Serverjev RSA par ključev
        final KeyPairGenerator rsaGenerator =
                KeyPairGenerator.getInstance("RSA");
        rsaGenerator.initialize(2048);

        final KeyPair serverKeyPair = rsaGenerator.generateKeyPair();

        // Skupno geslo Alice in Serverja
        final String password = "MojeGeslo123";

        env.add(new Agent("alice") {
            @Override
            public void task() throws Exception {
                final String signingAlgorithm =
                        "SHA256withRSA";
//         "SHA256withDSA";
//        "SHA256withECDSA";
                final String keyAlgorithm =
                        "RSA";
//         "DSA";
//         "EC";
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC"); //EC pomeni eliptic curves
                kpg.initialize(256);

                // Generate key pair
                final KeyPair keyPair = kpg.generateKeyPair();

                // send "PK" to bob ("PK": A = g^a, "SK": a)
                send("server", keyPair.getPublic().getEncoded());
                //print("My contribution to ECDH: %s", hex(keyPair.getPublic().getEncoded()));
                final byte[] B = receive("server");
                final byte[] ServerSignature = receive("server");
                final byte[] challCT = receive("server");
                final byte[] iv = receive("server");

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                outputStream.write(keyPair.getPublic().getEncoded());
                outputStream.write(B);

                final byte[] combinedAB = outputStream.toByteArray( );

                final Signature verifier = Signature.getInstance(signingAlgorithm);

                /*
                 * We have to initialize in the verification mode. We only need
                 * to know public key of the signer.
                 */
                verifier.initVerify(serverKeyPair.getPublic());

                // Check whether the signature is valid
                verifier.update(combinedAB);

                if (verifier.verify(ServerSignature)){
                    System.out.println("Valid signature.");

                    //Let's generate a sharedSecret
                    final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(B);
                    final ECPublicKey DHSerPK = (ECPublicKey) KeyFactory.getInstance("EC")
                            .generatePublic(keySpec);
                    // Run the agreement protocol
                    final KeyAgreement dh = KeyAgreement.getInstance("ECDH");
                    dh.init(keyPair.getPrivate());
                    dh.doPhase(DHSerPK, true);
                    // generate a sharedSecret
                    final byte[] sharedSecret = dh.generateSecret();
                    print("Shared secret: %s", hex(sharedSecret));
                    final MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-256");
                    final byte[] hashed = digestAlgorithm.digest(sharedSecret);

                    final SecretKeySpec aesKey = new SecretKeySpec(hashed, 0, 16, "AES");
                    final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                    final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv);
                    decrypt.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                    final byte[] dt = decrypt.doFinal(challCT);

                    ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream( );
                    outputStream2.write(password.getBytes(StandardCharsets.UTF_8));
                    outputStream2.write(dt);
                    final byte[] combinedpwddt = outputStream2.toByteArray( );

                    final MessageDigest digestAlgorithm2 = MessageDigest.getInstance("SHA-256");
                    final byte[] hashedchall = digestAlgorithm2.digest(combinedpwddt);

                    final Cipher aes2 = Cipher.getInstance("AES/GCM/NoPadding");
                    aes2.init(Cipher.ENCRYPT_MODE, aesKey);
                    final byte[] ct2 = aes2.doFinal(hashedchall);
                    final byte[] iv2 = aes2.getIV();
                    send("server", ct2);//encrypted chall
                    send("server", iv2);


                }
                else{
                    System.err.println("Invalid signature.");
                }
            }
        });

        env.add(new Agent("server") {
            @Override
            public void task() throws Exception {
                // get PK from alice and save it as a key
                final byte[] AliceDHPk= receive("alice");
                final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(AliceDHPk);
                final ECPublicKey alicePK = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(keySpec);

                final ECParameterSpec dhParamSpec = alicePK.getParams();

                // create your own DH key pair
                final KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                kpg.initialize(dhParamSpec);
                final KeyPair keyPair = kpg.generateKeyPair();
                //send("alice", keyPair.getPublic().getEncoded());
                print("My contribution to ECDH: %s", hex(keyPair.getPublic().getEncoded()));

                final KeyAgreement dh = KeyAgreement.getInstance("ECDH");
                dh.init(keyPair.getPrivate());
                dh.doPhase(alicePK, true);

                final byte[] sharedSecret = dh.generateSecret();
                print("Shared secret: %s", hex(sharedSecret));
                final MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-256");
                final byte[] hashed = digestAlgorithm.digest(sharedSecret);
                print("Hashed length: %s", hashed.length);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                outputStream.write(AliceDHPk);
                outputStream.write(keyPair.getPublic().getEncoded());

                final byte[] combinedAB = outputStream.toByteArray( );

                final String signingAlgorithm =
                        "SHA256withRSA";
//         "SHA256withDSA";
//        "SHA256withECDSA";
                final String keyAlgorithm =
                        "RSA";
//         "DSA";
//         "EC";
                final Signature signer = Signature.getInstance(signingAlgorithm);
                signer.initSign(serverKeyPair.getPrivate());
                // Finally, we load the document into the signature object and sign it
                signer.update(combinedAB);
                final byte[] signature = signer.sign();

                send("alice", keyPair.getPublic().getEncoded());//my B
                send("alice", signature);//signiture of B

                final SecureRandom random = new SecureRandom();
                byte[] chall = new byte[32]; //ustvarim nov prazen nonce
                random.nextBytes(chall);

                final SecretKeySpec aesKey = new SecretKeySpec(hashed, 0, 16, "AES");
                final Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
                aes.init(Cipher.ENCRYPT_MODE, aesKey);
                final byte[] ct = aes.doFinal(chall);
                final byte[] iv = aes.getIV();
                send("alice", ct);//encrypted chall
                send("alice", iv);

                final byte[] ct2 = receive("alice");
                final byte[] iv2 = receive("alice");
                final Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                final GCMParameterSpec ivSpec = new GCMParameterSpec(128, iv2);
                decrypt.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);
                final byte[] dt = decrypt.doFinal(ct2);

                ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream( );
                outputStream2.write(password.getBytes(StandardCharsets.UTF_8));
                outputStream2.write(chall);
                final byte[] combinedpwddt = outputStream2.toByteArray( );

                final MessageDigest digestAlgorithm2 = MessageDigest.getInstance("SHA-256");
                final byte[] hashedchall = digestAlgorithm2.digest(combinedpwddt);
                print("Alice hashed challange with pwd: %s", hex(dt));
                print("Server hashed challange with pwd: %s", hex(hashedchall));
                if(Arrays.equals(dt, hashedchall)){
                    System.out.println("Alice is authenticated.");
                }else{
                    System.out.println("Alice is NOT authenticated.");
                    return;
                }

            }
        });

        env.connect("alice", "server");
        env.start();
    }
}