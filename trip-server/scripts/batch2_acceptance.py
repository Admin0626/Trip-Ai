"""Real HTTP acceptance tests. Dedicated fixtures only; no seed-data reset.
Usage: python scripts/batch2_acceptance.py baseline|regression
MYSQL_PASSWORD defaults to the local development password. Responses redact tokens.
"""
import concurrent.futures as cf
import copy
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[2]
MODE = sys.argv[1] if len(sys.argv) > 1 else 'regression'
BASE = os.getenv('TEST_BASE_URL', 'http://localhost:8080/api')
OUT = ROOT / 'docs/dev/evidence' / MODE
OUT.mkdir(parents=True, exist_ok=True)
rows = []
tokens = {}
uids = []
routes = []
tag = 'b2_' + str(int(time.time()))
today = dt.date.today()
travel = str(today + dt.timedelta(days=60))

def sql(query):
    env = dict(os.environ, MYSQL_PWD=os.getenv('MYSQL_PASSWORD', '123456'))
    p = subprocess.run(['mysql', '-u', 'root', '--default-character-set=utf8mb4', '-N', '-B', 'trip_llm', '-e', query], env=env, capture_output=True, encoding='utf-8')
    if p.returncode: raise RuntimeError(p.stderr)
    return p.stdout.strip()

def redact(value):
    if isinstance(value, dict): return {k: ('<redacted>' if k.lower() in ('accesstoken','refreshtoken','password') else redact(v)) for k,v in value.items()}
    if isinstance(value, list): return [redact(v) for v in value]
    return value

