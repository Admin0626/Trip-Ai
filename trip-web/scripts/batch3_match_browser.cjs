// Real Edge + Vite proxy + backend. No fabricated API responses.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const base = process.env.TEST_WEB_URL || 'http://127.0.0.1:5173';
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const out = path.resolve(__dirname, '../../docs/dev/evidence/batch3-match', stamp + '-browser');
fs.mkdirSync(out, { recursive: true });
const checks = [], errors = [];
const username = 'bm_' + Date.now(), password = 'MatchUiTest123';
let uid, browser, page;
function sql(q) { return execFileSync('mysql', ['-u', 'root', '-N', '-B', '--default-character-set=utf8mb4', 'trip_llm', '-e', q], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '123456' } }).trim(); }
function save() { fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_BROWSER_HTTP', checks, errors }, null, 2)); }
function check(name, actual, expected) { checks.push({ name, actual, expected, passed: JSON.stringify(actual) === JSON.stringify(expected) }); save(); }
async function responseClick(button, endpoint) {
  const response = page.waitForResponse(r => r.url().endsWith(endpoint) && r.request().method() === 'POST');
  await page.getByRole('button', { name: button, exact: true }).click();
  return (await response).json();
}
(async () => {
  try {
    sql('SELECT 1'); // Check subprocess permissions before creating any fixture.
    const registered = await (await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password, nickname: '推荐验收' }) })).json();
    if (registered.code !== 200) throw new Error('Fixture registration failed');
    uid = Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
    page.on('pageerror', e => { errors.push(e.message); save(); });
    await page.goto(base + '/recommend');
    await page.waitForURL('**/login?redirect=/recommend');
    check('anonymous redirected with return URL', new URL(page.url()).searchParams.get('redirect'), '/recommend');
    await page.getByPlaceholder('user1001').fill(username);
    await page.getByPlaceholder('123456', { exact: true }).fill(password);
    await page.getByRole('button', { name: '登 录', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/recommend');
    check('login returns to recommendation', new URL(page.url()).pathname, '/recommend');
    await page.getByRole('textbox', { name: '旅行需求', exact: true }).fill('我想去云南玩5天，预算3000左右，喜欢自然风光和美食，不要太赶');
    const parsed = await responseClick('解析需求', '/ai/recommend/intent');
    check('actual parser fallback', parsed.data.source, 'RULE_FALLBACK');
    await page.getByTestId('intent-confirm').waitFor();
    check('days prefilled', await page.getByRole('spinbutton', { name: '旅行天数', exact: true }).inputValue(), '5');
    check('budget prefilled', await page.getByRole('spinbutton', { name: '预算上限', exact: true }).inputValue(), '3000');
    check('requires explicit confirmation', await page.getByTestId('match-results').count(), 0);
    // Clear restrictive conditions to exercise results against the existing demo database.
    await page.getByRole('textbox', { name: '目的地', exact: true }).fill('');
    for (const label of ['旅行天数', '预算上限']) {
      await page.getByRole('spinbutton', { name: label, exact: true }).fill('');
      await page.getByRole('spinbutton', { name: label, exact: true }).press('Tab');
    }
    const matched = await responseClick('确认条件并查找路线', '/ai/recommend/match');
    check('actual database matching', matched.code, 200);
    check('honest source', matched.data.source, 'RULE_BASED');
    check('database returns routes', matched.data.list.length > 0, true);
    await page.locator('.match-card').first().waitFor();
    check('cards match response', await page.locator('.match-card').count(), matched.data.list.length);
    check('detail link points to returned route', await page.getByRole('link', { name: '查看路线详情 →' }).first().getAttribute('href'), '/route/' + matched.data.list[0].routeId);
    await page.screenshot({ path: path.join(out, 'desktop.png'), fullPage: true });
    await page.getByRole('link', { name: '查看路线详情 →' }).first().click();
    await page.waitForURL('**/route/' + matched.data.list[0].routeId);
    check('detail navigation', new URL(page.url()).pathname, '/route/' + matched.data.list[0].routeId);
    await page.goto(base + '/recommend');
    await page.getByRole('textbox', { name: '旅行需求', exact: true }).fill('随便给我推荐一下');
    await responseClick('解析需求', '/ai/recommend/intent');
    await page.getByTestId('intent-confirm').waitFor();
    await page.getByRole('textbox', { name: '目的地', exact: true }).fill('绝不存在的验收目的地');
    const empty = await responseClick('确认条件并查找路线', '/ai/recommend/match');
    check('no-match response', empty.data.totalCandidates, 0);
    await page.getByText('没有符合条件的路线，试试减少筛选条件或提高预算。').waitFor();
    check('no-match guidance visible', true, true);
    await page.getByRole('textbox', { name: '目的地', exact: true }).fill('');
    check('editing conditions invalidates old results', await page.getByTestId('match-results').count(), 0);
    await responseClick('确认条件并查找路线', '/ai/recommend/match');
    await page.locator('.match-card').first().waitFor();
    // Delay forwarding a real request to test stale-response protection; do not mock its result.
    await page.route('**/api/ai/recommend/match', async route => { await new Promise(r => setTimeout(r, 600)); await route.continue(); });
    const pending = page.waitForResponse(r => r.url().endsWith('/ai/recommend/match'));
    await page.getByRole('button', { name: '确认条件并查找路线', exact: true }).click();
    await page.getByRole('textbox', { name: '目的地', exact: true }).fill('修改后的目的地');
    await pending;
    await page.waitForFunction(() => !document.querySelector('.el-button.is-loading'));
    check('stale response discarded after edit', await page.getByTestId('match-results').count(), 0);
    await page.unroute('**/api/ai/recommend/match');
    // Deliberate transport failure, separately marked from real successful API checks.
    await page.route('**/api/ai/recommend/match', route => route.abort('failed'));
    await page.getByRole('button', { name: '确认条件并查找路线', exact: true }).click();
    await page.getByText('路线匹配失败，请重试。', { exact: true }).waitFor();
    check('controlled transport failure shows retry guidance', true, true);
    await page.unroute('**/api/ai/recommend/match');
    await page.getByRole('textbox', { name: '目的地', exact: true }).fill('');
    await responseClick('确认条件并查找路线', '/ai/recommend/match');
    await page.locator('.match-card').first().waitFor();
    check('recovers after network failure', await page.locator('.match-card').count() > 0, true);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(out, 'mobile.png'), fullPage: true });
    check('mobile has no horizontal overflow', await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    check('mobile navigation remains a single readable line', await page.locator('.app-header__nav').evaluate(el => el.getBoundingClientRect().height < 50), true);
    await page.getByRole('textbox', { name: '旅行需求', exact: true }).fill('修改后重新描述需求');
    check('editing query invalidates parsed intent', await page.getByTestId('intent-confirm').count(), 0);
    check('no unhandled page errors', errors.length, 0);
  } catch (e) {
    check('browser script completed', e.message, 'no error');
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    if (uid) {
      sql(`DELETE FROM llm_call_log WHERE user_id=${uid}; DELETE FROM user_preference WHERE user_id=${uid}; DELETE FROM sys_user WHERE id=${uid}`);
      check('fixture cleanup', sql(`SELECT COUNT(*) FROM sys_user WHERE id=${uid}`), '0');
    }
    save();
    console.log(`${checks.filter(x => x.passed).length}/${checks.length} passed; evidence: ${out}`);
    if (checks.some(x => !x.passed) || errors.length) process.exitCode = 1;
  }
})();
