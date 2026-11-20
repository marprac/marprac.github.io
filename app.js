import { mountPdfReader } from "./pdf-reader.js";

const main = document.querySelector("main");
const searchInput = document.querySelector("#global-search");
const textCache = new Map();
let labs = [];
let documents = [];
let routeRevision = 0;
let disposeReader = null;
let collectionRequest = null;
let collectionReady = false;
let lastRoutedUrl = "";
let toastTimer;
let wrapCode = false;
try { wrapCode = localStorage.getItem("isp-wrap-code") === "true"; } catch {}

const paths = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  code: '<path d="m8 7-5 5 5 5m8-10 5 5-5 5m-3-13-2 16"/>',
  slides: '<rect x="3" y="3" width="18" height="13" rx="2"/><path d="M12 16v5m-4 0h8M7 8h10M7 11h6"/>',
  book: '<path d="M12 5c-3-2-6-2-10-1v15c4-1 7-1 10 1 3-2 6-2 10-1V4c-4-1-7-1-10 1Zm0 0v15"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
  shield: '<path d="m12 3 8 3v5c0 5-4 8-8 10-4-2-8-5-8-10V6l8-3Z"/><path d="m8 12 3 3 5-6"/>',
  key: '<circle cx="8" cy="8" r="5"/><path d="m12 12 9 9m-5-5 3-3m-6 0 3-3"/>',
  file: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-6-6Z"/><path d="M14 3v6h6M8 13h8M8 17h6"/>',
  terminal: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3m6 0h4"/>',
};

function icon(name) { return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.file}</svg>`; }
function escape(value) { return String(value).replace(/[&<>"']/g, (c) => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c])); }
function size(bytes) { return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(bytes < 10240 ? 1 : 0)} KB`; }
function labLink(lab, file, source = false) { return `#/lab/${encodeURIComponent(lab.id)}${file ? `?file=${encodeURIComponent(file.path)}${source ? "&view=source" : ""}` : ""}`; }
function docLink(doc) { return `#/document/${encodeURIComponent(doc.id)}`; }
function searchLink(query,scope = "all") { return `#/search?q=${encodeURIComponent(query)}${scope === "all" ? "" : `&scope=${scope}`}`; }
function matchLink(lab,file,query,line,scope) { return `${labLink(lab,file,file.type === "markdown")}&line=${line}&q=${encodeURIComponent(query)}&scope=${scope}`; }
function markedText(text,query) {
  if (!query) return escape(text);
  const folded = text.toLowerCase(), needle = query.toLowerCase();
  let output = "", offset = 0, index;
  while ((index = folded.indexOf(needle,offset)) !== -1) {
    output += escape(text.slice(offset,index)) + `<mark class="search-term">${escape(text.slice(index,index + query.length))}</mark>`;
    offset = index + query.length;
  }
  return output + escape(text.slice(offset));
}
async function readText(url) {
  if (!textCache.has(url)) {
    const request = fetch(url).then((response) => {
      if (!response.ok) throw new Error(`Could not load file (${response.status}).`);
      return response.text();
    }).catch((error) => { textCache.delete(url); throw error; });
    textCache.set(url, request);
  }
  return textCache.get(url);
}
async function readJSON(url) { return JSON.parse(await readText(url)); }

document.querySelectorAll("[data-icon]").forEach((element) => { element.innerHTML = icon(element.dataset.icon); });
document.querySelector(".skip-link").addEventListener("click", (event) => {
  event.preventDefault();
  main.focus({preventScroll:true});
  main.scrollIntoView({block:"start"});
});
document.querySelector(".mobile-menu").addEventListener("click", () => {
  const open = document.body.classList.toggle("menu-open");
  document.querySelector(".mobile-menu").setAttribute("aria-expanded", String(open));
});
document.addEventListener("keydown", (event) => {
  if (event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey && !event.target.closest("input,textarea,select,[contenteditable]")) {
    event.preventDefault(); searchInput.focus();
  }
  if (event.key === "Escape") {
    document.body.classList.remove("menu-open");
    document.querySelector(".mobile-menu").setAttribute("aria-expanded", "false");
  }
});
document.querySelector(".global-search").addEventListener("submit", (event) => {
  event.preventDefault();
  const query = searchInput.value.trim();
  navigate(query ? `#/search?q=${encodeURIComponent(query)}` : "#/");
});

