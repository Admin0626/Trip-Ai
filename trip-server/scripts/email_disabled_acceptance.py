"""Default-disabled mail service acceptance; no real SMTP configuration required."""
import json, urllib.request, urllib.error
import email_auth_acceptance as fixture
checks=[];user=None
def expect(name,path,body,token='',code=503):
    result=fixture.call(path,body,token);checks.append({'name':name,'actual':result['code'],'expected':code,'passed':result['code']==code});return result
try:
    address=fixture.email('disabled-service')
    expect('registration email unavailable','/auth/email/code',{'email':address})
    expect('password recovery email unavailable','/auth/password/code',{'email':address})
    name='em_off_'+fixture.marker;result=expect('plain registration stays available','/auth/register',{'username':name,'password':fixture.PASSWORD},code=200)
    user=(result['data']['id'],name)
    login=expect('plain login stays available','/auth/login',{'username':name,'password':fixture.PASSWORD},code=200)
    expect('logged-in email binding unavailable','/user/email/code',{'email':address,'password':fixture.PASSWORD},login['data']['accessToken'])
    expect('unissued reset cannot modify password','/auth/password/reset',fixture.reset(address,'123456'),code=400)
    exists=fixture.redis('KEYS','trip:mail:*'+fixture.digest(address))
    checks.append({'name':'disabled service creates no challenge or quota','actual':exists,'expected':'','passed':exists==''})
finally:
    if user:
        uid,name=user;fixture.sql(f"DELETE FROM sys_user WHERE id={uid} AND username='{name}'")
        for key in fixture.redis('KEYS',f'trip:auth:session:{{{uid}}}:*').splitlines():fixture.redis('DEL',key)
    out=fixture.OUT.with_name(fixture.OUT.stem+'-disabled.json');out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps({'mode':'REAL_HTTP_DEFAULT_MAIL_DISABLED','checks':checks},ensure_ascii=False,indent=2),encoding='utf8')
passed=sum(c['passed'] for c in checks);print(f'{passed}/{len(checks)} passed; {out}')
raise SystemExit(int(passed!=len(checks)))
