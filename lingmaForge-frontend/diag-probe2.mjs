import puppeteer from 'puppeteer-core'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = process.argv[2] || 'http://localhost:5178'
const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox','--disable-gpu','--window-size=1440,900'] })
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900 })
const errors = []
page.on('console', m => { if (m.type()==='error') errors.push('CONSOLE: '+m.text()) })
page.on('pageerror', e => errors.push('PAGEERROR: '+e.message))

// mascot stacking chain
await page.goto(BASE+'/', { waitUntil:'networkidle0', timeout:30000 })
await new Promise(r=>setTimeout(r,800))
const stack = await page.evaluate(() => {
  const m = document.querySelector('.mascot'); const nav = document.querySelector('.site-nav')
  function chain(el){ const out=[]; let c=el; while(c&&c!==document.documentElement){ const s=getComputedStyle(c); out.push({tag:c.tagName,cls:String(c.className||'').slice(0,30),pos:s.position,z:s.zIndex,of:s.overflow,df:s.backdropFilter!=='none'?s.backdropFilter:null}); c=c.parentElement } return out }
  return { mascotChain: chain(m), navChain: chain(nav) }
})
console.log('STACK:', JSON.stringify(stack, null, 1))

// workbench detailed
await page.goto(BASE+'/workbench', { waitUntil:'networkidle0', timeout:30000 })
await new Promise(r=>setTimeout(r,800))
await page.type('.lws-prompt-textarea','帮我做一个会员订阅商城')
await page.click('.lws-send-circle-btn')
for (const t of [300,800,1500,2500]) {
  await new Promise(r=>setTimeout(r,t-(t==300?0:t==800?300:t==1500?800:2500-1500)))
  const state = await page.evaluate(() => ({
    simple: !!document.querySelector('.lws-prompt-textarea'),
    gen: !!document.querySelector('.workbench-container'),
    wbView: !!document.querySelector('.wb-switch-enter-active, .wb-switch-leave-active'),
  }))
  console.log('t='+t+'ms', JSON.stringify(state))
}
console.log('ERRORS:', errors.length?errors:'none')
await browser.close()
