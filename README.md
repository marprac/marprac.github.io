# ISP Study Room

A temporary cryptography course library for [marprac.github.io](https://marprac.github.io), hosted with GitHub Pages. The personal portfolio is preserved in `archive/personal/` and has no links in the course site.

The site includes six labs with 27 Java source files, six READMEs and Maven configurations, the original assignment ZIP, and two original midterm assignment images. Six PDFs (four slide decks and two study scripts) have an integrated PDF/Text reader with page navigation, zoom, selectable text, search, and downloads. Global search covers the lab files and all 181 document pages.

The interface is static HTML, CSS, and JavaScript. All libraries and PDF rendering assets are vendored locally; visitors do not depend on a CDN. No build step or server application is required. Labs 1-5 retain their original code behavior and TODOs, with added line-by-line explanations for Alice, Bob, and the midterm server. Lab 5 contains the supplied authenticated ECDH midterm and readable protocol notes; the original assignment images also open in the file reader. Lab 6 preserves `MidtermA.java` and `MidtermB.java` exactly as supplied and adds `MidtermACommented.java` and `MidtermBCommented.java` with explanations for every code line. The commented copies use distinct public class names so all four compile together. The dashboard includes Maven compile, classpath, and run instructions for POSIX shells and Windows PowerShell.

## Preview

Serve the repository over HTTP; opening `index.html` directly as a `file:` URL does not support the fetches or PDF worker.

```powershell
python -m http.server 8080 --bind 127.0.0.1
```

Open `http://127.0.0.1:8080`. Hash-based links work with GitHub Pages, including direct links to individual lab files. Publish through the repository's existing GitHub Pages deployment by pushing the changes to its configured branch.

## Refresh the course materials

Lab snapshots come from the four sibling repositories (`isp-secrecy`, `isp-integrity`, `isp-ae-pke`, `isp-keyagreement`). Lab 5 imports `Midterm1KeyExchange.java` and the two assignment PNGs from `isp-keyagreement/src/main/java/isp/kolokvij/`, alongside that repository's Maven configuration. The midterm is excluded from Lab 4 to keep it in a separate fifth entry. Lab 6 preserves the two supplied Java midterms and their commented copies as local snapshots under `content/labs/isp-midterms-ab/`, with a Maven configuration from the key-agreement lab. Refreshing the site does not require the original Downloads files. After changing a lab, run:

```powershell
node scripts/sync-labs.mjs
```

Use `--source-root <directory>` to choose another directory containing the four repositories. This copies the selected source files, READMEs, and Maven configurations, verifies their SHA-256 hashes, and refreshes `content/labs.json`. The READMEs for Labs 5-6 and the Lab 6 Java snapshots are maintained locally. Existing Java annotations are retained when their non-comment tokens match the source; a code mismatch stops synchronization before any writes so source updates and study comments can be reconciled explicitly. The Lab 6 originals are checked against their original byte hashes, while the commented copies must retain the same non-comment Java tokens apart from their public class names. It never edits the source repositories or copies their Git metadata or agent instructions.

To reimport the six original PDFs from Downloads and regenerate the searchable text, install `pypdf` and run:

```powershell
python scripts/sync-documents.py
```

Use `--source <directory>` for another source directory. Original filenames are mapped to stable website URLs by the importer. Originals are copied unchanged; text extraction is an additional reading option, and PDF view preserves equations, illustrations, and page layouts.

## Restore the personal portfolio

The original files are in `archive/personal/`, including the original README. Restore the homepage, styling, icon, and interactions with:

```powershell
Copy-Item -LiteralPath archive/personal/index.html,archive/personal/styles.css,archive/personal/disclosures.js,archive/personal/favicon.svg,archive/personal/README.md -Destination . -Force
```

Commit and push the restored files through the existing GitHub Pages workflow. The course content can stay in the repository for future use.

## Verification

```powershell
node scripts/check-site.mjs
```

The check verifies all manifest paths, file hashes and line counts, unchanged non-comment Java tokens against available sibling sources, original image bytes, document page metadata, required local assets, archive preservation, and module syntax. DOM integration tests for the application and PDF reader can additionally run with an external `jsdom` installation; see the comments in `scripts/check-app.mjs` and `scripts/check-pdf-reader.mjs`.

Vendored library versions and licenses are under `vendor/`, including PDF.js licensing and attribution. The original lab source links point to the `lem-course` repositories; the Lab 6 midterms are supplied file snapshots.
