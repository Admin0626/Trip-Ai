// Real application and database, controlled model protocol fixture on port 11435.
// Start: python trip-server/scripts/user_planner_acceptance.py --serve
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs'), path = require('node:path');
const { execFileSync } = require('node:child_process');
const {cleanSessions,cleanModelState}=require('./redis_fixture.cjs');
const base = 'http://127.0.0.1:5173';
const out = path.resolve(__dirname, '../../docs/dev/evidence/user-planner', new Date().toISOString().replace(/[:.]/g, '-') + '-browser');
fs.mkdirSync(out, { recursive: true });
const checks = [], errors = [], users = [];
let browser, page;
const password = 'PlannerUi123456';
function sql(q) { return execFileSync('mysql', ['-u', 'root', '-N', '-B', '--default-character-set=utf8mb4', 'trip_llm', '-e', q], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '123456' } }).trim(); }
function save() { fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_BROWSER_WITH_CONTROLLED_PROVIDER_NOT_REAL_LLM', checks, errors }, null, 2)); }
function check(name, actual, expected) { checks.push({ name, actual, expected, passed: JSON.stringify(actual) === JSON.stringify(expected) }); save(); }
async function login(user) {
  await page.goto(base + '/login?redirect=/ai-planner');
  await page.getByPlaceholder('user1001').fill(user.name);
  await page.getByPlaceholder('123456', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登 录', exact: true }).click();
  await page.waitForURL(url => url.pathname === '/ai-planner');
  await page.getByText(/可信公网主机：api/).waitFor();
}
async function clickRequest(name, endpoint) {
  const response = page.waitForResponse(r => r.url().endsWith(endpoint) && r.request().method() === 'POST');
  await page.getByRole('button', { name, exact: true }).click();
  return (await response).json();
}
(async () => {
  try {
    sql('SELECT 1');
    for (const suffix of ['a', 'b']) {
      const name = 'ap_' + Date.now() + suffix;
      const registered = await (await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: name, password, nickname: 'AI验收' + suffix }) })).json();
      if (registered.code !== 200) throw new Error('fixture registration failed');
      users.push({ name, id: Number(sql(`SELECT id FROM sys_user WHERE username='${name}'`)) });
    }
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.on('pageerror', e => { errors.push(e.message); save(); });
    await login(users[0]);
    await page.getByRole('textbox', { name: 'API基础地址', exact: true }).fill('http://localhost:11435/v1');
    await page.getByRole('textbox', { name: '模型名称', exact: true }).fill('fixture-valid');
    await page.getByLabel('API Key', { exact: true }).fill('fixture-key-not-a-real-secret');
    check('key masked', await page.getByLabel('API Key', { exact: true }).getAttribute('type'), 'password');
    await page.getByRole('button', { name: '记住地址与模型', exact: true }).click();
    const saved = await page.evaluate(id => JSON.parse(localStorage.getItem('trip_ai_connection_' + id)), users[0].id);
    check('only endpoint and model remembered', Object.keys(saved).sort(), ['baseUrl', 'model']);
    check('key absent from persistent storage', await page.evaluate(() => JSON.stringify(localStorage).includes('fixture-key-not-a-real-secret') || JSON.stringify(sessionStorage).includes('fixture-key-not-a-real-secret')), false);
    await page.getByRole('textbox', { name: 'API基础地址', exact: true }).fill('http://localhost:11435/v1?api_key=synthetic-url-key');
    await page.getByRole('button', { name: '记住地址与模型', exact: true }).click();
    check('credential query cannot be remembered', await page.evaluate(() => JSON.stringify(localStorage).includes('synthetic-url-key')), false);
    await page.getByRole('textbox', { name: 'API基础地址', exact: true }).fill('http://localhost:11435/v1');
    check('connection test uses actual compatible fixture', (await clickRequest('测试连接', '/ai/planner/test')).data.connected, true);
    await page.getByRole('textbox', { name: 'AI旅行需求', exact: true }).fill('去大理旅行，喜欢美食和自然风光');
    await page.getByRole('spinbutton', { name: 'AI天数', exact: true }).fill('2');
    await page.getByRole('spinbutton', { name: 'AI天数', exact: true }).press('Tab');
    const result = await clickRequest('生成AI行程', '/ai/planner/generate');
    check('generate succeeds', result.code, 200);
    await page.getByTestId('ai-preview').waitFor();
    check('preview shows two days', await page.locator('.preview .day').count(), 2);
    check('preview not saved automatically', sql(`SELECT COUNT(*) FROM user_plan WHERE user_id=${users[0].id}`), '0');
    check('save disabled until acknowledgement', await page.getByRole('button', { name: '保存到我的规划并编辑', exact: true }).isDisabled(), true);
    // Explicitly mask the password field in evidence, including the synthetic fixture key.
    await page.waitForFunction(() => document.querySelectorAll('.el-message').length === 0);
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: path.join(out, 'desktop.png'), fullPage: true, mask: [page.getByLabel('API Key', { exact: true })] });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.evaluate(() => scrollTo(0, 0));
    check('mobile no horizontal overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(out, 'mobile.png'), fullPage: true, mask: [page.getByLabel('API Key', { exact: true })] });
    await page.getByText('我已查看行程，保存为可编辑草稿', { exact: true }).click();
    check('acknowledgement enables saving', await page.getByRole('button', { name: '保存到我的规划并编辑', exact: true }).isEnabled(), true);
    await page.getByRole('button', { name: '保存到我的规划并编辑', exact: true }).click();
    await page.waitForURL(url => /^\/plan\/\d+\/edit$/.test(url.pathname));
    check('saved as one editable draft', sql(`SELECT COUNT(*) FROM user_plan WHERE user_id=${users[0].id} AND status=0`), '1');
    await page.getByPlaceholder('例如：我的云南 5 日自由行').waitFor();
    await page.waitForFunction(expected => document.querySelector('input[placeholder="例如：我的云南 5 日自由行"]')?.value === expected, result.data.draft.title);
    check('plan editor receives generated title', await page.getByPlaceholder('例如：我的云南 5 日自由行').inputValue(), result.data.draft.title);
    await page.goto(base + '/ai-planner');
    await page.getByText(/可信公网主机：api/).waitFor();
    check('endpoint remembered for same user', await page.getByRole('textbox', { name: 'API基础地址', exact: true }).inputValue(), 'http://localhost:11435/v1');
    check('key cleared after leaving page', await page.getByLabel('API Key', { exact: true }).inputValue(), '');
    await page.reload();
    check('key absent after refresh', await page.getByLabel('API Key', { exact: true }).inputValue(), '');
    await page.getByRole('textbox', { name: '模型名称', exact: true }).fill('fixture-auth');
    const failed = await clickRequest('测试连接', '/ai/planner/test');
    check('authentication failure shown', failed.code, 3004);
    check('upstream response secret not displayed', (await page.locator('body').innerText()).includes('fixture-key-not-a-real-secret'), false);
    await login(users[1]);
    check('second user does not inherit endpoint', await page.getByRole('textbox', { name: 'API基础地址', exact: true }).inputValue(), '');
    check('second user does not inherit model', await page.getByRole('textbox', { name: '模型名称', exact: true }).inputValue(), '');
    check('no unhandled page errors', errors.length, 0);
  } catch (e) { check('browser completed', e.message, 'no error'); process.exitCode = 1; }
  finally {
    if (browser) await browser.close();
    for (const user of users) {
      check('fixture sessions cleaned '+user.id,cleanSessions(user.id)>=1,true);
      check('fixture model state cleaned '+user.id,cleanModelState(user.id)>=0,true);
      sql(`DELETE i FROM user_plan_item i JOIN user_plan_day d ON d.id=i.plan_day_id JOIN user_plan p ON p.id=d.user_plan_id WHERE p.user_id=${user.id}; DELETE d FROM user_plan_day d JOIN user_plan p ON p.id=d.user_plan_id WHERE p.user_id=${user.id}; DELETE FROM user_plan WHERE user_id=${user.id}; DELETE FROM llm_call_log WHERE user_id=${user.id}; DELETE FROM user_preference WHERE user_id=${user.id}; DELETE FROM sys_user WHERE id=${user.id}`);
    }
    check('both fixture accounts removed', users.every(u => sql(`SELECT COUNT(*) FROM sys_user WHERE id=${u.id}`) === '0'), true);
    save(); console.log(`${checks.filter(x => x.passed).length}/${checks.length} passed; ${out}`);
    if (checks.some(x => !x.passed) || errors.length) process.exitCode = 1;
  }
})();
