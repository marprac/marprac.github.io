// Real-module navigation regressions. The PDF reader module is not stubbed.
// Install jsdom outside this checkout, then set ISP_JSDOM_PATH to its lib/api.js
// if it is not in the default test directory. Run:
// node --experimental-vm-modules scripts/check-navigation.mjs
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath, pathToFileURL } from "node:url";
import vm from "node:vm";

if (!vm.SourceTextModule) throw new Error("Run with node --experimental-vm-modules scripts/check-navigation.mjs");
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const jsdomPath = process.env.ISP_JSDOM_PATH || path.join(tmpdir(), "isp-pdf-reader-dom-tests/node_modules/jsdom/lib/api.js");
const { JSDOM, VirtualConsole } = await import(pathToFileURL(jsdomPath));
const html = await readFile(path.join(root, "index.html"), "utf8");
const vendors = await Promise.all(["marked.umd.js", "purify.min.js", "highlight.min.js"].map(file => readFile(path.join(root, "vendor", file), "utf8")));
const { labs } = JSON.parse(await readFile(path.join(root, "content/labs.json"), "utf8"));
const delay = (ms = 5) => new Promise(resolve => setTimeout(resolve, ms));
function deferred() { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; }
async function waitFor(predicate, label) {
  for (let i = 0; i < 240; i++) { if (predicate()) return; await delay(); }
  throw new Error(`Timed out: ${label}`);
}

function createDOM(url = "https://isp.test/") {
  const errors = [];
  const console = new VirtualConsole();
  console.on("jsdomError", error => errors.push(error.message));
  const dom = new JSDOM(html, { url, runScripts:"outside-only", pretendToBeVisual:true, virtualConsole:console });
  const { window } = dom;
  window.scrollTo = () => {};
  window.HTMLElement.prototype.scrollIntoView = () => {};
  window.ResizeObserver = class { observe() {} disconnect() {} };
  return { dom, window, document:window.document, errors, context:dom.getInternalVMContext() };
}

