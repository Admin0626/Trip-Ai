"""Real revocation/overall timeout checks; requires dedicated 8081 with timeout=5 heartbeat=1."""
import argparse,datetime as dt,json,threading,uuid
from http.server import ThreadingHTTPServer
from planner_sse_acceptance import Provider,Stream,counts,modes,wait_for
from circuit_acceptance import Run,t
from redis_fixture import clean_sessions,clean_ai_state

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/planner-sse'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-lifecycle.json'
    a.report.update(mode='REAL_HTTP_SSE_REVOCATION_AND_ISOLATED_OVERALL_TIMEOUT',phase='RUNNING',streams=[])
    server=ThreadingHTTPServer(('127.0.0.1',0),Provider);threading.Thread(target=server.serve_forever,daemon=True).start();streams=[]
    try:
        u=a.register('life');uid=u['id'];pair=a.login(u,'InitialPass123','lifecycle fixture login')
        for mode in ['rotation','disabled','timeout']:
            if mode=='timeout':a.base=args.timeout_base
            model='sse-life-'+mode;modes[model]='slow';connection={'baseUrl':f'http://127.0.0.1:{server.server_port}/v1','model':model,'apiKey':'synthetic-lifecycle-key'}
            body={'requestId':str(uuid.uuid4()),'input':{'connection':connection,'query':'希望轻松体验当地美食','days':2,'budget':3000,'peopleNum':2}}
            s=Stream(a,body,pair['accessToken']);streams.append(s);s.next();a.check(mode+' actual outbound',wait_for(lambda:counts.get(model,0)==1,3),True)
            if mode=='rotation':pair=a.call('rotate while stream active','/auth/refresh',{'refreshToken':pair['refreshToken']},'POST')['data']
            elif mode=='disabled':t.sql(f'UPDATE sys_user SET status=0 WHERE id={uid}')
            terminal=s.finish();a.report['streams'].append(s.events);a.save()
            a.check(mode+' single error terminal',[e['event'] for e in s.events if e['event'] in ['done','error','cancelled']],['error'])
            a.check(mode+' code',terminal['payload']['data'].get('code') if terminal else None,408 if mode=='timeout' else 401)
            a.check(mode+' stops before slow provider',s.events[-1]['atMs']<7500,True)
            if mode=='disabled':t.sql(f'UPDATE sys_user SET status=1 WHERE id={uid}');pair=a.login(u,'InitialPass123','login enabled fixture')
            state=a.call(mode+' job state','/ai/planner/requests/'+s.id,token=pair['accessToken'])['data'];a.check(mode+' failed state',state['state'],'FAILED')
            c=a.call(mode+' circuit','/ai/planner/circuit',{'connection':connection},'POST',pair['accessToken'])['data'];a.check(mode+' neutral fault samples',[c['total'],c['failed']],[0,0])
            a.check(mode+' safe audit',t.sql(f"SELECT error_msg FROM llm_call_log WHERE user_id={uid} AND model='{model}'"),'STOPPED_'+str(408 if mode=='timeout' else 401))
    except Exception as e:a.check('lifecycle runner completed',str(e),'no error')
    finally:
        for s in streams:s.close()
        for u in a.users:
            if not u['id']:continue
            uid=u['id'];clean_sessions(uid);clean_ai_state(uid);clean_ai_state(uid,args.timeout_circuit_prefix,args.timeout_quota_prefix)
            t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('lifecycle fixture removed',t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        server.shutdown();server.server_close();a.report['phase']='COMPLETE';a.save()
    s=a.report['summary'];print(f"{s['passed']}/{s['total']} passed; {a.out}");return bool(s['failed'])
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080/api');p.add_argument('--label',default='lifecycle');p.add_argument('--timeout-base',default='http://localhost:8081/api')
    p.add_argument('--timeout-circuit-prefix',default='trip:test:circuit:sse-timeout');p.add_argument('--timeout-quota-prefix',default='trip:test:quota:sse-timeout');raise SystemExit(main(p.parse_args()))
