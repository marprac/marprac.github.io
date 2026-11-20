// PDF.js is vendored locally; document text remains available if canvas rendering fails.
let readerSequence = 0;

export function mountPdfReader(container, metadata) {
  const id = `pdf-reader-${++readerSequence}`;
  const root = document.createElement("section");
  root.className = "pdf-reader";
  root.setAttribute("aria-label", `${metadata.title} reader`);
  root.innerHTML = `
    <div class="pdf-toolbar">
      <div class="pdf-view-tabs" role="tablist" aria-label="Document view">
        <button class="pdf-tab" type="button" role="tab" aria-selected="true" aria-controls="${id}-visual" id="${id}-pdf-tab" data-view="pdf">PDF view</button>
        <button class="pdf-tab" type="button" role="tab" aria-selected="false" aria-controls="${id}-text" id="${id}-text-tab" data-view="text" tabindex="-1">Text view</button>
      </div>
      <div class="pdf-page-controls" aria-label="Page navigation">
        <button type="button" class="pdf-button pdf-previous">Previous</button>
        <label class="pdf-page-label">Page <input class="pdf-page-input" type="number" min="1" step="1" inputmode="numeric" aria-label="Page number"></label>
        <span class="pdf-page-count"></span>
        <button type="button" class="pdf-button pdf-next">Next</button>
      </div>
      <label class="pdf-zoom-label"><span>Zoom</span><select class="pdf-zoom" aria-label="PDF zoom"><option value="fit">Fit width</option><option value="0.75">75%</option><option value="1">100%</option><option value="1.25">125%</option><option value="1.5">150%</option><option value="2">200%</option></select></label>
    </div>
    <div class="pdf-secondary-toolbar">
      <form class="pdf-search" role="search" aria-label="Search this document">
        <label class="pdf-search-label" for="${id}-search">Find in document</label>
        <input id="${id}-search" class="pdf-search-input" type="search" placeholder="Find a term in all pages…" autocomplete="off">
        <button class="pdf-button pdf-find" type="submit">Find</button>
        <span class="pdf-search-status" role="status" aria-live="polite"></span>
      </form>
      <div class="pdf-file-links"><a class="pdf-open-original" target="_blank" rel="noopener">Open original</a><a class="pdf-download" download>Download PDF</a></div>
    </div>
    <div class="pdf-notice" role="status" aria-live="polite">Loading document…</div>
    <div class="pdf-canvas-stage" id="${id}-visual" role="tabpanel" aria-labelledby="${id}-pdf-tab" tabindex="0"></div>
    <div class="pdf-text-stage" id="${id}-text" role="tabpanel" aria-labelledby="${id}-text-tab" tabindex="0" hidden><p class="pdf-text-caption">Extracted text · use PDF view for the original layout, equations, and diagrams.</p><h3 class="pdf-text-heading"></h3><pre class="pdf-page-text"></pre></div>
    <p class="pdf-reader-hint">Select text directly on the page, or use Text view for easier reading on a small screen.</p>`;
  container.replaceChildren(root);

  const $ = (selector) => root.querySelector(selector);
  const stage = $(".pdf-canvas-stage");
  const textStage = $(".pdf-text-stage");
  const pageInput = $(".pdf-page-input");
  const previous = $(".pdf-previous");
  const next = $(".pdf-next");
  const zoom = $(".pdf-zoom");
  const notice = $(".pdf-notice");
  const searchInput = $(".pdf-search-input");
  const searchStatus = $(".pdf-search-status");
  const findButton = $(".pdf-find");
  const textOutput = $(".pdf-page-text");
  const abortController = new AbortController();
  const signal = abortController.signal;
  const on = (element, type, handler) => element.addEventListener(type, handler, { signal });
  const asset = (path) => new URL(path, import.meta.url).href;

  let disposed = false;
  let pageNumber = 1;
  let pageCount = metadata.pages || 1;
  let view = "pdf";
  let pdf = null;
  let pdfLibrary = null;
  let loadingTask = null;
  let renderTask = null;
  let textLayer = null;
  let renderRevision = 0;
  let extractedPages = null;
  let textFailed = false;
  let pdfFailed = false;
  let searchMatches = [];
  let searchQuery = "";
  let searchedCurrentQuery = false;
  let searchTimer = null;
  let resizeTimer = null;
  let lastWidth = 0;

  $(".pdf-open-original").href = metadata.url;
  $(".pdf-download").href = metadata.url;

  function setNotice(message) {
    notice.textContent = message;
    notice.hidden = !message;
  }

  function updateControls() {
    pageInput.value = pageNumber;
    pageInput.max = pageCount;
    $(".pdf-page-count").textContent = `of ${pageCount}`;
    previous.disabled = pageNumber <= 1;
    next.disabled = pageNumber >= pageCount;
    zoom.disabled = view === "text" || pdfFailed;
    $(".pdf-text-heading").textContent = `Page ${pageNumber}`;
    root.setAttribute("aria-label", `${metadata.title}, page ${pageNumber} of ${pageCount}`);
  }

  function showText() {
    textOutput.replaceChildren();
    if (!extractedPages) {
      textOutput.textContent = textFailed ? "Text could not be loaded. Open the original PDF to read this page." : "Loading page text…";
      return;
    }
    const content = extractedPages[pageNumber - 1] || "This page contains no extractable text. Use PDF view to see its contents.";
    const query = searchInput.value.trim();
    if (!query) {
      textOutput.textContent = content;
      return;
    }
    const folded = content.toLocaleLowerCase();
    const needle = query.toLocaleLowerCase();
    let offset = 0;
    let index;
    while ((index = folded.indexOf(needle, offset)) !== -1) {
      textOutput.append(document.createTextNode(content.slice(offset, index)));
      const mark = document.createElement("mark");
      mark.textContent = content.slice(index, index + query.length);
      textOutput.append(mark);
      offset = index + query.length;
    }
    textOutput.append(document.createTextNode(content.slice(offset)));
  }

  function updateSearch() {
    const query = searchInput.value.trim().toLocaleLowerCase();
    if (query !== searchQuery) {
      searchQuery = query;
      searchedCurrentQuery = false;
    }
    searchMatches = [];
    if (!query) {
      searchStatus.textContent = "";
      findButton.textContent = "Find";
    } else if (!extractedPages) {
      searchStatus.textContent = textFailed ? "Search unavailable. Open the original PDF." : "Loading searchable text…";
    } else {
      extractedPages.forEach((text, index) => {
        if (text.toLocaleLowerCase().includes(query)) searchMatches.push(index + 1);
      });
      searchStatus.textContent = searchMatches.length ? `${searchMatches.length} matching ${searchMatches.length === 1 ? "page" : "pages"}` : "No matches";
      findButton.textContent = searchedCurrentQuery ? "Find next" : "Find";
    }
    showText();
  }

  async function renderPage() {
    const revision = ++renderRevision;
    renderTask?.cancel();
    textLayer?.cancel();
    renderTask = null;
    textLayer = null;
    if (disposed || view !== "pdf" || !pdf) return;
    stage.setAttribute("aria-busy", "true");
    setNotice("Rendering page…");
    try {
      const page = await pdf.getPage(pageNumber);
      if (disposed || revision !== renderRevision || view !== "pdf") return;
      const natural = page.getViewport({ scale: 1 });
      const available = Math.max(220, stage.clientWidth - (window.innerWidth < 600 ? 24 : 48));
      const scale = zoom.value === "fit" ? Math.min(available / natural.width, 2) : Number(zoom.value);
      const viewport = page.getViewport({ scale });
      const ratio = Math.min(window.devicePixelRatio || 1, 2, 4096 / Math.max(viewport.width, viewport.height));
      const pageElement = document.createElement("div");
      pageElement.className = "pdf-page";
      pageElement.style.width = `${viewport.width}px`;
      pageElement.style.height = `${viewport.height}px`;
      pageElement.style.setProperty("--scale-factor", viewport.scale);
      pageElement.style.setProperty("--user-unit", viewport.userUnit || 1);
      pageElement.style.setProperty("--total-scale-factor", viewport.scale * (viewport.userUnit || 1));
      const canvas = document.createElement("canvas");
      canvas.width = Math.floor(viewport.width * ratio);
      canvas.height = Math.floor(viewport.height * ratio);
      canvas.style.width = `${viewport.width}px`;
      canvas.style.height = `${viewport.height}px`;
      canvas.setAttribute("aria-label", `${metadata.title}, page ${pageNumber}`);
      canvas.setAttribute("role", "img");
      const layerElement = document.createElement("div");
      layerElement.className = "pdf-text-layer";
      layerElement.setAttribute("aria-hidden", "true");
      pageElement.append(canvas, layerElement);
      stage.replaceChildren(pageElement);
      renderTask = page.render({ canvasContext: canvas.getContext("2d"), viewport, transform: ratio === 1 ? null : [ratio, 0, 0, ratio, 0, 0] });
      await renderTask.promise;
      if (disposed || revision !== renderRevision || view !== "pdf") return;
      setNotice("");
      stage.setAttribute("aria-busy", "false");
      textLayer = new pdfLibrary.TextLayer({ textContentSource: page.streamTextContent(), container: layerElement, viewport });
      await textLayer.render();
    } catch (error) {
      if (disposed || revision !== renderRevision || error?.name === "RenderingCancelledException" || error?.name === "AbortException") return;
      stage.setAttribute("aria-busy", "false");
      setNotice("This page could not be rendered. Try Text view, or open the original PDF.");
    }
  }

  function goToPage(number) {
    const value = Math.trunc(Number(number));
    if (!Number.isFinite(value)) {
      updateControls();
      return;
    }
    pageNumber = Math.min(pageCount, Math.max(1, value));
    updateControls();
    showText();
    if (view === "pdf") renderPage();
    stage.scrollTop = 0;
    stage.scrollLeft = 0;
    textStage.scrollTop = 0;
  }

  function switchView(nextView, focusTab = false) {
    view = nextView;
    for (const tab of root.querySelectorAll(".pdf-tab")) {
      const selected = tab.dataset.view === view;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (selected && focusTab) tab.focus();
    }
    stage.hidden = view !== "pdf";
    textStage.hidden = view !== "text";
    updateControls();
    if (view === "text") {
      ++renderRevision;
      renderTask?.cancel();
      textLayer?.cancel();
      stage.setAttribute("aria-busy", "false");
      setNotice("");
      showText();
    } else if (pdfFailed) {
      setNotice("PDF preview is unavailable in this browser. Use Text view or open the original PDF.");
    } else if (!pdf) {
      setNotice("Loading document…");
    } else {
      renderPage();
    }
  }

  on(previous, "click", () => goToPage(pageNumber - 1));
  on(next, "click", () => goToPage(pageNumber + 1));
  on(pageInput, "change", () => goToPage(pageInput.value));
  on(pageInput, "keydown", (event) => {
    if (event.key === "Enter") { event.preventDefault(); goToPage(pageInput.value); }
  });
  on(zoom, "change", () => renderPage());
  for (const tab of root.querySelectorAll(".pdf-tab")) {
    on(tab, "click", () => switchView(tab.dataset.view));
    on(tab, "keydown", (event) => {
      if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) {
        event.preventDefault();
        const nextView = event.key === "Home" ? "pdf" : event.key === "End" ? "text" : view === "pdf" ? "text" : "pdf";
        switchView(nextView, true);
      }
    });
  }
  on(searchInput, "input", () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(updateSearch, 150);
  });
  on($(".pdf-search"), "submit", (event) => {
    event.preventDefault();
    clearTimeout(searchTimer);
    updateSearch();
    if (!searchMatches.length) return;
    const target = searchMatches.find((number) => searchedCurrentQuery ? number > pageNumber : number >= pageNumber) ?? searchMatches[0];
    searchedCurrentQuery = true;
    findButton.textContent = "Find next";
    goToPage(target);
    searchStatus.textContent = `Page ${target} · ${searchMatches.length} matching ${searchMatches.length === 1 ? "page" : "pages"}`;
  });

  const observer = new ResizeObserver(() => {
    const width = stage.clientWidth;
    if (view !== "pdf" || zoom.value !== "fit" || Math.abs(width - lastWidth) < 2) return;
    lastWidth = width;
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => { if (pdf && !disposed) renderPage(); }, 140);
  });
  observer.observe(stage);
  updateControls();

  if (metadata.textUrl) {
    fetch(metadata.textUrl, { signal }).then((response) => {
      if (!response.ok) throw new Error("Text download failed");
      return response.json();
    }).then((data) => {
      if (disposed) return;
      if (!Array.isArray(data.pages)) throw new Error("Invalid text data");
      extractedPages = data.pages.map(String);
      updateSearch();
    }).catch(() => {
      if (disposed) return;
      textFailed = true;
      updateSearch();
    });
  } else {
    textFailed = true;
    showText();
  }

  (async () => {
    try {
      pdfLibrary = await import("./vendor/pdfjs/legacy/build/pdf.min.mjs");
      if (disposed) return;
      pdfLibrary.GlobalWorkerOptions.workerSrc = asset("./vendor/pdfjs/legacy/build/pdf.worker.min.mjs");
      loadingTask = pdfLibrary.getDocument({
        url: new URL(metadata.url, document.baseURI).href,
        cMapUrl: asset("./vendor/pdfjs/cmaps/"),
        cMapPacked: true,
        standardFontDataUrl: asset("./vendor/pdfjs/standard_fonts/"),
        wasmUrl: asset("./vendor/pdfjs/wasm/"),
        isEvalSupported: false,
      });
      pdf = await loadingTask.promise;
      if (disposed) { loadingTask.destroy().catch(() => {}); return; }
      pageCount = pdf.numPages;
      updateControls();
      if (view === "pdf") renderPage();
    } catch (error) {
      if (disposed) return;
      pdfFailed = true;
      updateControls();
      if (view === "pdf") setNotice("PDF preview is unavailable in this browser. Use Text view or open the original PDF.");
    }
  })();

  return function cleanup() {
    if (disposed) return;
    disposed = true;
    ++renderRevision;
    abortController.abort();
    observer.disconnect();
    clearTimeout(searchTimer);
    clearTimeout(resizeTimer);
    renderTask?.cancel();
    textLayer?.cancel();
    loadingTask?.destroy().catch(() => {});
  };
}
