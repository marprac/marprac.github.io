# ISP Study Room archive

The cryptography course library is preserved at [marprac.github.io/archive/isp/](https://marprac.github.io/archive/isp/), hosted with GitHub Pages. The personal portfolio is restored at the main website. This folder contains the full study room, including its course materials, libraries, and maintenance scripts.

The site includes six labs with 27 Java source files, six READMEs and Maven configurations, the original assignment ZIP, and two original midterm assignment images. Six PDFs (four slide decks and two study scripts) have an integrated PDF/Text reader with page navigation, zoom, selectable text, search, and downloads. Global search covers the lab files and all 181 document pages.

The interface is static HTML, CSS, and JavaScript. All libraries and PDF rendering assets are vendored locally; visitors do not depend on a CDN. No build step or server application is required. Labs 1-5 retain their original code behavior and TODOs, with added line-by-line explanations for Alice, Bob, and the midterm server. Lab 5 contains the supplied authenticated ECDH midterm and readable protocol notes; the original assignment images also open in the file reader. Lab 6 preserves `MidtermA.java` and `MidtermB.java` exactly as supplied and adds `MidtermACommented.java` and `MidtermBCommented.java` with explanations for every code line. The commented copies use distinct public class names so all four compile together. The dashboard includes Lab 3 Maven compile, classpath, and run examples for POSIX shells and Windows PowerShell, with links to all five Lab 3 programs.

## Preview

Serve the repository over HTTP; opening `index.html` directly as a `file:` URL does not support the fetches or PDF worker.

```powershell
python -m http.server 8080 --bind 127.0.0.1
```

Run the server from the repository root and open `http://127.0.0.1:8080/archive/isp/`. Hash-based links work with GitHub Pages, including direct links to individual lab files. Publish through the repository's existing GitHub Pages deployment by pushing the changes to its configured branch.

## Refresh the course materials

Lab snapshots come from the four sibling repositories (`isp-secrecy`, `isp-integrity`, `isp-ae-pke`, `isp-keyagreement`). Lab 5 imports `Midterm1KeyExchange.java` and the two assignment PNGs from `isp-keyagreement/src/main/java/isp/kolokvij/`, alongside that repository's Maven configuration. The midterm is excluded from Lab 4 to keep it in a separate fifth entry. Lab 6 preserves the two supplied Java midterms and their commented copies as local snapshots under `content/labs/isp-midterms-ab/`, with a Maven configuration from the key-agreement lab. Refreshing the site does not require the original Downloads files. After changing a lab, run:

```powershell
node archive/isp/scripts/sync-labs.mjs
```

Use `--source-root <directory>` to choose another directory containing the four repositories. This copies the selected source files, READMEs, and Maven configurations, verifies their SHA-256 hashes, and refreshes `archive/isp/content/labs.json`. The READMEs for Labs 5-6 and the Lab 6 Java snapshots are maintained locally. Existing Java annotations are retained when their non-comment tokens match the source; a code mismatch stops synchronization before any writes so source updates and study comments can be reconciled explicitly. The Lab 6 originals are checked against their original byte hashes, while the commented copies must retain the same non-comment Java tokens apart from their public class names. It never edits the source repositories or copies their Git metadata or agent instructions.

To reimport the six original PDFs from Downloads and regenerate the searchable text, install `pypdf` and run:

```powershell
python archive/isp/scripts/sync-documents.py
```

Use `--source <directory>` for another source directory. Original filenames are mapped to stable website URLs by the importer. Originals are copied unchanged; text extraction is an additional reading option, and PDF view preserves equations, illustrations, and page layouts.

## Verification

```powershell
node archive/isp/scripts/check-site.mjs
```

The check verifies all manifest paths, file hashes and line counts, unchanged non-comment Java tokens against available sibling sources, original image bytes, document page metadata, required local assets, archive preservation, and module syntax. DOM integration tests for the application and PDF reader can additionally run with an external `jsdom` installation; see the comments in `archive/isp/scripts/check-app.mjs` and `archive/isp/scripts/check-pdf-reader.mjs`. Run commands from the repository root; alternatively, enter `archive/isp/` and use the scripts' shorter paths shown in their comments.

Vendored library versions and licenses are under `vendor/`, including PDF.js licensing and attribution. The original lab source links point to the `lem-course` repositories; the Lab 6 midterms are supplied file snapshots.
