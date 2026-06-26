import puppeteer from 'puppeteer-core'
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = process.argv[2] || 'http://localhost:5178'
const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox','--disable-gpu','--window-size=1440,900'] })
const page = await browser.newPage()
await page.setViewport({ width: 1440, height: 900 })
const errors = []
page.on('console', m => errors.push((m.type()==='error'?'ERR:':'log:')+m.text()))
page.on('pageerror', e => errors.push('PAGEERROR: '+e.message))

await page.goto(BASE+'/workbench', { waitUntil:'networkidle0', timeout:30000 })
await new Promise(r=>setTimeout(r,800))
console.log('before submit, body children:', await page.evaluate(()=>document.body.innerHTML.length))
await page.type('.lws-prompt-textarea','帮我做一个会员订阅商城')
await page.click('.lws-send-circle-btn')
await new Promise(r=>setTimeout(r,2000))
const dump = await page.evaluate(() => {
  const root = document.querySelector('#app')
  return {
    appHTMLlen: root ? root.innerHTML.length : -1,
    hasLwsRoot: !!document.querySelector('.lws-page-root'),
    hasWbContainer: !!document.querySelector('.workbench-container'),
    hasLwsNav: !!document.querySelector('.lws-nav'),
    hasSiteNav: document.querySelectorAll('.site-nav').length,
    firstChildClass: root?.firstElementChild?.className,
  }
})
console.log('after submit:', JSON.stringify(dump, null, 1))
console.log('ALL LOGS:', errors)
await browser.close()