// Handle our links directly: embedded previews and some browser hosts do not
// dispatch hashchange consistently when they intercept fragment navigation.
document.addEventListener("click", (event) => {
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
  const link = event.target.closest('a[href^="#/"]');
  if (!link || link.target === "_blank" || link.hasAttribute("download")) return;
  event.preventDefault();
  navigate(link.getAttribute("href"));
});
function navigate(hash) {
  if (window.location.hash !== hash) {
    try { window.history.pushState(null,"",hash); }
    catch { try { window.location.hash = hash; } catch {} }
  }
  return route(false,hash);
}
function onHistoryChange() {
  if (lastRoutedUrl !== window.location.href) route();
}
window.addEventListener("hashchange",onHistoryChange);
window.addEventListener("popstate",onHistoryChange);

async function loadCollection() {
  if (collectionReady) return;
  if (!collectionRequest) {
    collectionRequest = (async () => {
      const [labData,documentData] = await Promise.all([readJSON("./content/labs.json"),readJSON("./content/documents.json")]);
      labs = labData.labs;
      documents = documentData.documents;
      document.querySelector("#lab-nav").innerHTML = labs.map((lab) => `<a href="${labLink(lab)}" data-lab-nav="${escape(lab.id)}"><span class="lab-nav-number">${String(lab.order).padStart(2,"0")}</span><span>${escape(lab.title)}</span></a>`).join("");
      collectionReady = true;
    })().finally(() => { collectionRequest = null; });
  }
  return collectionRequest;
}

