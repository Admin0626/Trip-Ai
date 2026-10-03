"""Live HTTP/MySQL/Redis quota acceptance; local controlled provider, not a real LLM.
Only temporary users and uniquely prefixed Lua keys are removed. Legacy shared
daily usage is deliberately never changed or reset.
"""
import concurrent.futures
import datetime as dt
import json
import subprocess
import time
import urllib.request
import urllib.error
import uuid
import user_planner_acceptance as p
from redis_fixture import clean_sessions,clean_ai_state

t = p.t
rows, users, owned_keys = [], [], set()
phase='RUNNING'
out = t.ROOT / 'docs/dev/evidence/ai-quota' / (dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '-http-redis.json')
zone = dt.timezone(dt.timedelta(hours=8))

def save():
    out.parent.mkdir(parents=True, exist_ok=True)
    payload=json.dumps({'mode':'REAL_HTTP_MYSQL_REDIS_CONTROLLED_PROVIDER_NOT_REAL_LLM','phase':phase,'fixtures':users,'checks':rows},ensure_ascii=False,indent=2)
    # Preserve the last complete JSON if Windows temporarily denies an output handle.
    pending=out.with_suffix('.writing')
    for attempt in range(3):
        try: pending.write_text(payload,encoding='utf-8'); pending.replace(out); return
        except OSError:
            if attempt==2: raise
            time.sleep(0.05)

def check(name, actual, expected):
    rows.append({'name':name,'actual':actual,'expected':expected,'passed':actual==expected}); save()

def redis(*args):
    return subprocess.check_output(['redis-cli','--raw',*map(str,args)],text=True,encoding='utf-8').strip()

def user_keys(uid):
    now=dt.datetime.now(zone)
    hour=int(now.replace(minute=0,second=0,microsecond=0).timestamp())
    day=int(now.replace(hour=0,minute=0,second=0,microsecond=0).timestamp())
    return [f'trip:ai:quota:{{ai-quota}}:user:{uid}:hour:{hour}',f'trip:ai:quota:{{ai-quota}}:user:{uid}:day:{day}']

