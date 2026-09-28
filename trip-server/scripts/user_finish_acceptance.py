"""Real user stats/preference recommendation tests, generated fixtures only."""
import argparse, datetime as dt, json, subprocess
from redis_fixture import clean_sessions, session_keys
from password_acceptance import Acceptance as Base, ROOT


class Acceptance(Base):
    def __init__(self,args):
        super().__init__(args);self.out=ROOT/'docs/dev/evidence/user-finish'/self.out.name
        self.report['mode']='REAL_USER_STATS_PREFERENCE_HTTP_MYSQL_REDIS';self.did=None;self.routes=[]

    def match(self,label,intent,token,use=True,expected=(200,200)):
        return self.call(label,'/ai/recommend/match',{'intent':intent,'topN':20,'useSavedPreference':use},'POST',token,expected=expected)

    def run(self):
        try:
            a,b=self.register('a'),self.register('b');self.report['fixtures']={'users':self.users,'routes':self.routes,'destinationId':None};self.save();ta=self.login(a,'InitialPass123','login a')['accessToken'];tb=self.login(b,'InitialPass123','login b')['accessToken']
            name=a['name']+'Destination';self.did=int(self.sql(f"INSERT INTO destination(name,province,city,longitude,latitude,cover_img,status) VALUES('{name}','TestProvince','TestCity',120,30,'',1); SELECT LAST_INSERT_ID()"))
            self.report['fixtures']['destinationId']=self.did;self.save()
            fixtures=[('best',3,'200.50','海滨,康养',1),('rated',3,'250.00','自然风光',5),('cheap',3,'150.49','海滨',5),('expensive',3,'300.51','海滨',5),('otherday',5,'200.00','海滨',5),('avoid',3,'240.00','海滨,探险',5)]
            for title,days,price,tags,score in fixtures:self.routes.append(int(self.sql(f"INSERT INTO route(title,destination_id,days,price,tags,avg_score,status) VALUES('{title}',{self.did},{days},{price},'{tags}',{score},1); SELECT LAST_INSERT_ID()")))
            pref=dict(preferenceTags=['海滨','康养'],avoidTags=['探险'],budgetMin=150.50,budgetMax=300.50,preferredDays=3,companions='family',pace='relaxed')
            self.call('save personal preference','/user/preference',pref,'PUT',ta)
            self.call('other user preference empty','/user/preference',token=tb)
            effective=self.match('saved preference applied',{'destinations':[name]},ta)
            self.check('saved filters and preference rank',[r['routeId'] for r in effective['data']['list']],self.routes[:2])
            self.check('server states applied fields',sorted(effective['data'].get('savedPreferenceFields',[])),sorted(['days','budgetRange','preferenceTags','avoid']))
            full=self.match('opt out personal preference',{'destinations':[name]},ta,use=False)
            self.check('opt out restores all fixture candidates',full['data']['totalCandidates'],6)
            neighbor=self.match('preference isolation',{'destinations':[name]},tb)
            self.check('other user does not inherit filters',neighbor['data']['totalCandidates'],6)
            overridden=self.match('explicit clear and fields win',{'destinations':[name],'days':5,'budget':None,'preferenceTags':[],'avoid':[]},ta)
            self.check('explicit day and empty tags honored',[r['routeId'] for r in overridden['data']['list']],[self.routes[4]])
            cleared=self.match('all explicit clears remove saved limits',{'destinations':[name],'days':None,'budget':None,'preferenceTags':[],'avoid':[]},ta)
            self.check('explicit clears restore six candidates',cleared['data']['totalCandidates'],6)
            decimal=self.match('explicit decimal budget range',{'destinations':[name],'days':3,'budgetMin':150.50,'budgetMax':200.50,'preferenceTags':['海滨'],'avoid':[]},ta,use=False)
            self.check('decimal boundaries inclusive',[r['routeId'] for r in (decimal.get('data') or {}).get('list',[])],[self.routes[0]])
            conflict=self.match('explicit preferred wins over saved avoid',{'destinations':[name],'preferenceTags':['探险']},ta)
            self.check('explicit favorite is no longer excluded',[r['routeId'] for r in conflict['data']['list']],[self.routes[5],self.routes[1],self.routes[0]])
            conflict=self.match('explicit avoid wins over saved preferred',{'destinations':[name],'avoid':['海滨']},ta)
            self.check('explicit exclusion honored',[r['routeId'] for r in conflict['data']['list']],[self.routes[1]])
            trimmed=self.match('trimmed explicit preferred overrides saved avoid',{'destinations':[name],'preferenceTags':[' 探险 ']},ta)
            self.check('trimmed preferred conflict resolved',[r['routeId'] for r in (trimmed.get('data') or {}).get('list',[])],[self.routes[5],self.routes[1],self.routes[0]])
            trimmed=self.match('trimmed explicit avoid overrides saved preferred',{'destinations':[name],'avoid':[' 海滨 ']},ta)
            self.check('trimmed avoid conflict resolved',[r['routeId'] for r in (trimmed.get('data') or {}).get('list',[])],[self.routes[1]])
            legacy=self.call('omitted opt in keeps legacy behavior','/ai/recommend/match',{'intent':{'destinations':[name]},'topN':20},'POST',ta)
            self.check('default opt in remains false',legacy['data']['totalCandidates'],6)
            forged=self.call('match forged userId ignored','/ai/recommend/match',{'intent':{'destinations':[name]},'topN':20,'useSavedPreference':True,'userId':b['id']},'POST',ta)
            self.check('match uses authenticated preference',[r['routeId'] for r in forged['data']['list']],self.routes[:2])
            exact=self.match('equal decimal bounds accepted',{'destinations':[name],'budgetMin':200.50,'budgetMax':200.50,'days':3,'avoid':[],'preferenceTags':[]},ta,use=False)
            self.check('equal bounds find exact price',[r['routeId'] for r in exact['data']['list']],[self.routes[0]])
            upper=self.match('manual upper clears saved lower',{'destinations':[name],'budgetMax':200.50,'preferenceTags':[],'avoid':[]},ta)
            self.check('manual budget pair is independent',upper['data']['effectiveCriteria']['budgetMin'],None)
            self.check('manual upper includes lower priced route',sorted(r['routeId'] for r in upper['data']['list']),sorted([self.routes[0],self.routes[2]]))
            self.match('strict opt in flag',{},ta,use='true',expected=(200,400))
            for key,value in [('budgetMin','100'),('budgetMax',True),('budgetMax',100.001)]:self.match('strict '+key,{'destinations':[name],key:value},ta,use=False,expected=(200,400))
            self.match('invalid range order',{'budgetMin':300,'budgetMax':200},ta,use=False,expected=(200,400))
            alltags=['自然风光','历史文化','美食','亲子','摄影','海滨','康养','古城']
            self.call('unified eight preference tags','/user/preference',{**pref,'preferenceTags':alltags},'PUT',ta)
            self.match('eight tags accepted by match',{'destinations':[name],'preferenceTags':alltags,'avoid':[]},ta,use=False)
            self.call('stats anonymous denied','/user/stats',expected=(401,401))
            self.call('preference anonymous denied','/user/preference',expected=(401,401))
            rid=self.routes[0];uid=a['id'];other=b['id'];when=(dt.date.today()+dt.timedelta(days=10)).isoformat()
            self.call('favorite fixture','/interaction/favorite',{'routeId':rid},'POST',ta)
            self.call('booking fixture','/interaction/booking',{'routeId':rid,'travelDate':when,'peopleNum':1,'contactName':'Fixture','contactPhone':'13800000000'},'POST',ta)
            comment=self.call('comment fixture','/interaction/comment',{'routeId':rid,'score':5,'content':'Preference stats fixture'},'POST',ta)['data']['id']
            self.sql(f"INSERT INTO user_plan(user_id,title,destination_ids,start_date,days,budget,people_num,status) VALUES({uid},'Stats fixture','{self.did}',CURRENT_DATE,1,100,1,0)")
            expected=dict(favoriteCount=1,bookingCount=1,commentCount=1,planCount=1,chatCount=0)
            stats=self.call('stats actual persisted counts','/user/stats',token=ta)['data'];self.check('stats totals match own rows',stats,expected)
            self.check('stats cannot select other user',self.call('stats forged userId ignored',f'/user/stats?userId={other}',token=ta)['data'],expected)
            self.check('other user stats isolated',self.call('other stats empty','/user/stats',token=tb)['data'],{k:0 for k in expected})
            self.call('delete own comment','/interaction/comment/'+str(comment),method='DELETE',token=ta)
            self.check('stats excludes deleted comment',self.call('stats after comment deletion','/user/stats',token=ta)['data']['commentCount'],0)
            self.check('rule preference match makes no model calls',self.sql(f'SELECT COUNT(*) FROM llm_call_log WHERE user_id={uid}'),'0')
        except Exception as e:self.check('execution completed',type(e).__name__,'no exception')
        finally:
            try:
                for u in self.users:
                    uid=u['id'];self.sql(f'DELETE FROM route_favorite WHERE user_id={uid}; DELETE FROM route_like WHERE user_id={uid}; DELETE FROM route_booking WHERE user_id={uid}; DELETE FROM route_comment_like WHERE user_id={uid}; DELETE FROM route_comment WHERE user_id={uid}; DELETE FROM user_plan WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM user_behavior WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
                    removed=clean_sessions(uid)
                    self.check('fixture sessions cleaned',session_keys(uid),[],removedCount=removed)
                    self.check('fixture user cleaned',self.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
                for rid in self.routes:self.sql(f'DELETE FROM route WHERE id={rid}')
                if self.did:self.sql(f'DELETE FROM destination WHERE id={self.did}')
                self.check('fixture content cleaned',True,True)
            except Exception as e:self.check('cleanup completed',type(e).__name__,'no exception')
            self.save()
        s=self.report['summary'];print(f"{s['passed']}/{s['total']} passed; {self.out}");return int(s['failed']>0)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8080/api');p.add_argument('--label',choices=['baseline','final'],default='baseline')
    raise SystemExit(Acceptance(p.parse_args()).run())