def call(name, method, path, body=None, user='a', expected=200, check=None, http=None):
    headers = {'Content-Type':'application/json'}
    if user in tokens: headers['Authorization'] = 'Bearer ' + tokens[user]
    req = urllib.request.Request(BASE+path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    start=time.perf_counter()
    try:
        res=urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as e: res=e
    raw=res.read().decode('utf-8')
    try: obj=json.loads(raw)
    except ValueError: obj={'raw':raw}
    ok=(obj.get('code') == expected if expected is not None else True) and (http is None or res.status == http)
    detail=''
    if check:
        try: ok=bool(check(obj)) and ok
        except Exception as e: ok=False; detail=str(e)
    record={'name':name,'method':method,'path':path,'user':user,'request':redact(body),'http':res.status,'expectedCode':expected,'expectedHttp':http,'response':redact(obj),'passed':ok,'durationMs':round((time.perf_counter()-start)*1000),'checkError':detail}
    rows.append(record)
    return obj

def parallel(jobs):
    import threading
    barrier=threading.Barrier(len(jobs))
    def run(job):
        barrier.wait()
        return job()
    with cf.ThreadPoolExecutor(max_workers=len(jobs)) as pool: return list(pool.map(run,jobs))

def verify(name, actual, expected):
    rows.append({'name':name,'method':'SQL/assert','path':'','request':None,'response':actual,'expected':expected,'passed':actual==expected})

def booking(r, people=1, date=travel):
    return {'routeId':r,'travelDate':date,'peopleNum':people,'contactName':'收尾测试','contactPhone':'13800000000','remark':tag}

def plan():
    return {'title':tag+'五日规划','destinationIds':[1],'startDate':str(today),'days':5,'budget':3000,'peopleNum':2,'status':1,'dayList':[{'dayIndex':9-i,'title':f'第{i+1}天','summary':'保留摘要','items':[{'sortNo':99,'title':f'景点{i+1}','timePoint':'09:00','cost':100,'hotel':'测试住宿','meal':'测试餐食'}]} for i in range(5)]}

def main():
    for user in ['a','b','c']:
        name=tag+user
        call('注册测试账号 '+user,'POST','/auth/register',{'username':name,'password':'Test123456','nickname':'验收'+user},user=None)
        uid=int(sql("SELECT id FROM sys_user WHERE username='"+name+"'")); uids.append(uid)
        login=call('登录 '+user,'POST','/auth/login',{'username':name,'password':'Test123456'},user=None)
        tokens[user]=login['data']['accessToken']
    tokens['admin']=call('管理员登录','POST','/auth/login',{'username':'admin','password':'123456'},user=None)['data']['accessToken']
    for i in range(3):
        routes.append(int(sql(f"INSERT INTO route(title,destination_id,quota_per_day,status) VALUES ('{tag}_{i}',1,3,1); SELECT LAST_INSERT_ID();")))
    r,s,t=routes
    call('匿名规划应401','GET','/plan/page',user=None,expected=401,http=401)
    call('USER不能管理预约','GET','/admin/interaction/booking/page',expected=403,http=403)
    call('公开评论','GET',f'/interaction/comment/page?routeId={r}',user=None)
    call('缺少routeId','GET','/interaction/comment/page',expected=400)
    call('非法路径参数','GET','/plan/abc',expected=400)
    call('负分页大小','GET','/plan/page?size=-1',expected=400)
    call('点赞缺参数','POST','/interaction/like',{},expected=400)
    call('点赞不存在路线','POST','/interaction/like',{'routeId':99999999},expected=2001)
    call('点赞','POST','/interaction/like',{'routeId':r},check=lambda o:o['data']['liked'] and o['data']['likeCount']==1)
    call('点赞状态','GET',f'/interaction/like/{r}/status',check=lambda o:o['data']['liked'])
    call('详情点赞回显','GET',f'/route/{r}',check=lambda o:o['data']['liked'])
    parallel([lambda:call('并发取消点赞1','POST','/interaction/like',{'routeId':r}),lambda:call('并发取消点赞2','POST','/interaction/like',{'routeId':r})])
    verify('并发toggle计数一致',sql(f'SELECT like_count=(SELECT COUNT(*) FROM route_like WHERE route_id={r}) FROM route WHERE id={r}'),'1')
    call('收藏','POST','/interaction/favorite',{'routeId':r},check=lambda o:o['data']['favorited'])
    call('收藏列表','GET','/interaction/favorite/page',check=lambda o:o['data']['total']==1)
    call('取消收藏','POST','/interaction/favorite',{'routeId':r},check=lambda o:not o['data']['favorited'])
    call('预约今天拒绝','POST','/interaction/booking',booking(r,date=str(today)),expected=400)
    call('预约人数0','POST','/interaction/booking',booking(r,0),expected=400)
    b1=call('预约3人','POST','/interaction/booking',booking(r,3))['data']['bookingId']
    call('预约重复','POST','/interaction/booking',booking(r,3),expected=2004)
    call('预约列表','GET','/interaction/booking/page',check=lambda o:o['data']['total']==1)
    call('越权取消预约','PUT',f'/interaction/booking/{b1}/cancel',user='b',expected=403)
    call('确认3人','PUT',f'/admin/interaction/booking/{b1}/status',{'status':1},user='admin')
    call('人数已满拒绝新预约','POST','/interaction/booking',booking(r,1),user='b',expected=2002)
    call('已确认不能再次确认','PUT',f'/admin/interaction/booking/{b1}/status',{'status':1},user='admin',expected=409)
    call('取消已确认预约','PUT',f'/interaction/booking/{b1}/cancel')
    call('重复取消预约','PUT',f'/interaction/booking/{b1}/cancel',expected=409)
    b2=call('取消后重约','POST','/interaction/booking',booking(r,1))['data']['bookingId']
    call('管理员拒绝预约','PUT',f'/admin/interaction/booking/{b2}/status',{'status':2},user='admin')
    verify('管理员取消同步预约计数',sql(f'SELECT booking_count=(SELECT COUNT(*) FROM route_booking WHERE route_id={r} AND status<>2) FROM route WHERE id={r}'),'1')
    c1=call('待审2人A','POST','/interaction/booking',booking(s,2))['data']['bookingId']
    c2=call('待审2人B','POST','/interaction/booking',booking(s,2),user='b')['data']['bookingId']
    ans=parallel([lambda:call('并发审核A','PUT',f'/admin/interaction/booking/{c1}/status',{'status':1},user='admin',expected=None),lambda:call('并发审核B','PUT',f'/admin/interaction/booking/{c2}/status',{'status':1},user='admin',expected=None)])
    verify('并发审核仅一单通过',sorted(x.get('code') for x in ans),[200,2002])
    verify('确认人数不超3',int(sql(f'SELECT COALESCE(SUM(people_num),0) FROM route_booking WHERE route_id={s} AND status=1'))<=3,True)
    comment={'routeId':r,'score':5,'content':'实际接口验收评论'}
    cid=call('发表评论','POST','/interaction/comment',comment)['data']['id']
    call('评论短文本','POST','/interaction/comment',{**comment,'content':'短'},expected=400)
    call('图片超过3张','POST','/interaction/comment',{**comment,'images':['a','b','c','d']},expected=400)
    call('跨路线回复应拒绝','POST','/interaction/comment',{**comment,'routeId':t,'parentId':cid},user='b',expected=400)
    rid=call('正常回复','POST','/interaction/comment',{**comment,'parentId':cid},user='b')['data']['id']
    call('拒绝第三层回复','POST','/interaction/comment',{**comment,'parentId':rid},user='c',expected=400)
    call('评论点赞','POST',f'/interaction/comment/{cid}/like',check=lambda o:o['data']['liked'])
    call('评论取消点赞','POST',f'/interaction/comment/{cid}/like',check=lambda o:not o['data']['liked'])
    call('越权删除评论','DELETE',f'/interaction/comment/{cid}',user='b',expected=403)
    call('隐藏缺status','PUT',f'/admin/interaction/comment/{cid}/status',{},user='admin',expected=400)
    low=call('低分评论','POST','/interaction/comment',{**comment,'score':1},user='c')['data']['id']
    call('管理员隐藏评论','PUT',f'/admin/interaction/comment/{low}/status',{'status':0},user='admin')
    call('隐藏后均分重算','GET',f'/admin/interaction/stat/{r}',user='admin',check=lambda o:o['data']['avgScore']==5)
    call('拒绝回复隐藏评论','POST','/interaction/comment',{**comment,'parentId':low},user='b',expected=400)
    call('显示评论','PUT',f'/admin/interaction/comment/{low}/status',{'status':1},user='admin')
    call('管理评论列表','GET',f'/admin/interaction/comment/page?routeId={r}',user='admin')
    parallel([lambda i=i:call('并发评论'+str(i),'POST','/interaction/comment',{**comment,'routeId':s},user='c',expected=None) for i in range(5)])
    verify('并发评论每天最多3条',sql(f'SELECT COUNT(*) FROM route_comment WHERE route_id={s} AND user_id={uids[2]}'),'3')
    call('删除自己的评论','DELETE',f'/interaction/comment/{cid}')
    p=plan()
    pid=call('创建五天规划','POST','/plan',p)['data']
    call('读取五天重排','GET',f'/plan/{pid}',check=lambda o:[d['dayIndex'] for d in o['data']['dayList']]==[1,2,3,4,5])
    for method,path,body in [('GET',f'/plan/{pid}',None),('PUT',f'/plan/{pid}',p),('DELETE',f'/plan/{pid}',None),('POST',f'/plan/{pid}/copy',None),('POST',f'/plan/{pid}/export',None)]:
        call('规划越权 '+method+path,method,path,body,user='b',expected=403)
    p['dayList'][0]['items'].append({'title':'额外景点','cost':50})
    p['dayList'][1]['items'].insert(0,p['dayList'][0]['items'].pop(0))
    call('跨天重排保存','PUT',f'/plan/{pid}',p)
    call('重开顺序和预算','GET',f'/plan/{pid}',check=lambda o:o['data']['dayList'][1]['items'][0]['title']=='景点1' and sum(i['cost'] for d in o['data']['dayList'] for i in d['items'])==550)
    call('导出文本','POST',f'/plan/{pid}/export',check=lambda o:'第 5 天' in o['data'])
    cp=call('深复制','POST',f'/plan/{pid}/copy')['data']
    call('副本草稿及内容','GET',f'/plan/{cp}',check=lambda o:o['data']['status']==0 and len(o['data']['dayList'])==5)
    call('模板生成','POST','/plan/from-route/201')
    call('AI占位','POST',f'/plan/{pid}/ai-optimize',expected=3001)
    for name,patch in [('草稿空天',{'status':0,'days':1,'dayList':[{'title':'空天','items':[]}]}),('天数不一致',{'days':2}),('非法status',{'status':9}),('过去日期',{'startDate':str(today-dt.timedelta(days=1))}),('预算0',{'budget':0}),('null天',{'dayList':[None]}),('null项',{'days':1,'dayList':[{'items':[None]}]}),('天标题过长',{'days':1,'dayList':[{'title':'x'*101,'items':[{'title':'景点'}]}]})]:
        call(name,'POST','/plan',{**p,**patch},expected=200 if name=='草稿空天' else 400)
    call('规划列表','GET','/plan/page?status=1')
    call('删除规划','DELETE',f'/plan/{pid}')
    call('删除后不可读取','GET',f'/plan/{pid}',expected=403)
    # Isolated user's 50-plan boundary, including concurrent creates.
    count=int(sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uids[0]} AND deleted=0'))
    for i in range(49-count): sql(f"INSERT INTO user_plan(user_id,title,start_date,days,budget) VALUES ({uids[0]},'{tag}_limit','{today}',1,1)")
    results=parallel([lambda:call('并发规划上限A','POST','/plan',plan(),expected=None),lambda:call('并发规划上限B','POST','/plan',plan(),expected=None)])
    verify('规划上限严格50',sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uids[0]} AND deleted=0'),'50')
    call('超过50条','POST','/plan',plan(),expected=2006)
    if MODE != 'baseline':
        call('上限时模板创建拒绝','POST','/plan/from-route/201',expected=2006)
        call('上限时复制拒绝','POST',f'/plan/{cp}/copy',expected=2006)
        d2=str(today+dt.timedelta(days=70))
        cross=parallel([lambda:call('跨路线并发订单A','POST','/interaction/booking',booking(r,date=d2),user='b'),lambda:call('跨路线并发订单B','POST','/interaction/booking',booking(t,date=d2),user='b')])
        verify('跨路线订单号不重复',len({x['data']['bookingNo'] for x in cross}),2)
        same=parallel([lambda:call('同账号重复预约竞争A','POST','/interaction/booking',booking(t,date=str(today+dt.timedelta(days=71))),expected=None),lambda:call('同账号重复预约竞争B','POST','/interaction/booking',booking(t,date=str(today+dt.timedelta(days=71))),expected=None)])
        verify('并发重复预约一成一拒',sorted(x['code'] for x in same),[200,2004])
        own=call('并发点赞用评论','POST','/interaction/comment',{**comment,'routeId':t},user='c')['data']['id']
        ans=parallel([lambda:call('评论点赞竞争A','POST',f'/interaction/comment/{own}/like'),lambda:call('评论点赞竞争B','POST',f'/interaction/comment/{own}/like')])
        verify('评论点赞两次恢复0',sql(f'SELECT like_count FROM route_comment WHERE id={own}'),'0')
        img=call('合法三张图片评论','POST','/interaction/comment',{**comment,'routeId':t,'images':['https://example.invalid/a.jpg','https://example.invalid/b.jpg','https://example.invalid/c.jpg']},user='b')['data']['id']
        call('图片列表回显','GET',f'/interaction/comment/page?routeId={t}',check=lambda o:len(next(x for x in o['data']['records'] if x['id']==img)['images'])==3)

def cleanup():
    if not uids: return
    ids=','.join(map(str,uids)); rs=','.join(map(str,routes)) or '0'
    sql(f'''DELETE i FROM user_plan_item i JOIN user_plan_day d ON i.plan_day_id=d.id JOIN user_plan p ON d.user_plan_id=p.id WHERE p.user_id IN ({ids});
DELETE d FROM user_plan_day d JOIN user_plan p ON d.user_plan_id=p.id WHERE p.user_id IN ({ids});
DELETE FROM user_plan WHERE user_id IN ({ids});
DELETE FROM route_comment_like WHERE user_id IN ({ids});
DELETE FROM route_comment WHERE user_id IN ({ids});
DELETE FROM route_booking WHERE user_id IN ({ids});
DELETE FROM route_like WHERE user_id IN ({ids});
DELETE FROM route_favorite WHERE user_id IN ({ids});
DELETE FROM user_preference WHERE user_id IN ({ids});
DELETE FROM user_behavior WHERE user_id IN ({ids});
DELETE FROM route WHERE id IN ({rs});
DELETE FROM sys_user WHERE id IN ({ids});''')
    verify('测试夹具清理',sql(f'SELECT COUNT(*) FROM sys_user WHERE id IN ({ids})'),'0')

try:
    main()
except Exception as e:
    rows.append({'name':'runner exception','passed':False,'response':repr(e)})
    raise
finally:
    try: cleanup()
    finally:
        (OUT/'responses.json').write_text(json.dumps({'time':dt.datetime.now().isoformat(),'baseUrl':BASE,'mode':MODE,'cases':rows},ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'total':len(rows),'passed':sum(x['passed'] for x in rows),'failures':[x['name'] for x in rows if not x['passed']]},ensure_ascii=False))

if MODE != 'baseline' and any(not row['passed'] for row in rows):
    sys.exit(1)
