"""Real local HTTP/MySQL/Redis export/import/closure. Generated fixtures only."""
import argparse, copy, csv, datetime as dt, io, json, threading, urllib.request, urllib.error, urllib.parse, uuid
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from circuit_acceptance import Run
from redis_fixture import clean_sessions,clean_ai_state,session_keys

def main(args):
    a=Run(args);a.out=Path(__file__).resolve().parents[2]/'docs/dev/evidence/basic-data'/f'{dt.datetime.now():%Y%m%d-%H%M%S}-{args.label}-http.json'
    a.report.update(mode='REAL_LOCAL_HTTP_MYSQL_REDIS_NO_MODEL',phase='RUNNING');dids=[];rids=[];marker='bd'+uuid.uuid4().hex[:8]
    def raw(path,payload=None,headers=None,token='',method='GET'):
        h=dict(headers or {});
        if token:h['Authorization']='Bearer '+token
        req=urllib.request.Request(a.base+path,data=payload,headers=h,method=method)
        try:r=urllib.request.urlopen(req,timeout=60)
        except urllib.error.HTTPError as e:r=e
        with r:return r.status,dict(r.headers),r.read()
    def upload(name,batch,token,commit=False,rid=None,expected=(200,200),confirmation='确认导入',content=None,record=True):
        bound='boundary'+uuid.uuid4().hex
        data=(json.dumps(batch,ensure_ascii=False).encode() if content is None else content)
        parts=[f'--{bound}\r\nContent-Disposition: form-data; name="file"; filename="fixture.json"\r\nContent-Type: application/json\r\n\r\n'.encode()+data+b'\r\n']
        if commit:
            for k,v in [('requestId',rid or str(uuid.uuid4())),('confirmation',confirmation)]:parts.append(f'--{bound}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        parts.append(f'--{bound}--\r\n'.encode());path='/admin/catalog/import/'+('commit' if commit else 'preview')
        status,_,body=raw(path,b''.join(parts),{'Content-Type':'multipart/form-data; boundary='+bound},token,'POST');result=json.loads(body)
        if record:a.check(name,[status,result.get('code')],list(expected),request={'path':path,'batch':batch if content is None else '<bounded malformed file>','requestId':rid},response=result)
        return result
    def dest(name):return dict(name=name,province='浙江省',city='验收城市',longitude=120,latitude=30,coverImg='https://example.com/test.jpg',tags=['nature'],status=0)
    def envelope(kind,records):return {'version':1,'type':kind,'records':records}
    try:
        normal=a.register('n');admin=a.register('adm');other=a.register('o');race=a.register('r')
        a.sql(f"UPDATE sys_user SET role='ADMIN',nickname=' =SUM(1,1)' WHERE id={admin['id']}")
        ap=a.login(admin,'InitialPass123','admin login');at=ap['accessToken'];p=a.login(normal,'InitialPass123','normal login');p2=a.login(normal,'InitialPass123','normal second device');ot=a.login(other,'InitialPass123','unrelated user login')['accessToken']
        for token,code in [('',401),(p['accessToken'],403)]:
            status,_,b=raw('/admin/user/export',token=token);a.check('export permission '+str(code),[status,json.loads(b)['code']],[code,code])
            upload('import permission '+str(code),envelope('DESTINATION',[dest(marker)]),token,expected=(code,code))
        status,headers,b=raw('/admin/user/export?keyword='+admin['name'],token=at)
        rows=list(csv.reader(io.StringIO(b.decode('utf-8-sig'))));a.check('CSV actual HTTP/status and BOM',[status,b.startswith(b'\xef\xbb\xbf')],[200,True]);a.check('CSV attachment and no store',['attachment' in headers.get('Content-Disposition',''),headers.get('Cache-Control')],[True,'no-store'])
        a.check('CSV filtered single user',len(rows),2,response={'columns':rows[0],'row':rows[1]});a.check('CSV formula escaped',rows[1][2].startswith("'"),True);a.check('CSV no credentials',not any('password' in x.lower() or 'token' in x.lower() for x in rows[0]),True)
        status,_,b=raw('/admin/user/export?status=2',token=at);a.check('invalid export filter',[status,json.loads(b)['code']],[200,400])
        status,_,b=raw('/admin/user/export?keyword='+marker+'nomatch',token=at);a.check('empty export has header only',len(list(csv.reader(io.StringIO(b.decode('utf-8-sig'))))),1)
        batch=envelope('DESTINATION',[dest(marker+'a'),dest(marker+'b')]);before=a.sql(f"SELECT COUNT(*) FROM destination WHERE name LIKE '{marker}%'")
        preview=upload('destination preview',batch,at);a.check('valid preview',preview['data']['valid'],True);a.check('preview does not write',a.sql(f"SELECT COUNT(*) FROM destination WHERE name LIKE '{marker}%'"),before)
        key=str(uuid.uuid4());res=upload('destination commit',batch,at,True,key);dids.extend(res['data']['ids']);again=upload('same file replay',batch,at,True,key);a.check('idempotent same IDs and replay',[again['data']['ids'],again['data']['replayed']],[dids,True]);upload('same request changed file',envelope('DESTINATION',[dest(marker+'c')]),at,True,key,expected=(200,409))
        a.check('all imported destinations are offline',a.sql('SELECT SUM(status) FROM destination WHERE id IN ('+','.join(map(str,dids))+')'),'0')
        duplicate=upload('database duplicate preview',batch,at);a.check('duplicate marked invalid',duplicate['data']['valid'],False)
        spots=envelope('ATTRACTION',[{'destinationId':dids[0],'name':marker+'spot','status':0}]);upload('attraction preview',spots,at);sid=upload('attraction commit',spots,at,True)['data']['ids'][0]
        route={'title':marker+'route','destinationId':dids[0],'coverImg':'https://example.com/route.jpg','days':1,'price':50,'status':0,'dayList':[{'title':'测试当天','items':[{'title':'自由活动','attractionId':0}]}]}
        rb=envelope('ROUTE',[route]);a.check('valid route preview',upload('route preview',rb,at)['data']['valid'],True);rids.extend(upload('route commit',rb,at,True)['data']['ids']);detail=a.call('imported daily itinerary',f'/admin/catalog/routes/{rids[0]}',token=at)['data'];a.check('daily item retained',detail['dayList'][0]['items'][0]['title'],'自由活动')
        for label,bad in [('mixed invalid row',[dest(marker+'good'),{**dest(marker+'bad'),'longitude':'120'}]),('unknown ID binding',[{**dest(marker+'id'),'id':999999}]),('unknown comma tag',[{**dest(marker+'tag'),'tags':['a,b']}]),('file duplicate',[dest(marker+'dup'),dest(marker+'dup')]),('published not allowed',[{**dest(marker+'public'),'status':1}])]:
            bch=envelope('DESTINATION',bad);v=upload(label+' preview',bch,at);a.check(label+' invalid',v['data']['valid'],False);upload(label+' whole batch denied',bch,at,True,expected=(200,400))
        a.check('invalid batch wrote no extra destination',a.sql(f"SELECT COUNT(*) FROM destination WHERE name LIKE '{marker}%'") ,'2')
        for label,bad in [('invalid parent',{**route,'title':marker+'noParent','destinationId':999999999}),('days mismatch',{**route,'title':marker+'days','days':2}),('hidden attraction',{**route,'title':marker+'spotRoute','dayList':[{'title':'一天','items':[{'title':'关联','attractionId':sid}]}]})]:
            v=upload(label,envelope('ROUTE',[bad]),at);a.check(label+' invalid',v['data']['valid'],False)
        for label,bch in [('empty records',envelope('DESTINATION',[])),('51 records',envelope('DESTINATION',[dest(marker+str(i)) for i in range(51)])),('overflow version',{**batch,'version':4294967297})]:upload(label,bch,at,expected=(200,400))
        upload('duplicate JSON fields',None,at,content=b'{"version":2,"version":1,"type":"DESTINATION","records":[{}]}',expected=(200,400));upload('malformed JSON',None,at,content=b'{',expected=(200,400));upload('oversized file',None,at,content=b' '*1048577,expected=(200,400));upload('commit confirmation missing',envelope('DESTINATION',[dest(marker+'confirm')]),at,True,confirmation='',expected=(200,400))
        # Preview must use database collation, including accent-insensitive duplicate names.
        atomic=envelope('DESTINATION',[dest(marker+'cafe'),dest(marker+'caf\u00e9')]);v=upload('collation duplicate preview',atomic,at)
        a.check('collation duplicate marked invalid',v['data']['valid'],False)
        upload('collation duplicate commits nothing',atomic,at,True,expected=(200,400 if not v['data']['valid'] else 409))
        a.check('atomic failed import inserts nothing',a.sql(f"SELECT COUNT(*) FROM destination WHERE name LIKE '{marker}%'") ,'2')
        large=upload('controller file size rejection',None,at,content=b' '*6291456,expected=(200,400))
        a.check('oversize import message describes JSON', 'JSON' in large['message'] and '1MiB' in large['message'],True)
        global_large=upload('server multipart size rejection',None,at,content=b' '*11534336,expected=(200,400))
        a.check('global multipart rejection describes JSON', 'JSON' in global_large['message'] and '1MiB' in global_large['message'],True)
        parallel=envelope('DESTINATION',[dest(marker+'parallel')]);parallel_key=str(uuid.uuid4());sync=threading.Barrier(2)
        def import_once():
            sync.wait();return upload('parallel import',parallel,at,True,parallel_key,record=False)
        with ThreadPoolExecutor(2) as pool:
            futures=[pool.submit(import_once) for _ in range(2)];commits=[f.result() for f in futures]
        for result in commits:dids.extend(result.get('data',{}).get('ids',[]))
        dids=list(dict.fromkeys(dids))
        a.check('parallel replay succeeds exactly once',[c['code'] for c in commits],[200,200],response=commits)
        a.check('parallel replay IDs match',commits[0]['data']['ids'],commits[1]['data']['ids'])
        a.check('parallel replay flags',sorted(c['data']['replayed'] for c in commits),[False,True])
        stale=envelope('DESTINATION',[dest(marker+'stale'),dest(marker+'never')]);a.check('stale preview initially valid',upload('stale preview',stale,at)['data']['valid'],True)
        occupied=a.call('catalog changed after preview','/admin/catalog/destinations',dest(marker+'stale'),'POST',at)['data'];dids.append(occupied)
        upload('commit revalidates changed catalog',stale,at,True,expected=(200,400))
        a.check('changed preview writes no second row',a.sql(f"SELECT COUNT(*) FROM destination WHERE name='{marker}never'"),'0')
        close={'password':'InitialPass123','confirmation':'注销账号'}
        a.call('anonymous closure denied','/user/account/close',close,'POST',expected=(401,401));a.call('admin closure protected','/user/account/close',close,'POST',at,expected=(200,403))
        for label,update in [('wrong password',{'password':'WrongPass123'}),('wrong confirmation',{'confirmation':'yes'}),('extra owner',{'userId':other['id']}),('numeric password',{'password':123})]:a.call(label,'/user/account/close',{**close,**update},'POST',p['accessToken'],expected=(200,400))
        # Publish the owned fixture route through the normal admin API for booking checks.
        a.call('publish parent',f'/admin/catalog/destinations/{dids[0]}/status',{'status':1},'PUT',at);a.call('publish route',f'/admin/catalog/routes/{rids[0]}/status',{'status':1},'PUT',at)
        book={'routeId':rids[0],'travelDate':str(dt.date.today()+dt.timedelta(days=1)),'peopleNum':1,'contactName':'验收用户','contactPhone':'13800009999','remark':'fixture'}
        booking=a.call('own active booking','/interaction/booking',book,'POST',p['accessToken'])['data'];a.call('active booking blocks closure','/user/account/close',close,'POST',p['accessToken'],expected=(200,409))
        bid=booking['bookingId'];a.call('cancel owned booking',f'/interaction/booking/{bid}/cancel',method='PUT',token=p['accessToken'])
        a.sql(f"UPDATE sys_user SET phone='13800008888',email='{marker}@example.invalid',city='测试城市' WHERE id={normal['id']}")
        a.call('close own account','/user/account/close',close,'POST',p['accessToken']);a.check('closure clears profile and tombstones',a.sql(f"SELECT CONCAT(deleted,':',status,':',nickname,':',IFNULL(phone,''),':',IFNULL(email,''),':',city) FROM sys_user WHERE id={normal['id']}"),'1:0:已注销用户:::')
        a.check('all own sessions removed',session_keys(normal['id']),[]);a.revoked('first closed device',p);a.revoked('second closed device',p2)
        a.call('closed login denied','/auth/login',{'username':normal['name'],'password':'InitialPass123'},'POST',expected=(200,1001));a.call('unrelated account stays active','/user/profile',token=ot)
        rp=a.login(race,'InitialPass123','race login');barrier=threading.Barrier(2)
        def compete(path,body):barrier.wait();return a.request(path,body,'POST',rp['accessToken'])
        with ThreadPoolExecutor(2) as pool:
            f1=pool.submit(compete,'/user/account/close',close);f2=pool.submit(compete,'/interaction/booking',book);results=[f1.result(),f2.result()]
        closed=a.sql(f"SELECT deleted FROM sys_user WHERE id={race['id']}")=='1';active=int(a.sql(f"SELECT COUNT(*) FROM route_booking WHERE user_id={race['id']} AND status IN (0,1)"))
        a.check('closure and booking race remains consistent',closed and active==0 or not closed and active==1,True,response=results)
        refresh_race=a.register('f');fp=a.login(refresh_race,'InitialPass123','refresh race login');refresh_sync=threading.Barrier(2)
        def closure_refresh(path,body,token=''):
            refresh_sync.wait();return a.request(path,body,'POST',token)
        with ThreadPoolExecutor(2) as pool:
            fc=pool.submit(closure_refresh,'/user/account/close',close,fp['accessToken']);fr=pool.submit(closure_refresh,'/auth/refresh',{'refreshToken':fp['refreshToken']});close_result,refresh_result=fc.result(),fr.result()
        a.check('closure wins eventual state against refresh',close_result['body']['code'],200,response=[close_result,refresh_result])
        a.check('refresh cannot revive closed account', [a.sql(f"SELECT deleted FROM sys_user WHERE id={refresh_race['id']}"),session_keys(refresh_race['id'])],['1',[]])
        a.revoked('refresh race old pair',fp)
        if refresh_result['body']['code']==200:a.revoked('refresh race returned pair',refresh_result['body']['data'])
        a.report['phase']='COMPLETED'
    except Exception as e:a.check('runner completed',str(e),'no exception');a.report['phase']='FAILED'
    finally:
        for rid in rids:a.sql(f'DELETE FROM route_booking WHERE route_id={rid}; DELETE ri FROM route_item ri JOIN route_day rd ON rd.id=ri.route_day_id WHERE rd.route_id={rid}; DELETE FROM route_day WHERE route_id={rid}; DELETE FROM route WHERE id={rid}')
        for did in dids:a.sql(f'DELETE FROM attraction WHERE destination_id={did}; DELETE FROM destination WHERE id={did}')
        for user in a.users:
            uid=user['id']
            if not uid:continue
            clean_sessions(uid);clean_ai_state(uid);a.sql(f'DELETE FROM catalog_import_batch WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM user_behavior WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('own fixture removed '+str(uid),a.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        a.save()
    print(json.dumps({'summary':a.report['summary'],'phase':a.report['phase'],'evidence':str(a.out)},ensure_ascii=True));return a.report['summary']['failed']>0

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8080/api');p.add_argument('--label',default='final');raise SystemExit(main(p.parse_args()))