def call(name,path,body=None,token=None,code=200,http=200):
    req=urllib.request.Request(t.BASE+path,None if body is None else json.dumps(body).encode(),
        {'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})})
    try: response=urllib.request.urlopen(req,timeout=120)
    except urllib.error.HTTPError as e: response=e
    result=json.loads(response.read())
    rows.append({'name':name,'path':path,'http':response.status,'expectedHttp':http,'request':p.redact(body),
        'response':p.redact(result),'expectedCode':code,'passed':response.status==http and result.get('code')==code}); save()
    return result

def lua_checks():
    source=(t.ROOT/'trip-server/src/main/resources/redis/ai_quota.lua').read_text(encoding='utf-8')
    prefix='trip:test:quota:'+uuid.uuid4().hex
    keys=[prefix+':hour',prefix+':day',prefix+':global']; owned_keys.update(keys)
    expiry=int(time.time())+300
    # Saturated legacy daily counters must neither deny nor get incremented.
    redis('SET',keys[1],200,'EX',300); redis('SET',keys[2],2000,'EX',300)
    def acquire(limit=7):
        return redis('EVAL',source,3,*keys,limit,expiry).splitlines()
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as executor:
        results=list(executor.map(lambda _:acquire(),range(40)))
    check('40 concurrent Lua requests admit exactly 7',sum(r[0]=='1' for r in results),7)
    check('only hour increments; saturated daily counters untouched',redis('MGET',*keys).splitlines(),['7','200','2000'])
    check('counter expiry bounded',0<int(redis('TTL',keys[0]))<=300,True)
    check('hour denial remains atomic',acquire(),['0','7'])
    check('denial leaves all counters untouched',redis('MGET',*keys).splitlines(),['7','200','2000'])
    for corrupt in ['1.5','1.0','01']:
        redis('SET',keys[0],corrupt,'EX',300)
        check('corrupt hour counter rejected '+corrupt,'INVALID_QUOTA_COUNTER' in '\n'.join(acquire(100)),True)
        check('corrupt hour counter remains untouched '+corrupt,redis('GET',keys[0]),corrupt)
    redis('SET',keys[0],0,'EX',300); redis('SET',keys[1],'invalid','EX',300)
    check('even corrupt retired daily counter is ignored',acquire(),['1','1'])

def main():
    global phase
    server=p.start_provider()
    connection={'baseUrl':f'http://127.0.0.1:{server.server_port}/v1','model':'fixture-valid','apiKey':'fixture-key-not-a-real-secret'}
    body={'connection':connection,'query':'去大理旅行，喜欢美食和散步','days':2,'budget':3000,'peopleNum':2}
    try:
        lua_checks()
        tokens=[]
        for role in ['USER','USER','ADMIN']:
            name='aq_'+uuid.uuid4().hex[:12]; credentials={'username':name,'password':'QuotaTest123456'}
            call('register '+role,'/auth/register',credentials)
            uid=int(t.sql(f"SELECT id FROM sys_user WHERE username='{name}'")); users.append(uid)
            owned_keys.update(user_keys(uid))
            if role=='ADMIN': t.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={uid}")
            tokens.append(call('login '+role,'/auth/login',credentials)['data']['accessToken'])
        a,b,admin=tokens
        call('anonymous usage denied','/ai/planner/usage',http=401,code=401)
        call('normal user cannot read audit','/admin/ai/recommend/log',token=a,http=403,code=403)
        usage=call('initial usage','/ai/planner/usage',token=a)['data']
        check('default hourly rate limit retained',usage['quota']['hourly']['limit'],20)
        for k in ['daily','globalDaily']:
            check(k+' explicitly disabled',usage['quota'][k],dict(enabled=False,limit=None,used=None,remaining=None,resetAt=None))
        check('own initial usage',usage['quota']['hourly']['used'],0)
        call('one connection attempt','/ai/planner/test',{'connection':connection},a)
        call('retry consumes two','/ai/planner/generate',{**body,'connection':{**connection,'model':'fixture-retry'}},a)
        call('failed provider attempt charged','/ai/planner/test',{'connection':{**connection,'model':'fixture-auth'}},a,code=3004)
        usage=call('attempts versus operations','/ai/planner/usage',token=a)['data']
        check('4 attempts charged',usage['quota']['hourly']['used'],4)
        check('3 operations including failed call',[usage['today'][k] for k in ['operations','succeeded','failed']],[3,2,1])
        call('free rule parsing','/ai/recommend/intent',{'query':'大理两天预算3000元'},a)
        call('free route matching','/ai/recommend/match',{'intent':{},'topN':3},a)
        check('rules do not consume quota',call('usage after rules','/ai/planner/usage',token=a)['data']['quota']['hourly']['used'],4)
        for i in range(15): call('fill hour '+str(i+1),'/ai/planner/test',{'connection':connection},a)
        call('only one remaining: retry denied','/ai/planner/generate',{**body,'connection':{**connection,'model':'fixture-invalid'}},a,code=429)
        check('retry cannot bypass quota',p.counts['fixture-invalid'],1)
        sent=sum(p.counts.values())
        call('hourly limit rejects test','/ai/planner/test',{'connection':connection},a,code=429)
        call('hourly limit rejects generation','/ai/planner/generate',body,a,code=429)
        check('quota rejection never reaches provider',sum(p.counts.values()),sent)
        usage=call('exhausted usage','/ai/planner/usage',token=a)['data']
        check('zero remaining',usage['quota']['hourly']['remaining'],0)
        check('counted 20 provider attempts',sent,20)
        check('19 operations, no phantom denied calls',[usage['today'][k] for k in ['operations','succeeded','failed']],[19,17,2])
        usage_b=call('other user isolated','/ai/planner/usage?userId='+str(users[0]),token=b)['data']
        check('query userId cannot select another account',usage_b['quota']['hourly']['used'],0)
        # Only this temporary user's daily key is seeded; real users/global are untouched.
        redis('SET',user_keys(users[1])[1],200,'EX',300)
        call('saturated old daily counter allows test','/ai/planner/test',{'connection':connection},b)
        call('saturated old daily counter allows generation','/ai/planner/generate',body,b)
        call('saturated old daily counter allows both invalid-structure attempts','/ai/planner/generate',{**body,'connection':{**connection,'model':'fixture-invalid'}},b,code=3004)
        check('new hourly count includes test, generation and retry',redis('GET',user_keys(users[1])[0]),'4')
        check('old daily counter is unchanged',redis('GET',user_keys(users[1])[1]),'200')
        check('calls despite old daily cap reached provider',sum(p.counts.values()),24)
        audit=call('admin paginated own fixture logs',f'/admin/ai/recommend/log?userId={users[0]}&size=2',token=admin)['data']
        check('admin page size',len(audit['records']),2)
        check('admin total includes free rule audit',audit['total'],20)
        check('admin fields do not expose provider data',sorted(audit['records'][0]),sorted(['id','userId','scene','model','costMs','success','isFallback','createTime']))
        call('admin invalid page rejected','/admin/ai/recommend/log?size=101',token=admin,code=400)
    except Exception as error:
        check('script completed',str(error),'no error')
    finally:
        for uid in users:
            check('fixture sessions removed '+str(uid),clean_sessions(uid)>=1,True)
            check('fixture AI state cleaned '+str(uid),clean_ai_state(uid)>=0,True)
            owned_keys.update(user_keys(uid))
            t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
        if owned_keys: redis('DEL',*sorted(owned_keys))
        check('temporary users cleaned',t.sql('SELECT COUNT(*) FROM sys_user WHERE id IN ('+','.join(map(str,users))+')') if users else '0','0')
        server.shutdown(); server.server_close(); phase='COMPLETE'; save()
    failed=sum(not row['passed'] for row in rows)
    print(f'{len(rows)-failed}/{len(rows)} passed; {out}')
    return bool(failed)

if __name__=='__main__': raise SystemExit(main())
