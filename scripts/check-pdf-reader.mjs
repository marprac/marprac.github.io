// DOM integration checks for the real reader's non-rendering/fallback behavior.
// Install jsdom outside this checkout, then provide its lib/api.js path as an
// argument or ISP_TEST_JSDOM. No browser or canvas implementation is emulated.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const jsdomPath = process.env.ISP_TEST_JSDOM || process.argv[2];
let JSDOM;
try {
  ({ JSDOM } = await import(jsdomPath ? pathToFileURL(jsdomPath).href : "jsdom"));
} catch {
  throw new Error("Provide an external jsdom installation: node scripts/check-pdf-reader.mjs <absolute-path-to-jsdom/lib/api.js>");
}

const dom = new JSDOM("<!doctype html><html><body><main id='reader'></main></body></html>", { url: "https://example.test/class/" });
const requests = [];
const behaviors = new Map();
const observers = [];
Object.assign(globalThis, {
  window: dom.window,
  document: dom.window.document,
  AbortController: dom.window.AbortController,
  ResizeObserver: class {
    constructor(callback) { this.callback = callback; this.disconnected = false; observers.push(this); }
    observe(element) { this.element = element; }
    disconnect() { this.disconnected = true; }
  },
  fetch(url, options = {}) {
    const request = { url, signal: options.signal };
    requests.push(request);
    const behavior = behaviors.get(url);
    if (!behavior) return Promise.reject(new Error(`Unexpected fetch: ${url}`));
    if (behavior.pending) return new Promise((resolve, reject) => Object.assign(request, { resolve, reject }));
    if (behavior.error) return Promise.reject(behavior.error);
    return Promise.resolve({ ok: behavior.ok !== false, json: async () => behavior.data });
  },
});

// A data-URL module cannot resolve the relative PDF.js import. This exercises
// the reader's actual unsupported-preview fallback without replacing its code.
const source = await readFile(new URL("../pdf-reader.js", import.meta.url), "utf8");
const { mountPdfReader } = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const container = document.querySelector("#reader");
const metadata = { id: "sample", title: "Exam <notes>", url: "content/sample.pdf", pages: 4, textUrl: "content/sample.text.json" };
const pages = ["AES alpha <img src=x onerror=alert(1)>", "Integrity beta and hashes", "AES gamma and AES delta", ""];
behaviors.set(metadata.textUrl, { data: { pages } });
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
const query = (selector) => container.querySelector(selector);
const click = (selector) => query(selector).click();
const changePage = (value) => {
  query(".pdf-page-input").value = value;
  query(".pdf-page-input").dispatchEvent(new window.Event("change", { bubbles: true }));
};
const submitSearch = (value) => {
  query(".pdf-search-input").value = value;
  query(".pdf-search").dispatchEvent(new window.Event("submit", { bubbles: true, cancelable: true }));
};
let passed = 0;
function check(name, test) {
  test();
  passed++;
  console.log(`PASS ${name}`);
}

