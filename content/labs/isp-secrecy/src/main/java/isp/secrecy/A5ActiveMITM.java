package isp.secrecy;

import java.nio.charset.StandardCharsets;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;

import fri.isp.Agent;
import fri.isp.Environment;

/**
 * The goal of this assignment is to demonstrate that ciphertexts are vulnerable
 * to modifications
 * and that in many cases these modifications have (painfully) predictable
 * effect on the plaintext.
 * 
 * The setting. Teaching assistant David wants to send a highly confidential
 * email to professor
 * Denis, most likely containing questions for the upcoming exam. Needless to
 * say, you are very
 * interested in seeing the contents of that email. Luckily, a few things go
 * your way.
 * 
 * First, David has no Internet connectivity, but your mobile phone does. So you
 * kindly offer to
 * set up a mobile hotspot through which David will be able to connect to an
 * FMTP server. (You get
 * to play the role of the man-in-the-middle!)
 * 
 * FMTP is a new kind of a mail protocol; it stands for Funny Mail Transfer
 * Protocol, and it is a
 * simple text-based protocol. The first line denotes the email of the
 * recipient, the second line
 * the email of the sender, the third line the subject of the email, then we
 * have an empty line and
 * finally we have the email contents.
 * 
 * All that David's mail client has to do to send an email, is to deliver a
 * string like the
 * following to the FMTP server.
 * 
 * ```txt
 * recipient@somedomain.com
 * sender@anotherdomain.com
 * Subject line
 * 
 * <Email body>
 * ```
 * 
 * Any preceding or trailing spaces on any line are removed before processing.
 * For instance, we
 * could have written the email above as follows and it would have made no
 * difference.
 * 
 * ```txt
 * recipient@somedomain.com
 * sender@anotherdomain.com
 * Subject line
 * 
 * <Email body>
 * ```
 * 
 * Second, David is naive enough to tell you that he's sending the email to
 * professor Denis.
 * So you know the contents of the first line of the plaintext.
 * 
 * Third, David is using AES in CTR mode. No integrity checks are in place.
 * 
 * The task. As the man-in-the-middle (MITM), modify the messages so that the
 * FMTP server will not send the
 * email to professor Denis, but instead will forward the email to the address
 * that you control. In
 * this exercise the address is `isp@gmail.com`.
 *
 * Modify only the student agent's task, using intercepted data and the known
 * recipient line.
 * Do not access the shared key or change David's task, the server's task, or
 * the environment.
 * The provided server only decrypts and prints the message; it does not
 * actually forward email.
 * Success means that the first line of its decrypted output, after trimming
 * surrounding spaces,
 * equals isp@gmail.com. Preserve the sender, subject, blank line, and body
 * unchanged.
 * 
 */
public class A5ActiveMITM {
    public static void main(String[] args) throws Exception {
        // David and FMTP server both know the same shared secret key
        final Key key = KeyGenerator.getInstance("AES").generateKey();

        final Environment env = new Environment();

        // Register David as a participant and define that agent's task.
        env.add(new Agent("david") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run David's communication steps; propagate cryptographic failures to the environment.
            public void task() throws Exception {
                // Begin the simulated email with the original recipient address and a newline.
                final String message = "prf.denis@fri.si\n" +
                        // Append David's sender address and its separating newline.
                        "david@fri.si\n" +
                        // Append the subject and blank line separating the header from the message body.
                        "Some ideas for the exam\n\n" +
                        // Finish the simulated email with its message body.
                        "Hi! Find attached <some secret stuff>!";

                // Create AES in counter mode; its keystream needs an IV and provides no message authentication.
                final Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
                // Initialize AES encryption with the shared secret key; this call generates a fresh IV.
                aes.init(Cipher.ENCRYPT_MODE, key);
                // Encrypt David's UTF-8 email with AES-CTR, which does not authenticate the ciphertext.
                final byte[] ct = aes.doFinal(message.getBytes(StandardCharsets.UTF_8));
                // Read the encryption IV, which the receiver needs but which does not need to stay secret.
                final byte[] iv = aes.getIV();
                // Display the original email and its ciphertext for comparison with the intercepted version.
                print("sending: '%s' (%s)", message, hex(ct));
                // Send Server the encrypted email bytes before their IV.
                send("server", ct);
                // Send Server the public IV needed to initialize decryption.
                send("server", iv);
            // Finish David's task after its communication steps complete.
            }
        // Finish defining David and add this agent to the environment.
        });

