import { readFileSync, readdirSync, lstatSync } from 'node:fs';
import { resolve, relative, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../content/', import.meta.url));
const files = readFileSync(join(root, 'public-files.txt'), 'utf8').trim().split(/\r?\n/);
assert.equal(new Set(files).size, files.length, 'Duplicate public manifest entries');
for (const file of files) {
  assert.match(file, /^(?:[a-z0-9-]+\/)*[a-z0-9-]+\.json$/);
  assert.ok(!file.split('/').some(part => part.startsWith('private-')), 'Private content in public manifest');
  let path = root;
  for (const part of file.split('/')) {
    path = join(path, part);
    assert.ok(!lstatSync(path).isSymbolicLink(), 'Public content must not resolve through symlinks');
  }
  JSON.parse(readFileSync(path, 'utf8'));
}
const sources = JSON.parse(readFileSync(join(root, 'sources.json'))).sources;
assert.ok(sources.length > 0);
assert.equal(new Set(sources.map(source => source.channelId)).size, sources.length);
const risks = JSON.parse(readFileSync(join(root, 'risk-terms.json')));
assert.ok(risks.hardBlock.length > 0 && Object.keys(risks.topics).length > 0);

const packaged = process.argv[2];
if (packaged) {
  const base = resolve(packaged);
  const actual = [];
  function visit(dir) {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name);
      const stat = lstatSync(path);
      assert.ok(!stat.isSymbolicLink());
      if (stat.isDirectory()) visit(path);
      else actual.push(relative(base, path));
    }
  }
  visit(base);
  assert.deepEqual(actual.sort(), [...files].sort(), 'Packaged content differs from public manifest');
  for (const file of files) assert.deepEqual(readFileSync(join(base, file)), readFileSync(join(root, file)), file);
}
console.log(`Validated ${files.length} public content files${packaged ? ' and packaged copies' : ''}.`);
