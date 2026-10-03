// Workbench acceptance: real browser, local educational interactions, no business API calls.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.WORKBENCH_URL || 'http://127.0.0.1:5173';
const out = path.resolve(__dirname, '../../docs/dev/evidence/workbench');
fs.mkdirSync(out, { recursive: true });
const checks = [], errors = [], apiCalls = [];
let browser;
function check(name, actual, expected = true) {
  const passed = JSON.stringify(actual) === JSON.stringify(expected);
  checks.push({ name, actual, expected, passed });
  if (!passed) throw Error(name + ': ' + JSON.stringify(actual));
}
(async () => {
  try {
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.on('pageerror', e => errors.push(e.message));
    page.on('request', r => { if (new URL(r.url()).pathname.startsWith('/api/')) apiCalls.push(r.url()); });
    await page.goto(base + '/workbench');
    await page.getByRole('heading', { name: '一张图，读懂 Trip-AI' }).waitFor();
    check('public entry without login', new URL(page.url()).pathname, '/workbench');
    await page.locator('[data-node="mapper"]').click();
    check('node inspector follows selection', await page.locator('.inspector h3').innerText(), 'Mapper / JDBC');
    await page.locator('.inspector summary').click();
    check('mapper code can be read', (await page.locator('.inspector pre').innerText()).includes('BaseMapper<Route>'));
    await page.locator('[data-node="service"]').click();
    await page.screenshot({ path: path.join(out, 'overview-desktop.png'), fullPage: true });
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: '导出导览', exact: true }).click();
    const download = await downloadPromise;
    await download.saveAs(path.join(out, 'exported-guide.md'));
    check('guide export contains architecture and boundary', fs.readFileSync(path.join(out, 'exported-guide.md'), 'utf8').includes('设计待实现'));
    await page.locator('nav').getByRole('button', { name: /Spring 装配/ }).click();
    await page.getByLabel('Bean 注册情况').selectOption('missing');
    check('missing Bean diagnosis', (await page.locator('.experiment-result').innerText()).includes('找不到 RouteService Bean'));
    await page.getByLabel('Bean 注册情况').selectOption('ambiguous');
    check('ambiguous Bean remedy', (await page.locator('.experiment-result').innerText()).includes('@Qualifier'));
    await page.locator('.step-tabs').getByRole('button', { name: /注入依赖/ }).click();
    check('constructor lesson', (await page.locator('.lesson-code pre').innerText()).includes('public RouteController(RouteService routeService)'));
    await page.screenshot({ path: path.join(out, 'spring-desktop.png'), fullPage: true });
    await page.locator('nav').getByRole('button', { name: /请求链路/ }).click();
    for (const scenario of ['保存规划', 'AI 定制行程', '本地资料检索', '浏览路线']) {
      await page.getByRole('button', { name: scenario, exact: true }).click();
      for (let i = 0; i < 5; i++) await page.getByRole('button', { name: '下一步', exact: true }).click();
      check(scenario + ' final step reached', await page.locator('.active-step .eyebrow').innerText(), 'STEP 06');
      await page.getByRole('button', { name: '重新回放', exact: true }).click();
      check(scenario + ' replay resets', await page.locator('.active-step .eyebrow').innerText(), 'STEP 01');
    }
    await page.locator('nav').getByRole('button', { name: /前端协作/ }).click();
    for (const state of ['loading', 'empty', 'error', 'success']) {
      await page.getByLabel('切换页面状态').selectOption(state);
      check('frontend state ' + state, await page.locator('.state-indicator').getAttribute('class'), 'state-indicator ' + state);
    }
    await page.locator('nav').getByRole('button', { name: /接口实验/ }).click();
    await page.getByLabel('size 每页条数').fill('200');
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('route size capped to 100', JSON.parse(await page.locator('.response-code').innerText()).data.size, 100);
    await page.getByLabel('keyword 关键词').fill('没有这条路线');
    check('changing params clears stale result', (await page.locator('.response-code').innerText()).includes('点击'));
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('empty result', JSON.parse(await page.locator('.response-code').innerText()).data.records.length, 0);
    await page.getByLabel('接口场景').selectOption('plan');
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('unauthenticated plan rejected', JSON.parse(await page.locator('.response-code').innerText()).code, 401);
    await page.getByLabel('请求身份').selectOption('user');
    await page.getByLabel('模拟缺少标题').check();
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('invalid plan rejected', JSON.parse(await page.locator('.response-code').innerText()).code, 400);
    await page.getByLabel('模拟缺少标题').uncheck();
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('valid simulated plan', JSON.parse(await page.locator('.response-code').innerText()).data, 1001);
    await page.getByLabel('接口场景').selectOption('admin');
    check('admin URL matches controller', (await page.locator('.lab-code pre').first().innerText()).includes('/api/admin/user/page'));
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('non admin rejected', JSON.parse(await page.locator('.response-code').innerText()).code, 403);
    await page.getByLabel('请求身份').selectOption('admin');
    await page.getByRole('button', { name: '运行教学请求' }).click();
    check('admin allowed', JSON.parse(await page.locator('.response-code').innerText()).code, 200);
    await page.screenshot({ path: path.join(out, 'api-desktop.png'), fullPage: true });
    await page.locator('nav').getByRole('button', { name: /数据关系/ }).click();
    await page.getByLabel('模拟明细写入失败').check();
    check('transaction rollback explained', (await page.locator('.transaction-outcome').innerText()).includes('全部回滚'));
    await page.getByLabel('模拟明细写入失败').uncheck();
    check('transaction commit explained', (await page.locator('.transaction-outcome').innerText()).includes('整体提交'));
    await page.locator('.quiz-options button').nth(0).click();
    check('quiz correct answer visible', await page.locator('.quiz-options button.correct').count(), 1);
    await page.getByRole('button', { name: '下一题', exact: true }).click();
    check('next question resets feedback', await page.locator('.quiz-feedback').count(), 0);
    await page.reload();
    check('selected view survives reload', await page.locator('nav button[aria-current="page"] strong').innerText(), '数据关系');
    for (const width of [1440, 1024, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      for (const view of ['overview', 'spring', 'request', 'frontend', 'api', 'data']) {
        await page.goto(base + '/workbench?view=' + view);
        await page.locator('h1').waitFor();
        check(view + ' no horizontal overflow at ' + width, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      }
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(base + '/workbench?view=spring');
    await page.locator('.relation-list').waitFor({ state: 'visible' });
    check('mobile shows explicit dependency relations', await page.locator('.relation-list li').count(), 3);
    await page.screenshot({ path: path.join(out, 'spring-mobile.png'), fullPage: true });
    check('no business API calls', apiCalls.length, 0);
    // Verify the new navigation entry in the real shared header, separately from offline workbench checks.
    page.removeAllListeners('request');
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(base + '/routes');
    await page.locator('#main-navigation').waitFor({ state: 'attached' });
    check('shared navigation has one workbench entry', await page.locator('#main-navigation').getByRole('link', { name: '学习工作台', exact: true }).count(), 1);
    for (const width of [1440, 1024, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      check('existing route page header fits at ' + width, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('button', { name: '导航菜单', exact: true }).click();
    const entry = page.locator('#main-navigation').getByRole('link', { name: '学习工作台', exact: true });
    check('mobile navigation exposes workbench', await entry.isVisible());
    await entry.click();
    await page.waitForURL(u => u.pathname === '/workbench');
    check('shared navigation opens workbench', new URL(page.url()).pathname, '/workbench');
    check('no unhandled browser errors', errors.length, 0);
  } catch (e) {
    checks.push({ name: 'browser acceptance completed', passed: false, error: e.message });
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    fs.writeFileSync(path.join(out, 'results.json'), JSON.stringify({ mode: 'REAL_EDGE_LOCAL_EDUCATIONAL_UI', checks, errors, apiCalls, summary: { total: checks.length, passed: checks.filter(c => c.passed).length, failed: checks.filter(c => !c.passed).length } }, null, 2));
    console.log(`${checks.filter(c => c.passed).length}/${checks.length} passed; ${out}`);
    if (checks.some(c => !c.passed)) process.exitCode = 1;
  }
})();