const cleanup = mountPdfReader(container, metadata);
await tick();
check("mount establishes page bounds, safe metadata, and original/download links", () => {
  assert.equal(typeof cleanup, "function");
  assert.equal(query(".pdf-page-input").value, "1");
  assert.equal(query(".pdf-page-input").max, "4");
  assert.equal(query(".pdf-previous").disabled, true);
  assert.equal(query(".pdf-next").disabled, false);
  assert.equal(query(".pdf-open-original").href, "https://example.test/class/content/sample.pdf");
  assert.equal(query(".pdf-download").getAttribute("download"), "");
  assert.equal(container.querySelectorAll("img").length, 0);
});
check("unsupported preview explains fallback and disables PDF zoom", () => {
  assert.match(query(".pdf-notice").textContent, /preview is unavailable/);
  assert.equal(query(".pdf-zoom").disabled, true);
});
click('[data-view="text"]');
check("Text view is accessible and displays extracted text without interpreting HTML", () => {
  assert.equal(query(".pdf-text-stage").hidden, false);
  assert.equal(query(".pdf-canvas-stage").hidden, true);
  assert.equal(query('[data-view="text"]').getAttribute("aria-selected"), "true");
  assert.equal(query('[data-view="text"]').tabIndex, 0);
  assert.equal(query('[data-view="pdf"]').tabIndex, -1);
  assert.equal(query(".pdf-page-text").textContent, pages[0]);
  assert.equal(query(".pdf-page-text img"), null);
});
click(".pdf-next");
check("Next and Previous move between matching text pages", () => {
  assert.equal(query(".pdf-page-input").value, "2");
  assert.equal(query(".pdf-page-text").textContent, pages[1]);
  click(".pdf-previous");
  assert.equal(query(".pdf-page-input").value, "1");
  assert.equal(query(".pdf-page-text").textContent, pages[0]);
});
check("direct page input clamps bounds and explains text-free pages", () => {
  changePage("999");
  assert.equal(query(".pdf-page-input").value, "4");
  assert.equal(query(".pdf-next").disabled, true);
  assert.match(query(".pdf-page-text").textContent, /no extractable text/);
  changePage("-10");
  assert.equal(query(".pdf-page-input").value, "1");
  changePage("2.9");
  assert.equal(query(".pdf-page-input").value, "2");
  changePage("3");
  query(".pdf-page-input").value = "2";
  query(".pdf-page-input").dispatchEvent(new window.KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true }));
  assert.equal(query(".pdf-text-heading").textContent, "Page 2");
});
check("search navigates all matching pages, wraps, and highlights case-insensitively", () => {
  changePage("2");
  submitSearch("aes");
  assert.equal(query(".pdf-page-input").value, "3");
  assert.equal(query(".pdf-page-text").querySelectorAll("mark").length, 2);
  assert.equal(query(".pdf-page-text mark").textContent, "AES");
  assert.match(query(".pdf-search-status").textContent, /2 matching pages/);
  submitSearch("aes");
  assert.equal(query(".pdf-page-input").value, "1");
  submitSearch("aes");
  assert.equal(query(".pdf-page-input").value, "3");
  submitSearch("unfindable");
  assert.equal(query(".pdf-page-input").value, "3");
  assert.equal(query(".pdf-search-status").textContent, "No matches");
  assert.equal(query(".pdf-page-text mark"), null);
  submitSearch("");
  assert.equal(query(".pdf-search-status").textContent, "");
});
check("a fresh search includes a match on the current page", () => {
  changePage("1");
  submitSearch("alpha");
  assert.equal(query(".pdf-page-input").value, "1");
  submitSearch("");
  submitSearch("AES");
  assert.equal(query(".pdf-page-input").value, "1");
});
check("tab keyboard navigation transfers selection and focus", () => {
  query('[data-view="text"]').dispatchEvent(new window.KeyboardEvent("keydown", { key: "Home", bubbles: true, cancelable: true }));
  assert.equal(query('[data-view="pdf"]').getAttribute("aria-selected"), "true");
  assert.equal(document.activeElement, query('[data-view="pdf"]'));
  query('[data-view="pdf"]').dispatchEvent(new window.KeyboardEvent("keydown", { key: "End", bubbles: true, cancelable: true }));
  assert.equal(query('[data-view="text"]').getAttribute("aria-selected"), "true");
});
const oldRoot = query(".pdf-reader");
const oldPage = query(".pdf-page-input");
check("cleanup aborts fetch/events, disconnects observer, and is idempotent", () => {
  cleanup();
  cleanup();
  assert.equal(requests[0].signal.aborted, true);
  assert.equal(observers[0].disconnected, true);
  oldRoot.querySelector(".pdf-next").click();
  assert.equal(oldPage.value, "1");
});

behaviors.set("slow.text.json", { pending: true });
const slowCleanup = mountPdfReader(container, { ...metadata, title: "Old slow document", textUrl: "slow.text.json" });
const slowRequest = requests.at(-1);
slowCleanup();
behaviors.set("new.text.json", { data: { pages: ["New document content"] } });
const newCleanup = mountPdfReader(container, { ...metadata, title: "New document", pages: 1, textUrl: "new.text.json" });
await tick();
click('[data-view="text"]');
slowRequest.resolve({ ok: true, json: async () => ({ pages: ["STALE OLD CONTENT"] }) });
await tick();
check("rapid document switching ignores late content from the disposed document", () => {
  assert.equal(slowRequest.signal.aborted, true);
  assert.equal(container.querySelectorAll(".pdf-reader").length, 1);
  assert.equal(query(".pdf-page-text").textContent, "New document content");
  assert.equal(query(".pdf-next").disabled, true);
});
newCleanup();

behaviors.set("broken.text.json", { ok: false });
const brokenCleanup = mountPdfReader(container, { ...metadata, textUrl: "broken.text.json" });
await tick();
click('[data-view="text"]');
check("failed text download retains useful original-PDF fallback", () => {
  assert.match(query(".pdf-page-text").textContent, /Text could not be loaded/);
  submitSearch("AES");
  assert.match(query(".pdf-search-status").textContent, /Search unavailable/);
  assert.ok(query(".pdf-open-original").href.endsWith("/content/sample.pdf"));
});
brokenCleanup();
dom.window.close();
console.log(`\n${passed} PDF reader DOM integration checks passed. Canvas rendering requires browser verification.`);
