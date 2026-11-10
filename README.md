# Marko Praček — Personal portfolio

A minimal, dark personal website for GitHub Pages. Plain HTML and CSS with a small optional JavaScript enhancement, no dependencies or build step. All navigation, expandable research/project notes, and hover effects work without JavaScript. Fonts and artwork are local; the page makes no third-party requests.

## Preview locally

Open `index.html` in a browser, or serve this folder:

```powershell
python -m http.server 8000 --bind 127.0.0.1
```

Then visit <http://127.0.0.1:8000>. Press `Ctrl+C` in that terminal to stop the server.

## Make it yours

`index.html` contains Marko Praček’s biography, FRI education, and software engineering role at KYBM d.o.o. Research currently lists his 2025 bachelor’s thesis, with its title, summary, and supervisor checked against the [University of Ljubljana repository](https://repozitorij.uni-lj.si/IzpisGradiva.php?id=173254&lang=eng).

The contact link uses `mp77516@student.uni-lj.si`. Projects remain placeholders and are hidden from the page, with all their HTML, styles, and interactions kept for later editing.

To show projects again, find the `<section id="projects">` element in `index.html` and remove its `hidden` attribute. The section has a comment marking it. Replace the project placeholders before showing it publicly.

The work, research, and projects sections are labeled in the HTML. Copy an existing `<article>` or research `<details>` to add another entry. Use the actual date in each `<time datetime="YYYY-MM">` attribute and the display date in its text.

Colors, spacing, typography, and mobile layouts are in `styles.css`. The `:root` variables control the palette. Decorative project artwork is inline SVG and CSS; the site icon is `favicon.svg`.

Hover motion is limited to individual work experiences, research entries, and projects in `styles.css`: each work experience and research entry slides to the right as one unit using relative positioning, while hovering a project moves all its content down. Hovering does not enlarge text or artwork. Adjust the `left` and `top` values in the hover rules to change the movement. Other entries and the rest of the page stay still. Transitions ease in and out over 520ms, preserve the page layout, run only with a fine pointer that supports hover, and respect reduced-motion settings.

`disclosures.js` gives research entries and project notes the same 480ms reveal-and-fade opening and 340ms closing animation. Click anywhere on a project to toggle its notes, or use the keyboard-accessible Project notes control. Research entries toggle from their summary. Links and other interactive controls retain their own behavior, and selecting text does not toggle the project. Each section allows one open entry: opening a research entry closes the other research entries, and opening project notes closes the other project notes. Research and projects remain separate groups. Repeated clicks reverse from the current position. Text is not transformed during the reveal. Reduced-motion preferences use immediate opening and closing. Without JavaScript the native `<details>` controls still work, and without animation support the script switches entries instantly. Plus icons become minus icons by shrinking away their vertical stroke while the horizontal stroke stays still.

## GitHub Pages

The site is ready to serve from the repository root. Nothing has been published as part of the local build. When ready, push these files to the `marprac.github.io` repository and configure GitHub Pages to serve the root of the chosen branch. The included `.nojekyll` file keeps this a plain static site. No compilation, account keys, or runtime service is needed.
