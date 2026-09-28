"""Verify actual production defaults and basic recommendation during an open circuit."""
import argparse, datetime as dt, uuid
from circuit_acceptance import Run,redis,scan,t
from redis_fixture import clean_sessions,clean_ai_state

def main():
    a=Run(argparse.Namespace(base='http://localhost:8080/api',label='production-defaults'))
    a.out=t.ROOT/'docs/dev/evidence/circuit'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-production-defaults.json'
    a.report.update(mode='REAL_HTTP_PRODUCTION_DEFAULTS_CONTROLLED_PROVIDER',phase='RUNNING')
    try:
        u=a.register('d'); uid=u['id']; token=a.login(u,'InitialPass123','login default fixture')['accessToken']
        conn={'baseUrl':'http://localhost:11436/v1','model':'circuit-ui-error-prod-'+uuid.uuid4().hex[:16],'apiKey':'controlled-default-key'}
        def state(): return a.call('production status','/ai/planner/circuit',{'connection':conn},'POST',token)['data']
        def usage(): return a.call('production usage','/ai/planner/usage',token=token)['data']
        s=state();a.check('production default values',[s['windowSeconds'],s['cooldownSeconds'],s['minimumCalls']],[300,600,10])
        for i in range(9): a.call('actual failure '+str(i+1),'/ai/planner/test',{'connection':conn},'POST',token,expected=(200,3004))
        a.check('nine failures below default minimum',state()['phase'],'CLOSED')
        a.call('tenth actual failure','/ai/planner/test',{'connection':conn},'POST',token,expected=(200,3004))
        s=state();a.check('ten failures open default circuit',[s['phase'],s['total'],s['failed']],['OPEN',10,10]);a.check('actual default cooldown',590<=s['retryAfterSeconds']<=600,True)
        before=usage();a.call('production open denies outbound','/ai/planner/test',{'connection':conn},'POST',token,expected=(200,503))
        a.call('free rule intent works during outage','/ai/recommend/intent',{'query':'大理两天预算3000元'},'POST',token)
        a.call('free route matching works during outage','/ai/recommend/match',{'intent':{},'topN':3},'POST',token)
        after=usage();a.check('blocked and rule calls do not consume quota or model audit',[after['quota']['hourly']['used'],after['today']['operations']],[before['quota']['hourly']['used'],before['today']['operations']])
    except Exception as e: a.check('runner completed',str(e),'no error')
    finally:
        for u in a.users:
            uid=u['id'];clean_sessions(uid);clean_ai_state(uid)
            t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('default fixture removed',t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        a.report['phase']='COMPLETE';a.save()
    s=a.report['summary'];print(f"{s['passed']}/{s['total']} passed; {a.out}");return bool(s['failed'])
if __name__=='__main__': raise SystemExit(main())
