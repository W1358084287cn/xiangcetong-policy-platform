/**
 * 美化效果截图：登录页 + 登录后首页/匹配/我的/申报进度（本机临时工具，不入库讨论）。
 * 用法：node scripts/_shot.mjs
 */
import { chromium } from 'playwright'

const BASE = 'http://127.0.0.1:5173'
const OUT = 'scripts/logs'

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 420, height: 900 } })

await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.screenshot({ path: `${OUT}/shot-login.png` })

await page.fill('input[type="tel"], input[placeholder*="手机号"]', '15632489079')
await page.fill('input[type="password"]', 'Demo@123')
await page.click('button[type="submit"], .van-button--primary')
await page.waitForURL('**/home', { timeout: 10000 })
await page.waitForTimeout(1000)
await page.screenshot({ path: `${OUT}/shot-home.png`, fullPage: true })

await page.goto(BASE + '/policies', { waitUntil: 'networkidle' })
await page.waitForTimeout(1000)
await page.screenshot({ path: `${OUT}/shot-policies.png`, fullPage: true })

await page.goto(BASE + '/mine', { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.screenshot({ path: `${OUT}/shot-mine.png` })

await page.goto(BASE + '/mine/applies', { waitUntil: 'networkidle' })
await page.waitForTimeout(800)
await page.screenshot({ path: `${OUT}/shot-applies.png`, fullPage: true })

await browser.close()
console.log('SHOTS_DONE')
