#!/usr/bin/env node
/**
 * Copy the course repositories' published materials into this static site.
 * Usage: node scripts/sync-labs.mjs [--source-root <GitHub-directory>]
 * Requires only Node.js 18+; no source repositories are changed.
 */
import { createHash } from 'node:crypto';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const definitions = [
  {
    id: 'isp-secrecy',
    order: 1,
    title: 'Symmetric encryption',
    description: 'Explore encrypted communication, weak-key recovery, and ciphertext modification with symmetric ciphers.',
    topics: ['AES-CBC', 'AES-CTR', 'ChaCha20', 'Exhaustive search', 'Active MITM'],
    exerciseCount: 5,
  },
  {
    id: 'isp-integrity',
    order: 2,
    title: 'Message integrity',
    description: 'Study cryptographic hashes and authenticate messages with HMAC.',
    topics: ['SHA-256', 'HMAC', 'Message authentication'],
    exerciseCount: 1,
  },
  {
    id: 'isp-ae-pke',
    order: 3,
    title: 'Authenticated & public-key encryption',
    description: 'Combine confidentiality and integrity with AES-GCM, verify bulk transfers, and explore RSA encryption.',
    topics: ['AES-GCM', 'Public-space integrity', 'RSA', 'ChaCha20-Poly1305'],
    exerciseCount: 3,
  },
  {
    id: 'isp-keyagreement',
    order: 4,
    title: 'Key exchange & digital signatures',
    description: 'Explore public-key key exchange, Diffie-Hellman, digital signatures, and key derivation.',
    topics: ['DH', 'ECDH', 'ECDSA', 'RSA signatures', 'Key derivation'],
    exerciseCount: 2,
  },
];

const comparePaths = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const asURL = (relativePath) => relativePath.split('/').map(encodeURIComponent).join('/');

function assertWithin(root, candidate) {
  const relative = path.relative(root, candidate);
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error(`Path escapes its expected directory: ${candidate}`);
  }
}

async function existingStat(filename) {
  try {
    return await fs.lstat(filename);
  } catch (error) {
    if (error.code === 'ENOENT') return null;
    throw error;
  }
}

async function requireRegularFile(filename, root) {
  assertWithin(root, filename);
  const stat = await fs.lstat(filename);
  if (!stat.isFile() || stat.isSymbolicLink()) {
    throw new Error(`Expected a regular source file: ${filename}`);
  }
  assertWithin(root, await fs.realpath(filename));
}

async function sourceFiles(directory, root, prefix = 'src') {
  assertWithin(root, directory);
  const stat = await fs.lstat(directory);
  if (!stat.isDirectory() || stat.isSymbolicLink()) {
    throw new Error(`Expected a source directory without symbolic links: ${directory}`);
  }
  assertWithin(root, await fs.realpath(directory));
  const files = [];
  const entries = await fs.readdir(directory, { withFileTypes: true });
  entries.sort((a, b) => comparePaths(a.name, b.name));
  for (const entry of entries) {
    const filename = path.join(directory, entry.name);
    const relativePath = `${prefix}/${entry.name}`;
    if (entry.isSymbolicLink()) throw new Error(`Symbolic links are not copied: ${filename}`);
    if (entry.isDirectory()) files.push(...await sourceFiles(filename, root, relativePath));
    else if (entry.isFile()) files.push(relativePath);
    else throw new Error(`Unsupported source entry: ${filename}`);
  }
  return files;
}

// Refuse links in the generated destination, including intermediate directories.
async function ensureDirectory(root, relativePath) {
  let current = root;
  for (const part of relativePath.split('/').filter(Boolean)) {
    if (part === '.' || part === '..') throw new Error(`Invalid directory segment: ${part}`);
    current = path.join(current, part);
    assertWithin(root, current);
    const stat = await existingStat(current);
    if (stat && (!stat.isDirectory() || stat.isSymbolicLink())) {
      throw new Error(`Destination directory is not a regular directory: ${current}`);
    }
    if (!stat) await fs.mkdir(current);
  }
  return current;
}

function describeFile(relativePath, bytes) {
  const extension = path.extname(relativePath).toLowerCase();
  if (['.zip', '.tar', '.gz', '.7z', '.rar'].includes(extension)) {
    return { type: 'archive', language: null, size: bytes.length };
  }
  let text;
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    if (text.includes('\0')) throw new Error('Binary content');
  } catch {
    return { type: 'binary', language: null, size: bytes.length };
  }
  const languages = {
    '.java': 'java', '.xml': 'xml', '.json': 'json', '.js': 'javascript',
    '.mjs': 'javascript', '.ts': 'typescript', '.py': 'python', '.css': 'css',
    '.html': 'html', '.sh': 'bash', '.ps1': 'powershell', '.yml': 'yaml', '.yaml': 'yaml',
  };
  const markdown = ['.md', '.markdown'].includes(extension);
  const type = markdown ? 'markdown' : languages[extension] ? 'code' : 'text';
  const language = markdown ? 'markdown' : languages[extension] ?? 'plaintext';
  const lines = text.length === 0 ? 0 : text.split(/\r\n|\r|\n/).length - (/\r?\n$|\r$/.test(text) ? 1 : 0);
  return { type, language, size: bytes.length, lines };
}

