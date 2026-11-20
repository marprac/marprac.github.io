// Dependency-free structural and content checks. Run with: node archive/isp/scripts/check-site.mjs
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import assert from "node:assert/strict";
import { sameJavaCode, sameJavaCodeIgnoringClassName } from "./java-tokens.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)),"..");
const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
const read = (file) => fs.readFile(path.join(root,file));
const json = async (file) => JSON.parse(await read(file));
const exists = async (file) => { try { return (await fs.stat(file)).isFile(); } catch { return false; } };
let checkedAssets = 0;
async function asset(url) {
  assert(!/^(?:https?:|data:|\/)/.test(url),`Asset must be local and relative: ${url}`);
  const target = path.resolve(root,decodeURIComponent(url.split(/[?#]/)[0]));
  assert(target.startsWith(root + path.sep),`Path outside repository: ${url}`);
  const bytes = await fs.readFile(target);
  assert(bytes.length,`Empty asset: ${url}`);
  checkedAssets++;
  return bytes;
}
const { labs } = await json("content/labs.json");
const { documents } = await json("content/documents.json");
assert.equal(labs.length,6);
assert.equal(documents.length,6);
const ids = new Set(labs.map((lab) => lab.id));
assert.equal(ids.size,6);
assert.deepEqual(labs.map((lab) => lab.order),[1,2,3,4,5,6]);
const midterm = labs.find((lab) => lab.id === "isp-midterm");
assert(midterm?.files.some((file) => file.path.endsWith("Midterm1KeyExchange.java")));
assert.equal(midterm.files.filter((file) => file.type === "image").length,2);
assert(!labs.find((lab) => lab.id === "isp-keyagreement").files.some((file) => file.path.includes("/kolokvij/")));
const midtermsAB = labs.find((lab) => lab.id === "isp-midterms-ab");
assert(midtermsAB && midtermsAB.order === 6);
assert.deepEqual(midtermsAB.files.filter((file) => file.language === "java").map((file) => file.path),[
  "src/main/java/isp/signatures/MidtermA.java", "src/main/java/isp/signatures/MidtermACommented.java",
  "src/main/java/isp/signatures/MidtermB.java", "src/main/java/isp/signatures/MidtermBCommented.java",
]);
assert.equal(midtermsAB.files.filter((file) => /^[a-f0-9]{64}$/.test(file.originalSha256)).length,2);
assert.equal(midtermsAB.files.filter((file) => file.annotatedFrom).length,2);
let originalCopies = 0;
let annotatedCopies = 0;
let javaCount = 0;
for (const lab of labs) {
  const files = new Set(lab.files.map((file) => file.path));
  assert(files.has("README.md") && files.has("pom.xml"));
  assert.equal(files.size,lab.files.length);
  for (const file of lab.files) {
    const bytes = await asset(file.url);
    assert.equal(bytes.length,file.size,`Size mismatch: ${file.url}`);
    assert.equal(hash(bytes),file.sha256,`Hash mismatch: ${file.url}`);
    if (file.originalSha256) assert.equal(hash(bytes),file.originalSha256,`Original midterm was modified: ${file.url}`);
    if (file.annotatedFrom) {
      const original = lab.files.find((entry) => entry.path === file.annotatedFrom && entry.originalSha256);
      assert(original,`Missing unchanged original for ${file.url}`);
      assert(sameJavaCodeIgnoringClassName((await asset(original.url)).toString("utf8"),bytes.toString("utf8")),`Commented copy changed its program: ${file.url}`);
    }
    if (file.lines !== undefined) {
      const text = bytes.toString("utf8");
      const lines = text ? text.split(/\r\n|\r|\n/).length - (/\r?\n$|\r$/.test(text) ? 1 : 0) : 0;
      assert.equal(lines,file.lines,`Line count mismatch: ${file.url}`);
    }
    assert(["README.md","pom.xml"].includes(file.path) || file.path.startsWith("src/"));
    assert(!file.path.includes(".."));
    if (file.language === "java") javaCount++;
    const original = file.source ? path.resolve(root,"../../..",...file.source.split("/")) : null;
    if (original && await exists(original)) {
      const sourceBytes = await fs.readFile(original);
      if (file.language === "java") {
        assert(sameJavaCode(bytes.toString("utf8"),sourceBytes.toString("utf8")),`Java program changed: ${file.url}`);
        annotatedCopies++;
      } else {
        assert.equal(hash(bytes),hash(sourceBytes),`Source changed: ${file.url}`);
        originalCopies++;
      }
    }
  }
}
assert.equal(javaCount,27);
const pdfSources = {
  "quick-reference":"Cryptography_Algorithms_Quick_Reference.pdf",
  "exam-study-script":"Cryptography_Complete_Exam_Study_Script.pdf",
  "01-communication-secrecy":"1.pdf", "02-integrity-authenticated-encryption":"2.pdf",
  "03-authenticated-public-key-encryption":"3.pdf", "04-key-exchange":"4.pdf",
};
let pageCount = 0;
for (const doc of documents) {
  const bytes = await asset(doc.url);
  assert.equal(bytes.subarray(0,5).toString(),"%PDF-");
  assert.equal(bytes.length,doc.size);
  const text = JSON.parse(await asset(doc.textUrl));
  assert.equal(text.pages.length,doc.pages);
  assert(text.pages.every((page) => typeof page === "string"));
  assert(text.pages.some((page) => page.trim()));
  assert(doc.labIds.every((id) => ids.has(id)));
  pageCount += doc.pages;
  const original = path.join(process.env.USERPROFILE || "", "Downloads", pdfSources[doc.id]);
  if (await exists(original)) { assert.equal(hash(bytes),hash(await fs.readFile(original))); originalCopies++; }
}
assert.equal(pageCount,181);
for (const file of ["bootstrap.js","app.js","pdf-reader.js","styles.css","pdf-reader.css","favicon.svg","vendor/marked.umd.js","vendor/purify.min.js","vendor/highlight.min.js","vendor/highlight-theme.css","vendor/pdfjs/legacy/build/pdf.min.mjs","vendor/pdfjs/legacy/build/pdf.worker.min.mjs"]) await asset(file);
const index = (await read("index.html")).toString();
const styles = (await read("styles.css")).toString();
assert(styles.includes(".app-shell") && styles.includes(".lab-workbench") && styles.includes(".code-line"),"Course styles are missing");
for (const match of index.matchAll(/(?:src|href)="\.\/([^"]+)"/g)) await asset(match[1]);
assert(!index.includes("archive/personal"),"Personal site must remain unlinked");
for (const file of ["bootstrap.js","app.js","pdf-reader.js","scripts/sync-labs.mjs","scripts/java-tokens.mjs"]) {
  const result = spawnSync(process.execPath,["--input-type=module","--check"],{input:await read(file),encoding:"utf8"});
  assert.equal(result.status,0,`${file}: ${result.stderr}`);
}
const backup = await json("../personal/manifest.json");
for (const file of backup.files) assert.equal(hash(await read(`../personal/${file.name}`)),file.sha256,`Portfolio backup changed: ${file.name}`);
console.log(`PASS: ${labs.length} labs, ${javaCount} Java files, 6 PDFs, ${pageCount} searchable pages, ${checkedAssets} local asset checks.`);
console.log(`PASS: ${annotatedCopies} Java programs match their sources; ${originalCopies} original files verified byte for byte; portfolio archive intact; module syntax valid.`);
