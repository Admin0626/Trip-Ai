// Real browser/API/Redis. Controlled provider must listen on localhost:11435.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs'), path = require('node:path'), { execFileSync } = require('node:child_process');
const base = 'http://127.0.0.1:5173';
const out = path.resolve(__dirname, '../../docs/dev/evidence/ai-quota', new Date().toISOString().replace(/[:.]/g, '-') + '-browser');
const checks = [], errors = [], keys = new Set();
let browser, page, uid;
fs.mkdirSync(out, { recursive: true });
function save() { fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_BROWSER_API_REDIS_WITH_CONTROLLED_PROVIDER', checks, errors }, null, 2)); }
function check(name, actual, expected) { checks.push({ name, actual, expected, passed: actual === expected }); save(); }
function sql(q) { return execFileSync('mysql', ['-u', 'root', '-N', '-B', '--default-character-set=utf8mb4', 'trip_llm', '-e', q], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '123456' } }).trim(); }
function redis(...args) { return execFileSync('redis-cli', ['--raw', ...args.map(String)], { encoding: 'utf8' }).trim(); }
function currentKeys() {
  const now = Date.now() / 1000, hour = Math.floor(now / 3600) * 3600, day = Math.floor((now + 28800) / 86400) * 86400 - 28800;
  const result = [`trip:ai:quota:{ai-quota}:user:${uid}:hour:${hour}`, `trip:ai:quota:{ai-quota}:user:${uid}:day:${day}`];
  result.forEach(k => keys.add(k)); return result;
}
async function refresh() {
  const response = page.waitForResponse(r => r.url().endsWith('/ai/planner/usage'));
  await page.getByRole('button', { name: '刷新额度', exact: true }).click();
  const data = (await response).json(); await page.getByRole('button', { name: '刷新额度', exact: true }).isEnabled(); return (await data).data;
}
(async () => {
  try {
    sql('SELECT 1'); redis('PING');
    const username = 'qu_' + Date.now(), password = 'QuotaUi123456';
    const register = await (await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })).json();
    if (register.code !== 200) throw new Error('Registration failed');
    uid = Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`)); currentKeys();
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.on('pageerror', e => { errors.push(e.message); save(); });
    await page.goto(base + '/login?redirect=/ai-planner');
    await page.getByPlaceholder('user1001').fill(username);
    await page.getByPlaceholder('123456', { exact: true }).fill(password);
    await page.getByRole('button', { name: '登 录', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/ai-planner');
    await page.getByTestId('hourly-remaining').waitFor();
    check('initial remaining 20', await page.getByTestId('hourly-remaining').locator('strong').innerText(), '20');
    await page.getByRole('textbox', { name: 'API基础地址', exact: true }).fill('http://localhost:11435/v1');
    await page.getByRole('textbox', { name: '模型名称', exact: true }).fill('fixture-valid');
    const updated = page.waitForResponse(r => r.url().endsWith('/ai/planner/usage'));
    await page.getByRole('button', { name: '测试连接', exact: true }).click();
    check('call refreshes usage from real API', (await (await updated).json()).data.quota.hourly.remaining, 19);
    await page.waitForFunction(() => document.querySelector('[data-testid="hourly-remaining"] strong')?.textContent === '19');
    check('page shows success count', (await page.getByTestId('ai-usage').innerText()).includes('成功 1'), true);
    // Simulate only this temporary user's exhausted hour; never mutate shared global key.
    redis('SET', currentKeys()[0], 20, 'EX', 300); await refresh();
    await page.getByText('AI额度已用尽。到重置时间后点击刷新额度，可继续使用基础旅行推荐。', { exact: true }).waitFor();
    check('exhausted connection disabled', await page.getByRole('button', { name: '测试连接', exact: true }).isDisabled(), true);
    check('exhausted generation disabled', await page.getByRole('button', { name: '生成AI行程', exact: true }).isDisabled(), true);
    check('exhaustion still links basic recommendations', await page.getByRole('link', { name: '使用基础旅行推荐', exact: true }).isVisible(), true);
    await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: path.join(out, 'quota-exhausted-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    check('mobile no horizontal overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.evaluate(() => scrollTo(0, 0)); await page.screenshot({ path: path.join(out, 'quota-exhausted-mobile.png'), fullPage: true });
    redis('DEL', currentKeys()[0]); await refresh();
    await page.waitForFunction(() => document.querySelector('[data-testid="hourly-remaining"] strong')?.textContent === '20');
    check('refresh after simulated reset enables generation', await page.getByRole('button', { name: '生成AI行程', exact: true }).isEnabled(), true);
    check('daily use retained through simulated hourly reset', await page.getByTestId('daily-remaining').locator('strong').innerText(), '199');
    check('no browser errors', errors.length, 0);
  } catch (e) { check('browser completed', e.message, 'no error'); process.exitCode = 1; }
  finally {
    if (browser) await browser.close();
    if (uid) {
      currentKeys(); redis('DEL', ...keys);
      sql(`DELETE FROM llm_call_log WHERE user_id=${uid}; DELETE FROM user_preference WHERE user_id=${uid}; DELETE FROM sys_user WHERE id=${uid}`);
      check('fixture removed', sql(`SELECT COUNT(*) FROM sys_user WHERE id=${uid}`), '0');
    }
    save(); console.log(`${checks.filter(x => x.passed).length}/${checks.length} passed; ${out}`);
    if (checks.some(x => !x.passed) || errors.length) process.exitCode = 1;
  }
})();
