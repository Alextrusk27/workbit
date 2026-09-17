import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const [distA, distB] = process.argv.slice(2)
if (!distA || !distB) {
  console.error('usage: node scripts/compare-prerender.mjs <distA> <distB>')
  process.exit(2)
}

function htmlFiles(dist) {
  return readdirSync(dist, { recursive: true })
    .map((f) => f.replaceAll('\\', '/'))
    .filter((f) => f === '404.html' || f.endsWith('index.html'))
    .sort()
}

function normalizeHtml(html) {
  return html
    .replaceAll('<!-- -->', '')
    .replace(/<meta name="app-(version|build)" content="[^"]*"\s*\/?>/g, '')
    .replace(/\/assets\/([\w.-]+?)-[\w-]{8}\.(\w+)/g, '/assets/$1.$2')
    .replace(/\s+/g, ' ')
    .replace(/>( ?)</g, '>\n$1<')
    .trim()
}

function normalizeSitemap(xml) {
  return xml.replace(/\s*<lastmod>[^<]*<\/lastmod>/g, '').trim()
}

function compare(file, normalize) {
  const a = normalize(readFileSync(join(distA, file), 'utf8')).split('\n')
  const b = normalize(readFileSync(join(distB, file), 'utf8')).split('\n')
  const n = Math.max(a.length, b.length)
  for (let i = 0; i < n; i++) {
    if (a[i] === b[i]) continue
    console.error(`${file}: line ${i + 1} differs`)
    for (let j = Math.max(0, i - 2); j <= Math.min(n - 1, i + 2); j++) {
      if (a[j] === b[j]) console.error(`  ${a[j]}`)
      else {
        if (a[j] !== undefined) console.error(`- ${a[j]}`)
        if (b[j] !== undefined) console.error(`+ ${b[j]}`)
      }
    }
    process.exit(1)
  }
}

const files = new Set([...htmlFiles(distA), ...htmlFiles(distB)])
for (const file of files) {
  if (!existsSync(join(distA, file))) {
    console.error(`${file}: only in ${distB}`)
    process.exit(1)
  }
  if (!existsSync(join(distB, file))) {
    console.error(`${file}: only in ${distA}`)
    process.exit(1)
  }
  compare(file, normalizeHtml)
}
compare('sitemap.xml', normalizeSitemap)
console.log(`${files.size} pages and sitemap.xml are equivalent`)