        // Register Student as a participant and define that agent's task.
        env.add(new Agent("student") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Student's communication steps; propagate cryptographic failures to the environment.
            public void task() throws Exception {
                // Intercept David's ciphertext through the environment's configured man-in-the-middle route.
                final byte[] bytes = receive("david");
                // Receive David's public IV after the ciphertext, matching his send order.
                final byte[] iv = receive("david");
                // Display the intercepted ciphertext before modifying its bytes.
                print(" IN: %s", hex(bytes));

                // As the person-in-the-middle, modify the ciphertext
                // so that the FMTP server will send the email to you
                // (Needless to say, you are not allowed to use the key
                // that is being used by david and server.)

                // Record the known original recipient text at the start of the encrypted email.
                String org = "prf.denis@fri.si";
                // Choose the replacement recipient address for this CTR malleability demonstration.
                String target = "isp@gmail.com";

                // Encode the known original recipient as the exact UTF-8 bytes to be replaced.
                byte[] orgBytes = org.getBytes(StandardCharsets.UTF_8);
                // Encode the replacement recipient so the byte differences can be applied to the ciphertext.
                byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);

                // Visit each byte occupied by the original recipient, preserving the length of the email header.
                for (int i = 0; i < orgBytes.length; i++) {
                    // Reserve a byte for the desired plaintext at this recipient position.
                    byte replacement;
                    // Use a replacement-address byte while that shorter address still has bytes remaining.
                    if (i < targetBytes.length) {
                        // Select the target address byte that should appear at this position after decryption.
                        replacement = targetBytes[i];
                    // Use a space when the shorter replacement address has no byte at this position.
                    } else {
                        // Pad the remaining positions with spaces so following email fields keep their offsets.
                        replacement = (byte) ' ';
                    // Finish the conditional block before continuing with the next step.
                    }
                    // XOR out the known plaintext byte and XOR in its replacement; CTR carries this change into plaintext.
                    bytes[i] = (byte) (bytes[i] ^ orgBytes[i] ^ replacement);
                // Finish applying the recipient-byte replacements.
                }

                // Display the modified ciphertext that will be forwarded to the server.
                print("OUT: %s", hex(bytes));
                // Send Server the modified ciphertext without needing the sender's secret key.
                send("server", bytes);
                // Send Server the public IV needed to initialize decryption.
                send("server", iv);
            // Finish Student's task after its communication steps complete.
            }
        // Finish defining Student and add this agent to the environment.
        });

        // Register Server as a participant and define that agent's task.
        env.add(new Agent("server") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Server's communication steps; propagate cryptographic failures to the environment.
            public void task() throws Exception {
                // Receive the email ciphertext on David's logical connection, after the interceptor has modified it.
                final byte[] ct = receive("david");
                // Receive David's public IV after the ciphertext, matching his send order.
                final byte[] iv = receive("david");
                // Create AES in counter mode; its keystream needs an IV and provides no message authentication.
                final Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
                // Prepare AES decryption with the shared key and the received IV.
                aes.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                // Decrypt the received CTR bytes; no authentication tag detects the changed recipient.
                final byte[] pt = aes.doFinal(ct);
                // Decode the recovered email bytes as UTF-8, including the modified recipient field.
                final String message = new String(pt, StandardCharsets.UTF_8);

                // Display the email the server recovered alongside the ciphertext it actually received.
                print("got: '%s' (%s)", message, hex(ct));
            // Finish Server's task after its communication steps complete.
            }
        // Finish defining Server and add this agent to the environment.
        });

        env.mitm("david", "server", "student");
        env.start();
    }
}