function navigation(section, labId = "") {
  document.querySelectorAll("[data-nav]").forEach((link) => {
    const active = link.dataset.nav === section;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page"); else link.removeAttribute("aria-current");
  });
  document.querySelectorAll("[data-lab-nav]").forEach((link) => {
    const active = link.dataset.labNav === labId;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page"); else link.removeAttribute("aria-current");
  });
}
function setHeading(crumb, title = crumb) {
  document.querySelector("#breadcrumb").textContent = crumb;
  document.title = `${title} · ISP Study Room`;
}
function heading(eyebrow, title, description) {
  return `<div class="page-heading"><div><p class="eyebrow">${escape(eyebrow)}</p><h1>${escape(title)}</h1><p>${escape(description)}</p></div></div>`;
}
function labCard(lab) {
  const symbols = ["lock", "shield", "terminal", "key"];
  const sourceCount = lab.files.filter((file) => file.path.startsWith("src/")).length;
  return `<a class="lab-card" href="${labLink(lab)}"><div class="lab-card-top"><span class="lab-icon">${icon(symbols[lab.order - 1])}</span><span class="lab-label">LAB ${String(lab.order).padStart(2,"0")} / JAVA</span></div><h3>${escape(lab.title)}</h3><p>${escape(lab.description)}</p><div class="tags">${lab.topics.map((tag) => `<span class="tag">${escape(tag)}</span>`).join("")}</div><div class="lab-card-bottom"><span>${sourceCount} source files · ${lab.exerciseCount} ${lab.exerciseCount === 1 ? "exercise" : "exercises"}</span><span class="open-label">Open lab</span></div></a>`;
}
function documentRow(doc) {
  return `<a class="document-row" href="${docLink(doc)}"><span class="document-icon ${doc.category}">${icon(doc.category === "slides" ? "slides" : "book")}</span><div class="document-info"><h3>${escape(doc.title)}</h3><p>${doc.category === "slides" ? "Lecture slides" : "Study script"} · PDF · ${size(doc.size)}</p></div><span class="doc-page-count">${doc.pages} pages</span><span class="doc-open">Read</span></a>`;
}
function collection(title, description, href, contents) {
  return `<section class="collection-section"><div class="section-heading"><div><h2>${title}</h2>${description ? `<p>${description}</p>` : ""}</div>${href ? `<a href="${href}">View all</a>` : ""}</div>${contents}</section>`;
}
function home() {
  navigation("home"); setHeading("Overview", "Cryptography collection");
  main.innerHTML = `<section class="hero"><div><p class="eyebrow">ISP / THE CRYPTOGRAPHY COLLECTION</p><h1>A clearer view of<br><span>cryptography.</span></h1><p class="hero-description">Your labs, lecture slides, and study scripts, together. Explore the code, revisit the theory, and make the connections.</p><div class="hero-actions"><a class="button primary" href="#/labs">${icon("code")}Explore the labs</a><a class="button subtle" href="#/scripts">${icon("book")}Read study scripts</a></div><div class="hero-meta"><span><strong>04</strong> LABS</span><span><strong>06</strong> DOCUMENTS</span><span><strong>22</strong> JAVA FILES</span></div></div><div class="hero-art" aria-hidden="true"><div class="cipher-row">7A 1F C4 09 E8</div><div class="cipher-row">D3 A9 2B 6E 01</div><div class="cipher-center">E<sub>k</sub>(m) = c</div><div class="cipher-row">B8 00 F2 5D 3C</div><div class="cipher-row">4E 6A 91 D0 7F</div></div></section>
    ${collection("The lab collection", "From symmetric ciphers to key exchange.", "#/labs", `<div class="lab-grid">${labs.map(labCard).join("")}</div>`)}
    <div class="resource-columns">${collection("Lecture slides", "The theory behind the code.", "#/slides", `<div class="document-list">${documents.filter((doc) => doc.category === "slides").map(documentRow).join("")}</div>`)}${collection("Study scripts", "Keep the essentials close.", "#/scripts", `<div class="document-list">${documents.filter((doc) => doc.category === "script").map(documentRow).join("")}</div>`)}</div>`;
}
function listLabs() {
  navigation("labs"); setHeading("Labs");
  main.innerHTML = heading("THE LAB COLLECTION", "Learn by reading the code.", "Four Java labs, with every source file, the original README, and Maven configuration.") + `<div class="lab-grid">${labs.map(labCard).join("")}</div>`;
}
function listDocuments(category) {
  const isSlides = category === "slides";
  navigation(isSlides ? "slides" : "scripts"); setHeading(isSlides ? "Lecture slides" : "Study scripts");
  main.innerHTML = heading("THE READING COLLECTION", isSlides ? "Lecture slides" : "Study scripts", isSlides ? "Four lecture decks, from communication secrecy to key exchange. Read each one here, page by page." : "A quick reference and a complete exam study script. Read, search, or download the original PDFs.") + `<div class="document-list">${documents.filter((doc) => doc.category === category).map(documentRow).join("")}</div>`;
}

