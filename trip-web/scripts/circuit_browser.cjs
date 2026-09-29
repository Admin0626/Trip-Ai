// Real Edge, real HTTP backend8081 and controlled provider11436. No API payload mocks.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const crypto=require('node:crypto');
const {cleanSessions}=require('./redis_fixture.cjs');
const web='http://127.0.0.1:5173',api='http://127.0.0.1:8081/api',provider='http://127.0.0.1:11436';
const out=path.resolve(__dirname,'../../docs/dev/evidence/circuit',new Date().toISOString().replace(/[:.]/g,'-')+'-browser');
fs.mkdirSync(out,{recursive:true});
const checks=[],errors=[],responses=[],users=[];let browser,page,delayState=false;
const password='CircuitUi123',key='controlled-browser-key',model='circuit-ui-error-'+Date.now();
function sql(q){return execFileSync('mysql',['-u','root','-N','-B','trip_llm','-e',q],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.MYSQL_PASSWORD||'123456'}}).trim();}
function redis(...args){return execFileSync('redis-cli',args.map(String),{encoding:'utf8'}).trim();}
function scan(prefix){let cursor='0',keys=new Set();do{const rows=redis('SCAN',cursor,'MATCH',prefix+'*','COUNT',100).split(/\r?\n/);cursor=rows[0];for(const k of rows.slice(1).filter(Boolean)){if(!k.startsWith(prefix))throw Error('Unsafe key');keys.add(k);}}while(cursor!=='0');return [...keys];}
function save(){fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({mode:'REAL_EDGE_HTTP_8081_CONTROLLED_PROVIDER',checks,errors,responses,fixtures:users,summary:{total:checks.length,passed:checks.filter(c=>c.passed).length}},null,2));}
function check(name,actual,expected){checks.push({name,actual,expected,passed:JSON.stringify(actual)===JSON.stringify(expected)});save();}
async function control(mode){await fetch(provider+'/__control?model='+model+'&mode='+mode);}
async function counts(){return (await (await fetch(provider+'/__counts')).json())[model]||0;}
async function action(name,endpoint){const r=page.waitForResponse(r=>r.url().endsWith(endpoint)&&r.request().method()==='POST');await page.getByRole('button',{name,exact:true}).click();const result=await(await r).json();await page.waitForFunction(()=>!document.querySelector('.el-button.is-loading'));return result;}
(async()=>{try{
  const name='cb_'+Date.now();const reg=await(await fetch(api+'/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:name,password})})).json();
  if(reg.code!==200)throw Error('fixture registration failed');users.push({name,id:Number(sql(`SELECT id FROM sys_user WHERE username='${name}'`))});save();
  browser=await chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});
  // Relay actual network replies to the existing dev UI; shortened timings are isolated from8080.
  await context.route(web+'/api/**',async route=>{
    const r=await route.fetch({url:route.request().url().replace(web+'/api',api)});
    const pathname=new URL(route.request().url()).pathname;
    if(pathname.startsWith('/api/ai/planner/')){const body=await r.json();responses.push({path:pathname,status:r.status(),body});save();}
    if(delayState&&pathname.endsWith('/circuit')){delayState=false;await new Promise(resolve=>setTimeout(resolve,800));}
    await route.fulfill({response:r});
  });
  page=await context.newPage();page.on('pageerror',e=>{errors.push(e.message);save();});
  await page.goto(web+'/login?redirect=/ai-planner');await page.getByPlaceholder('user1001').fill(name);await page.getByPlaceholder('123456',{exact:true}).fill(password);
  await page.getByRole('button',{name:'登 录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/ai-planner');await page.getByText(/可信公网主机：api/).waitFor();
  await page.getByRole('textbox',{name:'API基础地址',exact:true}).fill(provider+'/v1');await page.getByRole('textbox',{name:'模型名称',exact:true}).fill(model);await page.getByLabel('API Key',{exact:true}).fill(key);
  const state=page.getByTestId('ai-circuit'),test=page.getByRole('button',{name:'测试连接',exact:true}),generate=page.getByRole('button',{name:'生成AI行程',exact:true});
  check('state refresh is free',(await action('刷新模型状态','/ai/planner/circuit')).code,200);check('empty state visible',(await state.innerText()).includes('当前模型可尝试调用'),true);check('state reads do not call provider',await counts(),0);
  for(let i=0;i<4;i++)check('real failed connection '+i,(await action('测试连接','/ai/planner/test')).code,3004);
  check('open state visible',(await state.innerText()).includes('模型服务因故障暂停'),true);check('open disables connection test',await test.isDisabled(),true);check('open disables generation',await generate.isDisabled(),true);
  check('rule fallback link present',await state.getByRole('link',{name:'改用基础旅行推荐'}).isVisible(),true);
  check('no preview fabricated',await page.getByTestId('ai-preview').count(),0);check('exactly four actual failures',await counts(),4);
  await page.waitForFunction(()=>document.querySelectorAll('.el-message').length===0);
  await page.screenshot({path:path.join(out,'open-desktop.png'),fullPage:true,mask:[page.getByLabel('API Key',{exact:true})]});
  await page.setViewportSize({width:390,height:844});check('phone has no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:path.join(out,'open-mobile.png'),fullPage:true,mask:[page.getByLabel('API Key',{exact:true})]});
  await control('ok');await test.waitFor({state:'visible'});await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='测试连接'&&!b.disabled),{},{timeout:10000});
  check('cooldown reenables real probe',await test.isEnabled(),true);check('actual half-open recovery',(await action('测试连接','/ai/planner/test')).code,200);check('closed state after recovery',(await state.innerText()).includes('当前模型可尝试调用'),true);
  await page.getByRole('textbox',{name:'AI旅行需求',exact:true}).fill('希望轻松体验当地美食，安排两天旅行');await page.getByRole('spinbutton',{name:'AI天数',exact:true}).fill('2');await page.getByRole('spinbutton',{name:'AI天数',exact:true}).press('Tab');
  await page.getByTestId('planner-progress-mode').click(); // This regression covers the retained ordinary endpoint.
  check('real generation after recovery',(await action('生成AI行程','/ai/planner/generate')).code,200);await page.getByTestId('ai-preview').waitFor();check('two generated days',await page.locator('.preview .day').count(),2);
  check('no automatic save',sql(`SELECT COUNT(*) FROM user_plan WHERE user_id=${users[0].id}`),'0');
  // Buffer a real status response while configuration changes; old reply must be discarded.
  delayState=true;const pending=page.waitForRequest(r=>r.url().endsWith('/ai/planner/circuit'));await page.getByRole('button',{name:'刷新模型状态',exact:true}).click();await pending;
  await page.getByLabel('API Key',{exact:true}).fill('changed-controlled-key');await page.waitForTimeout(1100);
  check('changed connection discards stale state',(await state.innerText()).includes('点击刷新'),true);check('changing key clears old preview',await page.getByTestId('ai-preview').count(),0);
  check('new key has empty isolated circuit',(await action('刷新模型状态','/ai/planner/circuit')).data.total,0);
  // Another independent Redis client owns the real half-open lease; HTTP reads its state.
  await control('error');for(let i=0;i<4;i++)await action('测试连接','/ai/planner/test');
  await page.waitForTimeout(4100);
  const digest=crypto.createHash('sha256').update(provider+'/v1/chat/completions\0'+model+'\0changed-controlled-key').digest('hex');
  const circuitKeys=['state','events','failures'].map(k=>`trip:test:circuit:batch20260928:{${users[0].id}:${digest}}:${k}`);
  const lua=fs.readFileSync(path.resolve(__dirname,'../../trip-server/src/main/resources/redis/ai_circuit.lua'),'utf8');
  const lease=JSON.parse(redis('EVAL',lua,3,...circuitKeys,'acquire',crypto.randomUUID(),'','0',60000,4000,4,30,4000,136000));check('independent client owns probe',lease.allowed,true);
  await action('刷新模型状态','/ai/planner/circuit');check('half-open message visible',(await state.innerText()).includes('正在恢复探测'),true);
  check('half-open test disabled',await test.isDisabled(),true);check('half-open generation disabled',await generate.isDisabled(),true);
  await page.waitForTimeout(4100);await action('刷新模型状态','/ai/planner/circuit');check('expired probe shown as cooldown',(await state.innerText()).includes('因故障暂停'),true);
  await control('ok');await page.waitForTimeout(4100);check('UI recovers after expired probe',(await action('测试连接','/ai/planner/test')).code,200);
  await page.getByRole('button',{name:'记住地址与模型',exact:true}).click();check('Key never persisted',await page.evaluate(()=>JSON.stringify(localStorage).includes('controlled-key')||JSON.stringify(sessionStorage).includes('controlled-key')),false);
  await page.goto(web+'/recommend');await page.getByRole('heading',{name:'旅行推荐',exact:true}).waitFor();check('basic recommendation remains accessible',page.url().endsWith('/recommend'),true);
  await page.goto(web+'/ai-planner');await page.getByLabel('API Key',{exact:true}).waitFor();check('key cleared on navigation',await page.getByLabel('API Key',{exact:true}).inputValue(),'');
  check('no unhandled page errors',errors.length,0);
}catch(e){check('browser completes',e.message,'no error');process.exitCode=1;}
finally{if(browser)await browser.close();for(const u of users){cleanSessions(u.id);for(const prefix of ['trip:test:circuit:batch20260928:{'+u.id+':','trip:test:quota:circuit20260928:{ai-quota}:user:'+u.id+':'])for(const k of scan(prefix))redis('DEL',k);
  sql(`DELETE FROM llm_call_log WHERE user_id=${u.id}; DELETE FROM user_preference WHERE user_id=${u.id}; DELETE FROM sys_user WHERE id=${u.id}`);check('owned fixtures cleaned',sql(`SELECT COUNT(*) FROM sys_user WHERE id=${u.id}`),'0');}
save();console.log(`${checks.filter(c=>c.passed).length}/${checks.length} passed; ${out}`);if(checks.some(c=>!c.passed)||errors.length)process.exitCode=1;}})();
