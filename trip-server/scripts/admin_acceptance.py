"""Actual local basic-admin HTTP/MySQL/Redis tests. Touches generated fixtures only."""
import argparse, copy, datetime as dt, subprocess
from password_acceptance import Acceptance as Base, ROOT


class Acceptance(Base):
    def __init__(self, args):
        super().__init__(args)
        self.report['mode'] = 'REAL_LOCAL_BASIC_ADMIN_HTTP_MYSQL_REDIS'
        self.out = ROOT / 'docs/dev/evidence/admin' / self.out.name
        self.dest_ids, self.route_ids, self.banner_ids = [], [], []

    def run(self):
        try:
            user, admin = self.register('user'), self.register('admin')
            self.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']}")
            u = self.login(user, 'InitialPass123', 'user login')['accessToken']
            pair = self.login(admin, 'InitialPass123', 'admin login')
            a = pair['accessToken']
            for path in ['/admin/catalog/destinations','/admin/catalog/routes','/admin/banner/page','/admin/interaction/booking/page','/admin/interaction/comment/page','/admin/user/page','/admin/ai/recommend/log']:
                self.call('anonymous denied '+path, path, expected=(401,401))
                self.call('ordinary user denied '+path, path, token=u, expected=(403,403))
            self.call('ordinary user cannot create destination','/admin/catalog/destinations',{},'POST',u,expected=(403,403))
            for path in ['/admin/banner/1','/admin/interaction/booking/1/status','/admin/interaction/comment/1/status','/admin/user/1/status']:
                self.call('ordinary user cannot write '+path,path,{'status':0},'PUT',u,expected=(403,403))
            self.call('invalid booking filter','/admin/interaction/booking/page?status=4',token=a,expected=(200,400))
            self.call('invalid comment filter','/admin/interaction/comment/page?status=2',token=a,expected=(200,400))
            self.call('invalid user pagination','/admin/user/page?size=101',token=a,expected=(200,400))
            marker = admin['name']
            d = dict(name=marker+'中文目的地',province='TestProvince',city='TestCity',longitude=120,latitude=30,coverImg='https://example.com/admin-test.jpg',intro='Admin fixture',tags=['nature'],bestSeason='spring',avgCost=120,status=1)
            did=self.call('create destination','/admin/catalog/destinations',d,'POST',a)['data'];self.dest_ids.append(did)
            self.call('duplicate destination rejected','/admin/catalog/destinations',d,'POST',a,expected=(200,409))
            self.call('strict destination text','/admin/catalog/destinations',{**d,'name':123},'POST',a,expected=(200,400))
            self.call('invalid destination coordinates','/admin/catalog/destinations',{**d,'longitude':0},'POST',a,expected=(200,400))
            d['intro']='Updated description';self.call('update destination',f'/admin/catalog/destinations/{did}',d,'PUT',a)
            self.check('destination update stored',self.sql(f'SELECT intro FROM destination WHERE id={did}'),d['intro'])
            page=self.call('search destination','/admin/catalog/destinations?keyword='+marker,token=a)['data']
            self.check('destination search contains fixture',[r['id'] for r in page['records']],[did])
            self.check('Chinese destination name roundtrip',page['records'][0]['name'],d['name'])
            self.call('invalid catalog pagination','/admin/catalog/destinations?current=0',token=a,expected=(200,400))
            spot=dict(name='Admin attraction',coverImg='',intro='Attraction fixture',address='Test address',longitude=120,latitude=30,ticketPrice=20,openTime='09:00-17:00',durationMin=60,tags=['nature'],status=1)
            sid=self.call('create attraction',f'/admin/catalog/destinations/{did}/attractions',spot,'POST',a)['data']
            spot['ticketPrice']=25;self.call('update attraction',f'/admin/catalog/attractions/{sid}',spot,'PUT',a)
            self.check('attraction price stored',self.sql(f'SELECT ticket_price FROM attraction WHERE id={sid}'),'25.00')
            self.call('invalid attraction status',f'/admin/catalog/attractions/{sid}',{**spot,'status':2},'PUT',a,expected=(200,400))
            item=dict(title='Visit attraction',timePoint='09:00',attractionId=sid,activity='walking',transport='',hotel='',meal='',durationMin=60,cost=25,tips='Bring water')
            r=dict(title=marker+'-route',subtitle='test',coverImg=d['coverImg'],destinationId=did,days=1,price=250,difficulty=1,tags=['nature'],highlights='test highlight',notice='test notice',recommendWeight=0.8,isTop=0,quotaPerDay=20,status=1,dayList=[dict(title='Day one',summary='Morning walk',items=[item])])
            rid=self.call('create route','/admin/catalog/routes',r,'POST',a)['data'];self.route_ids.append(rid)
            detail=self.call('route admin detail',f'/admin/catalog/routes/{rid}',token=a)['data']
            self.check('day and item roundtrip',detail['dayList'][0]['items'][0]['title'],item['title'])
            self.call('reject day count mismatch',f'/admin/catalog/routes/{rid}',{**r,'days':2},'PUT',a,expected=(200,400))
            invalid=copy.deepcopy(r);invalid['dayList'][0]['items'][0]['timePoint']='25:00'
            self.call('reject malformed time',f'/admin/catalog/routes/{rid}',invalid,'PUT',a,expected=(200,400))
            self.call('strict route price',f'/admin/catalog/routes/{rid}',{**r,'price':'250'},'PUT',a,expected=(200,400))
            forged={**r,'createBy':user['id'],'likeCount':900,'bookingCount':900,'id':1}
            self.call('protected route fields ignored',f'/admin/catalog/routes/{rid}',forged,'PUT',a)
            self.check('creator and counters remain server owned',self.sql(f'SELECT CONCAT(create_by,\':\',like_count,\':\',booking_count) FROM route WHERE id={rid}'),str(admin['id'])+':0:0')
            r['days']=2;r['dayList'].append(dict(title='Day two',summary='',items=[{**item,'title':'Custom visit','attractionId':0}]))
            self.call('replace itinerary',f'/admin/catalog/routes/{rid}',r,'PUT',a)
            self.check('itinerary day count stored',self.sql(f'SELECT COUNT(*) FROM route_day WHERE route_id={rid}'),'2')
            self.call('set route top',f'/admin/catalog/routes/{rid}/top',{'isTop':1},'PUT',a)
            self.call('set recommendation weight',f'/admin/catalog/routes/{rid}/weight',{'recommendWeight':0.9},'PUT',a)
            self.check('weight stored',self.sql(f'SELECT recommend_weight FROM route WHERE id={rid}'),'0.9000')
            self.call('reject invalid weight',f'/admin/catalog/routes/{rid}/weight',{'recommendWeight':2},'PUT',a,expected=(200,400))
            self.call('public route detail',f'/route/{rid}')
            self.call('public route days',f'/route/{rid}/days')
            # Keep new fixtures disabled until link and schedule checks are complete.
            b=dict(title=marker+'-banner',imageUrl=d['coverImg'],linkType='ROUTE',linkValue=str(rid),sortNo=0,status=0,startTime=None,endTime=None)
            bid=self.call('create banner','/admin/banner',b,'POST',a)['data'];self.banner_ids.append(bid)
            self.call('banner unsafe URL rejected',f'/admin/banner/{bid}',{**b,'linkType':'URL','linkValue':'javascript:alert(1)'},'PUT',a,expected=(200,400))
            self.call('banner strict title',f'/admin/banner/{bid}',{**b,'title':123},'PUT',a,expected=(200,400))
            self.call('banner invalid schedule',f'/admin/banner/{bid}',{**b,'startTime':'2026-10-02T00:00:00','endTime':'2026-10-01T00:00:00'},'PUT',a,expected=(200,400))
            enabled=int(self.sql('SELECT COUNT(*) FROM banner WHERE status=1'))
            if enabled<8:
                b['status']=1;self.call('enable banner',f'/admin/banner/{bid}',b,'PUT',a)
                active=self.call('active banner list','/banner/list')['data'];self.check('linked route banner visible',bid in [x['id'] for x in active],True)
                future=(dt.datetime.now()+dt.timedelta(days=1)).isoformat(timespec='seconds')
                self.call('scheduled future banner',f'/admin/banner/{bid}',{**b,'startTime':future},'PUT',a)
                self.check('future banner hidden',bid in [x['id'] for x in self.call('scheduled public list','/banner/list')['data']],False)
                self.call('restore banner schedule',f'/admin/banner/{bid}',b,'PUT',a)
                past=(dt.datetime.now()-dt.timedelta(days=1)).isoformat(timespec='seconds')
                self.call('expired banner schedule',f'/admin/banner/{bid}',{**b,'endTime':past},'PUT',a)
                self.check('expired banner hidden',bid in [x['id'] for x in self.call('expired public list','/banner/list')['data']],False)
                self.call('clear expired schedule',f'/admin/banner/{bid}',b,'PUT',a)
            else:
                self.call('banner enable limit',f'/admin/banner/{bid}',{**b,'status':1},'PUT',a,expected=(200,400))
            when=(dt.date.today()+dt.timedelta(days=5)).isoformat()
            booking=dict(routeId=rid,travelDate=when,peopleNum=1,contactName='Fixture',contactPhone='13800000000',remark='Test booking')
            booking_id=self.call('create booking','/interaction/booking',booking,'POST',u)['data']['bookingId']
            cid=self.call('create comment','/interaction/comment',dict(routeId=rid,score=5,content='Admin acceptance comment',images=[]),'POST',u)['data']['id']
            comments=self.call('admin comment listing',f'/admin/interaction/comment/page?routeId={rid}',token=a)['data']['records']
            self.check('comment exposes moderation status',next(c for c in comments if c['id']==cid).get('status'),1)
            self.call('destination deletion with route blocked',f'/admin/catalog/destinations/{did}',method='DELETE',token=a,expected=(200,409))
            self.call('route deletion with booking blocked',f'/admin/catalog/routes/{rid}',method='DELETE',token=a,expected=(200,409))
            self.call('take route offline',f'/admin/catalog/routes/{rid}/status',{'status':0},'PUT',a)
            self.call('offline route detail hidden',f'/route/{rid}',expected=(200,2001))
            self.call('offline route days hidden',f'/route/{rid}/days',expected=(200,2001))
            self.call('admin can confirm offline route historical booking',f'/admin/interaction/booking/{booking_id}/status',{'status':1},'PUT',a)
            self.call('admin can hide offline route comment',f'/admin/interaction/comment/{cid}/status',{'status':0},'PUT',a)
            self.call('offline route comments inaccessible',f'/interaction/comment/page?routeId={rid}',expected=(200,2001))
            self.call('restore route',f'/admin/catalog/routes/{rid}/status',{'status':1},'PUT',a)
            self.call('restore comment',f'/admin/interaction/comment/{cid}/status',{'status':1},'PUT',a)
            self.call('take parent offline',f'/admin/catalog/destinations/{did}/status',{'status':0},'PUT',a)
            self.call('offline parent detail hidden',f'/destination/{did}',expected=(200,404))
            self.call('offline parent attractions hidden',f'/destination/{did}/attractions',expected=(200,404))
            self.call('offline parent route hidden',f'/route/{rid}',expected=(200,2001))
            self.call('offline parent comments hidden',f'/interaction/comment/page?routeId={rid}',expected=(200,2001))
            self.call('offline parent like blocked','/interaction/like',{'routeId':rid},'POST',u,expected=(200,2001))
            self.call('offline parent booking blocked','/interaction/booking',{**booking,'travelDate':(dt.date.today()+dt.timedelta(days=6)).isoformat()},'POST',u,expected=(200,2001))
            page=self.call('offline parent public route page',f'/route/page?destinationId={did}&status=0')['data']['records'];self.check('offline parent route excluded',rid in [x['id'] for x in page],False)
            self.check('offline linked banner hidden',bid in [x['id'] for x in self.call('offline linked banners','/banner/list')['data']],False)
            self.call('cannot publish route under offline parent',f'/admin/catalog/routes/{rid}/status',{'status':1},'PUT',a,expected=(200,409))
            self.call('admin can read offline destination',f'/admin/catalog/destinations/{did}',token=a)
            self.call('restore parent',f'/admin/catalog/destinations/{did}/status',{'status':1},'PUT',a)
            self.call('hide attraction',f'/admin/catalog/attractions/{sid}',{**spot,'status':0},'PUT',a)
            self.check('hidden attraction not public',sid in [x['id'] for x in self.call('public attractions list',f'/destination/{did}/attractions')['data']],False)
            self.call('reject route with hidden attraction',f'/admin/catalog/routes/{rid}',r,'PUT',a,expected=(200,400))
            self.call('restore attraction',f'/admin/catalog/attractions/{sid}',spot,'PUT',a)
            # User management returns a projection; no hashes, tokens or contacts.
            managed=self.call('user management search','/admin/user/page?keyword='+user['name'],token=a)
            if managed.get('code')==200:
                records=managed['data']['records'];self.check('user management status',records[0]['status'],1)
                self.check('user list has no sensitive fields',bool(set(records[0]) & {'password','accessToken','refreshToken','phone','email'}),False)
            self.call('disable user',f"/admin/user/{user['id']}/status",{'status':0},'PUT',a)
            self.call('disabled access rejected','/user/profile',token=u,expected=(401,401))
            self.login(user,'InitialPass123','disabled login denied',expected=(200,1002))
            self.call('enable user',f"/admin/user/{user['id']}/status",{'status':1},'PUT',a)
            self.call('reenabling does not revive old access','/user/profile',token=u,expected=(401,401))
            self.login(user,'InitialPass123','new login after enable')
            self.call('protect admin account',f"/admin/user/{admin['id']}/status",{'status':0},'PUT',a,expected=(200,409))
            self.call('strict user status',f"/admin/user/{user['id']}/status",{'status':'0'},'PUT',a,expected=(200,400))
            fresh=self.login(user,'InitialPass123','fresh session before concurrent disable')
            results=self.simultaneous([dict(path=f"/admin/user/{user['id']}/status",body={'status':0},method='PUT',token=a),dict(path='/auth/login',body={'username':user['name'],'password':'InitialPass123'},method='POST')])
            self.check('concurrent disable succeeds',results[0]['body']['code'],200)
            self.check('concurrent login accepted or disabled',results[1]['body']['code'] in [200,1002],True)
            if results[1]['body']['code']==200:
                self.call('concurrent issued access revoked','/user/profile',token=results[1]['body']['data']['accessToken'],expected=(401,401))
            self.call('concurrent disabled refresh denied','/auth/refresh',{'refreshToken':fresh['refreshToken']},'POST',expected=(200,401))
            self.call('reenable after race',f"/admin/user/{user['id']}/status",{'status':1},'PUT',a)
            self.call('reenable does not revive refresh','/auth/refresh',{'refreshToken':fresh['refreshToken']},'POST',expected=(200,401))
            self.call('admin sanitized logs',f"/admin/ai/recommend/log?userId={user['id']}",token=a)
            extra_banners=[]
            enabled=int(self.sql('SELECT COUNT(*) FROM banner WHERE status=1'))
            for index in range(8-enabled):
                extra=self.call('fill banner enable limit '+str(index),'/admin/banner',{**b,'title':marker+'-limit-'+str(index),'linkType':'NONE','linkValue':'','status':1},'POST',a)['data']
                self.banner_ids.append(extra);extra_banners.append(extra)
            self.call('ninth enabled banner rejected','/admin/banner',{**b,'title':marker+'-overflow','status':1},'POST',a,expected=(200,400))
            self.check('enabled limit remains eight',self.sql('SELECT COUNT(*) FROM banner WHERE status=1'),'8')
            for extra in extra_banners:self.call('remove limit fixture',f'/admin/banner/{extra}',method='DELETE',token=a)
            self.call('delete banner',f'/admin/banner/{bid}',method='DELETE',token=a)
            self.call('delete attraction',f'/admin/catalog/attractions/{sid}',method='DELETE',token=a)
            self.check('attraction soft deleted',self.sql(f'SELECT deleted FROM attraction WHERE id={sid}'),'1')
            self.sql(f'DELETE FROM route_booking WHERE route_id={rid}; DELETE FROM route_comment WHERE route_id={rid}')
            self.call('delete route with no booking',f'/admin/catalog/routes/{rid}',method='DELETE',token=a)
            self.check('route soft deleted',self.sql(f'SELECT deleted FROM route WHERE id={rid}'),'1')
            self.call('delete destination with no route',f'/admin/catalog/destinations/{did}',method='DELETE',token=a)
            self.check('destination soft deleted',self.sql(f'SELECT deleted FROM destination WHERE id={did}'),'1')
        except Exception as error:
            self.check('execution completed',type(error).__name__,'no exception')
        finally:
            try:
                for bid in self.banner_ids:self.sql(f'DELETE FROM banner WHERE id={bid}')
                for rid in self.route_ids:
                    self.sql(f'DELETE FROM route_booking WHERE route_id={rid}; DELETE FROM route_comment WHERE route_id={rid}; DELETE FROM route_like WHERE route_id={rid}; DELETE FROM route_favorite WHERE route_id={rid}; DELETE FROM user_behavior WHERE target_type=\'ROUTE\' AND target_id={rid}; DELETE ri FROM route_item ri JOIN route_day rd ON rd.id=ri.route_day_id WHERE rd.route_id={rid}; DELETE FROM route_day WHERE route_id={rid}; DELETE FROM route WHERE id={rid}')
                    self.check('fixture route and days cleaned',self.sql(f'SELECT (SELECT COUNT(*) FROM route WHERE id={rid})+(SELECT COUNT(*) FROM route_day WHERE route_id={rid})'),'0')
                for did in self.dest_ids:self.sql(f'DELETE FROM attraction WHERE destination_id={did}; DELETE FROM destination WHERE id={did}')
                for user in self.users:
                    uid=user['id'];self.sql(f'DELETE FROM user_behavior WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
                    from redis_fixture import clean_sessions,session_keys
                    clean_sessions(uid)
                    self.check('fixture account and session cleaned',[self.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),session_keys(uid)],['0',[]])
                self.check('fixture cleanup completed',True,True)
            except Exception as error:self.check('cleanup completed',type(error).__name__,'no exception')
            self.save()
        s=self.report['summary'];print(f"{s['passed']}/{s['total']} passed; {self.out}")
        return int(s['failed']>0)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',default='http://127.0.0.1:8080/api');p.add_argument('--label',default='baseline',choices=['baseline','final'])
    raise SystemExit(Acceptance(p.parse_args()).run())
