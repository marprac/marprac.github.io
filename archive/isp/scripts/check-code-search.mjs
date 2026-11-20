// Search interaction checks. Install jsdom outside this checkout and set
// ISP_JSDOM_PATH to its lib/api.js if it is not in the default temp directory.
// Run: node scripts/check-code-search.mjs
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const jsdomPath = process.env.ISP_JSDOM_PATH || path.join(tmpdir(), "isp-pdf-reader-dom-tests/node_modules/jsdom/lib/api.js");
const { JSDOM, VirtualConsole } = await import(pathToFileURL(jsdomPath));
const html = await readFile(path.join(root,"index.html"),"utf8");
const appSource = (await readFile(path.join(root,"app.js"),"utf8"))
  .replace(/^import\s*\{\s*mountPdfReader\s*\}\s*from\s*["']\.\/pdf-reader\.js["'];?\s*/m,"const mountPdfReader = window.__mountPdfReader;\n");
assert(!/^import\s/m.test(appSource),"Update search harness for new module dependencies");
const vendors = await Promise.all(["marked.umd.js","purify.min.js","highlight.min.js"].map(file => readFile(path.join(root,"vendor",file),"utf8")));
const { labs } = JSON.parse(await readFile(path.join(root,"content/labs.json"),"utf8"));
const { documents } = JSON.parse(await readFile(path.join(root,"content/documents.json"),"utf8"));
const fixtureLab = labs[0];
const fixtureFile = fixtureLab.files.find(file => file.language === "java");
const fixtureReadme = fixtureLab.files.find(file => file.type === "markdown");
const needle = "ispNeedle.[x]";
const source = [
  "class SearchFixture {",
  '  String html = "<img src=x onerror=evil()><script>evil()</script>";',
  `  String first = "${needle}";`,
  '  String second = "ISPNEEDLE.[X]";',
  `  // ${needle}`,
  `  // ${needle}`,
  "}", "",
].join("\r\n");
const readme = `# Search fixture\n\nRead the source below.\n\nA README also mentions ${needle}.\n`;
const overrides = () => new Map([
  [fixtureFile.url,source], [fixtureReadme.url,readme],
  [documents[0].textUrl,JSON.stringify({pages:[`A document also mentions ${needle}.`,"Other text","Other text","Other text"]})],
]);
const delay = (ms = 5) => new Promise(resolve => setTimeout(resolve,ms));
async function waitFor(predicate,label) {
  for (let i = 0; i < 240; i++) { if (predicate()) return; await delay(); }
  throw new Error(`Timed out: ${label}`);
}
async function createApp(replacements = overrides(),hash = "") {
  const errors = [], copied = [], scrolled = [];
  const virtualConsole = new VirtualConsole();
  virtualConsole.on("jsdomError",error => errors.push(error.message));
  const dom = new JSDOM(html,{url:`https://isp.test/${hash}`,runScripts:"outside-only",pretendToBeVisual:true,virtualConsole});
  const { window } = dom;
  window.scrollTo = () => {};
  window.HTMLElement.prototype.scrollIntoView = function () { scrolled.push(this); };
  Object.defineProperty(window.navigator,"clipboard",{value:{writeText:async text => { copied.push(text); }}});
  window.__mountPdfReader = () => () => {};
  window.fetch = async input => {
    const relative = decodeURIComponent(new URL(input,window.document.baseURI).pathname).replace(/^\//,"");
    let text;
    if (replacements.has(relative)) {
      const replacement = replacements.get(relative);
      text = typeof replacement === "function" ? await replacement() : replacement;
    } else {
      try { text = await readFile(path.join(root,relative),"utf8"); }
      catch { return {ok:false,status:404,text:async () => "Missing file"}; }
    }
    return {ok:true,status:200,text:async () => text,json:async () => JSON.parse(text)};
  };
  vendors.forEach(vendor => window.eval(vendor));
  await window.eval(`(async () => {\n${appSource}\n})()`);
  return {dom,window,document:window.document,errors,copied,scrolled};
}
function searchHash(query,scope = "all") {
  return `#/search?q=${encodeURIComponent(query)}${scope === "all" ? "" : `&scope=${scope}`}`;
}
async function go(app,hash,ready = document => !!document.querySelector(".search-summary")) {
  app.window.location.hash = hash;
  await delay();
  await waitFor(() => ready(app.document),hash);
}
const results = app => [...app.document.querySelectorAll(".search-result")];
const fileResult = (app,file = fixtureFile) => results(app).find(result => result.textContent.includes(file.path));
const apps = [];
let passed = 0;
const failures = [];
async function test(name,run) {
  try { await run(); passed++; process.stdout.write(`PASS ${name}\n`); }
  catch (error) { failures.push({name,error}); process.stderr.write(`FAIL ${name}: ${error.message}\n`); }
}
const app = await createApp(); apps.push(app);

await test("global search has an explicit submit control and finds terms inside source code",async () => {
  const form = app.document.querySelector(".global-search");
  assert(form.querySelector('button[type="submit"]'),"Header search needs a visible submit button");
  assert(app.document.querySelector('[data-nav="search"]'),"Search must be discoverable from navigation");
  form.querySelector("input").value = needle;
  form.dispatchEvent(new app.window.Event("submit",{bubbles:true,cancelable:true}));
  await waitFor(() => !!app.document.querySelector(".search-summary"),"header search results");
  assert(fileResult(app),"Content query must find a file whose name does not contain the term");
  assert.equal(new URLSearchParams(app.window.location.hash.split("?")[1]).get("q"),needle);
});

await test("All, Code, and Documents filters retain the query and separate source from PDFs",async () => {
  assert(fileResult(app));
  assert(fileResult(app,fixtureReadme),"All should include README contents");
  assert(results(app).some(result => /STUDY SCRIPT|LECTURE SLIDES/.test(result.textContent)));
  const codeFilter = app.document.querySelector('a[href*="scope=code"]');
  assert(codeFilter,"Code filter should be a navigable link");
  codeFilter.click();
  await waitFor(() => !!app.document.querySelector(".search-summary") && app.window.location.hash.includes("scope=code"),"Code filter");
  await delay();
  assert.equal(results(app).length,1);
  assert(fileResult(app));
  assert.equal(fileResult(app,fixtureReadme),undefined);
  assert.equal(new URLSearchParams(app.window.location.hash.split("?")[1]).get("q"),needle);
  app.document.querySelector('a[href*="scope=documents"]').click();
  await waitFor(() => !!app.document.querySelector(".search-summary") && app.window.location.hash.includes("scope=documents"),"Documents filter");
  await delay();
  assert.equal(results(app).length,1);
  assert(results(app).every(result => /STUDY SCRIPT|LECTURE SLIDES/.test(result.textContent)));
});

await test("code search treats punctuation literally, ignores case, and groups matching lines by file",async () => {
  await go(app,searchHash(needle.toUpperCase(),"code"));
  assert.equal(results(app).length,1);
  const result = fileResult(app);
  const snippets = [...result.querySelectorAll("a.search-excerpt")];
  assert.equal(snippets.filter(snippet => !snippet.closest(".search-more")).length,3,"Show a bounded preview of three matching lines");
  assert.equal(snippets.length,4,"Every matching line should remain accessible");
  assert.match(result.textContent,/4 (?:matching )?(?:lines|matches)/i,"Result should count all four matching lines");
  assert(snippets.every(snippet => snippet.textContent.toLowerCase().includes(needle.toLowerCase())));
  assert(result.querySelectorAll("mark.search-term").length >= 3);
});

await test("global code search searches every lab rather than only the open repository",async () => {
  const replacements = new Map();
  for (const lab of labs) replacements.set(lab.files.find(file => file.language === "java").url,"// crosslab_search_token\nclass Fixture {}\n");
  const isolated = await createApp(replacements); apps.push(isolated);
  await go(isolated,searchHash("crosslab_search_token","code"));
  assert.equal(results(isolated).length,labs.length);
  for (const lab of labs) assert(results(isolated).some(result => [...result.querySelectorAll('a[href]')].some(link => link.hash.startsWith(`#/lab/${lab.id}?`))),`Missing ${lab.id}`);
});

await test("a matching snippet opens, highlights, and scrolls to its exact source line without changing copied code",async () => {
  await go(app,searchHash(needle,"code"));
  const snippet = fileResult(app).querySelectorAll("a.search-excerpt")[1];
  const params = new URLSearchParams(snippet.hash.split("?")[1]);
  const line = Number(params.get("line"));
  assert(line > 0);
  assert.equal(params.get("q"),needle);
  snippet.click();
  await waitFor(() => !!app.document.querySelector(".code-line-target"),"source match opened");
  const target = app.document.querySelector(".code-line-target");
  assert.equal(Number(target.dataset.line),line);
  assert(target.querySelector("mark.search-term"));
  assert(app.scrolled.includes(target),"Selected source line should be scrolled into view");
  app.document.querySelector('[data-action="copy"]').click();
  await delay();
  assert.equal(app.copied.at(-1),source,"Copy must preserve original bytes, not highlighted display text");
});

await test("README search snippets open its source view at the matching line",async () => {
  await go(app,searchHash(needle));
  const snippet = fileResult(app,fixtureReadme).querySelector("a.search-excerpt");
  assert(snippet);
  const params = new URLSearchParams(snippet.hash.split("?")[1]);
  assert.equal(params.get("view"),"source");
  snippet.click();
  await waitFor(() => !!app.document.querySelector(".code-line-target"),"README source match opened");
  assert.equal(app.document.querySelector(".markdown-body"),null);
  assert(app.document.querySelector(".code-line-target").textContent.includes(needle));
});

await test("HTML-shaped code and query text stay inert in search snippets",async () => {
  await go(app,searchHash("<img","code"));
  assert.equal(results(app).length,1);
  const result = fileResult(app);
  assert(result.textContent.includes("<img"));
  assert.equal(result.querySelector("img,script,[onerror]"),null);
  assert(result.querySelector("mark.search-term"));
  assert.equal(app.window.evil,undefined);
});

await test("empty and unmatched queries offer clear states without spurious results",async () => {
  await go(app,"#/search?scope=code",document => !document.querySelector(".search-results .loading-state"));
  assert.equal(results(app).length,0);
  assert.match(app.document.querySelector("main").textContent,/enter|search|find/i);
  await go(app,searchHash("isp-impossible-code-term-781b","code"));
  assert.equal(results(app).length,0);
  assert.match(app.document.querySelector(".search-summary").textContent,/^0\s/);
});

await test("a bookmarked code result restores its target line and query highlight",async () => {
  const hash = `#/lab/${fixtureLab.id}?file=${encodeURIComponent(fixtureFile.path)}&line=4&q=${encodeURIComponent(needle)}`;
  const isolated = await createApp(overrides(),hash); apps.push(isolated);
  const target = isolated.document.querySelector(".code-line-target");
  assert(target);
  assert.equal(target.dataset.line,"4");
  assert(target.querySelector("mark.search-term"));
  assert(isolated.scrolled.includes(target));
});

await test("queries crossing syntax-highlighting spans retain exact source text",async () => {
  const query = "class SearchFixture";
  await go(app,searchHash(query,"code"));
  const snippet = fileResult(app).querySelector("a.search-excerpt");
  snippet.click();
  await waitFor(() => !!app.document.querySelector(".code-line-target"),"cross-span source match opened");
  const target = app.document.querySelector(".code-line-target");
  assert.equal(target.dataset.line,"1");
  assert.equal(target.querySelector("mark.search-term").textContent,query);
  assert.deepEqual([...app.document.querySelectorAll(".code-line-content")].map(line => line.textContent),source.replace(/\r\n/g,"\n").replace(/\n$/,"").split("\n"));
});

await test("a slow code search cannot replace a newer page",async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const replacements = overrides(); replacements.set(fixtureFile.url,() => gate);
  const isolated = await createApp(replacements); apps.push(isolated);
  isolated.window.location.hash = searchHash(needle,"code");
  await waitFor(() => !!isolated.document.querySelector(".search-results"),"pending code search");
  isolated.document.querySelector('[data-nav="labs"]').click();
  await waitFor(() => isolated.document.querySelectorAll("main .lab-card").length === labs.length,"newer Labs page");
  release(source);
  await delay(30);
  assert.equal(isolated.document.querySelector(".search-results"),null);
  assert.equal(isolated.document.querySelectorAll("main .lab-card").length,labs.length);
});

for (const active of apps) {
  if (active.errors.length) failures.push({name:"No uncaught runtime errors",error:new Error(active.errors.join("; "))});
  active.dom.window.close();
}
process.stdout.write(`\n${passed} code-search checks passed; ${failures.length} failed.\n`);
if (failures.length) process.exitCode = 1;
