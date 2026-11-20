// DOM integration checks. Install jsdom outside this checkout, then set
// ISP_JSDOM_PATH to its lib/api.js if it is not in the default temp directory.
// Run: node scripts/check-app.mjs
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const jsdomPath = process.env.ISP_JSDOM_PATH || path.join(tmpdir(), "isp-pdf-reader-dom-tests/node_modules/jsdom/lib/api.js");
const { JSDOM, VirtualConsole } = await import(pathToFileURL(jsdomPath));
const html = await readFile(path.join(root, "index.html"), "utf8");
const appSource = (await readFile(path.join(root, "app.js"), "utf8"))
  .replace(/^import\s*\{\s*mountPdfReader\s*\}\s*from\s*["']\.\/pdf-reader\.js["'];?\s*/m,
    "const mountPdfReader = window.__mountPdfReader;\n");
assert(!/^import\s/m.test(appSource), "Update test harness for new module dependencies");
const vendors = await Promise.all(["marked.umd.js", "purify.min.js", "highlight.min.js"]
  .map(name => readFile(path.join(root, "vendor", name), "utf8")));
const { labs } = JSON.parse(await readFile(path.join(root, "content/labs.json"), "utf8"));
const { documents } = JSON.parse(await readFile(path.join(root, "content/documents.json"), "utf8"));
const delay = (ms = 10) => new Promise(resolve => setTimeout(resolve, ms));
async function waitFor(predicate, label) {
  for (let attempt = 0; attempt < 160; attempt++) {
    if (predicate()) return;
    await delay(5);
  }
  throw new Error(`Timed out: ${label}`);
}
async function createApp(overrides = new Map()) {
  const errors = [];
  const console = new VirtualConsole();
  console.on("jsdomError", error => errors.push(error.message));
  const dom = new JSDOM(html, { url: "https://isp.test/", runScripts: "outside-only", pretendToBeVisual: true, virtualConsole: console });
  const { window } = dom;
  window.scrollTo = () => {};
  window.HTMLElement.prototype.scrollIntoView = () => {};
  const copied = [];
  Object.defineProperty(window.navigator, "clipboard", { value: { writeText: async text => { copied.push(text); } } });
  const mounts = [];
  window.__mountPdfReader = (element, document) => {
    element.dataset.documentId = document.id;
    mounts.push({ document, disposed: false });
    const mount = mounts.at(-1);
    return () => { mount.disposed = true; };
  };
  window.fetch = async input => {
    const url = new URL(input, window.document.baseURI);
    const relative = decodeURIComponent(url.pathname).replace(/^\//, "");
    let text;
    if (overrides.has(relative)) {
      const value = overrides.get(relative);
      text = typeof value === "function" ? await value() : value;
    } else {
      try { text = await readFile(path.join(root, relative), "utf8"); }
      catch { return { ok: false, status: 404, text: async () => "missing" }; }
    }
    return { ok: true, status: 200, text: async () => text };
  };
  vendors.forEach(source => window.eval(source));
  await window.eval(`(async () => {\n${appSource}\n})()`);
  return { dom, window, document: window.document, errors, copied, mounts, overrides };
}
async function go(app, hash, predicate) {
  app.window.location.hash = hash;
  await delay();
  if (predicate) await waitFor(() => predicate(app.document), hash);
}
function labHash(lab, file, source = false) {
  return `#/lab/${lab.id}${file ? `?file=${encodeURIComponent(file.path)}${source ? "&view=source" : ""}` : ""}`;
}
function codeText(document) {
  // Empty display lines may contain a placeholder space or an empty highlighted
  // comment span. Preserve every nonempty line and normalize only that sentinel.
  return [...document.querySelectorAll(".code-line-content")].map(line => line.textContent || " ");
}
function expectedCode(source) {
  return source.replace(/\r\n?/g, "\n").replace(/\n$/, "").split("\n").map(line => line || " ");
}
const app = await createApp();
let passed = 0;
const failures = [];
async function test(name, run) {
  try { await run(); passed++; process.stdout.write(`PASS ${name}\n`); }
  catch (error) { failures.push({ name, error }); process.stderr.write(`FAIL ${name}: ${error.message}\n`); }
}

await test("overview includes all four labs, six documents and accurate Java count", async () => {
  assert.equal(app.document.querySelectorAll("main .lab-card").length, labs.length);
  assert.equal(app.document.querySelectorAll("main .document-row").length, documents.length);
  const javaCount = labs.flatMap(lab => lab.files).filter(file => file.language === "java").length;
  assert.equal(javaCount, 22);
  assert.match(app.document.querySelector(".hero-meta").textContent, /22\s*JAVA FILES/);
  assert.equal(app.document.querySelectorAll("#lab-nav a").length, 4);
  assert.equal(app.document.querySelector('[data-nav="home"]').getAttribute("aria-current"), "page");
});

for (const lab of labs) {
  await test(`${lab.id} README renders headings, tables, highlighted examples and related readings`, async () => {
    await go(app, labHash(lab), document => !!document.querySelector(".markdown-body"));
    const article = app.document.querySelector(".markdown-body");
    assert(article.querySelector("h1"));
    assert(article.querySelector(".table-scroll table"));
    assert(article.querySelector("pre code.hljs"));
    assert.equal(app.document.querySelectorAll(".file-tree .file-link").length, lab.files.length);
    assert.equal(app.document.querySelectorAll(".related-resources .document-row").length, documents.filter(doc => doc.labIds.includes(lab.id)).length);
    assert.equal(app.document.querySelector(`[data-lab-nav="${lab.id}"]`).getAttribute("aria-current"), "page");
    const externalLinks = [...article.querySelectorAll('a[target="_blank"]')];
    assert(externalLinks.every(link => link.rel.includes("noopener")));
  });
}

for (const lab of labs) {
  for (const file of lab.files.filter(file => file.type === "code")) {
    await test(`${lab.id}/${file.path} renders every code line and syntax highlighting`, async () => {
      await go(app, labHash(lab, file), document => document.querySelector(".file-meta")?.textContent.startsWith(file.path) && !!document.querySelector(".code-view"));
      const original = await readFile(path.join(root, file.url), "utf8");
      assert.deepEqual(codeText(app.document), expectedCode(original));
      assert.equal(app.document.querySelectorAll(".code-line").length, expectedCode(original).length);
      assert(app.document.querySelector('.code-line [class^="hljs-"]'));
      assert.equal(new URL(app.document.querySelector('.file-actions a[download]').href).pathname, `/${file.url}`);
    });
  }
}

await test("README preview/source toggle preserves the complete original source", async () => {
  const lab = labs[0], file = lab.files.find(file => file.type === "markdown");
  await go(app, labHash(lab), document => !!document.querySelector(".markdown-body"));
  const sourceLink = [...app.document.querySelectorAll(".file-actions a")].find(link => link.textContent === "Source");
  assert(sourceLink);
  await go(app, sourceLink.hash, document => !!document.querySelector(".code-view"));
  assert.deepEqual(codeText(app.document), expectedCode(await readFile(path.join(root, file.url), "utf8")));
  const previewLink = [...app.document.querySelectorAll(".file-actions a")].find(link => link.textContent === "Preview");
  assert(previewLink);
  await go(app, previewLink.hash, document => !!document.querySelector(".markdown-body"));
});

await test("code copy uses the exact source and line wrapping persists", async () => {
  const lab = labs[0], file = lab.files.find(file => file.language === "java");
  await go(app, labHash(lab, file), document => !!document.querySelector(".code-view"));
  app.document.querySelector('[data-action="copy"]').click();
  await waitFor(() => app.copied.length > 0, "clipboard write");
  assert.equal(app.copied.at(-1), await readFile(path.join(root, file.url), "utf8"));
  const wrap = app.document.querySelector('[data-action="wrap"]');
  wrap.click();
  assert.equal(wrap.getAttribute("aria-pressed"), "true");
  assert(app.document.querySelector(".code-view.wrap"));
  assert.equal(app.window.localStorage.getItem("isp-wrap-code"), "true");
  await go(app, "#/scripts", document => document.querySelectorAll("main .document-row").length === 2);
  await go(app, labHash(lab, file), document => !!document.querySelector(".code-view.wrap"));
});

await test("file filtering is case insensitive, handles no matches and restores the tree", async () => {
  const lab = labs.find(lab => lab.id === "isp-ae-pke");
  await go(app, labHash(lab), document => !!document.querySelector(".markdown-body"));
  const filter = app.document.querySelector(".file-search");
  filter.value = "rsaexample";
  filter.dispatchEvent(new app.window.Event("input"));
  assert.equal(app.document.querySelectorAll(".file-link").length, 1);
  assert.match(app.document.querySelector(".file-link").textContent, /RSAExample.java/);
  filter.value = "no-file-matches-this";
  filter.dispatchEvent(new app.window.Event("input"));
  assert(app.document.querySelector(".file-empty"));
  filter.value = "";
  filter.dispatchEvent(new app.window.Event("input"));
  assert.equal(app.document.querySelectorAll(".file-link").length, lab.files.length);
});

await test("ZIP archive has working original download links and is not treated as code", async () => {
  const lab = labs[0], file = lab.files.find(file => file.type === "archive");
  await go(app, labHash(lab, file), document => !!document.querySelector(".archive-panel"));
  assert.equal(app.document.querySelector(".code-view"), null);
  const download = app.document.querySelector(".archive-panel a[download]");
  assert.equal(new URL(download.href).pathname, `/${file.url}`);
  const bytes = await readFile(path.join(root, file.url));
  assert.equal(bytes.subarray(0, 2).toString(), "PK");
});

await test("all document routes mount the correct reader and connect to related labs", async () => {
  for (const doc of documents) {
    await go(app, `#/document/${doc.id}`, document => document.querySelector(".document-reader")?.dataset.documentId === doc.id);
    assert.equal(app.mounts.at(-1).document.id, doc.id);
    assert.equal(app.document.querySelectorAll("main .lab-card").length, doc.labIds.length);
    assert.equal(app.document.querySelector("main h1").textContent, doc.title);
  }
  await go(app, "#/", document => !!document.querySelector(".hero"));
  assert(app.mounts.every(mount => mount.disposed));
});

await test("search finds code matches with line references and PDF page matches", async () => {
  await go(app, "#/search?q=AES", document => !!document.querySelector(".search-summary"));
  const results = [...app.document.querySelectorAll(".search-result")];
  assert(results.some(result => result.querySelector("small").textContent.startsWith("FILE") && /line \d+/.test(result.textContent)));
  assert(results.some(result => /LECTURE SLIDES|STUDY SCRIPT/.test(result.querySelector("small").textContent) && /Found on \d+ pages?:/.test(result.textContent)));
  assert(results.some(result => result.querySelector(".search-excerpt")));
  assert(!app.document.querySelector(".search-summary").textContent.includes("could not be searched"));
  await go(app, "#/search?q=isp-search-no-results-6f2d", document => !!document.querySelector(".search-summary"));
  assert.match(app.document.querySelector(".search-summary").textContent, /^0 results/);
  assert.equal(app.document.querySelectorAll(".search-result").length, 0);
});

await test("missing files, unknown pages and malformed route encodings fail gracefully", async () => {
  await go(app, "#/lab/isp-secrecy?file=missing.java", document => document.querySelector("main h1")?.textContent === "Page not found");
  assert.match(app.document.querySelector("main").textContent, /file is not in the collection/);
  await go(app, "#/unknown", document => document.querySelector("main h1")?.textContent === "Page not found");
  await go(app, "#/lab/%E0%A4%A", document => document.querySelector("main h1")?.textContent === "Could not open this page");
  assert(app.document.querySelector('main a[href="#/"]'));
});

await test("search shortcut, search form and mobile navigation work with keyboard state", async () => {
  await go(app, "#/", document => !!document.querySelector(".hero"));
  app.document.body.dispatchEvent(new app.window.KeyboardEvent("keydown", { key: "/", bubbles: true, cancelable: true }));
  assert.equal(app.document.activeElement.id, "global-search");
  app.document.querySelector("#global-search").value = "HMAC";
  app.document.querySelector(".global-search").dispatchEvent(new app.window.Event("submit", { bubbles: true, cancelable: true }));
  await waitFor(() => !!app.document.querySelector(".search-summary"), "form search results");
  assert.equal(app.window.location.hash, "#/search?q=HMAC");
  const menu = app.document.querySelector(".mobile-menu");
  menu.click();
  assert(app.document.body.classList.contains("menu-open"));
  assert.equal(menu.getAttribute("aria-expanded"), "true");
  app.document.body.dispatchEvent(new app.window.KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
  assert(!app.document.body.classList.contains("menu-open"));
  assert.equal(menu.getAttribute("aria-expanded"), "false");
});

await test("skip-to-content focuses the main region without opening a missing route", async () => {
  await go(app, "#/", document => !!document.querySelector(".hero"));
  app.document.querySelector(".skip-link").click();
  await delay(20);
  assert(app.document.querySelector(".hero"), "Skip link must preserve the current page");
  assert.equal(app.document.activeElement, app.document.querySelector("main"));
});

await test("source and Markdown injection remain inert and relative README links open the file viewer", async () => {
  const lab = labs[0], file = lab.files.find(file => file.language === "java");
  const code = 'class Demo { String html = "<img src=x onerror=alert(1)><script>bad()</script>"; }\n';
  const readme = `[Open code](./${file.path})\n\n<script>window.injected=true</script><img src="x" onerror="window.injected=true">\n\n[Unsafe](javascript:alert(1))`;
  const isolated = await createApp(new Map([[file.url, code], [lab.files[0].url, readme]]));
  try {
    await go(isolated, labHash(lab, file), document => !!document.querySelector(".code-view"));
    assert.deepEqual(codeText(isolated.document), expectedCode(code));
    assert.equal(isolated.document.querySelector(".code-view img,.code-view script"), null);
    await go(isolated, labHash(lab), document => !!document.querySelector(".markdown-body"));
    const article = isolated.document.querySelector(".markdown-body");
    assert.equal(article.querySelector("script,[onerror]"), null);
    assert(![...article.querySelectorAll("a")].some(link => link.href.startsWith("javascript:")));
    assert.equal(article.querySelector("a").hash, labHash(lab, file));
    assert.equal(isolated.window.injected, undefined);
  } finally { isolated.dom.window.close(); }
});

await test("a stale delayed file response cannot overwrite a newer route", async () => {
  const lab = labs[0], file = lab.files.find(file => file.language === "java");
  let release;
  const delayed = new Promise(resolve => { release = resolve; });
  const isolated = await createApp(new Map([[file.url, () => delayed]]));
  try {
    await go(isolated, labHash(lab, file), document => !!document.querySelector(".file-content .loading-state"));
    await go(isolated, "#/scripts", document => document.querySelectorAll("main .document-row").length === 2);
    release("class LateResponse {}\n");
    await delay(30);
    assert.equal(isolated.document.querySelectorAll("main .document-row").length, 2);
    assert.equal(isolated.document.querySelector(".code-view"), null);
    assert.equal(isolated.document.querySelector("main h1").textContent, "Study scripts");
    assert.deepEqual(isolated.errors, []);
  } finally { isolated.dom.window.close(); }
});

await test("a stale delayed search cannot overwrite a newer route", async () => {
  let release;
  const delayed = new Promise(resolve => { release = resolve; });
  const isolated = await createApp(new Map([[documents[0].textUrl, () => delayed]]));
  try {
    await go(isolated, "#/search?q=AES", document => !!document.querySelector(".search-results"));
    await go(isolated, "#/labs", document => document.querySelectorAll("main .lab-card").length === 4);
    release(JSON.stringify({ pages: ["AES"] }));
    await delay(30);
    assert.equal(isolated.document.querySelectorAll("main .lab-card").length, 4);
    assert.equal(isolated.document.querySelector(".search-results"), null);
    assert.deepEqual(isolated.errors, []);
  } finally { isolated.dom.window.close(); }
});

await test("DOM interactions produce no uncaught runtime errors", async () => {
  assert.deepEqual(app.errors, []);
});
app.dom.window.close();
process.stdout.write(`\n${passed} checks passed; ${failures.length} failed.\n`);
if (failures.length) process.exitCode = 1;
