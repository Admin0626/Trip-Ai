// Real Edge + application + MySQL/Redis; no API mocking and no token snapshots.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '../..'), base = 'http://127.0.0.1:5173';
const out = path.join(root, 'docs/dev/evidence/avatar', new Date().toISOString().replace(/[:.]/g, '-') + '-browser');
const checks = [], errors = [], uploads = new Set();
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFElEQVR4nGOUqzjBAAZMEIqBgQEAGC4BYoYfTS8AAAAASUVORK5CYII=', 'base64');
const username = 'avui_' + crypto.randomUUID().replaceAll('-', '').slice(0, 12);
let browser, uid, registered = false;
fs.mkdirSync(out, { recursive: true });
function save() { fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_EDGE_AVATAR_HTTP_MYSQL_REDIS', checks, errors }, null, 2)); }
function check(name, actual, expected) { checks.push({ name, actual, expected, passed: actual === expected }); save(); }
function sql(query) { return execFileSync('mysql', ['-u', 'root', '-N', '-B', '--default-character-set=utf8mb4', 'trip_llm', '-e', query], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '123456' } }).trim(); }
function redis(...args) { return execFileSync('redis-cli', args, { encoding: 'utf8' }).trim(); }
async function run() {
  try {
    sql('SELECT 1'); redis('PING');
    const data = await (await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: 'AvatarUi123', nickname: 'Avatar UI' }) })).json();
    check('temporary user registered', data.code, 200);
    if (data.code !== 200) throw new Error('Registration failed');
    registered = true;
    uid = Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.on('pageerror', e => { errors.push(e.message); save(); });
    await page.goto(base + '/login?redirect=/user/profile');
    await page.getByPlaceholder('user1001').fill(username);
    await page.getByPlaceholder('123456', { exact: true }).fill('AvatarUi123');
    await page.getByRole('button', { name: '登 录', exact: true }).click();
    await page.waitForURL(url => url.pathname === '/user/profile');
    await page.waitForFunction(() => document.querySelector('input[aria-label="昵称"]')?.value === 'Avatar UI');
    check('profile form loaded', await page.getByLabel('昵称', { exact: true }).inputValue(), 'Avatar UI');
    const uploaded = page.waitForResponse(r => r.url().endsWith('/file/upload') && r.request().method() === 'POST');
    await page.getByLabel('选择头像').setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: png });
    const result = await (await uploaded).json();
    check('browser uploads PNG', result.code, 200);
    if (result.code !== 200 || !/^[a-f0-9]{32}\.png$/.test(result.data.name)) throw new Error('Upload failed or filename invalid');
    uploads.add(result.data.name);
    const avatar = result.data.url;
    await page.waitForFunction(url => document.querySelector('.avatar-edit img')?.getAttribute('src') === url, avatar);
    check('uploaded preview decodes', await page.locator('.avatar-edit img').evaluate(img => img.complete && img.naturalWidth > 0), true);
    const saved = page.waitForResponse(r => r.url().endsWith('/user/profile') && r.request().method() === 'PUT');
    await page.getByRole('button', { name: '保存资料', exact: true }).click();
    const savedData = await (await saved).json();
    check('profile save succeeds', savedData.code, 200);
    check('save response has current avatar', savedData.data.avatar, avatar);
    await page.waitForFunction(url => document.querySelector('.user-chip img')?.getAttribute('src') === url, avatar);
    check('header shows saved avatar', await page.locator('.user-chip img').getAttribute('src'), avatar);
    check('database contains saved avatar', sql(`SELECT avatar FROM sys_user WHERE id=${uid}`), avatar);
    check('local profile stores saved avatar', await page.evaluate(() => JSON.parse(localStorage.getItem('trip_userInfo')).avatar), avatar);
    await page.reload();
    await page.waitForFunction(url => {
      const image = document.querySelector('.avatar-edit img');
      return image?.getAttribute('src') === url && image.complete && image.naturalWidth > 0;
    }, avatar);
    check('reload keeps visible avatar', await page.locator('.avatar-edit img').getAttribute('src'), avatar);
    await page.screenshot({ path: path.join(out, 'profile-desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    check('mobile has no horizontal overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    check('mobile upload control visible', await page.getByLabel('选择头像').isVisible(), true);
    await page.screenshot({ path: path.join(out, 'profile-mobile.png'), fullPage: true });
    check('no page errors', errors.length, 0);
  } catch (error) {
    check('browser run completed', error.message, 'no error');
  } finally {
    if (browser) await browser.close();
    function cleanup(name, action) { try { action(); check(name, true, true); } catch (e) { check(name, e.name, true); } }
    if (registered) cleanup('temporary user removed', () => {
      if (!uid) uid = Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));
      if (!Number.isSafeInteger(uid) || uid <= 0) throw new Error('Invalid temporary ID');
      sql(`DELETE FROM sys_user WHERE id=${uid} AND username='${username}'`);
      if (sql(`SELECT COUNT(*) FROM sys_user WHERE id=${uid}`) !== '0') throw new Error('User remains');
    });
    if (uid) cleanup('temporary sessions removed', () => {
      const prefix = `trip:auth:session:{${uid}}:`;
      for (const key of redis('--scan', '--pattern', prefix + '*').split(/\r?\n/).filter(Boolean)) {
        if (!key.startsWith(prefix)) throw new Error('Unexpected session key');
        redis('DEL', key);
      }
      if (redis('--scan', '--pattern', prefix + '*')) throw new Error('Sessions remain');
    });
    const directory = path.resolve(root, 'trip-server/uploads');
    for (const name of uploads) cleanup('uploaded fixture removed', () => {
      const target = path.resolve(directory, name);
      if (path.dirname(target) !== directory || !fs.readFileSync(target).equals(png)) throw new Error('Cleanup file mismatch');
      fs.unlinkSync(target);
    });
    save();
    console.log(`${checks.filter(c => c.passed).length}/${checks.length} passed; ${out}`);
    if (checks.some(c => !c.passed) || errors.length) process.exitCode = 1;
  }
}
run();