async function removeStaleGeneratedFiles(directory, allowed, prefix = '') {
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const relativePath = prefix ? `${prefix}/${entry.name}` : entry.name;
    const filename = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`Unexpected link in generated content: ${filename}`);
    if (entry.isDirectory()) {
      await removeStaleGeneratedFiles(filename, allowed, relativePath);
      if ((await fs.readdir(filename)).length === 0) await fs.rmdir(filename);
    } else if (entry.isFile()) {
      if (!allowed.has(relativePath)) await fs.unlink(filename);
    } else throw new Error(`Unexpected entry in generated content: ${filename}`);
  }
}

async function main() {
  const projectRoot = await fs.realpath(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'));
  let sourceRoot = path.resolve(projectRoot, '..');
  const args = process.argv.slice(2);
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === '--help' || argument === '-h') {
      console.log('Usage: node scripts/sync-labs.mjs [--source-root <GitHub-directory>]');
      return;
    }
    if (argument === '--source-root') {
      if (!args[index + 1] || args[index + 1].startsWith('--')) throw new Error('--source-root requires a directory.');
      sourceRoot = path.resolve(args[++index]);
    } else if (argument.startsWith('--source-root=')) {
      if (!argument.slice(14)) throw new Error('--source-root requires a directory.');
      sourceRoot = path.resolve(argument.slice(14));
    } else throw new Error(`Unknown argument: ${argument}`);
  }
  sourceRoot = await fs.realpath(sourceRoot);
  if (!(await fs.stat(sourceRoot)).isDirectory()) throw new Error('The source root must be a directory.');

  // Validate and read every source before making any changes to generated content.
  const prepared = [];
  for (const definition of definitions) {
    const root = path.join(sourceRoot, definition.id);
    const rootStat = await fs.lstat(root);
    if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) throw new Error(`Invalid source repository directory: ${root}`);
    assertWithin(sourceRoot, await fs.realpath(root));
    const paths = ['README.md', 'pom.xml', ...await sourceFiles(path.join(root, 'src'), root)].sort(comparePaths);
    const files = [];
    for (const relativePath of paths) {
      const source = path.join(root, ...relativePath.split('/'));
      await requireRegularFile(source, root);
      const bytes = await fs.readFile(source);
      files.push({ path: relativePath, bytes, hash: sha256(bytes), ...describeFile(relativePath, bytes) });
    }
    prepared.push({ definition, files });
  }

  await ensureDirectory(projectRoot, 'content/labs');
  const labs = [];
  let copied = 0;
  for (const { definition, files } of prepared) {
    const relativeRoot = `content/labs/${definition.id}`;
    const destinationRoot = await ensureDirectory(projectRoot, relativeRoot);
    const manifestFiles = [];
    for (const file of files) {
      const parent = path.posix.dirname(file.path);
      if (parent !== '.') await ensureDirectory(projectRoot, `${relativeRoot}/${parent}`);
      const destination = path.join(destinationRoot, ...file.path.split('/'));
      assertWithin(destinationRoot, destination);
      const destinationStat = await existingStat(destination);
      if (destinationStat && (!destinationStat.isFile() || destinationStat.isSymbolicLink())) {
        throw new Error(`Destination file is not a regular file: ${destination}`);
      }
      await fs.writeFile(destination, file.bytes);
      if (sha256(await fs.readFile(destination)) !== file.hash) throw new Error(`Copy verification failed: ${destination}`);
      const { bytes, hash, ...metadata } = file;
      manifestFiles.push({ path: metadata.path, url: asURL(`${relativeRoot}/${file.path}`), ...Object.fromEntries(Object.entries(metadata).filter(([key]) => key !== 'path')) });
      copied += 1;
    }
    await removeStaleGeneratedFiles(destinationRoot, new Set(files.map((file) => file.path)));
    labs.push({ ...definition, repository: `https://github.com/lem-course/${definition.id}`, files: manifestFiles });
  }
  const manifestPath = path.join(projectRoot, 'content', 'labs.json');
  const existingManifest = await existingStat(manifestPath);
  if (existingManifest && (!existingManifest.isFile() || existingManifest.isSymbolicLink())) {
    throw new Error('The lab manifest destination must be a regular file.');
  }
  await fs.writeFile(manifestPath, `${JSON.stringify({ labs }, null, 2)}\n`, 'utf8');
  console.log(`Synced ${labs.length} labs and ${copied} files; every copy passed SHA-256 verification.`);
  console.log('Manifest: content/labs.json');
}

main().catch((error) => {
  console.error(`Lab synchronization failed: ${error.message}`);
  process.exitCode = 1;
});
