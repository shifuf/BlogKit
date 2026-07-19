import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const base = process.env.SMOKE_BASE || 'http://127.0.0.1:3256';
const outDir = path.resolve('scripts/smoke-out');
fs.mkdirSync(outDir, { recursive: true });

const results = [];
const log = (name, ok, detail = '') => {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
  userAgent:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1'
});
const page = await context.newPage();
page.setDefaultTimeout(30000);

try {
  // ---------- Article detail + neighbors ----------
  await page.goto(`${base}/article/24`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.article-detail-hero, .article-neighbors, main', { timeout: 45000 });
  // 等全局 loading 遮罩消失，避免挡住点击
  await page.waitForSelector('.app-loading', { state: 'detached', timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(outDir, '01-article-top.png'), fullPage: false });

  const header = page.locator('header.site-header');
  const headerBg = await header.evaluate(el => getComputedStyle(el).backgroundColor);
  const headerClasses = await header.getAttribute('class');
  log(
    'article mobile header immersive class',
    headerClasses?.includes('site-header--article-mobile'),
    headerClasses || ''
  );
  // transparent-ish (not solid white)
  const isTransparent =
    headerBg === 'rgba(0, 0, 0, 0)' ||
    headerBg === 'transparent' ||
    /rgba\(\s*0,\s*0,\s*0,\s*0\s*\)/.test(headerBg) ||
    /color\(srgb\s+1\s+1\s+1\s*\/\s*0\)/.test(headerBg);
  log('article header background transparent at top', isTransparent, headerBg);

  // scroll to neighbors
  const neighbors = page.locator('.article-neighbors');
  await neighbors.scrollIntoViewIfNeeded();
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(outDir, '02-article-neighbors.png'), fullPage: false });

  const prevText = (await page.locator('.article-neighbors__item.is-prev strong').textContent().catch(() => ''))?.trim() || '';
  const nextEmpty = await page.locator('.article-neighbors__item.is-empty').count();
  log('neighbors previous visible', Boolean(prevText), prevText || 'empty');
  log('neighbors next empty for latest article', nextEmpty > 0, `emptyCount=${nextEmpty}`);

  // click previous if present
  if (prevText) {
    await page.waitForSelector('.app-loading', { state: 'detached', timeout: 15000 }).catch(() => {});
    await page.locator('.article-neighbors__item.is-prev').click({ force: true });
    await page.waitForURL(/\/article\/(?!24(?:\D|$))\d+/, { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1000);
    const url = page.url();
    log('click previous navigates', /\/article\/\d+/.test(url) && !url.endsWith('/24'), url);
    await page.screenshot({ path: path.join(outDir, '03-article-prev-nav.png'), fullPage: false });
  }

  // open reply form as guest and check label
  await page.goto(`${base}/article/24`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.comments-section, .aero-comment, main', { timeout: 45000 });
  await page.waitForTimeout(1200);
  const replyBtn = page.locator('.aero-comment__reply-toggle').first();
  if (await replyBtn.count()) {
    await replyBtn.scrollIntoViewIfNeeded();
    await replyBtn.click();
    await page.waitForTimeout(400);
    const identity = (await page.locator('.comment-editor--reply .comment-editor__field--identity').first().textContent())?.trim() || '';
    log('guest reply identity label', identity.includes('游客'), identity);
    await page.screenshot({ path: path.join(outDir, '04-comment-reply-guest.png'), fullPage: false });
  } else {
    log('guest reply identity label', false, 'no comments to open reply');
  }

  // ---------- Archive page ----------
  await page.goto(`${base}/archive`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.archive-page, main', { timeout: 45000 });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(outDir, '05-archive.png'), fullPage: false });
  const yearBtns = page.locator('.archive-years__item');
  const yearCount = await yearBtns.count();
  log('archive years rendered', yearCount > 0, `count=${yearCount}`);
  const activeYear = (await page.locator('.archive-years__item.active').textContent().catch(() => ''))?.trim() || '';
  log('archive default active year', Boolean(activeYear), activeYear);
  const monthHeads = await page.locator('.archive-month__head h3, .archive-month h3').count();
  log('archive months for selected year', monthHeads > 0, `months=${monthHeads}`);
  if (yearCount > 1) {
    await yearBtns.nth(1).click();
    await page.waitForTimeout(800);
    const after = (await page.locator('.archive-years__item.active').textContent())?.trim() || '';
    log('archive year switch', after && after !== activeYear, `${activeYear} -> ${after}`);
    await page.screenshot({ path: path.join(outDir, '06-archive-year-switch.png'), fullPage: false });
  } else {
    log('archive year switch', true, 'only one year available, skipped switch');
  }

  // ---------- Friend link apply ----------
  await page.goto(`${base}/friend-link-apply`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('.friend-apply, main', { timeout: 45000 });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(outDir, '07-friend-apply.png'), fullPage: false });
  const captchaImg = page.locator('.friend-apply__captcha-btn img');
  const hasCaptcha = (await captchaImg.count()) > 0 && Boolean(await captchaImg.getAttribute('src'));
  log('friend apply captcha image', hasCaptcha, hasCaptcha ? 'image present' : 'missing');

  // submit without captcha should warn
  await page.fill('input[placeholder="https://..."]', 'https://example.com/smoke');
  // first input is site name (no placeholder unique); fill by labels via order
  const inputs = page.locator('.friend-apply__form .el-input__inner, .friend-apply__form input');
  // Element Plus may use input.el-input__inner
  const nameInput = page.locator('.el-form-item').filter({ hasText: '站点名称' }).locator('input');
  await nameInput.fill('浏览器点检友链');
  await page.locator('button', { hasText: '提交申请' }).click();
  await page.waitForTimeout(600);
  // expect element-plus message about captcha
  const msg = page.locator('.el-message, .el-message__content');
  const msgText = ((await msg.first().textContent().catch(() => '')) || '').trim();
  log('friend apply blocks empty captcha', /验证码|填写/.test(msgText), msgText || 'no toast');
  await page.screenshot({ path: path.join(outDir, '08-friend-apply-validate.png'), fullPage: false });
} catch (error) {
  log('unexpected error', false, error?.stack || String(error));
  await page.screenshot({ path: path.join(outDir, '99-error.png'), fullPage: true }).catch(() => {});
} finally {
  await browser.close();
}

const failed = results.filter(r => !r.ok);
console.log('\n--- summary ---');
console.log(`passed=${results.filter(r => r.ok).length} failed=${failed.length}`);
if (failed.length) {
  process.exitCode = 1;
}
