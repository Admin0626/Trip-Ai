"""Real HTTP/MySQL/Redis/local SMTP tests. Only dedicated loopback Redis 6387 is permitted.
Codes, passwords, tokens and message bodies are never written to evidence.
"""
import concurrent.futures, datetime as dt, hashlib, json, os, re, subprocess, time, urllib.error, urllib.parse, urllib.request, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE=os.getenv('TEST_BASE_URL','http://127.0.0.1:8087/api');SINK='http://127.0.0.1:8027'
OUT=ROOT/'docs/dev/evidence/email-auth'/(dt.datetime.now().strftime('%Y%m%d-%H%M%S')+'-http.json')
assert urllib.parse.urlparse(BASE).hostname in {'127.0.0.1','localhost'}
checks=[];users=[];emails=[];PASSWORD='EmailTest123';marker=uuid.uuid4().hex[:9]
def save():
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps({'mode':'REAL_HTTP_MYSQL_REDIS_6387_SMTP_LOOPBACK','checks':checks,'summary':{'total':len(checks),'passed':sum(c['passed'] for c in checks)}},ensure_ascii=False,indent=2),encoding='utf8')
def check(name,actual,expected=True):checks.append({'name':name,'actual':actual,'expected':expected,'passed':actual==expected});save()
def sql(q):return subprocess.check_output(['mysql','-u','root','-N','-B','--default-character-set=utf8mb4','trip_llm','-e',q],text=True,env={**os.environ,'MYSQL_PWD':os.getenv('MYSQL_PASSWORD','123456')}).strip()
def redis(*args):return subprocess.check_output(['redis-cli','-p','6387','--raw',*map(str,args)],text=True,encoding='utf8').strip()
def digest(s):return hashlib.sha256(s.encode()).hexdigest()
def mail(email,path='/message'):return json.load(urllib.request.urlopen(SINK+path+'?email='+urllib.parse.quote(email),timeout=5))
def email(label):
    value=f'{label}-{marker}@trip.invalid';emails.append(value);return value
def call(path,body=None,token='',method='POST'):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(BASE+path,None if body is None else json.dumps(body).encode(),headers,method=method)
    try:r=urllib.request.urlopen(req,timeout=30)
    except urllib.error.HTTPError as e:r=e
    return json.loads(r.read())
def expect(name,path,body=None,token='',method='POST',code=200):
    result=call(path,body,token,method);check(name,result['code'],code);return result
def create(label,email_value=None,code=None):
    name=f'em_{label}_{marker}'[:20];body={'username':name,'password':PASSWORD}
    if email_value:body.update(email=email_value,emailCode=code)
    result=expect('register '+label,'/auth/register',body)
    uid=int(sql(f"SELECT id FROM sys_user WHERE username='{name}'"));users.append((uid,name))
    return uid,name,result['data']
def login(name,password=PASSWORD):return call('/auth/login',{'username':name,'password':password})
def cooldown(e):redis('DEL','trip:mail:cooldown:'+digest(e))
def send(e,purpose='RESET',token='',password=PASSWORD):
    cooldown(e)
    # Independent scenarios reset only this fixture address's hourly budget.
    redis('DEL','trip:mail:limit:email:'+digest(e))
    path={'REGISTER':'/auth/email/code','RESET':'/auth/password/code','BIND':'/user/email/code'}[purpose]
    body={'email':e}
    if purpose=='BIND':body['password']=password
    expect('SMTP send '+purpose,path,body,token)
    return mail(e)['code']
