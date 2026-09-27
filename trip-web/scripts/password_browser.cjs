// Real headless Edge + application. Two contexts simulate independent devices.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const base = 'http://127.0.0.1:5173', label = process.argv.includes('--final') ? 'final' : 'baseline';
const out = path.resolve(__dirname, '../../docs/dev/evidence/password', new Date().toISOString().replace(/[:.]/g, '-') + '-' + label + '-browser');
const checks = [], errors = [];
const username = 'pwui_' + crypto.randomUUID().replaceAll('-', '').slice(0, 12);
const initial = 'InitialUi123', changed = 'ChangedUi456';
let browser, uid, registered = false;
fs.mkdirSync(out, { recursive: true });
function save() { fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_EDGE_PASSWORD_MULTI_DEVICE', label, checks, errors }, null, 2)); }
function check(name, actual, expected) { checks.push({ name, actual, expected, passed: actual === expected }); save(); }
function sql(query) { return execFileSync('mysql', ['-u', 'root', '-N', '-B', 'trip_llm', '-e', query], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '123456' } }).trim(); }
function redis(...args) { return execFileSync('redis-cli', args, { encoding: 'utf8' }).trim(); }
function watch(page, name) { page.on('pageerror', e => { errors.push({ page: name, message: e.message }); save(); }); }
async function login(page, password) {
  await page.goto(base + '/login?redirect=/user/profile');
  await page.getByPlaceholder('user1001').fill(username);
  await page.getByPlaceholder('123456', { exact: true }).fill(password);
  const response = page.waitForResponse(r => r.url().endsWith('/auth/login'));
  await page.getByRole('button', { name: '登 录', exact: true }).click();
  const data = await (await response).json();
  if (data.code === 200) {
    await page.waitForURL(url => url.pathname === '/user/profile');
    await page.waitForFunction(() => document.querySelector('input[aria-label="昵称"]')?.value === 'Password UI');
  }
  return data.code;
}
async function fillPasswords(page, old, fresh, confirmation = fresh) {
  await page.getByLabel('原密码', { exact: true }).fill(old);
  await page.getByLabel('新密码', { exact: true }).fill(fresh);
  await page.getByLabel('确认新密码', { exact: true }).fill(confirmation);
}
async function submitChange(page, readBody = true) {
  // Consume the body immediately: the current page can navigate after success.
  const response = page.waitForResponse(r => r.url().endsWith('/user/password') && r.request().method() === 'PUT')
    .then(r => readBody ? r.json() : { httpStatus: r.status() });
  const [data] = await Promise.all([response, page.getByRole('button', { name: '修改密码', exact: true }).click()]);
  return data;
}
const noSession = () => ['trip_token', 'trip_refreshToken', 'trip_userInfo'].every(key => !localStorage.getItem(key));
async function run() {
  try {
    sql('SELECT 1'); redis('PING');
    const registration = await (await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: initial, nickname: 'Password UI' }) })).json();
    check('temporary user registered', registration.code, 200);
    if (registration.code !== 200) throw new Error('Registration failed');
    registered = true; uid = Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const remoteContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await context.newPage(), remote = await remoteContext.newPage();
    page.setDefaultTimeout(10000); remote.setDefaultTimeout(10000);
    watch(page, 'changing device'); watch(remote, 'other device');
    check('first device login', await login(page, initial), 200);
    check('second device login', await login(remote, initial), 200);
    const tab = await context.newPage(); watch(tab, 'same browser tab');
    await tab.goto(base + '/user/profile');
    await tab.getByLabel('原密码', { exact: true }).waitFor();
    let changeRequests = 0;
    page.on('request', r => { if (r.url().endsWith('/user/password')) changeRequests++; });
    await fillPasswords(page, initial, changed, 'DifferentUi789');
    await page.getByRole('button', { name: '修改密码', exact: true }).click();
    await page.getByText('两次新密码不一致', { exact: true }).waitFor();
    check('mismatch blocked before HTTP request', changeRequests, 0);
    await fillPasswords(page, 'IncorrectUi123', changed);
    check('wrong old password API rejection', (await submitChange(page)).code, 400);
    await page.getByText('原密码不正确', { exact: true }).waitFor();
    check('failure remains on profile', new URL(page.url()).pathname, '/user/profile');
    check('failure keeps login', await page.evaluate(() => Boolean(localStorage.getItem('trip_token'))), true);
    await fillPasswords(page, initial, initial);
    check('same password API rejection', (await submitChange(page)).code, 400);
    await page.getByText('新密码不能与原密码相同', { exact: true }).waitFor();
    await page.setViewportSize({ width: 390, height: 844 });
    await fillPasswords(page, initial, changed);
    check('mobile no horizontal overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(out, 'password-mobile.png'), fullPage: true,
      mask: [page.getByLabel('原密码', { exact: true }), page.getByLabel('新密码', { exact: true }), page.getByLabel('确认新密码', { exact: true })] });
    check('password change HTTP response', (await submitChange(page, false)).httpStatus, 200);
    await page.waitForURL(url => url.pathname === '/login');
    await page.getByText('密码已修改，请重新登录', { exact: true }).waitFor();
    check('success explanation survives navigation', await page.getByText('密码已修改，请重新登录', { exact: true }).isVisible(), true);
    check('changing device clears all auth storage', await page.evaluate(noSession), true);
    await tab.waitForFunction(noSession);
    check('same browser tab observes cleared login', await tab.evaluate(noSession), true);
    await tab.goto(base + '/user/profile');
    await tab.waitForURL(url => url.pathname === '/login');
    check('same browser tab cannot reopen protected page', new URL(tab.url()).pathname, '/login');
    // Other browser has independent localStorage; its next real request must fail
    // and its automatic refresh must also be rejected by the application.
    const expired = remote.waitForResponse(r => new URL(r.url()).pathname === '/api/user/profile' && r.request().method() === 'GET');
    const refresh = remote.waitForResponse(r => r.url().endsWith('/auth/refresh'));
    await remote.reload();
    check('other device old access rejected', (await expired).status(), 401);
    check('other device refresh rejected', (await (await refresh).json()).code, 401);
    await remote.waitForURL(url => url.pathname === '/login');
    check('other device clears its storage', await remote.evaluate(noSession), true);
    check('old password login rejected', await login(page, initial), 1001);
    await page.getByText('用户名或密码错误', { exact: true }).waitFor();
    check('new password login succeeds', await login(page, changed), 200);
    check('new login opens protected profile', new URL(page.url()).pathname, '/user/profile');
    check('password fields empty after login', await page.getByLabel('原密码', { exact: true }).inputValue(), '');
    await page.screenshot({ path: path.join(out, 'profile-after-relogin.png'), fullPage: true });
    check('no unhandled browser errors', errors.length, 0);
  } catch (error) {
    check('browser run completed', error.message.replaceAll(initial, '<redacted>').replaceAll(changed, '<redacted>'), 'no error');
  } finally {
    if (browser) await browser.close();
    if (registered) {
      try {
        if (!uid) uid = Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));
        if (!Number.isSafeInteger(uid) || uid <= 0) throw new Error('Invalid temporary ID');
        sql(`DELETE FROM sys_user WHERE id=${uid} AND username='${username}'`);
        check('temporary user removed', sql(`SELECT COUNT(*) FROM sys_user WHERE id=${uid}`), '0');
        const prefix = `trip:auth:session:{${uid}}:`;
        for (const key of redis('--scan', '--pattern', prefix + '*').split(/\r?\n/).filter(Boolean)) {
          if (!key.startsWith(prefix)) throw new Error('Unexpected session key');
          redis('DEL', key);
        }
        check('temporary sessions removed', redis('--scan', '--pattern', prefix + '*'), '');
      } catch (error) { check('cleanup completed', error.name, 'no error'); }
    }
    save(); console.log(`${checks.filter(c => c.passed).length}/${checks.length} passed; ${out}`);
    if (checks.some(c => !c.passed) || errors.length) process.exitCode = 1;
  }
}
run();
