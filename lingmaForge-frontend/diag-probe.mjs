import puppeteer from 'puppeteer-core'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = process.argv[2] || 'http://localhost:5178'
const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox','--disable-gpu','--window-size=1440,900'] })
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900 })
const errors = []
page.on('console', m => { if (m.type()==='error') errors.push('CONSOLE: '+m.text()) })
page.on('pageerror', e => errors.push('PAGEERROR: '+e.message))

// 1. Home mascot layering
await page.goto(BASE+'/', { waitUntil:'networkidle0', timeout:30000 })
await new Promise(r=>setTimeout(r,800))
const mascotInfo = await page.evaluate(() => {
  const m = document.querySelector('.mascot'); const nav = document.querySelector('.site-nav')
  if (!m||!nav) return { found:false }
  const mr = m.getBoundingClientRect(), nr = nav.getBoundingClientRect()
  const x = mr.left + 60, y = Math.max(mr.top + 20, 10)
  const topEl = document.elementFromPoint(x,y)
  return { found:true, mascotTop: mr.top, navBottom: nr.bottom, mascotAboveNav: mr.top < nr.bottom, elementAtPoint: topEl? topEl.tagName+'.'+String(topEl.className).slice(0,40):'none' }
})
console.log('MASCOT:', JSON.stringify(mascotInfo))

// 2. Workbench simple->generation
await page.goto(BASE+'/workbench', { waitUntil:'networkidle0', timeout:30000 })
await new Promise(r=>setTimeout(r,800))
console.log('Simple present:', !!await page.$('.lws-prompt-textarea'))
await page.type('.lws-prompt-textarea','帮我做一个会员订阅商城')
await new Promise(r=>setTimeout(r,200))
await page.click('.lws-send-circle-btn')
await new Promise(r=>setTimeout(r,1500))
console.log('After send -> Gen present:', !!await page.$('.workbench-container'), '| Simple still:', !!await page.$('.lws-prompt-textarea'))

// 3. footers across pages
const pages = ['/','/creative','/subscription','/pricing','/doc','/profile','/works']
for (const p of pages) {
  await page.goto(BASE+p, { waitUntil:'networkidle0', timeout:30000 })
  await new Promise(r=>setTimeout(r,300))
  const f = await page.evaluate(() => {
    const el = document.querySelector('footer')
    return el ? el.className : 'NO FOOTER'
  })
  console.log('PAGE', p, '-> footer:', f)
}

console.log('ERRORS:', errors.length? errors:'none')
await browser.close()