def challenge(e,purpose='RESET',uid=None):return f"trip:mail:code:{purpose}:{uid if uid else 'public'}:{digest(e)}"
def limits():redis('DEL','trip:mail:limit:ip:'+digest('127.0.0.1'),'trip:mail:limit:global')
def reset(e,c,p='ResetNew123'):return {'email':e,'code':c,'newPassword':p}
def main():
  try:
    limits()
    expect('binding requires login','/user/email/verify',{'email':email('anon'),'code':'123456'},code=401)
    expect('strict email type','/auth/email/code',{'email':123},code=400)
    expect('strict extra fields','/auth/password/code',{'email':'x@trip.invalid','purpose':'BIND'},code=400)
    expect('reject header injection','/auth/email/code',{'email':'x@trip.invalid\r\nBcc:other@trip.invalid'},code=400)
    plain,pname,pdata=create('plain');check('registration without email remains available',pdata['emailVerified'],False)
    pt=login(pname)['data']['accessToken']
    expect('profile cannot bypass email ownership','/user/profile',{'email':email('bypass')},pt,'PUT',400)
    e=email('verified');c=send(e,'REGISTER');check('local SMTP produces six digit code',bool(re.fullmatch(r'\d{6}',c)))
    state=redis('HGETALL',challenge(e,'REGISTER'));check('Redis does not store plaintext code',c not in state)
    check('challenge expiry ten minutes',590<=int(redis('TTL',challenge(e,'REGISTER')))<=600)
    expect('mail cooldown applies','/auth/email/code',{'email':e},code=429)
    wrong='000000' if c!='000000' else '999999'
    expect('wrong registration code','/auth/register',{'username':'bad_'+marker,'password':PASSWORD,'email':e,'emailCode':wrong},code=400)
    expect('registration rejects numeric code','/auth/register',{'username':'numeric_'+marker,'password':PASSWORD,'email':e,'emailCode':int(c)},code=400)
    uid,name,vo=create('verified',e.upper(),c);check('verified registration normalizes email',vo['email'],e)
    check('verified registration flag',vo['emailVerified']);check('verification persisted',sql(f'SELECT email_verified FROM sys_user WHERE id={uid}'),'1')
    check('registration code consumed',redis('EXISTS',challenge(e,'REGISTER')),'0')
    first=login(name)['data'];second=login(name)['data'];token=first['accessToken']
    expect('registration code cannot reset password','/auth/password/reset',reset(e,c),code=400)
    c=send(e);known=call('/auth/password/code',{'email':e});check('known address cooldown response',known['code'],429)
    for n in range(5):expect(f'wrong reset attempt {n+1}','/auth/password/reset',reset(e,wrong if wrong!=c else '111111'),code=400)
    expect('correct code rejected after five failures','/auth/password/reset',reset(e,c),code=400)
    check('exhausted challenge removed',redis('EXISTS',challenge(e)),'0')
    c=send(e);redis('EXPIRE',challenge(e),1);time.sleep(1.2)
    expect('expired code rejected','/auth/password/reset',reset(e,c),code=400)
    c=send(e);c2=send(e)
    if c!=c2:expect('resend invalidates previous code','/auth/password/reset',reset(e,c),code=400)
    expect('numeric code rejected','/auth/password/reset',reset(e,int(c2)),code=400)
    expect('weak new password rejected','/auth/password/reset',reset(e,c2,'12345678'),code=400)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(call,'/auth/password/reset',reset(e,c2,p)) for p in ['Parallel123','Parallel456']]
        result=[f.result()['code'] for f in futures]
    check('concurrent reset only succeeds once',sorted(result),[200,400])
    changed='Parallel123' if result[0]==200 else 'Parallel456'
    check('old password rejected',login(name)['code'],1001)
    expect('first old access revoked','/user/profile',None,token,'GET',401)
    expect('second old access revoked','/user/profile',None,second['accessToken'],'GET',401)
    expect('first old refresh revoked','/auth/refresh',{'refreshToken':first['refreshToken']},code=401)
    expect('second old refresh revoked','/auth/refresh',{'refreshToken':second['refreshToken']},code=401)
    new=login(name,changed);check('new password works',new['code'],200);token=new['data']['accessToken']
    expect('successful reset code cannot replay','/auth/password/reset',reset(e,c2),code=400)
    limits()
    unknown=email('unknown');u=send(unknown);knowncode=send(e)
    cooldown(unknown);cooldown(e)
    unknown_response=expect('unknown recovery accepted','/auth/password/code',{'email':unknown})
    known_response=expect('known recovery accepted','/auth/password/code',{'email':e})
    check('unknown and known recovery responses identical',unknown_response['data'],known_response['data'])
    check('unknown account still follows SMTP path',len(u),6)
    expect('unknown address cannot reset','/auth/password/reset',reset(unknown,u),code=400)
    legacy=email('legacy');sql(f"UPDATE sys_user SET email='{legacy}',email_verified=0 WHERE id={plain}")
    lc=send(legacy);expect('legacy unverified email cannot recover','/auth/password/reset',reset(legacy,lc),code=400)
    bc=send(legacy,'BIND',pt)
    expect('occupied email rejected for another user','/user/email/verify',{'email':legacy,'code':bc},token,code=409)
    cross=email('cross-user');cc=send(cross,'BIND',pt)
    expect('unused email challenge is scoped to sender user','/user/email/verify',{'email':cross,'code':cc},token,code=400)
    expect('bind verifies old account','/user/email/verify',{'email':legacy,'code':bc},pt)
    expect('bind code replay rejected','/user/email/verify',{'email':legacy,'code':bc},pt,code=400)
    lc=send(legacy);expect('verified legacy account can now recover','/auth/password/reset',reset(legacy,lc))
    pending=send(e);replacement=email('replacement');bc=send(replacement,'BIND',token,changed)
    expect('replace verified email','/user/email/verify',{'email':replacement,'code':bc},token)
    expect('previous email cannot use pending reset','/auth/password/reset',reset(e,pending),code=400)
    check('replacement persisted',sql(f'SELECT email FROM sys_user WHERE id={uid}'),replacement)
    pending=send(replacement);expect('logged-in password change','/user/password',{'oldPassword':changed,'newPassword':'ChangedAgain123'},token,'PUT')
    expect('pending reset rejected after password change','/auth/password/reset',reset(replacement,pending),code=400)
    limits();disabled=email('disabled');sql(f"UPDATE sys_user SET email='{disabled}',email_verified=1,status=0 WHERE id={plain}")
    dc=send(disabled);expect('disabled account cannot recover','/auth/password/reset',reset(disabled,dc),code=400)
    failed=email('failed');mail(failed,'/reject');expect('SMTP failure reported','/auth/email/code',{'email':failed},code=503)
    check('failed delivery leaves no challenge',redis('EXISTS',challenge(failed,'REGISTER')),'0')
    check('failed delivery releases cooldown',redis('EXISTS','trip:mail:cooldown:'+digest(failed)),'0')
    mail(failed,'/clear');send(failed,'REGISTER')
    limiting=email('limiting');redis('SET','trip:mail:limit:email:'+digest(limiting),5,'EX',3600)
    expect('email hourly quota','/auth/email/code',{'email':limiting},code=429)
    redis('SET','trip:mail:limit:ip:'+digest('127.0.0.1'),20,'EX',3600)
    expect('IP hourly quota','/auth/email/code',{'email':email('iplimit')},code=429)
    limits();redis('SET','trip:mail:limit:global',100,'EX',3600)
    expect('global hourly quota','/auth/email/code',{'email':email('global')},code=429)
  except Exception as exc:
    check('script completed',type(exc).__name__,'no exception')
  finally:
    for uid,name in users:
      sql(f"DELETE FROM user_preference WHERE user_id={uid};DELETE FROM user_behavior WHERE user_id={uid};DELETE FROM sys_user WHERE id={uid} AND username='{name}'")
      keys=redis('KEYS',f'trip:auth:session:{{{uid}}}:*').splitlines()
      for key in keys:redis('DEL',key)
      check('fixture account removed '+name,sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
      check('fixture sessions removed '+name,redis('KEYS',f'trip:auth:session:{{{uid}}}:*'),'')
    for e in emails:
      for key in redis('KEYS','trip:mail:*'+digest(e)).splitlines():redis('DEL',key)
      mail(e,'/clear')
    limits();check('fixture mail challenges removed',all(not redis('KEYS','trip:mail:*'+digest(e)) for e in emails))
    save()
  passed=sum(c['passed'] for c in checks);print(f'{passed}/{len(checks)} passed; {OUT}');return int(passed!=len(checks))
if __name__=='__main__':raise SystemExit(main())