function fileTree(lab, selected, filter = "") {
  const root = { directories:new Map(), files:[] };
  for (const file of lab.files.filter((entry) => entry.path.toLowerCase().includes(filter.toLowerCase()))) {
    const pieces = file.path.split("/");
    let parent = root;
    for (const directory of pieces.slice(0,-1)) {
      if (!parent.directories.has(directory)) parent.directories.set(directory, {directories:new Map(),files:[]});
      parent = parent.directories.get(directory);
    }
    parent.files.push(file);
  }
  function render(node) {
    return node.files.map((file) => `<a class="file-link${file.path === selected ? " active" : ""}" href="${labLink(lab,file)}" title="${escape(file.path)}"${file.path === selected ? ' aria-current="page"' : ""}>${icon(file.type === "markdown" ? "book" : "file")}<span>${escape(file.path.split("/").pop())}</span></a>`).join("") + [...node.directories].map(([name,child]) => `<details open><summary>${escape(name)}</summary><div class="file-children">${render(child)}</div></details>`).join("");
  }
  return render(root) || '<p class="file-empty">No matching files.</p>';
}
// Close and reopen highlighting spans at line breaks so multiline comments keep
// their colour while every source line has valid, independent markup.
function highlightedLines(source, language) {
  const clean = source.replace(/\r\n?/g,"\n").replace(/\n$/,"");
  let html = escape(clean);
  if (window.hljs && window.hljs.getLanguage(language)) html = window.hljs.highlight(clean,{language,ignoreIllegals:true}).value;
  const lines = [];
  const spans = [];
  let line = "";
  for (const part of html.split(/(\n|<\/?span\b[^>]*>)/)) {
    if (part === "\n") { lines.push(line + "</span>".repeat(spans.length)); line = spans.join(""); }
    else { line += part; if (part.startsWith("<span")) spans.push(part); else if (part === "</span>") spans.pop(); }
  }
  lines.push(line);
  return lines.map((content) => `<span class="code-line"><span class="code-line-content">${content || " "}</span></span>`).join("");
}
function showCode(source,file,params) {
  const code = document.createElement("pre");
  code.className = `code-view${wrapCode ? " wrap" : ""}`;
  code.setAttribute("aria-label", `${file.path} source code`);
  code.innerHTML = highlightedLines(source,file.language);
  const query = params.get("q")?.trim() || "";
  const targetLine = Number(params.get("line"));
  code.querySelectorAll(".code-line").forEach((line,index) => {
    line.dataset.line = String(index + 1);
    if (index + 1 === targetLine) {
      line.classList.add("code-line-target");
      line.setAttribute("aria-label",`Line ${index + 1}, search result`);
    }
    if (!query) return;
    // A match can cross multiple syntax-highlighting spans. DOM ranges retain
    // those spans and the exact source text, without inserting query HTML.
    const content = line.querySelector(".code-line-content");
    const folded = content.textContent.toLowerCase(), needle = query.toLowerCase();
    const matches = [];
    for (let start = folded.indexOf(needle); start !== -1; start = folded.indexOf(needle,start + needle.length)) matches.push(start);
    for (const start of matches.reverse()) {
      const walker = document.createTreeWalker(content,NodeFilter.SHOW_TEXT);
      let node, offset = 0, first, last;
      while ((node = walker.nextNode())) {
        const end = offset + node.textContent.length;
        if (!first && start < end) first = {node,offset:start - offset};
        if (start + query.length <= end) { last = {node,offset:start + query.length - offset}; break; }
        offset = end;
      }
      if (!first || !last) continue;
      const range = document.createRange();
      range.setStart(first.node,first.offset); range.setEnd(last.node,last.offset);
      const mark = document.createElement("mark"); mark.className = "search-term";
      mark.append(range.extractContents()); range.insertNode(mark);
    }
  });
  const scroll = document.createElement("div");
  scroll.className = "code-scroll"; scroll.tabIndex = 0;
  scroll.append(code);
  return scroll;
}
function renderMarkdown(source,lab,file) {
  const article = document.createElement("article"); article.className = "markdown-body";
  if (!window.marked || !window.DOMPurify) { article.textContent = source; return article; }
  article.innerHTML = window.DOMPurify.sanitize(window.marked.parse(source),{USE_PROFILES:{html:true}});
  let headingIndex = 0;
  article.querySelectorAll("h1,h2,h3,h4").forEach((h) => { h.id = `readme-${++headingIndex}-${h.textContent.toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"")}`; });
  article.querySelectorAll("table").forEach((table) => { const wrapper = document.createElement("div"); wrapper.className = "table-scroll"; table.replaceWith(wrapper); wrapper.append(table); });
  article.querySelectorAll("pre code").forEach((code) => { if (window.hljs) window.hljs.highlightElement(code); });
  article.querySelectorAll("a[href]").forEach((link) => {
    const original = link.getAttribute("href");
    if (original.startsWith("#")) {
      link.addEventListener("click", (event) => { event.preventDefault(); const target = [...article.querySelectorAll("[id]")].find((node) => node.id.endsWith(original.slice(1))); target?.scrollIntoView({block:"start"}); });
      return;
    }
    const url = new URL(original,new URL(file.url,document.baseURI));
    const relatedFile = lab.files.find((candidate) => new URL(candidate.url,document.baseURI).href === url.href);
    link.href = relatedFile ? labLink(lab,relatedFile) : url.href;
    if (url.origin !== window.location.origin) { link.target = "_blank"; link.rel = "noopener noreferrer"; }
  });
  article.querySelectorAll("img[src]").forEach((image) => { image.src = new URL(image.getAttribute("src"),new URL(file.url,document.baseURI)).href; });
  return article;
}
function toast(message) {
  const element = document.querySelector(".toast");
  clearTimeout(toastTimer); element.textContent = message; element.hidden = false;
  toastTimer = setTimeout(() => { element.hidden = true; },3000);
}
async function showLab(id,params,revision) {
  const lab = labs.find((entry) => entry.id === id);
  if (!lab) { notFound(); return; }
  const selectedPath = params.get("file") || "README.md";
  const file = lab.files.find((entry) => entry.path === selectedPath);
  if (!file) { notFound("This file is not in the collection."); return; }
  navigation("labs",lab.id); setHeading(`Lab ${String(lab.order).padStart(2,"0")}`,lab.title);
  const sourceView = file.type !== "markdown" || params.get("view") === "source";
  const isText = ["code","text","markdown"].includes(file.type);
  const related = documents.filter((doc) => doc.labIds.includes(lab.id));
  const query = params.get("q")?.trim() || "";
  const backLink = query ? `<a class="back-link" href="${searchLink(query,params.get("scope") === "code" ? "code" : "all")}">Back to search results</a>` : '<a class="back-link" href="#/labs">All labs</a>';
  main.innerHTML = `${backLink}<div class="page-heading reader-heading"><div><p class="eyebrow">LAB ${String(lab.order).padStart(2,"0")} / ${escape(lab.id)}</p><h1>${escape(lab.title)}</h1><p>${escape(lab.description)}</p></div><a class="button" href="${escape(lab.repository)}" target="_blank" rel="noopener noreferrer">Source repository</a></div>
    <div class="lab-workbench"><aside class="file-explorer" aria-label="Lab files"><div class="explorer-heading">FILES <span>${lab.files.length}</span></div><input class="file-search" type="search" placeholder="Filter files…" aria-label="Filter lab files"><nav class="file-tree" aria-label="Source files">${fileTree(lab,file.path)}</nav></aside><section class="file-panel" aria-label="File reader"><div class="file-toolbar"><div class="file-title">${icon(file.type === "markdown" ? "book" : "code")}<span>${escape(file.path.split("/").pop())}</span></div><div class="file-actions">${file.type === "markdown" ? `<a href="${labLink(lab,file,!sourceView)}">${sourceView ? "Preview" : "Source"}</a>` : ""}${isText ? `<button type="button" data-action="copy">Copy</button>${sourceView ? `<button type="button" data-action="wrap" aria-pressed="${wrapCode}">Wrap lines</button>` : ""}<a href="${escape(file.url)}" target="_blank" rel="noopener">Raw</a>` : ""}<a href="${escape(file.url)}" download>Download</a></div></div><div class="file-meta">${escape(file.path)} · ${size(file.size)}${file.lines ? ` · ${file.lines} lines` : ""}</div><div class="file-content"><div class="loading-state">Loading file…</div></div></section></div>
    ${collection("Read alongside this lab", "Related lecture slides and study scripts.", "", `<div class="document-list related-resources">${related.map(documentRow).join("")}</div>`)}`;
  main.querySelector(".file-search").addEventListener("input", (event) => { main.querySelector(".file-tree").innerHTML = fileTree(lab,file.path,event.target.value); });
  const content = main.querySelector(".file-content");
  if (!isText) {
    content.innerHTML = `<div class="archive-panel"><span class="lab-icon">${icon("file")}</span><h2>${file.type === "archive" ? "Original lab archive" : "Original file"}</h2><p>${file.type === "archive" ? "This ZIP is included exactly as supplied in the lab’s source folder. Download it to inspect its contents. The Java files are also available in the file browser." : "Download this file to open it in a suitable application."}</p><a class="button primary" href="${escape(file.url)}" download>Download ${escape(file.path.split("/").pop())}</a></div>`;
    return;
  }
  try {
    const source = await readText(file.url);
    if (revision !== routeRevision) return;
    content.replaceChildren(sourceView ? showCode(source,file,params) : renderMarkdown(source,lab,file));
    main.querySelector('[data-action="copy"]').addEventListener("click",async (event) => {
      try { await navigator.clipboard.writeText(source); event.target.textContent = "Copied"; toast("File copied to clipboard."); }
      catch { toast("Copy is unavailable here. Use Raw to select and copy the original text."); }
    });
    main.querySelector('[data-action="wrap"]')?.addEventListener("click", (event) => {
      wrapCode = !wrapCode;
      try { localStorage.setItem("isp-wrap-code",String(wrapCode)); } catch {}
      content.querySelector(".code-view").classList.toggle("wrap",wrapCode);
      event.target.setAttribute("aria-pressed",String(wrapCode));
    });
  } catch (error) {
    if (revision === routeRevision) content.innerHTML = `<div class="empty-state"><h2>File could not be loaded</h2><p>${escape(error.message)}</p><a class="text-link" href="${escape(file.url)}">Open original file</a></div>`;
  }
}
function showDocument(id) {
  const doc = documents.find((entry) => entry.id === id);
  if (!doc) { notFound(); return; }
  const category = doc.category === "slides" ? "slides" : "scripts";
  const label = category === "slides" ? "Lecture slides" : "Study scripts";
  navigation(category); setHeading(label,doc.title);
  main.innerHTML = `<a class="back-link" href="#/${category}">All ${label.toLowerCase()}</a>${heading(doc.category === "slides" ? "LECTURE SLIDES" : "STUDY SCRIPT",doc.title,doc.description)}<div class="document-reader"></div>${collection("Related labs", "Connect the theory to the code.", "#/labs", `<div class="lab-grid">${labs.filter((lab) => doc.labIds.includes(lab.id)).map(labCard).join("")}</div>`)}`;
  disposeReader = mountPdfReader(main.querySelector(".document-reader"),doc);
}
async function search(query,revision,requestedScope) {
  const scope = ["code","documents"].includes(requestedScope) ? requestedScope : "all";
  navigation("search"); setHeading("Search"); searchInput.value = query;
  main.innerHTML = `${heading("GLOBAL SEARCH","Search the collection","Find methods, algorithms, comments, and exact code across all four labs, alongside READMEs, slides, and study scripts.")}<form class="search-page-form" role="search" aria-label="Search the entire collection"><label class="sr-only" for="collection-query">Search code and course materials</label><span>${icon("search")}</span><input id="collection-query" type="search" value="${escape(query)}" placeholder="Try Cipher.getInstance, HMAC, or Diffie-Hellman" autocomplete="off"><button class="button primary" type="submit">Search</button></form><nav class="search-scopes" aria-label="Search scope">${[["all","Everything"],["code","Code"],["documents","Documents"]].map(([value,label]) => `<a href="${searchLink(query,value)}" data-search-scope="${value}"${value === scope ? ' aria-current="page"' : ""}>${label}</a>`).join("")}</nav><div class="search-results" aria-live="polite" aria-busy="true"><div class="loading-state">Searching the collection…</div></div>`;
  main.querySelector(".search-page-form").addEventListener("submit",(event) => {
    event.preventDefault(); navigate(searchLink(main.querySelector("#collection-query").value.trim(),scope));
  });
  const results = [];
  const needle = query.toLowerCase();
  if (!needle) { const list = main.querySelector(".search-results"); list.setAttribute("aria-busy","false"); list.innerHTML = '<p class="search-summary">Search every lab at once. Enter a code fragment, class name, method, or topic, then select Search.</p>'; return; }
  let unavailable = 0;
  const work = [];
  for (const lab of scope === "documents" ? [] : labs) {
    if (scope === "all" && `${lab.title} ${lab.description} ${lab.topics.join(" ")}`.toLowerCase().includes(needle)) results.push({type:"lab",kind:`LAB ${lab.order}`,title:lab.title,description:lab.description,url:labLink(lab),order:20});
    for (const file of lab.files) {
      if (!["code","markdown","text"].includes(file.type)) continue;
      if (scope === "code" && file.type !== "code") continue;
      work.push((async () => {
        const source = await readText(file.url);
        const matches = [];
        source.replace(/\r\n?/g,"\n").split("\n").forEach((text,index) => {
          const at = text.toLowerCase().indexOf(needle);
          if (at < 0) return;
          const start = Math.max(0,at - 60), end = Math.min(text.length,at + query.length + 160);
          matches.push({line:index + 1,text:`${start ? "…" : ""}${text.slice(start,end)}${end < text.length ? "…" : ""}`,url:matchLink(lab,file,query,index + 1,scope)});
        });
        if (matches.length || file.path.toLowerCase().includes(needle)) {
          const type = file.type === "code" ? "code" : "readme";
          results.push({type,kind:`FILE / ${type === "code" ? "CODE" : "README"} / ${lab.title}`,title:file.path.split("/").pop(),description:`${file.path}${matches.length ? ` · ${matches.length} matching ${matches.length === 1 ? "line" : "lines"}` : " · filename match"}`,matches,url:matches[0]?.url || labLink(lab,file),order:type === "code" ? 1 : 5});
        }
      })());
    }
  }
  for (const doc of scope === "code" ? [] : documents) work.push((async () => {
    const text = await readJSON(doc.textUrl);
    const pages = text.pages.map((page,index) => page.toLocaleLowerCase().includes(needle) ? index + 1 : 0).filter(Boolean);
    if (pages.length || `${doc.title} ${doc.description}`.toLowerCase().includes(needle)) results.push({type:"document",kind:doc.category === "slides" ? "LECTURE SLIDES" : "STUDY SCRIPT",title:doc.title,description:pages.length ? `Found on ${pages.length} ${pages.length === 1 ? "page" : "pages"}: ${pages.slice(0,8).join(", ")}${pages.length > 8 ? ", …" : ""}` : doc.description,url:docLink(doc),order:10});
  })());
  const outcomes = await Promise.allSettled(work);
  unavailable = outcomes.filter((result) => result.status === "rejected").length;
  if (revision !== routeRevision) return;
  results.sort((a,b) => a.order - b.order || a.title.localeCompare(b.title));
  const list = main.querySelector(".search-results");
  const codeLines = results.filter((result) => result.type === "code").reduce((sum,result) => sum + result.matches.length,0);
  function snippet(match) { return `<a class="search-excerpt" href="${match.url}" aria-label="Open matching line ${match.line}"><span class="search-line-number">line ${match.line}</span><code>${markedText(match.text,query)}</code></a>`; }
  list.setAttribute("aria-busy","false");
  list.innerHTML = `<p class="search-summary">${results.length} ${results.length === 1 ? "result" : "results"}${codeLines ? ` · ${codeLines} matching code ${codeLines === 1 ? "line" : "lines"}` : ""}${unavailable ? ` · ${unavailable} files could not be searched` : ""}</p>${results.length ? results.map((result) => `<article class="search-result" data-result-type="${result.type}"><small>${escape(result.kind)}</small><h2><a href="${result.url}">${markedText(result.title,query)}</a></h2><p>${escape(result.description)}</p>${result.matches?.length ? `<div class="search-snippets">${result.matches.slice(0,3).map(snippet).join("")}${result.matches.length > 3 ? `<details class="search-more"><summary>Show ${result.matches.length - 3} more matching ${result.matches.length === 4 ? "line" : "lines"}</summary>${result.matches.slice(3).map(snippet).join("")}</details>` : ""}</div>` : ""}</article>`).join("") : '<div class="empty-state"><h2>No matches yet</h2><p>Try a method such as getInstance, or an algorithm such as AES, HMAC, or RSA.</p></div>'}`;
}
function notFound(message = "This page is not in the collection.") {
  navigation(""); setHeading("Page not found");
  main.innerHTML = `<div class="empty-state"><h1>Page not found</h1><p>${escape(message)}</p><a class="button primary" href="#/">Back to overview</a></div>`;
}
async function route(initial = false, requestedHash = window.location.hash) {
  const revision = ++routeRevision;
  lastRoutedUrl = window.location.href;
  if (disposeReader) { disposeReader(); disposeReader = null; }
  document.body.classList.remove("menu-open");
  document.querySelector(".mobile-menu").setAttribute("aria-expanded","false");
  if (!collectionReady) {
    main.innerHTML = '<div class="loading-state" role="status">Opening your study room…</div>';
    try { await loadCollection(); }
    catch {
      if (revision !== routeRevision) return;
      main.innerHTML = '<div class="empty-state"><h1>The collection could not be loaded</h1><p>Check your connection and try again. You can also select Labs, Lecture slides, or Study scripts to retry.</p><button class="button primary" type="button" data-action="retry">Try again</button></div>';
      main.querySelector('[data-action="retry"]').addEventListener("click",() => route(false,requestedHash));
      return;
    }
    if (revision !== routeRevision) return;
  }
  const raw = requestedHash.replace(/^#/,"") || "/";
  const split = raw.indexOf("?");
  const path = split < 0 ? raw : raw.slice(0,split);
  const params = new URLSearchParams(split < 0 ? "" : raw.slice(split + 1));
  if (path !== "/search") searchInput.value = params.get("q") || "";
  try {
    const parts = path.split("/").filter(Boolean).map(decodeURIComponent);
    if (parts.length === 0) home();
    else if (parts.length === 1 && parts[0] === "labs") listLabs();
    else if (parts.length === 1 && parts[0] === "slides") listDocuments("slides");
    else if (parts.length === 1 && parts[0] === "scripts") listDocuments("script");
    else if (parts.length === 2 && parts[0] === "lab") await showLab(parts[1],params,revision);
    else if (parts.length === 2 && parts[0] === "document") showDocument(parts[1]);
    else if (parts.length === 1 && parts[0] === "search") await search(params.get("q")?.trim() || "",revision,params.get("scope"));
    else notFound();
  } catch (error) {
    if (revision === routeRevision) { main.innerHTML = `<div class="empty-state"><h1>Could not open this page</h1><p>${escape(error.message)}</p><a class="button primary" href="#/">Back to overview</a></div>`; }
  }
  if (!initial && revision === routeRevision) { main.focus({preventScroll:true}); window.scrollTo({top:0,behavior:"instant"}); }
  if (revision === routeRevision) main.querySelector(".code-line-target")?.scrollIntoView({block:"center",inline:"nearest",behavior:"auto"});
}

await route(true);
