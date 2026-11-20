# Local browser libraries

The site serves these dependencies locally, without runtime CDN requests:

- Marked: `marked.umd.js` (MIT), version in `versions.json`.
- DOMPurify: `purify.min.js` (Apache-2.0 OR MPL-2.0), version in `versions.json`.
- Highlight.js: `highlight.min.js` (BSD-3-Clause), version in `versions.json`.
- PDF.js: `pdfjs/`, pinned version and origin in `pdfjs/VERSION.txt`; Apache-2.0 and bundled third-party licenses.

The matching library license files are included alongside each distribution. The highlighting palette in `highlight-theme.css` is customized for the study room. Marked output is sanitized with DOMPurify before being inserted in the page.
