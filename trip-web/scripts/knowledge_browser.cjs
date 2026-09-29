// Real Edge + Vite + HTTP + MySQL. No interception, model, or stored credentials.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {execFileSync}=require('node:child_process');
const {cleanSessions,cleanModelState}=require('./redis_fixture.cjs');
const base='http://127.0.0.1:5173',label=process.argv[2]||'final';
const prefix='KUI_'+crypto.randomUUID().replaceAll('-','').slice(0,10),password='InitialUi123';
const out=path.resolve(__dirname,'../../docs/dev/evidence/knowledge',new Date().toISOString().replace(/[:.]/g,'-')+'-'+label+'-browser');
const report={mode:'REAL_EDGE_LOCAL_KNOWLEDGE_NO_LLM',label,phase:'RUNNING',checks:[],errors:[],fixtures:[]};
let browser,destination;const users=[];
fs.mkdirSync(out,{recursive:true});
function save(){report.summary={total:report.checks.length,passed:report.checks.filter(c=>c.passed).length,failed:report.checks.filter(c=>!c.passed).length};fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));}
function check(name,actual,expected){report.checks.push({name,actual,expected,passed:JSON.stringify(actual)===JSON.stringify(expected)});save();}
function sql(query){return execFileSync('mysql',['-u','root','--default-character-set=utf8mb4','-N','-B','trip_llm','-e',query],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.MYSQL_PASSWORD||'123456'}}).trim();}
async function request(url,body,method='POST',token){const r=await fetch(base+'/api'+url,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body!==undefined?{body:JSON.stringify(body)}:{})});return r.json();}
function watch(page,name){page.setDefaultTimeout(12000);page.on('pageerror',e=>{report.errors.push({page:name,message:e.message});save();});}
async function login(page,user,redirect){await page.goto(base+'/login?redirect='+encodeURIComponent(redirect));await page.getByPlaceholder('user1001').fill(user.username);await page.getByPlaceholder('123456',{exact:true}).fill(password);const response=page.waitForResponse(r=>r.url().endsWith('/auth/login')).then(r=>r.json());await page.getByRole('button',{name:'登 录',exact:true}).click();check(user.role+' browser login',(await response).code,200);await page.waitForURL(u=>u.pathname===redirect);return page.evaluate(()=>localStorage.getItem('trip_token'));}
async function changed(page,action,pathEnd,method){const response=page.waitForResponse(r=>new URL(r.url()).pathname===('/api'+pathEnd)&&r.request().method()===method).then(r=>r.json());await action();return response;}
async function run(){try{
  for(const role of ['ADMIN','USER']){const username=prefix+'_'+role.toLowerCase();const r=await request('/auth/register',{username,password,nickname:'Knowledge '+role});check('register '+role,r.code,200);if(r.code!==200)throw Error('fixture register failed');const id=Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));users.push({id,username,role});if(role==='ADMIN')sql(`UPDATE sys_user SET role='ADMIN' WHERE id=${id}`);}
  browser=await chromium.launch({channel:'msedge',headless:true});
  const admin=await (await browser.newContext({viewport:{width:1440,height:1000}})).newPage();
  const reader=await (await browser.newContext({viewport:{width:1360,height:900}})).newPage();watch(admin,'admin');watch(reader,'reader');
  const at=await login(admin,users[0],'/admin/ai/knowledge'),rt=await login(reader,users[1],'/knowledge');
  check('knowledge admin navigation',await admin.getByRole('navigation',{name:'后台导航'}).getByRole('link',{name:'知识资料',exact:true}).isVisible(),true);
  const title=prefix+'霁澜原文';const text='霁澜原文清晨步行。<img src=x onerror="window.knowledgeXss=1">\n'+('甲乙丙丁戊己庚辛壬癸'.repeat(100));
  await admin.getByRole('button',{name:'新增资料',exact:true}).click();let dialog=admin.getByRole('dialog');
  check('new document defaults private',await dialog.locator('.el-select').last().innerText().then(t=>t.includes('停用')),true);
  await dialog.getByLabel('资料标题',{exact:true}).fill(title);await dialog.getByLabel('资料正文',{exact:true}).fill(text);
  const created=await changed(admin,()=>dialog.getByRole('button',{name:'保存资料',exact:true}).click(),'/admin/ai/knowledge','POST');check('UI save real response',created.code,200);if(!created.data)throw Error('UI save failed');let doc=created.data;report.fixtures.push({docId:doc.id,title});save();await dialog.waitFor({state:'hidden'});
  await admin.getByLabel('资料标题筛选').fill(prefix);await admin.getByRole('button',{name:'查询',exact:true}).click();
  const row=()=>admin.getByRole('row').filter({hasText:title}).first();await row().getByText('本地索引就绪',{exact:false}).waitFor();check('saved durable chunks',doc.chunkCount,3);
  const hidden=await request('/ai/knowledge/documents/'+doc.id,undefined,'GET',rt);check('private source unavailable',hidden.code,404);
  const enabled=await changed(admin,()=>row().getByRole('button',{name:'启用',exact:true}).click(),'/admin/ai/knowledge/'+doc.id+'/status','PUT');check('UI enable actual response',enabled.code,200);doc=enabled.data;await row().getByRole('button',{name:'停用',exact:true}).waitFor();
  async function search(query){await reader.getByLabel('资料检索问题').fill(query);return changed(reader,()=>reader.getByRole('button',{name:'检索资料',exact:true}).click(),'/ai/knowledge/search','POST');}
  const found=await search('霁澜原文');check('UI search matched actual references',[found.code,found.data.mode,found.data.matched],[200,'LOCAL_NGRAM',true]);await reader.getByTestId('knowledge-reference').first().waitFor();check('literal markup remains text',await reader.getByTestId('knowledge-reference').first().locator('pre').textContent().then(t=>t.includes('<img src=x')),true);check('no injected image element',await reader.getByTestId('knowledge-results').locator('img').count(),0);check('no script execution',await reader.evaluate(()=>window.knowledgeXss===undefined),true);
  await reader.screenshot({path:path.join(out,'knowledge-search-desktop.png'),fullPage:true});
  await reader.getByRole('link',{name:'查看完整资料与更新时间',exact:true}).first().click();await reader.getByTestId('knowledge-document').waitFor();check('source route real doc',new URL(reader.url()).pathname,'/knowledge/'+doc.id);check('full original source',await reader.getByTestId('knowledge-document').locator('pre').textContent(),text);check('updated/revision visible',await reader.getByTestId('knowledge-document').innerText().then(t=>t.includes('更新时间：')&&t.includes('第2版')),true);
  await reader.setViewportSize({width:375,height:812});check('mobile full source no overflow',await reader.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await reader.screenshot({path:path.join(out,'knowledge-source-mobile.png'),fullPage:true});
  await reader.getByRole('link',{name:'返回资料检索',exact:true}).click();await search('霁澜原文');check('mobile search no overflow',await reader.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);check('mobile travel materials link usable',await reader.getByRole('link',{name:'旅行资料',exact:true}).isVisible(),true);await reader.screenshot({path:path.join(out,'knowledge-search-mobile.png'),fullPage:true});
  for(const width of [390,768]){await reader.setViewportSize({width,height:900});await reader.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));check('navigation no overflow '+width,await reader.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);}
  await reader.setViewportSize({width:375,height:812});
  // Concurrent editor must retain the user's input after the server's real 409.
  await row().getByRole('button',{name:'编辑',exact:true}).click();dialog=admin.getByRole('dialog');await dialog.getByLabel('资料正文',{exact:true}).fill('霁澜原文我的待保存内容');
  const concurrent=await request('/admin/ai/knowledge/'+doc.id,{title,docType:'GUIDE',sourceId:null,content:'霁澜原文另一位管理员内容',status:1,expectedRevision:doc.revision},'PUT',at);check('other editor actual save',concurrent.code,200);doc=concurrent.data;
  const conflict=await changed(admin,()=>dialog.getByRole('button',{name:'保存资料',exact:true}).click(),'/admin/ai/knowledge/'+doc.id,'PUT');check('stale UI save conflict',conflict.code,409);await dialog.getByText('资料版本已变化，输入已保留。请复制所需内容，关闭并刷新列表后重新编辑。',{exact:true}).waitFor();check('conflict preserves input',await dialog.getByLabel('资料正文',{exact:true}).inputValue(),'霁澜原文我的待保存内容');await dialog.getByRole('button',{name:'取消',exact:true}).click();await admin.getByRole('button',{name:'查询',exact:true}).click();
  // Fresh detail prevents a stale list from silently overwriting content.
  await row().getByRole('button',{name:'编辑',exact:true}).click();dialog=admin.getByRole('dialog');check('reopen fetches fresh version',await dialog.getByLabel('资料正文',{exact:true}).inputValue(),'霁澜原文另一位管理员内容');await dialog.getByRole('button',{name:'取消',exact:true}).click();
  const rebuilt=await changed(admin,()=>row().getByRole('button',{name:'重建分片',exact:true}).click(),'/admin/ai/knowledge/rebuild','POST');check('UI rebuild current document',[rebuilt.code,rebuilt.data.indexedRevision],[200,doc.revision]);
  await admin.setViewportSize({width:375,height:812});check('mobile admin no page overflow',await admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await admin.getByRole('button',{name:'导入TXT/MD',exact:true}).click();dialog=admin.getByRole('dialog');await dialog.getByLabel('资料标题',{exact:true}).fill(prefix+'晴岚导入');await dialog.getByLabel('资料文件',{exact:true}).setInputFiles({name:'guide.md',mimeType:'text/markdown',buffer:Buffer.from('\ufeff晴岚导入\r\n导入正文')});
  check('mobile upload dialog no overflow',await admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await admin.screenshot({path:path.join(out,'knowledge-upload-mobile.png'),fullPage:true});
  const imported=await changed(admin,()=>dialog.getByRole('button',{name:'保存资料',exact:true}).click(),'/admin/ai/knowledge/upload','POST');check('UI multipart actual UTF8 normalization',[imported.code,imported.data.sourceType,imported.data.content],[200,'FILE','晴岚导入\n导入正文']);await dialog.waitFor({state:'hidden'});report.fixtures.push({docId:imported.data.id,title:imported.data.title});save();
  await admin.setViewportSize({width:1440,height:1000});
  // Choose an actual visible catalog item through the remote select and follow its link.
  sql(`INSERT INTO destination(name,province,longitude,latitude,cover_img,status) VALUES ('${prefix}目的地','夹具',100,25,'/fixture.png',1)`);destination=Number(sql(`SELECT id FROM destination WHERE name='${prefix}目的地'`));
  await admin.getByRole('button',{name:'新增资料',exact:true}).click();dialog=admin.getByRole('dialog');await dialog.getByLabel('资料标题',{exact:true}).fill(prefix+'澄岳来源');await dialog.getByLabel('资料正文',{exact:true}).fill('澄岳来源目的地散步资料');await dialog.locator('.el-select').first().click();await admin.getByRole('option',{name:'目的地',exact:true}).click();await dialog.getByLabel('关联资料来源').fill(prefix);await admin.getByRole('option',{name:prefix+'目的地',exact:true}).click();await dialog.locator('.el-select').last().click();await admin.getByRole('option',{name:'启用并公开',exact:true}).click();
  const linked=await changed(admin,()=>dialog.getByRole('button',{name:'保存资料',exact:true}).click(),'/admin/ai/knowledge','POST');check('UI selected published source',linked.data.sourceId,destination);report.fixtures.push({docId:linked.data.id,title:linked.data.title});save();await dialog.waitFor({state:'hidden'});
  const sourceSearch=await search('澄岳来源');check('verified destination reference',sourceSearch.data.references[0].sourcePath,'/destination/'+destination);await reader.getByRole('link',{name:'查看关联目的地',exact:true}).first().click();await reader.waitForURL(u=>u.pathname==='/destination/'+destination);await reader.getByRole('heading',{name:prefix+'目的地',exact:true}).waitFor();check('actual catalog link resolves',new URL(reader.url()).pathname,'/destination/'+destination);
  await reader.goto(base+'/knowledge');await search('霁澜原文');
  const disabled=await changed(admin,()=>row().getByRole('button',{name:'停用',exact:true}).click(),'/admin/ai/knowledge/'+doc.id+'/status','PUT');check('UI disable real response',disabled.code,200);doc=disabled.data;
  const absent=await search('霁澜原文');check('disabled excluded actual query',[absent.code,absent.data.matched,absent.data.references],[200,false,[]]);await reader.goto(base+'/knowledge/'+doc.id);await reader.getByText('资料未公开、已过期或不存在',{exact:true}).first().waitFor();check('hidden source clears content',await reader.getByTestId('knowledge-document').count(),0);
  await reader.goto(base+'/admin/ai/knowledge');await reader.waitForURL(u=>u.pathname==='/');check('reader frontend admin guard',new URL(reader.url()).pathname,'/');
  await reader.goto(base+'/knowledge');const empty=await search('土星雷暴天气');check('outside corpus no generated answer',[empty.data.matched,empty.data.references],[false,[]]);
  const deletion=admin.waitForResponse(r=>new URL(r.url()).pathname==='/api/admin/ai/knowledge/'+doc.id&&r.request().method()==='DELETE').then(r=>r.json());await row().getByRole('button',{name:'删除',exact:true}).click();await admin.getByRole('dialog',{name:'删除资料'}).getByRole('button',{name:'确定',exact:true}).click();check('UI confirmed logical delete',(await deletion).code,200);check('deleted chunks removed',sql(`SELECT COUNT(*) FROM knowledge_chunk WHERE doc_id=${doc.id}`),'0');
  await admin.screenshot({path:path.join(out,'knowledge-admin-desktop.png'),fullPage:true});check('no uncaught browser errors',report.errors.length,0);
}catch(e){check('browser run completed',e.message.replaceAll(password,'<redacted>'),'no error');}finally{
  if(browser)await browser.close();
  const ids=sql(`SELECT id FROM knowledge_doc WHERE title LIKE '${prefix}%'`).split(/\r?\n/).filter(Boolean).map(Number);for(const id of ids){if(!Number.isSafeInteger(id)||id<1)throw Error('Unsafe fixture doc ID');sql(`DELETE FROM knowledge_doc WHERE id=${id}`);}
  if(destination)sql(`DELETE FROM destination WHERE id=${destination}`);
  check('owned documents cleaned',sql(`SELECT COUNT(*) FROM knowledge_doc WHERE title LIKE '${prefix}%'`),'0');
  for(const user of users){cleanSessions(user.id);cleanModelState(user.id);sql(`DELETE FROM llm_call_log WHERE user_id=${user.id}; DELETE FROM user_preference WHERE user_id=${user.id}; DELETE FROM sys_user WHERE id=${user.id}`);check('owned user cleaned '+user.role,sql(`SELECT COUNT(*) FROM sys_user WHERE id=${user.id}`),'0');}
  report.phase='COMPLETE';save();
}console.log(`${report.summary.passed}/${report.summary.total} passed; ${out}`);process.exitCode=report.summary.failed?1:0;}
run();
