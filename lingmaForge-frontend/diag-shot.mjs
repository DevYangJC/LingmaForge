import puppeteer from 'puppeteer-core'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = process.argv[2] || 'http://localhost:5178'
const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox','--disable-gpu','--window-size=1440,900'] })
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900 })
await page.goto(BASE+'/', { waitUntil:'networkidle0', timeout:30000 })
await new Promise(r=>setTimeout(r,1200))
// sample pixel colors across the nav band where mascot should overlap (right side)
const px = await page.evaluate(() => {
  const nav = document.querySelector('.site-nav').getBoundingClientRect()
  const shot = []
  // sample at the nav-actions region (right) and menu region (center)
  const pts = [
    {x: Math.round(nav.right - 240), y: 38, label: 'nav-right'},
    {x: Math.round(nav.right - 120), y: 38, label: 'nav-far-right'},
    {x: 720, y: 38, label: 'nav-center'},
    {x: 200, y: 38, label: 'nav-left'},
  ]
  return pts.map(p => {
    const el = document.elementFromPoint(p.x, p.y)
    return {label: p.label, x: p.x, y: p.y, el: el ? el.tagName+'.'+String(el.className).slice(0,25) : 'none'}
  })
})
console.log('NAV-BAND HIT TEST:', JSON.stringify(px, null, 1))
await page.screenshot({ path: '../tmp/home-after.png', clip: {x:0,y:0,width:1440,height:320} })
console.log('screenshot saved ../tmp/home-after.png')
await browser.close()
