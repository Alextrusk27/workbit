import { execFileSync } from 'node:child_process'
import {
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const scriptsDir = dirname(fileURLToPath(import.meta.url))
const dist = resolve(scriptsDir, '../dist')
const distSsr = resolve(scriptsDir, '../dist-ssr')
const repoRoot = resolve(scriptsDir, '../..')

const { render, seoPages, notFoundSeo, SITE } = await import(
  pathToFileURL(join(distSsr, 'entry-server.js')).href
)

const ROOT_MARKER = '<div id="root"></div>'
const HEAD_MARKER = '</head>'
const DESCRIPTION_RE = /<meta\s+name="description"\s+content="[^"]*"\s*\/>/s
const TITLE_RE = /<title>.*?<\/title>/s

function escapeAttr(value) {
  return value.replaceAll('&', '&amp;').replaceAll('"', '&quot;')
}

function fontPreloads() {
  const files = readdirSync(join(dist, 'assets')).filter((f) =>
    /^inter-(cyrillic|latin)-wght-normal-.*\.woff2$/.test(f),
  )
  if (files.length !== 2) {
    throw new Error(`Expected 2 font files to preload, found: ${files}`)
  }
  return files
    .map(
      (f) =>
        `    <link rel="preload" href="/assets/${f}" as="font" type="font/woff2" crossorigin />`,
    )
    .join('\n')
}

function withHead(html, extra) {
  return html.replace(HEAD_MARKER, `${extra}\n  ${HEAD_MARKER}`)
}

function pageHtml(base, { title, description, headExtra, appHtml }) {
  let html = base
  if (!TITLE_RE.test(html) || !DESCRIPTION_RE.test(html)) {
    throw new Error('Template is missing title or description meta')
  }
  html = html.replace(TITLE_RE, `<title>${title}</title>`)
  html = html.replace(
    DESCRIPTION_RE,
    `<meta name="description" content="${escapeAttr(description)}" />`,
  )
  html = withHead(html, headExtra)
  return html.replace(ROOT_MARKER, `<div id="root">${appHtml}</div>`)
}

function metaBlock(page, canonical) {
  const lines = [
    `    <link rel="canonical" href="${canonical}" />`,
    `    <meta property="og:type" content="website" />`,
    `    <meta property="og:site_name" content="Workbit" />`,
    `    <meta property="og:locale" content="ru_RU" />`,
    `    <meta property="og:title" content="${escapeAttr(page.title)}" />`,
    `    <meta property="og:description" content="${escapeAttr(page.description)}" />`,
    `    <meta property="og:url" content="${canonical}" />`,
    `    <meta property="og:image" content="${SITE}/og-image.png" />`,
    `    <meta property="og:image:width" content="1200" />`,
    `    <meta property="og:image:height" content="630" />`,
    `    <meta name="twitter:card" content="summary_large_image" />`,
  ]
  for (const obj of page.jsonLd ? page.jsonLd() : []) {
    const json = JSON.stringify(obj).replaceAll('<', '\\u003c')
    lines.push(`    <script type="application/ld+json">${json}</script>`)
  }
  return lines.join('\n')
}

function lastmod(sources) {
  try {
    const out = execFileSync(
      'git',
      ['log', '-1', '--format=%cs', '--', ...sources],
      { cwd: repoRoot, encoding: 'utf8' },
    ).trim()
    if (out) return out
  } catch {
    return new Date().toISOString().slice(0, 10)
  }
  return new Date().toISOString().slice(0, 10)
}

function sitemap() {
  const urls = seoPages
    .map((page) => {
      const loc = page.path === '/' ? `${SITE}/` : `${SITE}${page.path}`
      return `  <url>\n    <loc>${loc}</loc>\n    <lastmod>${lastmod(page.sources)}</lastmod>\n  </url>`
    })
    .join('\n')
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`
}

const template = readFileSync(join(dist, 'index.html'), 'utf8')
if (!template.includes(ROOT_MARKER)) {
  throw new Error('Template is missing an empty #root container')
}
const base = withHead(template, fontPreloads())
writeFileSync(join(dist, 'spa.html'), base)

for (const page of seoPages) {
  const canonical = page.path === '/' ? `${SITE}/` : `${SITE}${page.path}`
  const html = pageHtml(base, {
    title: page.title,
    description: page.description,
    headExtra: metaBlock(page, canonical),
    appHtml: await render(page.path),
  })
  const target =
    page.path === '/'
      ? join(dist, 'index.html')
      : join(dist, page.path.slice(1), 'index.html')
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, html)
}

const notFoundHtml = pageHtml(base, {
  title: notFoundSeo.title,
  description: notFoundSeo.description,
  headExtra: '    <meta name="robots" content="noindex" />',
  appHtml: await render('/404'),
})
writeFileSync(join(dist, '404.html'), notFoundHtml)

writeFileSync(join(dist, 'sitemap.xml'), sitemap())

rmSync(distSsr, { recursive: true, force: true })

console.log(`Prerendered ${seoPages.length} routes + 404, sitemap.xml written`)
