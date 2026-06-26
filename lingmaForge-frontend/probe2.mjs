import puppeteer from 'puppeteer-core'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox','--disable-gpu'] })
const page = await browser.newPage()
await page.goto('http://localhost:5173/', { waitUntil: 'networkidle0' })
await new Promise(r=>setTimeout(r,900))

const info = await page.evaluate(() => {
  function chain(sel) {
    const el = document.querySelector(sel)
    if (!el) return null
    const chain = []
    let cur = el
    while (cur && cur !== document.documentElement) {
      const s = getComputedStyle(cur)
      chain.push({
        tag: cur.tagName,
        cls: cur.className && typeof cur.className === 'string' ? cur.className : '',
        position: s.position,
        zIndex: s.zIndex,
        opacity: s.opacity,
        transform: s.transform,
        filter: s.filter,
        willChange: s.willChange,
        contain: s.contain,
        isolation: s.isolation,
        overflow: s.overflow + '/' + s.overflowX + '/' + s.overflowY,
        display: s.display,
      })
      cur = cur.parentElement
    }
    return chain
  }
  return { mascot: chain('.mascot'), nav: chain('.site-nav') }
})
console.log(JSON.stringify(info, null, 2))
await browser.close()
