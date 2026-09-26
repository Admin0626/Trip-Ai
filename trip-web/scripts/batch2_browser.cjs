// Real browser + real Vite proxy API; no response mocking.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'C:/Users/wlky0/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const mode=process.argv[2] || 'regression';
const out=path.resolve(__dirname,'../../docs/dev/evidence/browser-'+mode);
fs.mkdirSync(out,{recursive:true});
const rows=[], errors=[];
const username='ui_'+Date.now(), password='Test123456';
let token, uid, browser, page;
function sql(q) {return execFileSync('mysql',['-u','root','-N','-B','--default-character-set=utf8mb4','trip_llm','-e',q],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.MYSQL_PASSWORD||'123456'}}).trim()}
async function api(method,url,body) { const r=await fetch('http://127.0.0.1:5173/api'+url,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined}); return r.json(); }
function check(name,actual,expected) {rows.push({name,actual,expected,passed:JSON.stringify(actual)===JSON.stringify(expected)})}
(async()=>{
 try {
  await api('POST','/auth/register',{username,password,nickname:'浏览器收尾测试'});
  uid=Number(sql(`SELECT id FROM sys_user WHERE username='${username}'`));
  token=(await api('POST','/auth/login',{username,password})).data.accessToken;
  const date=new Date().toLocaleDateString('sv-SE');
  const dto={title:'浏览器五天验收',destinationIds:[1],startDate:date,days:5,budget:3000,peopleNum:2,status:1,dayList:Array.from({length:5},(_,i)=>({title:'第'+(i+1)+'天',summary:'摘要'+i,items:[{title:'景点'+(i+1),cost:100,hotel:'酒店'+i,meal:'餐食'+i}]}))};
  const pid=(await api('POST','/plan',dto)).data;
  browser=await chromium.launch({channel:'msedge',headless:true});
  page=await browser.newPage({viewport:{width:1440,height:1000}});
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:5173/login');
  await page.getByPlaceholder('user1001').fill(username);
  await page.getByPlaceholder('123456',{exact:true}).fill(password);
  await page.getByRole('button',{name:'登 录',exact:true}).click();
  await page.waitForURL('http://127.0.0.1:5173/');
  check('浏览器登录跳转首页',new URL(page.url()).pathname,'/');
  await page.goto(`http://127.0.0.1:5173/plan/${pid}/edit`);
  await page.getByPlaceholder('行程标题（必填）').first().waitFor({state:'attached'});
  check('编辑器出行人数上限',await page.getByRole('spinbutton').first().getAttribute('aria-valuemax'),'10');
  check('跨天目标可见',await page.locator('.cross-day-target').count(),5);
  await page.screenshot({path:path.join(out,'plan-editor.png'),fullPage:true});
  await page.getByRole('button',{name:'保存规划',exact:true}).click();
  await page.waitForURL('**/plan');
  const saved=(await api('GET',`/plan/${pid}`)).data;
  check('保存保留住宿',saved.dayList[0].items[0].hotel,'酒店0');
  check('保存保留用餐',saved.dayList[0].items[0].meal,'餐食0');
  check('保存保留摘要',saved.dayList[0].summary,'摘要0');
  if(mode!=='baseline') {
    await page.goto(`http://127.0.0.1:5173/plan/${pid}/edit`);
    await page.getByPlaceholder('行程标题（必填）').first().waitFor();
    await page.getByRole('button',{name:'+ 手动添加一条'}).click();
    await page.getByPlaceholder('行程标题（必填）').nth(1).fill('同日第二项');
    // Pointer drag into another day's persistent drop target.
    const drag=await page.locator('.item-row__drag').first().boundingBox();
    const target=await page.locator('.cross-day-target').nth(1).boundingBox();
    await page.mouse.move(drag.x+drag.width/2,drag.y+drag.height/2);
    await page.mouse.down(); await page.mouse.move(drag.x+30,drag.y+10,{steps:5});
    await page.mouse.move(target.x+target.width/2,target.y+target.height/2,{steps:20});
    await page.waitForTimeout(350); await page.mouse.up();
    check('鼠标跨天拖拽后首日剩一项',await page.locator('.item-row').count(),1);
    await page.getByRole('button',{name:'保存规划',exact:true}).click();
    await page.waitForURL('**/plan');
    const moved=(await api('GET',`/plan/${pid}`)).data;
    check('跨天保存到第二天',moved.dayList[1].items.map(x=>x.title),['景点2','景点1']);
    check('五天预算保持500',moved.dayList.flatMap(d=>d.items).reduce((n,x)=>n+x.cost,0),500);
    await page.goto(`http://127.0.0.1:5173/plan/${pid}/edit`);
    await page.getByPlaceholder('行程标题（必填）').first().waitFor();
    check('刷新重开首日顺序',await page.getByPlaceholder('行程标题（必填）').first().inputValue(),'同日第二项');
    await page.getByRole('button',{name:'+ 从景点库添加'}).click();
    await page.locator('.attraction-item').first().waitFor();
    check('景点抽屉真实数据',await page.locator('.attraction-item').count()>0,true);
  }
  await page.goto('http://127.0.0.1:5173/route/201');
  await page.getByRole('button',{name:'立即预约',exact:true}).waitFor();
  await page.getByRole('button',{name:'点赞',exact:true}).click();
  await page.getByRole('button',{name:'已点赞',exact:true}).waitFor();
  check('页面点赞回显',true,true);
  await page.getByRole('button',{name:'已点赞',exact:true}).click();
  await page.getByRole('button',{name:'点赞',exact:true}).waitFor();
  await page.getByRole('button',{name:'收藏',exact:true}).click();
  await page.getByRole('button',{name:'已收藏',exact:true}).waitFor();
  check('页面收藏回显',true,true);
  await page.getByRole('button',{name:'已收藏',exact:true}).click();
  await page.getByRole('button',{name:'收藏',exact:true}).waitFor();
  if(mode!=='baseline') {
    await page.getByPlaceholder('写下你的评价…').fill('浏览器实测评论 '+username);
    const posted=page.waitForResponse(r=>r.url().endsWith('/interaction/comment') && r.request().method()==='POST');
    await page.getByRole('button',{name:'发表评论',exact:true}).click();
    check('页面发表评论',(await (await posted).json()).code,200);
    const ownComment=page.locator('.comment-item').filter({hasText:'浏览器实测评论 '+username});
    await ownComment.waitFor();
    check('unknown不显示中评',await ownComment.locator('.el-tag').count(),0);
    await ownComment.getByRole('button',{name:'回复',exact:true}).click();
    await page.getByPlaceholder('写下你的评价…').fill('浏览器实测回复 '+username);
    const reply=page.waitForResponse(r=>r.url().endsWith('/interaction/comment') && r.request().method()==='POST');
    await page.getByRole('button',{name:'发表评论',exact:true}).click();
    check('页面回复评论',(await (await reply).json()).code,200);
    const ownReply=page.locator('.comment-item').filter({hasText:'浏览器实测回复 '+username});
    await ownReply.waitFor();
    check('回复展示父评论',await ownReply.getByText(/回复评论 #/).count(),1);
    for(const item of [ownReply,ownComment]) {
      await item.getByRole('button',{name:'删除评论',exact:true}).click();
      const deleted=page.waitForResponse(r=>r.url().includes('/interaction/comment/') && r.request().method()==='DELETE');
      await page.getByRole('button',{name:'确定',exact:true}).click();
      check('页面删除本人评论',(await (await deleted).json()).code,200);
      await item.waitFor({state:'detached'});
    }
  }
  await page.getByRole('button',{name:'立即预约',exact:true}).click();
  check('预约人数上限',await page.getByRole('dialog').getByRole('spinbutton').getAttribute('aria-valuemax'),'10');
  await page.getByPlaceholder('手机号').fill('13800000000');
  const bookingResponse=page.waitForResponse(r=>r.url().endsWith('/interaction/booking') && r.request().method()==='POST');
  await page.getByRole('button',{name:'提交预约',exact:true}).click();
  check('浏览器预约接口成功',(await (await bookingResponse).json()).code,200);
  await page.goto('http://127.0.0.1:5173/user/bookings');
  await page.getByRole('button',{name:'取消预约',exact:true}).first().click();
  await page.getByRole('button',{name:'确定',exact:true}).click();
  await page.getByText('已取消',{exact:true}).first().waitFor();
  check('页面取消预约',true,true);
  if(mode!=='baseline') {
    await page.goto('http://127.0.0.1:5173/plan/create');
    await page.getByPlaceholder('例如：我的云南 5 日自由行').fill('空天草稿验收');
    const draft=page.waitForResponse(r=>r.url().endsWith('/plan') && r.request().method()==='POST');
    await page.getByRole('button',{name:'保存草稿',exact:true}).click();
    const draftResult=await (await draft).json();
    check('浏览器空天草稿保存',draftResult.code,200);
    await page.waitForURL('**/plan');
    check('空草稿三天持久化',(await api('GET','/plan/'+draftResult.data)).data.dayList.length,3);
    await page.goto('http://127.0.0.1:5173/plan/create?fromRoute=201');
    await page.waitForURL(/\/plan\/\d+\/edit$/);
    const canonical=page.url();
    const total=(await api('GET','/plan/page')).data.total;
    await page.reload();
    await page.getByPlaceholder('行程标题（必填）').first().waitFor();
    check('模板创建后规范URL',page.url(),canonical);
    check('刷新不重复创建规划',(await api('GET','/plan/page')).data.total,total);
  }
  check('浏览器运行时异常',errors,[]);
 } catch(e) {rows.push({name:'browser runner',passed:false,actual:e.stack}); if(page) await page.screenshot({path:path.join(out,'failure.png'),fullPage:true});}
 finally {
  if(browser) await browser.close();
  if(uid) {
   // Restore only routes actually touched by this fixture before physically removing its rows.
   const touched=sql(`SELECT DISTINCT route_id FROM route_booking WHERE user_id=${uid} UNION SELECT route_id FROM route_comment WHERE user_id=${uid} UNION SELECT route_id FROM route_like WHERE user_id=${uid} UNION SELECT route_id FROM route_favorite WHERE user_id=${uid}`).split(/\s+/).filter(Boolean).map(Number);
   sql(`DELETE i FROM user_plan_item i JOIN user_plan_day d ON i.plan_day_id=d.id JOIN user_plan p ON d.user_plan_id=p.id WHERE p.user_id=${uid}; DELETE d FROM user_plan_day d JOIN user_plan p ON d.user_plan_id=p.id WHERE p.user_id=${uid}; DELETE FROM user_plan WHERE user_id=${uid}; DELETE FROM route_booking WHERE user_id=${uid}; DELETE FROM route_comment_like WHERE user_id=${uid}; DELETE FROM route_comment WHERE user_id=${uid}; DELETE FROM route_like WHERE user_id=${uid}; DELETE FROM route_favorite WHERE user_id=${uid}; DELETE FROM sys_user WHERE id=${uid};`);
   if(touched.length) sql(`UPDATE route r SET like_count=(SELECT COUNT(*) FROM route_like WHERE route_id=r.id),favorite_count=(SELECT COUNT(*) FROM route_favorite WHERE route_id=r.id),booking_count=(SELECT COUNT(*) FROM route_booking WHERE route_id=r.id AND status<>2),comment_count=(SELECT COUNT(*) FROM route_comment WHERE route_id=r.id AND deleted=0),avg_score=(SELECT COALESCE(ROUND(AVG(score),2),0) FROM route_comment WHERE route_id=r.id AND deleted=0 AND status=1) WHERE r.id IN (${touched.join(',')})`);
  }
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({time:new Date().toISOString(),rows,errors},null,2));
  console.log(JSON.stringify(rows,null,2));
  if(mode!=='baseline' && rows.some(x=>!x.passed)) process.exitCode=1;
 }
})();
