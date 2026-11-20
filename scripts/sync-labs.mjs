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
import { sameJavaCode, sameJavaCodeIgnoringClassName } from './java-tokens.mjs';

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
    excludePrefixes: ['src/main/java/isp/kolokvij/'],
  },
  {
    id: 'isp-midterm',
    order: 5,
    title: 'Midterm: authenticated key exchange',
    description: 'Follow Alice and the server through signed ECDH, AES-GCM encryption, and password challenge-response authentication.',
    topics: ['ECDH', 'RSA signatures', 'SHA-256', 'AES-GCM', 'Password challenge'],
    exerciseCount: 1,
    sourceId: 'isp-keyagreement',
    sourcePaths: [
      'pom.xml',
      'src/main/java/isp/kolokvij/Midterm1KeyExchange.java',
      'src/main/java/isp/kolokvij/izpit.png',
      'src/main/java/isp/kolokvij/Screenshot_2023-11-20_at_18-58-27_Midterm_1_Programming_assignment.png',
    ],
    localPaths: ['README.md'],
    fileDescriptions: {
      'src/main/java/isp/kolokvij/izpit.png': 'Original midterm assignment: detailed key exchange and authentication protocol',
      'src/main/java/isp/kolokvij/Screenshot_2023-11-20_at_18-58-27_Midterm_1_Programming_assignment.png': 'Original midterm assignment: protocol overview and initial keys and password',
    },
  },
  {
    id: 'isp-midterms-ab',
    order: 6,
    title: 'Midterms A & B',
    description: 'Compare the original Midterm A and B implementations with copies explaining every line of their cryptographic protocols.',
    topics: ['DH', 'ECDH', 'RSA signatures', 'AES-GCM', 'Password authentication'],
    exerciseCount: 2,
    repository: 'https://github.com/marprac/marprac.github.io/tree/main/content/labs/isp-midterms-ab',
    sourceId: 'isp-keyagreement',
    sourcePaths: ['pom.xml'],
    localPaths: [
      'README.md',
      'src/main/java/isp/signatures/MidtermA.java',
      'src/main/java/isp/signatures/MidtermACommented.java',
      'src/main/java/isp/signatures/MidtermB.java',
      'src/main/java/isp/signatures/MidtermBCommented.java',
    ],
    originalHashes: {
      'src/main/java/isp/signatures/MidtermA.java': '4e9143f9278c5984c5cddecd35bac2baaed253b7527a9e6a0ced1709965e3a48',
      'src/main/java/isp/signatures/MidtermB.java': '0010124746ebcc6a1a7f842bc893dc4591250babedff43164825efbc62460cd5',
    },
    annotatedCopies: {
      'src/main/java/isp/signatures/MidtermACommented.java': 'src/main/java/isp/signatures/MidtermA.java',
      'src/main/java/isp/signatures/MidtermBCommented.java': 'src/main/java/isp/signatures/MidtermB.java',
    },
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
  if (['.png', '.jpg', '.jpeg', '.webp', '.gif'].includes(extension)) {
    return { type: 'image', language: null, size: bytes.length };
  }
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
    const sourceId = definition.sourceId || definition.id;
    const root = path.join(sourceRoot, sourceId);
    const rootStat = await fs.lstat(root);
    if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) throw new Error(`Invalid source repository directory: ${root}`);
    assertWithin(sourceRoot, await fs.realpath(root));
    const paths = (definition.sourcePaths || ['README.md', 'pom.xml', ...await sourceFiles(path.join(root, 'src'), root)])
      .filter((filename) => !(definition.excludePrefixes || []).some((prefix) => filename.startsWith(prefix)))
      .sort(comparePaths);
    const files = [];
    for (const relativePath of paths) {
      const source = path.join(root, ...relativePath.split('/'));
      await requireRegularFile(source, root);
      let bytes = await fs.readFile(source);
      // Keep the site's line-by-line explanations when the upstream Java program
      // is unchanged. Reject a code conflict rather than discarding annotations.
      const destination = path.join(projectRoot, 'content', 'labs', definition.id, ...relativePath.split('/'));
      const destinationStat = await existingStat(destination);
      if (relativePath.endsWith('.java') && destinationStat) {
        await requireRegularFile(destination, projectRoot);
        const annotated = await fs.readFile(destination);
        if (!sameJavaCode(bytes.toString('utf8'), annotated.toString('utf8'))) {
          throw new Error(`Java code differs from its source; reconcile the annotated file before syncing: ${destination}`);
        }
        bytes = annotated;
      }
      const description = definition.fileDescriptions?.[relativePath];
      files.push({ path: relativePath, source: `${sourceId}/${relativePath}`, bytes, hash: sha256(bytes), ...describeFile(relativePath, bytes), ...(description ? { description } : {}) });
    }
    for (const relativePath of definition.localPaths || []) {
      const filename = path.join(projectRoot, 'content', 'labs', definition.id, relativePath);
      await requireRegularFile(filename, projectRoot);
      const bytes = await fs.readFile(filename);
      const originalSha256 = definition.originalHashes?.[relativePath];
      if (originalSha256 && sha256(bytes) !== originalSha256) {
        throw new Error(`Original file must remain byte-for-byte unchanged: ${filename}`);
      }
      const annotatedFrom = definition.annotatedCopies?.[relativePath];
      if (annotatedFrom) {
        const originalFilename = path.join(projectRoot, 'content', 'labs', definition.id, annotatedFrom);
        await requireRegularFile(originalFilename, projectRoot);
        const original = await fs.readFile(originalFilename, 'utf8');
        if (!sameJavaCodeIgnoringClassName(original, bytes.toString('utf8'))) {
          throw new Error(`Commented copy differs from its original program: ${filename}`);
        }
      }
      files.push({ path: relativePath, bytes, hash: sha256(bytes), ...describeFile(relativePath, bytes), ...(originalSha256 ? { originalSha256 } : {}), ...(annotatedFrom ? { annotatedFrom } : {}) });
    }
    files.sort((a, b) => comparePaths(a.path, b.path));
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
      manifestFiles.push({ path: metadata.path, url: asURL(`${relativeRoot}/${file.path}`), ...Object.fromEntries(Object.entries(metadata).filter(([key]) => key !== 'path')), sha256: hash });
      copied += 1;
    }
    await removeStaleGeneratedFiles(destinationRoot, new Set(files.map((file) => file.path)));
    const { sourceId, sourcePaths, localPaths, excludePrefixes, fileDescriptions, originalHashes, annotatedCopies, ...lab } = definition;
    labs.push({ ...lab, repository: definition.repository || `https://github.com/lem-course/${sourceId || definition.id}`, files: manifestFiles });
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
