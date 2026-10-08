"""Real local analytics HTTP/MySQL/Redis acceptance; generated fixtures only, no model calls."""
import argparse, datetime as dt, json, uuid
from password_acceptance import Acceptance as Base, ROOT
from redis_fixture import clean_sessions, session_keys

class Acceptance(Base):
    def __init__(self,args):
        super().__init__(args)
        self.report['mode']='REAL_LOCAL_ANALYTICS_HTTP_MYSQL_REDIS_WITH_OWN_SQL_FIXTURES'
        self.out=ROOT/'docs/dev/evidence/analysis'/self.out.name
        self.dest_ids=[]; self.route_ids=[]

    def run(self):
        marker='an_'+uuid.uuid4().hex[:12]
        try:
            self.call('anonymous analytics denied','/admin/analysis/dashboard',expected=(401,401))
            user=self.register('u');admin=self.register('a')
            self.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']}")
            ut=self.login(user,'InitialPass123','normal login')['accessToken'];at=self.login(admin,'InitialPass123','admin login')['accessToken']
            self.call('ordinary user denied','/admin/analysis/dashboard',token=ut,expected=(403,403))
            for query in ['days=0','days=-1','days=8','days=365','days=abc','days=7.5','days=2147483648','destinationId=0','destinationId=-1','destinationId=abc','destinationId=9007199254740992']:
                self.call('invalid '+query,'/admin/analysis/dashboard?'+query,token=at,expected=(200,400))
            self.call('missing destination','/admin/analysis/dashboard?destinationId=9007199254740991',token=at,expected=(200,404))
            def create_dest(suffix,deleted=0):
                did=int(self.sql(f"INSERT INTO destination(name,province,city,longitude,latitude,cover_img,deleted,status) VALUES('{marker+suffix}','fixture','fixture',120,30,'/fixture.png',{deleted},0);SELECT LAST_INSERT_ID()"));self.dest_ids.append(did);return did
            did=create_dest('active');empty=create_dest('empty');gone=create_dest('gone',1)
            def create_route(d,title,price=100,deleted=0):
                rid=int(self.sql(f"INSERT INTO route(title,destination_id,price,deleted,status,booking_count) VALUES('{marker+title}',{d},{price},{deleted},0,999);SELECT LAST_INSERT_ID()"));self.route_ids.append(rid);return rid
            rid=create_route(did,'zero',0);rid2=create_route(did,'high',500);deleted=create_route(did,'deleted',20,1);gone_route=create_route(gone,'gone',30)
            for i in range(101):create_route(did,'filler'+str(i),i+1)
            before=self.call('actual initial seven day snapshot','/admin/analysis/dashboard?days=7',token=at)['data']
            start=before['startDate']+' 00:00:00';until=(dt.date.fromisoformat(before['endDate'])+dt.timedelta(days=1)).isoformat()+' 00:00:00'
            old=(dt.date.fromisoformat(before['startDate'])-dt.timedelta(days=1)).isoformat()+' 23:59:59'
            yesterday=(dt.date.fromisoformat(before['endDate'])-dt.timedelta(days=1)).isoformat()+' 12:00:00'
            def book(r,status,when):
                no=marker+uuid.uuid4().hex[:8]
                self.sql(f"INSERT INTO route_booking(booking_no,route_id,user_id,travel_date,people_num,contact_name,contact_phone,status,create_time) VALUES('{no}',{r},{user['id']},'2099-01-01',1,'统计夹具','00000000000',{status},'{when}')")
            book(rid,0,start);book(rid,1,yesterday);book(rid,2,yesterday);book(rid2,3,yesterday)
            book(rid,0,old);book(rid,0,until);book(deleted,1,yesterday);book(gone_route,1,yesterday)
            for success,cost,when in [(1,100,yesterday),(0,300,yesterday),(1,400,old),(1,999,until)]:
                self.sql(f"INSERT INTO llm_call_log(user_id,scene,model,cost_ms,success,create_time) VALUES({user['id']},'ANALYSIS_FIXTURE','no-provider',{cost},{success},'{when}')")
            data=self.call('filtered actual dashboard','/admin/analysis/dashboard?days=7&destinationId='+str(did),token=at)['data']
            self.check('snapshot timezone',['Asia/Shanghai',True],[data['timezone'],data['generatedAt'].endswith('+08:00')])
            self.check('filters echoed',[data['days'],data['destinationId']],[7,did])
            self.check('active catalog includes offline and excludes deleted',data['summary']['routes'],103)
            self.check('actual bookings exclude cached 999 and boundaries',[data['summary']['bookings'],data['summary']['cancelled']],[4,1])
            self.check('state counts once each',[d['count'] for d in data['bookingStatuses']],[1,1,1,1])
            self.check('trend has exactly 7 ordered days',[len(data['trend']),data['trend'][0]['date'],data['trend'][-1]['date']],[7,before['startDate'],before['endDate']])
            self.check('date start included',data['trend'][0]['bookings'],1)
            self.check('daily counts reconcile',[sum(d['bookings'] for d in data['trend']),sum(d['cancelled'] for d in data['trend'])],[4,1])
            self.check('missing days filled with zero',any(d['bookings']==0 for d in data['trend']),True)
            self.check('destination grouping no multiplication',data['destinationHeat'],[{'id':did,'name':marker+'active','bookings':4}])
            self.check('scatter cap honestly metadata',[len(data['routeScatter']),data['matchingRoutes'],data['scatterLimit']],[100,103,100])
            points={p['id']:p for p in data['routeScatter']}
            self.check('scatter exact zero price and real count',[points[rid]['price'],points[rid]['bookings'],points[rid2]['price'],points[rid2]['bookings']],[0,3,500,1])
            self.check('scatter contains zero booking routes',any(p['bookings']==0 for p in points.values()),True)
            self.check('deterministic zero-count tie IDs', [p['id'] for p in data['routeScatter'][2:]],sorted(p['id'] for p in data['routeScatter'][2:]))
            self.check('deleted records not leaked',deleted not in points and gone_route not in points,True)
            lo=data['startDate']+' 00:00:00';hi=until
            expected_users=int(self.sql(f"SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND create_time>='{lo}' AND create_time<'{hi}'"))
            self.check('new user summary agrees SQL',data['summary']['newUsers'],expected_users)
            self.check('new user trend agrees SQL',sum(d['newUsers'] for d in data['trend']),expected_users)
            ai=list(map(float,self.sql(f"SELECT COUNT(*),COALESCE(SUM(success=1),0),AVG(cost_ms) FROM llm_call_log WHERE create_time>='{lo}' AND create_time<'{hi}'").split('\t')))
            self.check('AI aggregate agrees actual SQL',[data['summary']['aiCalls'],data['summary']['aiSucceeded']],[int(ai[0]),int(ai[1])])
            self.check('AI average agrees SQL',round(data['summary']['aiAverageMs'],3),round(ai[2],3))
            self.check('AI trend and states reconcile',[sum(d['aiCalls'] for d in data['trend']),sum(d['count'] for d in data['aiStatuses'])],[int(ai[0]),int(ai[0])])
            empty_data=self.call('empty destination dashboard','/admin/analysis/dashboard?days=7&destinationId='+str(empty),token=at)['data']
            self.check('empty scope no invented chart data',[empty_data['summary']['routes'],empty_data['summary']['bookings'],empty_data['routeScatter'],empty_data['destinationHeat']],[0,0,[],[]])
            self.check('dest filter leaves global user and AI metrics',[empty_data['summary']['newUsers'],empty_data['summary']['aiCalls']],[expected_users,int(ai[0])])
            self.call('deleted destination rejected','/admin/analysis/dashboard?destinationId='+str(gone),token=at,expected=(200,404))
            for days in [30,90]:
                period=self.call(str(days)+' day window','/admin/analysis/dashboard?days='+str(days)+'&destinationId='+str(did),token=at)['data']
                self.check(str(days)+' day old booking included',[len(period['trend']),period['summary']['bookings']],[days,5])
            glob=self.call('global current snapshot','/admin/analysis/dashboard?days=7',token=at)['data']
            route_total=int(self.sql('SELECT COUNT(*) FROM route r JOIN destination d ON d.id=r.destination_id WHERE r.deleted=0 AND d.deleted=0'))
            self.check('global route total agrees SQL',glob['summary']['routes'],route_total)
            self.check('no private data or keys returned',not any(s in json.dumps(glob).lower() for s in ['password','accesstoken','contactphone','contactname','error_msg','prompt_tokens']),True)
            self.report['phase']='COMPLETED'
        except Exception as e:
            self.check('execution completed',type(e).__name__+': '+str(e),'no error');self.report['phase']='FAILED'
        finally:
            if self.route_ids:
                ids=','.join(map(str,self.route_ids));self.sql(f'DELETE FROM route_booking WHERE route_id IN ({ids});DELETE FROM route WHERE id IN ({ids})')
                self.check('all owned routes removed',self.sql(f'SELECT COUNT(*) FROM route WHERE id IN ({ids})'),'0')
            if self.dest_ids:
                ids=','.join(map(str,self.dest_ids));self.sql(f'DELETE FROM destination WHERE id IN ({ids})')
                self.check('all owned destinations removed',self.sql(f'SELECT COUNT(*) FROM destination WHERE id IN ({ids})'),'0')
            for user in self.users:
                self.sql(f"DELETE FROM llm_call_log WHERE user_id={user['id']};DELETE FROM sys_user WHERE id={user['id']} AND username='{user['name']}'")
                clean_sessions(user['id']);self.check('owned account/session removed '+str(user['id']),[self.sql(f"SELECT COUNT(*) FROM sys_user WHERE id={user['id']}"),session_keys(user['id'])],['0',[]])
            self.save()
        s=self.report['summary'];print(f"{s['passed']}/{s['total']} passed; {self.out}");return int(s['failed']>0)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8080/api');p.add_argument('--label',default='final');raise SystemExit(Acceptance(p.parse_args()).run())
