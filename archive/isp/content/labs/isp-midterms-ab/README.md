# Lab 6: Midterms A & B

This lab contains four Java files: the two supplied originals and two copies
with detailed explanations for every line of code. The originals retain their
code, comments, formatting, encoding, and line endings exactly as received.

## Files

| File | Contents |
| --- | --- |
| [MidtermA.java](src/main/java/isp/signatures/MidtermA.java) | Original Midterm A implementation. |
| [MidtermACommented.java](src/main/java/isp/signatures/MidtermACommented.java) | Midterm A with detailed comments for every line of code. |
| [MidtermB.java](src/main/java/isp/signatures/MidtermB.java) | Original Midterm B implementation. |
| [MidtermBCommented.java](src/main/java/isp/signatures/MidtermBCommented.java) | Midterm B with detailed comments for every line of code. |
| [pom.xml](pom.xml) | Maven configuration from the key-agreement lab, providing the agent communication dependency. |

Open any Java file in the file browser to read, copy, or download it. All four
files belong to the `isp.signatures` package. Each commented copy has a matching
`MidtermACommented` or `MidtermBCommented` public class name, so all four files
compile together. Apart from that class name, the copies preserve the original
programs and add comments only.

## Setup

Use JDK 21 and Maven 3. Download the Maven configuration and the Java files,
retaining the `src/main/java/isp/signatures/` directory structure. From the lab
directory, compile with:

```sh
mvn compile
```

Open the directory as a Maven project in your IDE and select
`isp.signatures.MidtermA`, `isp.signatures.MidtermACommented`,
`isp.signatures.MidtermB`, or `isp.signatures.MidtermBCommented` to run the
corresponding program. The first Maven build downloads the agent communication
dependency. The dashboard includes commands for building the classpath and
running a class from a terminal, with separate POSIX and PowerShell examples.
