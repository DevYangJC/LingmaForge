import puppeteer from 'puppeteer-core'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'

async function dump(page, label) {
  console.log(`\n===== ${label} =====`)
}

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox', '--disable-gpu', '--window-size=1440,900'],
})
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900 })
const errors = []
page.on('console', (m) => {
  if (m.type() === 'error') errors.push('CONSOLE: ' + m.text())
})
page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

// 1. Home page: check mascot layering vs nav
await page.goto('http://localhost:5173/', { waitUntil: 'networkidle0', timeout: 30000 })
await new Promise((r) => setTimeout(r, 800))
const mascotInfo = await page.evaluate(() => {
  const m = document.querySelector('.mascot')
  const nav = document.querySelector('.site-nav')
  if (!m || !nav) return { found: false }
  const mr = m.getBoundingClientRect()
  const nr = nav.getBoundingClientRect()
  // elementFromPoint at mascot's top-left area over the nav
  const x = mr.left + 60
  const y = Math.max(mr.top + 20, 10)
  const topEl = document.elementFromPoint(x, y)
  return {
    found: true,
    mascotRect: { top: mr.top, left: mr.left, width: mr.width, height: mr.height },
    navRect: { top: nr.top, height: nr.height },
    mascotTopAboveNav: mr.top < nr.bottom,
    elementAtPoint: topEl ? topEl.tagName + '.' + topEl.className : 'none',
  }
})
console.log('MASCOT INFO:', JSON.stringify(mascotInfo, null, 2))

// 2. Workbench mode switching
await page.goto('http://localhost:5173/workbench', { waitUntil: 'networkidle0', timeout: 30000 })
await new Promise((r) => setTimeout(r, 800))
const simplePresent = await page.$('.lws-prompt-textarea')
console.log('SimpleMode present on load:', !!simplePresent)

// type and click send
await page.type('.lws-prompt-textarea', '帮我做一个会员订阅商城')
await new Promise((r) => setTimeout(r, 200))
await page.click('.lws-send-circle-btn')
await new Promise((r) => setTimeout(r, 1500))
const genPresent = await page.$('.workbench-container')
const simpleStill = await page.$('.lws-prompt-textarea')
console.log('After send -> GenerationMode present:', !!genPresent, '| SimpleMode still:', !!simpleStill)

// check for a "back to simple" trigger
const hasResetBtn = await page.evaluate(() => {
  const txt = document.body.innerText
  return txt.includes('新建') || txt.includes('简洁') || txt.includes('返回')
})
console.log('GenerationMode has back/new trigger text:', hasResetBtn)

console.log('\nERRORS:', errors.length ? errors : 'none')

await browser.close()
