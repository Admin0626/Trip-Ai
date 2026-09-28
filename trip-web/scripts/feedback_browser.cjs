// Real application and Edge; fixtures are scoped to generated accounts only.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '../..'), base = 'http://127.0.0.1:5173';
const label = process.argv.includes('--final') ? 'final' : 'baseline';
const out = path.join(root, 'docs/dev/evidence/feedback', new Date().toISOString().replace(/[:.]/g, '-') + '-' + label + '-browser');
const checks = [], errors = [], users = [], files = new Set();
const marker = 'Feedback-' + crypto.randomUUID().slice(0, 8), password = 'FeedbackUi123';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAFElEQVR4nGOUqzjBAAZMEIqBgQEAGC4BYoYfTS8AAAAASUVORK5CYII=', 'base64');
let browser, diagnosticPage;
const diagnostics = { stage: 'setup', adminRequests: [] };
fs.mkdirSync(out, { recursive: true });
function save() { fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_EDGE_FEEDBACK_ADMIN_USER', label, fixtures: { users, uploads: [...files] }, diagnostics, checks, errors }, null, 2)); }
function check(name, actual, expected) { checks.push({ name, actual, expected, passed: actual === expected }); save(); }
function sql(q) { return execFileSync('mysql', ['-u', 'root', '-N', '-B', 'trip_llm', '-e', q], { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: process.env.MYSQL_PASSWORD || '123456' } }).trim(); }
function redis(...args) { return execFileSync('redis-cli', args, { encoding: 'utf8' }).trim(); }
async function createUser(admin) {
  const name = 'fbui_' + crypto.randomUUID().replaceAll('-', '').slice(0, 10);
  const result = await (await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: name, password, nickname: admin ? 'Feedback Admin' : 'Feedback User' }) })).json();
  if (result.code !== 200) throw new Error('Fixture registration failed');
  const user = { name }; users.push(user); save();
  user.id = Number(sql(`SELECT id FROM sys_user WHERE username='${name}'`));
  save();
  if (admin) sql(`UPDATE sys_user SET role='ADMIN' WHERE id=${user.id} AND username='${name}'`);
  return user;
}
function watch(page) { page.setDefaultTimeout(10000); page.on('pageerror', e => { errors.push(e.message); save(); }); }
async function login(page, user, route) {
  await page.goto(base + '/login?redirect=' + route);
  await page.getByPlaceholder('user1001').fill(user.name);
  await page.getByPlaceholder('123456', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登 录', exact: true }).click();
  await page.waitForURL(url => url.pathname === route);
}
async function reply(page) {
  const response = page.waitForResponse(r => /\/api\/admin\/feedback\/\d+\/reply$/.test(r.url()) && r.request().method() === 'PUT').then(r => r.json());
  const [data] = await Promise.all([response, page.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()]);
  return data;
}
async function filter(page, text) {
  diagnostics.stage = 'filter ' + text; save();
  const response = page.waitForResponse(r => r.url().includes('/api/admin/feedback/page'));
  await Promise.all([response, (async () => {
    await page.bringToFront();
    const combo = page.getByRole('combobox', { name: '筛选反馈状态', exact: true });
    await combo.focus();
    await combo.press('ArrowDown');
    diagnostics.expanded = await combo.getAttribute('aria-expanded'); save();
    const popup = page.locator('#' + await combo.getAttribute('aria-controls'));
    await popup.getByRole('option', { name: text, exact: true }).click();
    diagnostics.stage = 'selected ' + text; save();
  })()]);
  await page.locator('.feedback-pagination').waitFor();
}
async function run() {
  try {
    sql('SELECT 1'); redis('PING');
    const user = await createUser(false), admin = await createUser(true);
    // Seed only this user's older records to exercise both real pagination APIs.
    for (let i = 0; i < 21; i++) sql(`INSERT INTO feedback(user_id,type,title,content) VALUES(${user.id},'OTHER','${marker}-older-${i}','Pagination fixture')`);
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const uc = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const ac = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const userPage = await uc.newPage(), adminPage = await ac.newPage(); watch(userPage); watch(adminPage);
    diagnosticPage = adminPage;
    adminPage.on('request', request => { if (request.url().includes('/api/admin/feedback/page')) { diagnostics.adminRequests.push(new URL(request.url()).pathname + new URL(request.url()).search); save(); } });
    await login(userPage, user, '/user/feedback');
    await userPage.getByRole('button', { name: '提交反馈', exact: true }).click();
    await userPage.getByText('请填写标题和具体内容', { exact: true }).waitFor();
    check('empty form gives explanation', true, true);
    await userPage.getByLabel('反馈标题', { exact: true }).fill(marker);
    await userPage.getByLabel('反馈内容', { exact: true }).fill('Screenshot issue details <script>alert(1)</script>');
    await userPage.getByLabel('反馈联系方式', { exact: true }).fill('fixture@example.com');
    const uploading = userPage.waitForResponse(r => r.url().endsWith('/api/file/upload')).then(r => r.json());
    await userPage.getByLabel('上传图片', { exact: true }).setInputFiles({ name: 'feedback.png', mimeType: 'image/png', buffer: png });
    const uploaded = await uploading;
    check('feedback screenshot uploaded', uploaded.code, 200);
    if (!/^[a-f0-9]{32}\.png$/.test(uploaded.data.name)) throw new Error('Invalid fixture filename');
    files.add(uploaded.data.name);
    save();
    await userPage.locator('.image-uploader img').waitFor();
    const submitted = userPage.waitForResponse(r => new URL(r.url()).pathname === '/api/feedback' && r.request().method() === 'POST').then(r => r.json());
    await userPage.getByRole('button', { name: '提交反馈', exact: true }).click();
    const created = await submitted;
    check('feedback submitted from page', created.code, 200);
    const record = userPage.locator('article').filter({ hasText: marker }).first();
    await record.getByRole('heading', { name: marker, exact: true }).waitFor();
    check('user sees submitted screenshot', await record.locator('.el-image img').count(), 1);
    await login(adminPage, admin, '/admin');
    const row = adminPage.locator('.el-table__row').filter({ hasText: marker }).first();
    await row.waitFor();
    check('admin pagination available', await adminPage.locator('.feedback-pagination').count(), 1);
    await row.getByRole('button', { name: '处理', exact: true }).click();
    const dialog = adminPage.getByRole('dialog');
    check('pending dialog defaults to processing', await dialog.getByText('处理中', { exact: true }).isVisible(), true);
    check('admin sees contact', await dialog.getByText('fixture@example.com', { exact: false }).isVisible(), true);
    check('admin sees attachment', await dialog.locator('.el-image img').count(), 1);
    check('script text is displayed literally', (await dialog.innerText()).includes('<script>alert(1)</script>'), true);
    await dialog.getByLabel('处理回复', { exact: true }).fill('We are investigating');
    // A second real admin tab keeps a stale pending snapshot.
    const stale = await ac.newPage(); watch(stale);
    await stale.goto(base + '/admin');
    await stale.locator('.el-table__row').filter({ hasText: marker }).first().getByRole('button', { name: '处理', exact: true }).click();
    await stale.getByLabel('处理回复', { exact: true }).fill('Stale administrator reply');
    const processing = await reply(adminPage);
    check('default processing reply succeeds', processing.code, 200);
    if (processing.code !== 200) throw new Error('Cannot continue after pending reply rejection');
    await adminPage.getByRole('dialog').waitFor({ state: 'hidden' });
    check('stale tab receives conflict', (await reply(stale)).code, 409);
    check('conflict keeps typed reply', await stale.getByLabel('处理回复', { exact: true }).inputValue(), 'Stale administrator reply');
    await stale.close();
    await userPage.reload();
    await userPage.getByText('We are investigating', { exact: true }).waitFor();
    check('owner sees processing reply', true, true);
    await filter(adminPage, '处理中');
    await adminPage.locator('.el-table__row').filter({ hasText: marker }).getByRole('button', { name: '处理', exact: true }).click();
    check('processing dialog defaults to solved', await dialog.getByText('已解决', { exact: true }).isVisible(), true);
    await dialog.getByLabel('处理回复', { exact: true }).fill('Fixed and verified');
    check('admin resolves feedback', (await reply(adminPage)).code, 200);
    await dialog.waitFor({ state: 'hidden' });
    await filter(adminPage, '已解决');
    await adminPage.locator('.el-table__row').filter({ hasText: marker }).getByRole('button', { name: '查看', exact: true }).click();
    check('terminal dialog has no save button', await dialog.getByRole('button', { name: '保存', exact: true }).count(), 0);
    check('terminal reply read only', await dialog.getByLabel('处理回复', { exact: true }).isDisabled(), true);
    await adminPage.setViewportSize({ width: 390, height: 844 });
    // Element Plus puts role=dialog on the full-screen overlay wrapper.
    // Measure/hit-test the visible panel rather than the overlay's top edge.
    const panel = dialog.locator('.el-dialog');
    const bounds = await panel.boundingBox();
    check('mobile dialog fits viewport', bounds.x >= 0 && bounds.x + bounds.width <= 390, true);
    await adminPage.screenshot({ path: path.join(out, 'admin-reply-mobile.png'), animations: 'disabled' });
    check('mobile dialog is above page content', await panel.evaluate(element => {
      const box = element.getBoundingClientRect();
      return element.contains(document.elementFromPoint(box.x + box.width / 2, box.y + 40));
    }), true);
    await dialog.getByRole('button', { name: '关闭', exact: true }).click();
    await filter(adminPage, '全部状态');
    const next = adminPage.waitForResponse(r => r.url().includes('/api/admin/feedback/page') && r.url().includes('current=2'));
    await adminPage.locator('.feedback-pagination .btn-next').click();
    check('admin second page fetched', (await (await next).json()).data.current, 2);
    await userPage.reload();
    await userPage.getByText('Fixed and verified', { exact: true }).waitFor();
    check('owner sees final reply', true, true);
    await userPage.setViewportSize({ width: 390, height: 844 });
    await userPage.evaluate(() => scrollTo(0, 0));
    check('user mobile has no horizontal overflow', await userPage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await userPage.screenshot({ path: path.join(out, 'user-feedback-mobile.png'), fullPage: true, animations: 'disabled' });
    const nextMine = userPage.waitForResponse(r => r.url().includes('/api/feedback/my/page') && r.url().includes('current=2'));
    await userPage.locator('.el-pagination .btn-next').click();
    check('user second page fetched', (await (await nextMine).json()).data.current, 2);
    await userPage.goto(base + '/admin');
    await userPage.waitForURL(url => url.pathname === '/');
    check('ordinary user route blocked', new URL(userPage.url()).pathname, '/');
    check('no page errors', errors.length, 0);
  } catch (error) {
    check('browser completed', error.message.replaceAll(password, '<redacted>'), 'no error');
    if (diagnosticPage && !diagnosticPage.isClosed()) {
      diagnostics.filterText = await diagnosticPage.locator('.feedback-tools').innerText().catch(() => 'unavailable');
      await diagnosticPage.screenshot({ path: path.join(out, 'failure.png'), mask: [diagnosticPage.locator('.el-table')] }).catch(() => {});
      save();
    }
  }
  finally {
    if (browser) await browser.close();
    for (const user of users) {
      try {
        const id = user.id || Number(sql(`SELECT id FROM sys_user WHERE username='${user.name}'`));
        if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Invalid fixture user');
        sql(`DELETE FROM feedback WHERE user_id=${id}; DELETE FROM sys_user WHERE id=${id} AND username='${user.name}'`);
        const {cleanSessions,sessionKeys}=require('./redis_fixture.cjs');
        cleanSessions(id);
        check('fixture rows removed', sql(`SELECT (SELECT COUNT(*) FROM sys_user WHERE id=${id})+(SELECT COUNT(*) FROM feedback WHERE user_id=${id})`), '0');
        check('fixture sessions removed', sessionKeys(id).length, 0);
      } catch (error) { check('fixture cleanup', error.name, 'no error'); }
    }
    const directory = path.resolve(root, 'trip-server/uploads');
    for (const file of files) {
      try {
        const target = path.resolve(directory, file);
        if (path.dirname(target) !== directory || !fs.readFileSync(target).equals(png)) throw new Error('Upload cleanup mismatch');
        fs.unlinkSync(target); check('uploaded image removed', true, true);
      } catch (error) { check('image cleanup', error.name, 'no error'); }
    }
    save(); console.log(`${checks.filter(c => c.passed).length}/${checks.length} passed; ${out}`);
    if (checks.some(c => !c.passed) || errors.length) process.exitCode = 1;
  }
}
run();