async function createApp({ suppressHashchange = false, ignoreHistory = false, gate, failures = new Map() } = {}) {
  const app = createDOM();
  const { window, context } = app;
  const requests = new Map();
  if (suppressHashchange) {
    const add = window.addEventListener.bind(window);
    window.addEventListener = (type, listener, options) => { if (type !== "hashchange") add(type, listener, options); };
  }
  if (ignoreHistory) window.history.pushState = () => {};
  window.fetch = async input => {
    const url = new URL(input, window.document.baseURI);
    const relative = decodeURIComponent(url.pathname).replace(/^\//, "");
    requests.set(relative, (requests.get(relative) || 0) + 1);
    if (gate && ["content/labs.json", "content/documents.json"].includes(relative)) await gate.promise;
    if ((failures.get(relative) || 0) > 0) {
      failures.set(relative, failures.get(relative) - 1);
      return { ok:false, status:503, text:async () => "Temporary failure", json:async () => { throw new Error("Temporary failure"); } };
    }
    let text;
    try { text = await readFile(path.join(root, relative), "utf8"); }
    catch { return { ok:false, status:404, text:async () => "Missing file" }; }
    return { ok:true, status:200, text:async () => text, json:async () => JSON.parse(text) };
  };
  vendors.forEach(source => window.eval(source));
  const modules = new Map();
  async function moduleFor(relative) {
    relative = path.posix.normalize(relative.split("?")[0]);
    if (modules.has(relative)) return modules.get(relative);
    const source = await readFile(path.join(root, relative), "utf8");
    const module = new vm.SourceTextModule(source, {
      context, identifier:relative,
      initializeImportMeta(meta) { meta.url = new URL(relative, window.document.baseURI).href; },
      // Canvas rendering is tested separately. Exercise the real reader's
      // unsupported-preview fallback while keeping this suite DOM-only.
      importModuleDynamically() { throw new Error("Canvas preview intentionally unavailable in DOM navigation test"); },
    });
    modules.set(relative, module);
    return module;
  }
  const entry = await moduleFor("app.js");
  await entry.link((specifier, parent) => moduleFor(path.posix.join(path.posix.dirname(parent.identifier), specifier)));
  const evaluated = entry.evaluate().catch(error => { app.errors.push(error.message); });
  return { ...app, requests, evaluated };
}
async function ready(app) { await waitFor(() => app.document.querySelectorAll("main .lab-card").length === labs.length, "initial overview"); }
async function click(app, selector, predicate, label = selector) {
  const element = app.document.querySelector(selector);
  assert(element, `Missing clickable element: ${selector}`);
  element.click();
  if (predicate) await waitFor(() => predicate(app.document), label);
}
const active = (document, section) => document.querySelector(`[data-nav="${section}"]`)?.getAttribute("aria-current") === "page";
const apps = [];
let passed = 0;
const failed = [];
async function test(name, run) {
  try { await run(); passed++; process.stdout.write(`PASS ${name}\n`); }
  catch (error) { failed.push({ name, error }); process.stderr.write(`FAIL ${name}: ${error.message}\n`); }
}

await test("all primary links work with hashchange events suppressed", async () => {
  const app = await createApp({ suppressHashchange:true }); apps.push(app); await ready(app);
  for (const [section, heading, count] of [["labs","Learn by reading the code.",labs.length],["slides","Lecture slides",4],["scripts","Study scripts",2],["home","A clearer view ofcryptography.",labs.length]]) {
    await click(app, `[data-nav="${section}"]`, d => active(d, section) && d.querySelector("h1")?.textContent === heading);
    assert.equal(app.window.location.hash, section === "home" ? "#/" : `#/${section}`);
    assert.equal(app.document.querySelectorAll(section === "slides" || section === "scripts" ? "main .document-row" : "main .lab-card").length, count);
  }
  assert.deepEqual(app.errors, []);
});

await test("clicks while manifests are pending render the latest selected collection", async () => {
  const gate = deferred(); const app = await createApp({ suppressHashchange:true, gate }); apps.push(app);
  await waitFor(() => app.requests.has("content/labs.json"), "manifest request begins");
  await click(app, '[data-nav="labs"]');
  await click(app, '[data-nav="slides"]');
  assert.equal(app.window.location.hash, "#/slides");
  gate.resolve();
  await waitFor(() => active(app.document, "slides") && app.document.querySelectorAll("main .document-row").length === 4, "latest early navigation survives initialization");
  assert.equal(app.document.querySelector("h1").textContent, "Lecture slides");
  assert.deepEqual(app.errors, []);
});

await test("navigation still works when a host silently ignores history updates", async () => {
  const app = await createApp({ suppressHashchange:true, ignoreHistory:true }); apps.push(app); await ready(app);
  for (const section of ["labs","slides","scripts"]) {
    await click(app, `[data-nav="${section}"]`, d => active(d,section));
    assert.equal(app.window.location.hash,"","The simulated host must leave the URL unchanged");
    assert(app.document.querySelector("main h1"));
  }
  assert.equal(app.document.querySelectorAll("main .document-row").length,2);
  assert.deepEqual(app.errors, []);
});

await test("a failed initial manifest request can recover by clicking Labs", async () => {
  const failures = new Map([["content/labs.json",1]]);
  const app = await createApp({ suppressHashchange:true, failures }); apps.push(app);
  await waitFor(() => /could not|couldn.t|unable|failed/i.test(app.document.querySelector("main").textContent), "initial loading error");
  await click(app, '[data-nav="labs"]', d => active(d,"labs") && d.querySelectorAll("main .lab-card").length === labs.length, "retry follows Labs click");
  assert.equal(app.requests.get("content/labs.json"),2);
  assert.deepEqual(app.errors, []);
});

await test("lab cards and source-file links work without native hashchange", async () => {
  const app = await createApp({ suppressHashchange:true }); apps.push(app); await ready(app);
  await click(app, 'main .lab-card[href="#/lab/isp-secrecy"]', d => !!d.querySelector(".markdown-body"));
  assert.match(app.document.querySelector(".markdown-body h1").textContent,/symmetric encryption/i);
  await click(app, '.file-link[title$="A1AESInCBCMode.java"]', d => !!d.querySelector(".code-view"));
  assert.match(app.document.querySelector(".file-meta").textContent,/A1AESInCBCMode\.java/);
  assert(app.document.querySelector('.code-line [class^="hljs-"]'));
  assert.deepEqual(app.errors, []);
});

await test("real PDF reader mounts, shows text, and navigation disposes it", async () => {
  const app = await createApp({ suppressHashchange:true }); apps.push(app); await ready(app);
  await click(app, 'main .document-row[href="#/document/quick-reference"]', d => !!d.querySelector(".pdf-reader"));
  await click(app, '.pdf-tab[data-view="text"]', d => !d.querySelector(".pdf-text-stage").hidden && /cryptography/i.test(d.querySelector(".pdf-page-text").textContent));
  assert.equal(app.document.querySelector(".pdf-page-count").textContent,"of 4");
  await click(app, '[data-nav="labs"]', d => active(d,"labs"));
  assert.equal(app.document.querySelector(".pdf-reader"),null);
  assert.deepEqual(app.errors, []);
});

await test("Back, Forward, and direct hash changes preserve navigation", async () => {
  const app = await createApp(); apps.push(app); await ready(app);
  await click(app, '[data-nav="labs"]', d => active(d,"labs"));
  await click(app, '[data-nav="slides"]', d => active(d,"slides"));
  app.window.history.back(); await waitFor(() => active(app.document,"labs"),"Back to labs");
  app.window.history.forward(); await waitFor(() => active(app.document,"slides"),"Forward to slides");
  app.window.location.hash = "#/scripts"; await waitFor(() => active(app.document,"scripts"),"direct hash navigation");
  assert.deepEqual(app.errors, []);
});

await test("duplicate popstate/hashchange events do not remount the same PDF", async () => {
  const app = await createApp(); apps.push(app); await ready(app);
  await click(app, 'main .document-row[href="#/document/quick-reference"]', d => !!d.querySelector(".pdf-reader"));
  const reader = app.document.querySelector(".pdf-reader");
  app.window.dispatchEvent(new app.window.PopStateEvent("popstate"));
  app.window.dispatchEvent(new app.window.HashChangeEvent("hashchange"));
  await delay(30);
  assert.equal(app.document.querySelector(".pdf-reader"),reader,"The same URL should retain its current reader instance");
  assert.deepEqual(app.errors, []);
});

await test("bootstrap explains local-file loading and links to the live site", async () => {
  const app = createDOM("file:///C:/ISP/index.html"); apps.push(app);
  const bootstrap = await readFile(path.join(root,"bootstrap.js"),"utf8");
  let imports = 0;
  const script = new vm.Script(bootstrap,{importModuleDynamically(){ imports++; throw new Error("Unexpected local-file module import"); }});
  script.runInContext(app.context);
  await delay(20);
  assert.equal(imports,0);
  assert.match(app.document.querySelector("main").textContent,/local|file|http/i);
  assert([...app.document.querySelectorAll("main a")].some(a => a.href.startsWith("https://marprac.github.io")));
});

await test("bootstrap module failures leave an actionable retry instead of dead navigation", async () => {
  const app = createDOM(); apps.push(app);
  const bootstrap = await readFile(path.join(root,"bootstrap.js"),"utf8");
  let imports = 0;
  const script = new vm.Script(bootstrap,{importModuleDynamically(){ imports++; throw new Error("Test module download failure"); }});
  script.runInContext(app.context);
  await waitFor(() => /could not|couldn.t|unable|failed/i.test(app.document.querySelector("main").textContent),"bootstrap module failure message");
  assert.equal(imports,1);
  assert([...app.document.querySelectorAll("main button, main a")].some(node => /retry|try again|reload|refresh/i.test(node.textContent)),"Failure must offer a retry or reload control");
});

for (const app of apps) app.dom.window.close();
process.stdout.write(`\n${passed} real-module navigation checks passed; ${failed.length} failed.\n`);
if (failed.length) process.exitCode = 1;
