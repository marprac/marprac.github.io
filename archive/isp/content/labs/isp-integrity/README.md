# Message integrity lab

This Java lab introduces cryptographic hashes and message authentication with
HMAC. The two examples are complete; `A1AgentCommunicationHMAC` is a starter
exercise for you to implement.

## Setup and running

Install JDK 21 and Maven 3. Check `java -version` and `mvn -version`;
Maven must use the intended JDK. The first build requires network access to
download Maven dependencies and the communication library from JitPack.

```sh
mvn compile
```

Open the repository as a Maven project and run the desired class's `main()`
method under `src/main/java/isp/integrity/`. Alternatively, in a POSIX terminal:

```sh
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" isp.integrity.HMACExample
```

Replace the class name to run another example or the exercise. On Windows, use
your IDE or adapt the classpath separator and file-reading syntax to your shell.
Recompile after editing. A successful build does not mean the exercise is complete.

## Examples and exercise

| Class | Goal |
| --- | --- |
| `MessageDigestExample` | Compute a SHA-256 digest and display it in hexadecimal. |
| `HMACExample` | Generate a shared key, calculate and verify an HMAC, and observe rejection of a modified message. |
| `A1AgentCommunicationHMAC` | Implement ten authenticated request-reply rounds between Alice and Bob. |

For A1, each round consists of Alice's message and Bob's reply: twenty messages
in total. For **each** message, compute an HmacSHA256 tag over its UTF-8 bytes,
send the message and tag, and have the recipient independently recompute and
compare the tag with `MessageDigest.isEqual`. Only display or process a message
after successful verification. On failure, reject it and stop the exchange rather
than waiting indefinitely for a reply. Both agents must use the same shared key,
but separate `Mac` instances. Agree on the order of `send` and `receive` calls;
the tag is binary data, not UTF-8 text. Use hexadecimal for logging if needed.

Run the completed exercise and check that all ten rounds finish and both agents
display verified messages. Then deliberately change a message or tag in transit
and confirm that the recipient rejects it. Restore your implementation afterward.
If the program waits, check that sends and receives match in both order and count.

A plain hash detects accidental changes only when the expected digest is trusted:
an attacker can replace both a message and its digest. HMAC uses a secret key to
authenticate messages and detect changes. It does not provide confidentiality,
prevent replay on its own, or prove which of two shared-key holders sent a message.
This exercise assumes the key was shared securely and does not implement replay
protection.

## References

- [MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [Mac](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html)
- [KeyGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyGenerator.html)
- [Java standard algorithm names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html)
- [Communication library source](https://github.com/lem-course/isp-communication-model)
