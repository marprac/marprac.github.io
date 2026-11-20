# Authenticated and public-key encryption lab

This Java lab explores authenticated encryption with AES in GCM mode,
integrity of a bulk transfer using the public-space technique, and public-key
encryption with RSA. The two examples are complete; A1–A3 are starter
exercises for you to implement.

## Setup and running

Install JDK 21 and Maven 3. Check `java -version` and `mvn -version`;
Maven must use the intended JDK. The first build requires network access to
download Maven dependencies and the communication library from JitPack.

```sh
mvn compile
```

Open the repository as a Maven project and run the desired class's `main()`
method under `src/main/java/isp/`. Alternatively, in a POSIX terminal:

```sh
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" isp.GCMExample
```

Replace the class name to run another example or exercise. On Windows, use
your IDE or adapt the classpath separator and file-reading syntax to your
shell. Recompile after editing. A successful build does not mean the
exercises are complete: the starter skeletons also compile.

## Examples and exercises

| Class | Goal |
| --- | --- |
| `GCMExample` | Study the AES-GCM lifecycle: shared key, cipher-generated IV, `GCMParameterSpec`, and automatic authentication. The header asks what happens when the ciphertext, IV, or key changes. |
| `RSAExample` | Study RSA key pairs and padding, and complete the header assignments: change the modulus size and try `NoPadding`. |
| `A1AgentCommunicationGCM` | Complete ten request-reply rounds between Alice and Bob using AES-GCM with a fresh IV for every encryption. |
| `A2AgentCommunicationPublicSpace` | Send a 200 MB payload over an insecure high-bandwidth channel and verify its integrity through digests forwarded by the public-space agent. |
| `A3AgentCommunicationRSA` | Send a confidential message from Alice to Bob using Bob's public key; Bob decrypts and displays it. |

Read each class's header and inline comments for the full requirements.

## Working and checking your implementation

- Use the two examples as references for the `Cipher` lifecycle and the
  agent `send()`/`receive()` API.
- A1 requires twenty encrypted messages in total: Alice's message and Bob's
  reply in each of ten rounds, both directions under the shared key. Agree
  on the order of `send` and `receive` calls before implementing.
- Every GCM or ChaCha20-Poly1305 encryption needs a fresh IV (nonce). The
  IV is not secret: send it together with the ciphertext and use it for
  decryption. A cipher-generated IV is available via `Cipher.getIV()`;
  for GCM decryption, wrap the received IV in a `GCMParameterSpec`.
- GCM authenticates what it encrypts: decryption must fail when the
  ciphertext or IV is modified, rather than silently producing wrong output.
  Let the example questions guide you: try modifying the ciphertext, the IV,
  and the key, and observe the failures.
- In A2 the payload travels unprotected by design; only the small digest
  crosses the secure low-bandwidth channels. Success is Bob printing
  `data valid`. The payload is allocated in memory (about 200 MB), so give
  the JVM enough heap and let the run finish.
- In A3, OAEP is the preferred padding. RSA encrypts only a limited amount
  of data per call and provides confidentiality, not sender authenticity.
- Use UTF-8 explicitly when converting between text and bytes. Ciphertext,
  IVs, and digests are binary data: log them in hexadecimal, not as text.
- If an agent blocks, check which `receive()` is waiting and whether its
  peer sends the expected data in the same order and count.

## Java 21 references

- [Cipher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [KeyGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/KeyGenerator.html)
- [GCMParameterSpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)
- [IvParameterSpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/IvParameterSpec.html)
- [MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- [KeyPairGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyPairGenerator.html)
- [Java standard algorithm names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html)
- [Communication library source](https://github.com/lem-course/isp-communication-model)