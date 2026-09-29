// Live Edge + Vite proxy + HTTP SSE. SSE responses are never intercepted/buffered.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const {cleanSessions,cleanModelState}=require('./redis_fixture.cjs');
const base='http://127.0.0.1:5173',provider='http://127.0.0.1:11437',modelSuffix='-'+Date.now();
const out=path.resolve(__dirname,'../../docs/dev/evidence/planner-sse',new Date().toISOString().replace(/[:.]/g,'-')+'-browser');
fs.mkdirSync(out,{recursive:true});let browser,page;const checks=[],errors=[],users=[],requests=[];
function save(){fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({mode:'REAL_EDGE_LIVE_HTTP_SSE_PLUS_SEPARATE_LOCAL_PARSER_TESTS',phase:finished?'COMPLETE':'RUNNING',fixtures:users,checks,errors,requests},null,2));}
let finished=false,lastContentType='';
function check(name,actual,expected){checks.push({name,actual,expected,passed:JSON.stringify(actual)===JSON.stringify(expected)});save();}
function sql(q){return execFileSync('mysql',['-u','root','-N','-B','--default-character-set=utf8mb4','trip_llm','-e',q],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.MYSQL_PASSWORD||'123456'}}).trim();}
async function control(model,mode){await fetch(provider+'/__control?model='+model+modelSuffix+'&mode='+mode);}
async function pair(user){return (await(await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:user.name,password:'SseUi123456'})})).json()).data;}
async function state(id,token){return (await(await fetch(base+'/api/ai/planner/requests/'+id,{headers:{Authorization:'Bearer '+token}})).json()).data;}
async function waitFor(fn,seconds=12){const end=Date.now()+seconds*1000;while(Date.now()<end){if(await fn())return true;await new Promise(r=>setTimeout(r,100));}return false;}
async function login(user){await page.goto(base+'/login?redirect=/ai-planner');await page.getByPlaceholder('user1001').fill(user.name);await page.getByPlaceholder('123456',{exact:true}).fill('SseUi123456');await page.getByRole('button',{name:'登 录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/ai-planner');await page.getByText(/可信公网主机：api/).waitFor();}
async function config(model){await page.getByRole('textbox',{name:'API基础地址',exact:true}).fill(provider+'/v1');await page.getByRole('textbox',{name:'模型名称',exact:true}).fill(model+modelSuffix);await page.getByLabel('API Key',{exact:true}).fill('synthetic-sse-ui-key');await page.getByRole('textbox',{name:'AI旅行需求',exact:true}).fill('希望轻松体验当地美食和自然风光');await page.getByRole('spinbutton',{name:'AI天数',exact:true}).fill('2');await page.getByRole('spinbutton',{name:'AI天数',exact:true}).press('Tab');}
async function generate(){const pending=page.waitForRequest(r=>r.url().endsWith('/ai/planner/generate-stream')),response=page.waitForResponse(r=>r.url().endsWith('/ai/planner/generate-stream'));await page.getByRole('button',{name:'生成AI行程',exact:true}).click();const r=await pending;lastContentType=(await response).headers()['content-type'];return r.postDataJSON().requestId;}
async function idle(){await page.waitForFunction(()=>!document.querySelector('[data-testid="planner-progress"]'));if(!await waitFor(()=>page.getByRole('button',{name:'生成AI行程',exact:true}).isEnabled()))throw Error('Generation remained busy');}
(async()=>{
 try {
  for(const suffix of ['a','b']){const name='ss_ui_'+Date.now()+suffix;const r=await(await fetch(base+'/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:name,password:'SseUi123456'})})).json();if(r.code!==200)throw Error('Fixture registration failed');users.push({name,id:Number(sql(`SELECT id FROM sys_user WHERE username='${name}'`))});}
  const readerPair=await pair(users[0]);browser=await chromium.launch({channel:'msedge',headless:true});page=await browser.newPage({viewport:{width:1440,height:1000}});
  page.on('pageerror',e=>{errors.push(e.message);save();});page.on('request',r=>{if(r.url().includes('/ai/planner/generate')){const b=r.postDataJSON();requests.push({path:new URL(r.url()).pathname,requestId:b.requestId||null});save();}});
  await login(users[0]);check('progress enabled by default',await page.getByTestId('planner-progress-mode').getByRole('checkbox').isChecked(),true);
  await control('ui-sse-cancel','slow');await config('ui-sse-cancel');const id=await generate();await page.getByText('模型正在生成完整行程',{exact:true}).waitFor();
  check('cancel enabled while form busy',await page.getByRole('button',{name:'取消生成',exact:true}).isEnabled(),true);
  check('preview hidden before validated done',await page.getByTestId('ai-preview').count(),0);
  check('real SSE content type',lastContentType.includes('text/event-stream'),true);
  await page.waitForFunction(()=>document.querySelector('[data-testid="planner-progress"]')?.textContent.includes('已等待1秒'));
  await page.waitForFunction(()=>document.querySelectorAll('.el-message').length===0);await page.evaluate(()=>scrollTo(0,0));
  await page.screenshot({path:path.join(out,'desktop-progress.png'),fullPage:true,mask:[page.getByLabel('API Key',{exact:true})]});
  await page.setViewportSize({width:390,height:844});check('mobile progress no horizontal overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile-progress.png'),fullPage:true,mask:[page.getByLabel('API Key',{exact:true})]});
  await page.getByRole('button',{name:'取消生成',exact:true}).click();await page.getByTestId('planner-cancelled').waitFor();await idle();
  check('real backend job cancelled',(await state(id,readerPair.accessToken)).state,'CANCELLED');check('cancel no preview',await page.getByTestId('ai-preview').count(),0);
  check('cancel no saved plan',sql(`SELECT COUNT(*) FROM user_plan WHERE user_id=${users[0].id}`),'0');
  check('key not persisted',await page.evaluate(()=>JSON.stringify(localStorage).includes('synthetic-sse-ui-key')||JSON.stringify(sessionStorage).includes('synthetic-sse-ui-key')),false);
  check('cancel does not automatically retry',requests.length,1);
  await page.setViewportSize({width:1440,height:1000});await control('ui-sse-valid','ok');await config('ui-sse-valid');await generate();await page.getByTestId('ai-preview').waitFor();await idle();
  check('SSE done renders two validated days',await page.locator('.preview .day').count(),2);check('save requires explicit acknowledgement',await page.getByRole('button',{name:'保存到我的规划并编辑',exact:true}).isDisabled(),true);
  await control('ui-sse-retry','retry');await config('ui-sse-retry');await generate();await page.getByTestId('ai-preview').waitFor();await idle();
  check('live provider received two structural attempts',(await(await fetch(provider+'/__counts')).json())['ui-sse-retry'+modelSuffix],2);
  await control('ui-sse-error','auth');await config('ui-sse-error');await generate();await page.getByRole('alert').filter({hasText:'模型服务拒绝认证'}).waitFor();await idle();
  check('stream error no stale preview',await page.getByTestId('ai-preview').count(),0);check('provider error body absent',(await page.locator('body').innerText()).includes('DO_NOT_EXPOSE'),false);
  await config('ui-sse-valid');await page.getByTestId('planner-progress-mode').click();const legacy=page.waitForResponse(r=>r.url().endsWith('/ai/planner/generate'));await page.getByRole('button',{name:'生成AI行程',exact:true}).click();check('manual ordinary fallback actual endpoint',(await legacy).status(),200);await page.getByTestId('ai-preview').waitFor();check('ordinary fallback no progress panel',await page.getByTestId('planner-progress').count(),0);
  await idle();await page.getByTestId('planner-progress-mode').click();await control('ui-sse-leave','slow');await config('ui-sse-leave');const leave=await generate();await page.getByText('模型正在生成完整行程',{exact:true}).waitFor();await page.goto(base+'/recommend');
  check('navigation closes real stream',await waitFor(async()=>(await state(leave,readerPair.accessToken))?.state==='CANCELLED'),true);
  await page.goto(base+'/ai-planner');await page.getByText(/可信公网主机：api/).waitFor();check('leaving clears API Key',await page.getByLabel('API Key',{exact:true}).inputValue(),'');
  // Fault injection is limited to cancel transport; generation SSE stays live end-to-end.
  await page.route('**/ai/planner/requests/*/cancel',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:404,message:'测试取消请求不可达',data:null})}));
  await control('ui-sse-cancel-network','slow');await config('ui-sse-cancel-network');const lost=await generate();await page.getByText('模型正在生成完整行程',{exact:true}).waitFor();await page.getByRole('button',{name:'取消生成',exact:true}).click();await page.getByTestId('planner-cancelled').waitFor();await idle();
  check('cancel endpoint fault aborts local stream',await waitFor(async()=>(await state(lost,readerPair.accessToken))?.state==='CANCELLED'),true);await page.unroute('**/ai/planner/requests/*/cancel');
  // Parser checks execute the actual TS module with deliberate chunk boundaries, independently of HTTP tests.
  const parser=await page.evaluate(async()=>{
   const {readPlannerStream}=await import('/src/utils/plannerStream.ts');const id='parser-fixture',enc=new TextEncoder();
   const preview={source:'USER_MODEL',model:'本地中文模型',attempts:1,draft:{title:'中文行程',dayList:[{title:'第一天',items:[{title:'散步'}]}]}};
   const frame=(event,data,sep='\n')=>'event:'+event+sep+'data:'+JSON.stringify({requestId:id,data})+sep+sep;
   async function parse(raw,width=7){const bytes=typeof raw==='string'?enc.encode(raw):raw;return readPlannerStream(new ReadableStream({start(c){for(let i=0;i<bytes.length;i+=width)c.enqueue(bytes.slice(i,i+width));c.close();}}),id,()=>{});}
   const checks=[];async function fails(name,raw){try{await parse(raw,raw instanceof Uint8Array?1024:7);checks.push([name,false]);}catch{checks.push([name,true]);}}
   for(const sep of ['\n','\r\n','\r'])checks.push(['UTF8 every-byte '+JSON.stringify(sep),(await parse(':comment'+sep+sep+frame('progress',{stage:'GENERATING',attempt:1},sep)+frame('done',preview,sep),1)).draft.title==='中文行程']);
   const multiline='event:done\ndata:{"requestId":"parser-fixture",\ndata:'+JSON.stringify({data:preview}).slice(1)+'\n\n';checks.push(['multiple data lines',(await parse(multiline)).model==='本地中文模型']);
   checks.push(['cancelled returns null',(await parse(frame('cancelled',{code:499})))===null]);
   await fails('missing terminal',frame('heartbeat',{elapsedSeconds:1}));await fails('unterminated done',frame('done',preview).trimEnd());
   await fails('wrong request ID',frame('done',preview).replace('parser-fixture','other'));await fails('malformed JSON','event:done\ndata:broken\n\n');
   await fails('invalid stage',frame('progress',{stage:'FAKE',attempt:1}));await fails('invalid attempt',frame('progress',{stage:'GENERATING',attempt:3}));
   await fails('incomplete itinerary',frame('done',{...preview,draft:{title:'x',dayList:[]}}));await fails('terminal error',frame('error',{code:3004,message:'safe'}));
   await fails('oversize',new Uint8Array(2097153));await fails('invalid UTF8',new Uint8Array([0xc3,0x28]));return checks;
  });for(const [name,result]of parser)check('local actual parser: '+name,result,true);
  await login(users[1]);check('new account no key',await page.getByLabel('API Key',{exact:true}).inputValue(),'');check('new account no stale preview',await page.getByTestId('ai-preview').count(),0);check('unhandled page errors',errors.length,0);
 }catch(e){check('browser completed',e.message,'no error');process.exitCode=1;}
 finally{if(browser)await browser.close();for(const u of users){cleanSessions(u.id);cleanModelState(u.id);sql(`DELETE FROM llm_call_log WHERE user_id=${u.id}; DELETE FROM user_preference WHERE user_id=${u.id}; DELETE FROM sys_user WHERE id=${u.id}`);check('owned fixture removed '+u.id,sql(`SELECT COUNT(*) FROM sys_user WHERE id=${u.id}`),'0');}finished=true;save();console.log(`${checks.filter(c=>c.passed).length}/${checks.length} passed; ${out}`);if(checks.some(c=>!c.passed)||errors.length)process.exitCode=1;}
})();
