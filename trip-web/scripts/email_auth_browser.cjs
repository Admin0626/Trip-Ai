// Real Edge UI -> HTTP/MySQL/Redis -> loopback SMTP. No codes/tokens in saved results.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{execFileSync}=require('node:child_process');
const base=process.env.TEST_WEB_URL||'http://127.0.0.1:5187',out=path.resolve(__dirname,'../../docs/dev/evidence/email-auth',new Date().toISOString().replace(/[:.]/g,'-')+'-browser');
const checks=[],errors=[],emails=[],marker=crypto.randomUUID().replaceAll('-','').slice(0,9),name='mailui_'+marker,password='BrowserMail123';let browser,page,uid;
function save(){fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({mode:'REAL_EDGE_HTTP_SMTP_LOOPBACK',fixtures:{username:name,uid,emails},checks,errors,summary:{total:checks.length,passed:checks.filter(c=>c.passed).length}},null,2));}
function check(name,actual,expected=true){checks.push({name,actual,expected,passed:JSON.stringify(actual)===JSON.stringify(expected)});save();}
function sql(q){return execFileSync('mysql',['-u','root','-N','-B','--default-character-set=utf8mb4','trip_llm','-e',q],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.MYSQL_PASSWORD||'123456'}}).trim();}
function red(...args){return execFileSync('redis-cli',['-p','6387','--raw',...args.map(String)],{encoding:'utf8'}).trim();}
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
function address(tag){const e=tag+'-'+marker+'@trip.invalid';emails.push(e);return e;}
function cooldown(e){red('DEL','trip:mail:cooldown:'+hash(e));}
async function sink(e,command='message'){return(await fetch('http://127.0.0.1:8027/'+command+'?email='+encodeURIComponent(e))).json();}
async function code(e){return(await sink(e)).code;}
async function action(button,ending,method='POST'){
  const result=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith(ending)&&r.request().method()===method).then(r=>r.json());
  const [data]=await Promise.all([result,button.click()]);return data;
}
async function login(p){await p.goto(base+'/login?redirect=/user/profile');await p.getByPlaceholder('user1001').fill(name);await p.getByPlaceholder('123456',{exact:true}).fill(password);await p.getByRole('button',{name:'登 录',exact:true}).click();await p.waitForURL(u=>u.pathname==='/user/profile');await p.getByRole('heading',{name:'邮箱验证',exact:true}).waitFor();}
async function run(){try{
 red('DEL','trip:mail:limit:ip:'+hash('127.0.0.1'),'trip:mail:limit:global');
 browser=await chromium.launch({channel:'msedge',headless:true});const ctx=await browser.newContext({viewport:{width:1440,height:1000}});page=await ctx.newPage();page.setDefaultTimeout(15000);page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base+'/login');await page.getByRole('link',{name:'忘记密码？'}).click();await page.waitForURL('**/forgot-password');check('login links to public recovery',true);
 await page.getByLabel('新密码',{exact:true}).fill('NoDigits');await page.getByRole('button',{name:'重置密码',exact:true}).click();await page.getByText('请输入6位邮箱验证码',{exact:true}).waitFor();check('recovery validates missing code',true);
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(out,'recovery-mobile.png'),fullPage:true});check('recovery mobile no overflow',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
 await page.goto(base+'/register');await page.getByPlaceholder('4-20 位用户名').fill(name);await page.getByPlaceholder('8-20 位，含字母和数字').fill(password);await page.getByPlaceholder('再次输入密码').fill(password);
 const first=address('registered');await page.getByLabel('注册邮箱',{exact:true}).fill(first);
 await page.getByRole('button',{name:'注 册',exact:true}).click();await page.getByText('填写邮箱时须输入6位验证码',{exact:true}).waitFor();check('register requires code when email provided',true);
 check('registration SMTP response',(await action(page.getByRole('button',{name:'发送验证码',exact:true}),'/api/auth/email/code')).code,200);
 await page.getByRole('button',{name:/秒后重发/}).waitFor();check('send button shows disabled countdown',await page.getByRole('button',{name:/秒后重发/}).isDisabled());
 check('registration receipt has no code',await page.getByRole('status').innerText().then(t=>!t.includes('验证码：')));
 const rc=await code(first);await page.getByLabel('邮箱验证码',{exact:true}).fill(rc);await page.getByLabel('注册邮箱',{exact:true}).fill(address('changed'));
 check('changing email clears entered code',await page.getByLabel('邮箱验证码',{exact:true}).inputValue(),'');await page.getByLabel('注册邮箱',{exact:true}).fill(first);await page.getByLabel('邮箱验证码',{exact:true}).fill(rc);
 check('registration submits verified account',(await action(page.getByRole('button',{name:'注 册',exact:true}),'/api/auth/register')).code,200);await page.waitForURL('**/login');uid=Number(sql(`SELECT id FROM sys_user WHERE username='${name}'`));
 await login(page);check('profile displays verified status',await page.locator('.email-status .el-tag').innerText(),'已验证');check('profile email is read-only',await page.getByLabel('邮箱',{exact:true}).getAttribute('readonly')!==null);
 const second=address('replacement');await page.getByLabel('待验证邮箱',{exact:true}).fill(second);await page.getByLabel('当前密码',{exact:true}).fill('WrongPassword123');
 check('wrong current password is rejected',(await action(page.getByRole('button',{name:'发送验证码',exact:true}),'/api/user/email/code')).code,400);
 cooldown(second);await page.getByLabel('当前密码',{exact:true}).fill(password);check('binding email send succeeds',(await action(page.getByRole('button',{name:'发送验证码',exact:true}),'/api/user/email/code')).code,200);
 await page.getByLabel('邮箱验证码',{exact:true}).fill(await code(second));check('binding confirmation succeeds',(await action(page.getByRole('button',{name:'验证并绑定邮箱',exact:true}),'/api/user/email/verify')).code,200);
 await page.getByLabel('邮箱',{exact:true}).waitFor();check('profile reflects new email',await page.getByLabel('邮箱',{exact:true}).inputValue(),second);
 check('normal profile save still works',(await action(page.getByRole('button',{name:'保存资料',exact:true}),'/api/user/profile','PUT')).code,200);
 await page.reload();await page.getByRole('heading',{name:'邮箱验证',exact:true}).waitFor();await page.waitForFunction(()=>document.querySelectorAll('.el-loading-mask').length===0);check('verification survives refresh',await page.locator('.email-status .el-tag').innerText(),'已验证');
 await page.setViewportSize({width:390,height:844});check('profile email panel fits mobile',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.screenshot({path:path.join(out,'verified-profile-mobile.png'),fullPage:true});
 const secondCtx=await browser.newContext();const secondPage=await secondCtx.newPage();await login(secondPage);
 const recoveryCtx=await browser.newContext({viewport:{width:390,height:844}});page=await recoveryCtx.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(base+'/forgot-password');
 const failing=address('failure');await sink(failing,'reject');await page.getByLabel('已验证邮箱',{exact:true}).fill(failing);
 check('SMTP failure visible to recovery user',(await action(page.getByRole('button',{name:'发送验证码',exact:true}),'/api/auth/password/code')).code,503);await page.getByText('邮件发送失败，请稍后重新发送',{exact:true}).waitFor();check('failure allows retry',await page.getByRole('button',{name:'发送验证码',exact:true}).isEnabled());
 cooldown(second);await page.getByLabel('已验证邮箱',{exact:true}).fill(second);check('recovery SMTP accepted',(await action(page.getByRole('button',{name:'发送验证码',exact:true}),'/api/auth/password/code')).code,200);
 const recoveryCode=await code(second);await page.getByLabel('邮箱验证码',{exact:true}).fill(recoveryCode==='000000'?'999999':'000000');await page.getByLabel('新密码',{exact:true}).fill('FreshPassword123');await page.getByLabel('确认新密码',{exact:true}).fill('Different123');
 await page.getByRole('button',{name:'重置密码',exact:true}).click();await page.getByText('两次新密码不一致',{exact:true}).waitFor();check('confirm password mismatch explained',true);
 await page.getByLabel('确认新密码',{exact:true}).fill('FreshPassword123');check('wrong code handled without navigation',(await action(page.getByRole('button',{name:'重置密码',exact:true}),'/api/auth/password/reset')).code,400);check('failed reset keeps email draft',await page.getByLabel('已验证邮箱',{exact:true}).inputValue(),second);
 await page.getByLabel('邮箱验证码',{exact:true}).fill(recoveryCode);check('recovery succeeds through page',(await action(page.getByRole('button',{name:'重置密码',exact:true}),'/api/auth/password/reset')).code,200);await page.waitForURL('**/login');check('successful reset returns to login',true);
 const firstPage=ctx.pages()[0];await firstPage.reload();await firstPage.waitForURL('**/login?**');check('first device session revoked',true);await secondPage.reload();await secondPage.waitForURL('**/login?**');check('second device session revoked',true);
 await page.getByPlaceholder('user1001').fill(name);await page.getByPlaceholder('123456',{exact:true}).fill('FreshPassword123');await page.getByRole('button',{name:'登 录',exact:true}).click();await page.waitForURL(u=>u.pathname==='/');check('new password login succeeds',true);
}catch(e){check('browser completed',e.message,'no exception');}
finally{
 if(browser)await browser.close();
 try{
   uid=uid||Number(sql(`SELECT id FROM sys_user WHERE username='${name}'`));
   if(uid){sql(`DELETE FROM user_preference WHERE user_id=${uid};DELETE FROM user_behavior WHERE user_id=${uid};DELETE FROM sys_user WHERE id=${uid} AND username='${name}'`);for(const key of red('KEYS',`trip:auth:session:{${uid}}:*`).split(/\r?\n/).filter(Boolean))red('DEL',key);check('browser fixture account removed',sql(`SELECT COUNT(*) FROM sys_user WHERE id=${uid}`),'0');check('browser fixture sessions removed',red('KEYS',`trip:auth:session:{${uid}}:*`),'');}
   for(const e of emails){for(const key of red('KEYS','trip:mail:*'+hash(e)).split(/\r?\n/).filter(Boolean))red('DEL',key);await sink(e,'clear');}
   red('DEL','trip:mail:limit:ip:'+hash('127.0.0.1'),'trip:mail:limit:global');check('browser mail fixtures removed',emails.every(e=>red('KEYS','trip:mail:*'+hash(e))===''));
 }catch(e){check('fixture cleanup',e.message,'no exception');}
 check('no unhandled page errors',errors.length,0);save();console.log(`${checks.filter(c=>c.passed).length}/${checks.length} passed; ${out}`);process.exitCode=checks.every(c=>c.passed)?0:1;
}}
run();
