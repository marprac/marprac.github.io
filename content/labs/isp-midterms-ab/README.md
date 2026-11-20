# Lab 6: Midterms A & B

The two supplied midterm files are included exactly as received, with their
original code, comments, formatting, encoding, and line endings.

## Files

| File | Contents |
| --- | --- |
| [MidtermA.java](src/main/java/isp/signatures/MidtermA.java) | Original Midterm A implementation. |
| [MidtermB.java](src/main/java/isp/signatures/MidtermB.java) | Original Midterm B implementation. |
| [pom.xml](pom.xml) | Maven configuration from the key-agreement lab, providing the agent communication dependency. |

Open either Java file in the file browser to read, copy, or download it. Both
files belong to the `isp.signatures` package and retain their original filenames.

## Setup

Use JDK 21 and Maven 3. Download the Maven configuration and both Java files,
retaining the `src/main/java/isp/signatures/` directory structure. From the lab
directory, compile with:

```sh
mvn compile
```

Open the directory as a Maven project in your IDE and select
`isp.signatures.MidtermA` or `isp.signatures.MidtermB` to run the corresponding
program. The first Maven build downloads the agent communication dependency.
