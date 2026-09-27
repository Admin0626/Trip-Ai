"""Actual local HTTP/MySQL/Redis acceptance for the basic user/content loop.
Creates one temporary account and removes only that account and its owned rows.
"""
import base64, datetime as dt, json, os, subprocess, urllib.error, urllib.request, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; BASE=os.getenv('TEST_BASE_URL','http://127.0.0.1:8080/api')
PWD=os.getenv('MYSQL_PASSWORD','123456'); checks=[]; uid=None; feedback_id=None; token=''; refresh=''
OUT=ROOT/'docs/dev/evidence/basic-user'/(dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'-http.json')
def sql(q): return subprocess.check_output(['mysql','-u','root','-N','-B','--default-character-set=utf8mb4','trip_llm','-e',q],text=True,env={**os.environ,'MYSQL_PWD':PWD}).strip()
def red(v):
    if isinstance(v,dict): return {k:'<redacted>' if k.lower() in {'password','token','accesstoken','refreshtoken','authorization'} else red(x) for k,x in v.items()}
    if isinstance(v,list): return [red(x) for x in v]
    return v
def save(): OUT.parent.mkdir(parents=True,exist_ok=True); OUT.write_text(json.dumps({'mode':'REAL_LOCAL_BASIC_HTTP_MYSQL_REDIS','checks':checks},ensure_ascii=False,indent=2),encoding='utf8')
def check(name,actual,expected): checks.append({'name':name,'actual':red(actual),'expected':expected,'passed':actual==expected}); save()
def call(name,path,body=None,method='GET',auth=True,expected=200):
    headers={'Content-Type':'application/json'}
    if token and auth: headers['Authorization']='Bearer '+token
    data=None if body is None else json.dumps(body,ensure_ascii=False).encode()
    req=urllib.request.Request(BASE+path,data,headers,method=method)
    try:r=urllib.request.urlopen(req,timeout=30)
    except urllib.error.HTTPError as e:r=e
    value=json.loads(r.read());check(name,value.get('code'),expected);return r,value
def main():
    global uid,feedback_id,token,refresh
    name='basic_'+uuid.uuid4().hex[:12]; password='BasicLoop123'; creds={'username':name,'password':password,'nickname':'基础验收'}
    try:
        _,result=call('register', '/auth/register',creds,'POST',False)
        uid=int(sql(f"SELECT id FROM sys_user WHERE username='{name}'"))
        _,result=call('login','/auth/login',creds,'POST',False); token=result['data']['accessToken'];refresh=result['data']['refreshToken']
        call('profile read','/user/profile'); call('preference read','/user/preference'); call('stats read','/user/stats')
        call('profile update','/user/profile',{'nickname':'基础验收已改','avatar':'','phone':'','email':'','city':'杭州'},'PUT')
        pref={'preferenceTags':['美食'],'avoidTags':['探险'],'budgetMin':1000,'budgetMax':3000,'preferredDays':3,'companions':'family','pace':'relaxed'}
        call('preference update','/user/preference',pref,'PUT')
        _,refreshed=call('refresh rotates tokens','/auth/refresh',{'refreshToken':refresh},'POST',False); old=refresh; token=refreshed['data']['accessToken'];refresh=refreshed['data']['refreshToken']
        call('old refresh rejected','/auth/refresh',{'refreshToken':old},'POST',False,401)
        call('public destination page','/destination/page?size=2',None,'GET',False)
        _,dest=call('destination detail','/destination/1',None,'GET',False)
        call('destination attractions','/destination/1/attractions',None,'GET',False);call('public banner list','/banner/list',None,'GET',False)
        _,created=call('feedback create','/feedback',{'type':'BUG','title':'基础验收反馈','content':'用于接口闭环验收','contact':'','images':[]},'POST');feedback_id=created['data']
        _,mine=call('my feedback page','/feedback/my/page');check('feedback returned own row',any(x['id']==feedback_id for x in mine['data']['records']),True)
        call('user cannot read admin feedback','/admin/feedback/page',None,'GET',True,403)
        call('invalid preference type','/user/preference',{'preferenceTags':['美食'],'avoidTags':[],'budgetMin':'1000','budgetMax':3000,'preferredDays':3,'companions':'family','pace':'relaxed'},'PUT',True,400)
        call('logout revokes session','/auth/logout',None,'POST'); old_token=token; token='';
        req=urllib.request.Request(BASE+'/user/profile',headers={'Authorization':'Bearer '+old_token})
        try:r=urllib.request.urlopen(req,timeout=30)
        except urllib.error.HTTPError as e:r=e
        check('revoked access rejected',r.status,401)
    finally:
        if feedback_id: sql(f'DELETE FROM feedback WHERE id={feedback_id}')
        if uid:
            sql(f'DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM user_behavior WHERE user_id={uid}; DELETE FROM route_like WHERE user_id={uid}; DELETE FROM route_favorite WHERE user_id={uid}; DELETE FROM route_booking WHERE user_id={uid}; DELETE FROM route_comment WHERE user_id={uid}; DELETE FROM user_plan WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            check('temporary account cleaned',sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        save()
    failed=sum(not x['passed'] for x in checks); print(f'{len(checks)-failed}/{len(checks)} passed; {OUT}'); return 1 if failed else 0
if __name__=='__main__': raise SystemExit(main())
