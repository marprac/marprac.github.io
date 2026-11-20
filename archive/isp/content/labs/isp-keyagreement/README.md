# Key exchange and digital signatures lab

This Java lab explores public-key based key exchange and digital signatures.
The example classes are complete; `A1AgentCommunicationKeyExchange` and
`A2AgentCommunicationSignature` are exercises for you to implement. Read the
instructions in each exercise as well as the guidance below.

## Setup and running

Install JDK 21 and Maven 3. Check `java -version` and `mvn -version`;
Maven must use the intended JDK. The first build needs network access to
download Maven dependencies and the communication library from JitPack.

```sh
mvn compile
```

Open the repository as a Maven project and run a class's `main()` method in
your IDE. Alternatively, in a POSIX terminal:

```sh
mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" isp.keyagreement.AgentCommunicationECDH
```

Replace the fully qualified class name to run another example or exercise.
On Windows, use your IDE or adapt the classpath separator and file-reading
syntax to your shell. Recompile after editing. The unfinished exercise skeletons
also compile, so a successful build does not establish correctness.

## Examples and exercises

| Class | Purpose |
| --- | --- |
| `isp.keyagreement.AgentCommunicationDH` | Demonstrates Diffie–Hellman agreement and encrypted agent communication. |
| `isp.keyagreement.AgentCommunicationECDH` | Demonstrates elliptic-curve Diffie–Hellman and AES-GCM communication. |
| `isp.signatures.SignatureExampleRSA` | Demonstrates signing and verification using the Java `Signature` API. |
| `isp.signatures.SignatureExampleRSAPSS` | Demonstrates an RSA-PSS signature. |
| `isp.signatures.KeyDerivation` | Demonstrates password-based key derivation and HMAC. |
| `isp.keyagreement.A1AgentCommunicationKeyExchange` | Exchange a shared secret using public-key encryption, then send an AES-GCM encrypted message. |
| `isp.signatures.A2AgentCommunicationSignature` | Exchange and verify ECDSA-signed messages in both directions. |

### A1: key exchange using public-key encryption

Implement the unauthenticated exchange described in the course slides. Use
public-key **encryption** to establish a shared symmetric secret between Alice
and Bob; a Diffie–Hellman or ECDH agreement alone is not the requested exchange.
Then Alice sends Bob a message encrypted with AES-GCM. Bob must decrypt and
display the original message. Arrange for Bob to receive the GCM parameters
needed to decrypt and authenticate it. The exchange does not authenticate the
public key, so it is vulnerable to an active man-in-the-middle; GCM does not
fix that problem. The DH examples illustrate agent communication and GCM,
but implement a different key exchange from the one required here.

### A2: ECDSA signatures

Assume each agent already knows the other's authentic public key. Implement ten
request–reply rounds: Alice sends a signed message to Bob and Bob sends a signed
reply to Alice in each round, for twenty signed messages in total. Each recipient
must verify the signature against the received message and the sender's public
key, and reject an invalid signature. Use ECDSA, not RSA, for this exercise.
Signatures provide integrity and evidence of the signer under the stated key
assumption; they do not encrypt the messages or prevent replay by themselves.

## Checking your work

- Run each exercise independently and check that it terminates and produces the
  expected received messages. In A2 check both directions and all ten rounds.
- Try modifying a ciphertext or authentication tag in A1 and a message or
  signature in A2; confirm that the recipient rejects the modified data. Restore
  your implementation after testing.
- If an agent blocks, check that sends and receives match in order and count.
  Use UTF-8 explicitly for text; keys, signatures and ciphertext are binary data.
- For GCM, never reuse an IV with the same key. The IV is not secret but must
  accompany the corresponding ciphertext. Do not treat an authentication failure
  as successful decryption.
- The examples are teaching demonstrations, not production protocols. In
  particular, unauthenticated DH/ECDH is vulnerable to active attacks, and the
  examples' direct use of shared-secret bytes as an AES key omits a proper KDF.
  `KeyDerivation` uses a fixed demonstration password and salt; do not reuse them
  in real applications.

## References

- [Cipher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html)
- [GCMParameterSpec](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html)
- [KeyPairGenerator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/KeyPairGenerator.html)
- [Signature](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/Signature.html)
- [Java standard algorithm names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html)
- [Communication library source](https://github.com/lem-course/isp-communication-model)
