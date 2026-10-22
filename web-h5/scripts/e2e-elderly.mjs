/**
 * H5 适老点点点：登录 → 字号/高对比 → 匹配 → 详情收藏 → 我的退出确认取消
 * 用法：node scripts/e2e-elderly.mjs
 * 依赖：本机已 npm i playwright；服务已起 5173/8080
 */
import { chromium } from 'playwright'

const BASE = process.env.H5_BASE || 'http://127.0.0.1:5173'
const phone = '13800000001'
const password = 'Demo@123'

function ok(msg) {
  console.log(`[OK] ${msg}`)
}
function fail(msg) {
  console.error(`[FAIL] ${msg}`)
  process.exitCode = 1
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 390, height: 844 } })

try {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 60000 })
  await page.fill('input[name="phone"], input[type="tel"]', phone)
  await page.fill('input[name="password"], input[type="password"]', password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/home/, { timeout: 15000 })
  ok('UI-01 登录进入首页')

  const fontFab = page.locator('button.font-fab')
  await fontFab.click()
  await page.waitForTimeout(400)
  await fontFab.click()
  await page.waitForTimeout(200)
  await fontFab.click()
  const font = await page.evaluate(() => document.documentElement.dataset.font)
  if (!['normal', 'large', 'xlarge'].includes(font)) fail(`UI-02 字号 data-font=${font}`)
  else ok(`UI-02 字号循环 data-font=${font}`)

  const contrastFab = page.locator('button.contrast-fab')
  await contrastFab.click()
  await page.waitForTimeout(300)
  let contrast = await page.evaluate(() => document.documentElement.dataset.contrast)
  if (contrast !== 'high') fail(`UI-03 高对比未开启 contrast=${contrast}`)
  else ok('UI-03 高对比开启')
  await contrastFab.click()
  await page.waitForTimeout(200)
  contrast = await page.evaluate(() => document.documentElement.dataset.contrast)
  if (contrast === 'high') fail('UI-03 高对比未能关闭')
  else ok('UI-03 高对比关闭')

  await page.getByRole('button', { name: '立即匹配补贴' }).click()
  await page.waitForURL(/\/(policies|mine)/, { timeout: 15000 })
  if (page.url().includes('/mine')) {
    ok('UI-04 资料未齐进入向导（可接受）')
  } else {
    ok('UI-04 进入匹配列表')
    const more = page.getByRole('button', { name: /更多筛选|收起更多筛选/ })
    if (await more.count()) {
      await more.first().click()
      await page.waitForTimeout(200)
      ok('UI-05 更多筛选可点')
    }
    const card = page.locator('.policy-card').first()
    if (await card.count()) {
      await card.click()
      await page.waitForURL(/\/policy\//, { timeout: 10000 })
      ok('进入政策详情')
      const fav = page.locator('button.fav-hit')
      if (await fav.count()) {
        await fav.click()
        await page.waitForTimeout(500)
        ok('UI-06 收藏热区可点')
      }
      const applyBtn = page.getByRole('button', { name: /去申报|暂不符合/ })
      if (await applyBtn.count()) {
        const text = await applyBtn.first().innerText()
        if (text.includes('去申报')) {
          await applyBtn.first().click()
          await page.waitForURL(/\/apply/, { timeout: 15000 })
          ok('UI-07 去申报进入材料/申报页')
        } else {
          ok('UI-07 暂不符合不可申报（跳过）')
        }
      }
    } else {
      ok('匹配列表暂无卡片（跳过详情）')
    }
  }

  await page.goto(`${BASE}/mine`, { waitUntil: 'networkidle' })
  const logout = page.getByRole('button', { name: '退出登录' })
  await logout.click()
  await page.waitForTimeout(300)
  // 取消确认
  const cancel = page.getByRole('button', { name: /取消/ })
  if (await cancel.count()) {
    await cancel.click()
    await page.waitForTimeout(200)
    if (page.url().includes('/login')) fail('UI-10 取消退出却进了登录页')
    else ok('UI-10 退出确认取消有效')
  } else {
    fail('UI-10 未出现退出确认框')
  }

  console.log(process.exitCode ? 'E2E 有失败项' : 'E2E 全部通过')
} catch (e) {
  fail(e.message || String(e))
  console.error(e)
} finally {
  await browser.close()
}
