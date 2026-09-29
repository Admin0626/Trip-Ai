"""Actual incremental HTTP/SSE, MySQL/Redis and controlled provider, no real LLM."""
import argparse, datetime as dt, json, threading, time, uuid, urllib.request, urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from circuit_acceptance import Run, t, redis, keys
from redis_fixture import clean_sessions, clean_ai_state

counts, modes = {}, {}
class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_GET(self):
        from urllib.parse import urlsplit, parse_qs
        q=parse_qs(urlsplit(self.path).query)
        if self.path.startswith('/__control?'):
            modes[q['model'][0]]=q['mode'][0]; data={'configured':True}
        elif self.path=='/__counts': data=counts.copy()
        else: self.send_error(404);return
        raw=json.dumps(data).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(raw)
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        model=body['model'];counts[model]=counts.get(model,0)+1;n=counts[model]
        mode=modes.get(model,'ok')
        if mode=='slow':time.sleep(8)
        if mode=='heartbeat':time.sleep(6)
        status={'auth':401,'rate':429,'error':500}.get(mode,200)
        days=json.loads(body['messages'][-1]['content'].split('\n')[0])['days'] if body['max_tokens']!=16 else 1
        content=json.dumps({'title':'SSE协议夹具','dayList':[{'title':f'第{i+1}天','summary':'受控服务非真实AI','items':[{'title':'散步','cost':0}]} for i in range(days)]},ensure_ascii=False)
        if mode=='invalid' or (mode=='retry' and n==1):content='invalid itinerary'
        data={'choices':[{'message':{'content':content}}]} if status==200 else {'error':'DO_NOT_EXPOSE_PROVIDER_KEY'}
        if mode=='malformed':data={'choices':[]}
        if mode=='big':data={'large':'x'*1048600}
        raw=json.dumps(data,ensure_ascii=False).encode()
        self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers()
        try:self.wfile.write(raw)
        except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError):pass

class Stream:
    def __init__(self,a,body,token):
        self.id=body['requestId'];self.started=time.monotonic();self.events=[]
        headers={'Content-Type':'application/json','Accept':'text/event-stream'}
        if token:headers['Authorization']='Bearer '+token
        request=urllib.request.Request(a.base+'/ai/planner/generate-stream',json.dumps(body).encode(),headers,method='POST')
        try:self.response=urllib.request.urlopen(request,timeout=20)
        except urllib.error.HTTPError as e:self.response=e
        self.kind=self.response.headers.get('Content-Type','');self.http=self.response.status;self.json=None
        if 'text/event-stream' not in self.kind:
            raw=self.response.read();self.json=json.loads(raw) if raw else {'code':None,'emptyBody':True};self.response.close()
    def next(self):
        event='message';data=[]
        while True:
            raw=self.response.readline()
            if not raw:return None
            line=raw.decode('utf8').rstrip('\r\n')
            if not line:
                if not data:continue
                item={'event':event,'payload':json.loads('\n'.join(data)),'atMs':round((time.monotonic()-self.started)*1000)}
                self.events.append(item);return item
            if line.startswith('event:'):event=line[6:].strip()
            if line.startswith('data:'):data.append(line[5:].lstrip(' '))
    def finish(self):
        if self.json is not None:return None
        try:
            while self.next() is not None:pass
        finally:self.response.close()
        terminal=[e for e in self.events if e['event'] in ['done','error','cancelled']]
        return terminal[-1] if terminal else None
    def close(self):self.response.close()

def wait_for(predicate,seconds=12):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        if predicate():return True
        time.sleep(.1)
    return False

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/planner-sse'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-{args.label}.json'
    a.report.update(mode='REAL_INCREMENTAL_HTTP_SSE_MYSQL_REDIS_CONTROLLED_PROVIDER',phase='RUNNING',streams=[])
    server=ThreadingHTTPServer(('127.0.0.1',0),Provider);threading.Thread(target=server.serve_forever,daemon=True).start();active=[]
    try:
        u=a.register('s');uid=u['id'];pair=a.login(u,'InitialPass123','login SSE fixture');token=pair['accessToken']
        other=a.register('o');ot=a.login(other,'InitialPass123','login isolated fixture')['accessToken']
        conn={'baseUrl':f'http://127.0.0.1:{server.server_port}/v1','model':'sse-valid','apiKey':'controlled-sse-key'}
        def body(model='sse-valid',**overrides):return {'requestId':str(uuid.uuid4()),'input':{'connection':{**conn,'model':model},'query':'希望轻松体验当地美食','days':2,'budget':3000,'peopleNum':2,**overrides}}
        def start(b,owner=None):
            s=Stream(a,b,token if owner is None else owner);active.append(s);return s
        def finish(s,event,code=None):
            terminal=s.finish();a.report['streams'].append({'requestId':s.id,'http':s.http,'contentType':s.kind,'json':s.json,'events':s.events});a.save()
            a.check('single terminal '+s.id,[e['event'] for e in s.events if e['event'] in ['done','error','cancelled']],[event])
            a.check('all event request IDs match '+s.id,all(e['payload']['requestId']==s.id for e in s.events),True)
            if code is not None:a.check('terminal business code '+s.id,terminal['payload']['data']['code'] if terminal else None,code)
            return terminal['payload']['data'] if terminal else {}
        def status(s,owner=None,code=200):return a.call('read owned request state','/ai/planner/requests/'+s.id,token=token if owner is None else owner,expected=(200,code)).get('data')
        def cancel(s,owner=None,code=200):return a.call('cancel owned request','/ai/planner/requests/'+s.id+'/cancel',{},'POST',token if owner is None else owner,expected=(200,code))
        def usage():return a.call('read actual quota/audit summary','/ai/planner/usage',token=token)['data']
        def circuit(model):return a.call('read actual model fault samples','/ai/planner/circuit',{'connection':{**conn,'model':model}},'POST',token)['data']
        def admitted(model):return wait_for(lambda:counts.get(model,0)>0,3)
        modes['sse-heartbeat']='heartbeat';s=start(body('sse-heartbeat'));first=s.next()
        a.check('start before upstream completes',[first['event'],first['atMs']<3000],['start',True])
        a.check('UTF8/no cache/no proxy buffering',['charset=UTF-8' in s.kind,s.response.headers.get('Cache-Control'),s.response.headers.get('X-Accel-Buffering')],[True,'no-cache, no-store','no'])
        p=finish(s,'done');a.check('validated preview',[p.get('source'),p.get('attempts'),len(p.get('draft',{}).get('dayList',[]))],['USER_MODEL',1,2])
        a.check('heartbeat while waiting','heartbeat' in [e['event'] for e in s.events],True)
        a.check('stages ordered',[e['payload']['data']['stage'] for e in s.events if e['event']=='progress'],['CONNECTING','GENERATING','VALIDATING'])
        a.check('preview never auto saved',t.sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uid}'),'0')
        a.check('request final status',status(s)['state'],'SUCCEEDED');cancel(s,code=409);status(s,ot,404);cancel(s,ot,404)
        before=counts.get('sse-heartbeat',0);duplicate=start({'requestId':s.id,'input':body('sse-heartbeat')['input']})
        a.check('duplicate JSON error',duplicate.json.get('code') if duplicate.json else None,409);a.check('duplicate no outbound',counts.get('sse-heartbeat',0),before)
        for label,b in [('invalid UUID',{**body(),'requestId':'bad'}),('strict invalid field',body(days='2')),('old date',body(startDate='2000-01-01'))]:
            bad=start(b);a.check(label+' JSON before SSE',bad.json.get('code') if bad.json else None,400)
        bad=start(body(),owner='');a.check('anonymous auth denied',[bad.http,bad.json.get('code')],[401,401])
        modes['sse-retry']='retry';before=usage();s=start(body('sse-retry'));p=finish(s,'done');after=usage()
        a.check('retry attempts',[p.get('attempts'),counts.get('sse-retry')],[2,2])
        a.check('retry quota versus operation',[after['quota']['hourly']['used']-before['quota']['hourly']['used'],after['today']['operations']-before['today']['operations']],[2,1])
        a.check('retry progression',[e['payload']['data'] for e in s.events if e['event']=='progress'],[{'stage':'CONNECTING','attempt':1},{'stage':'GENERATING','attempt':1},{'stage':'VALIDATING','attempt':1},{'stage':'RETRYING','attempt':1},{'stage':'CONNECTING','attempt':2},{'stage':'GENERATING','attempt':2},{'stage':'VALIDATING','attempt':2}])
        for mode in ['invalid','auth','rate','error','malformed','big']:
            model='sse-'+mode;modes[model]=mode;s=start(body(model));p=finish(s,'error',3004)
            a.check('provider error text suppressed '+mode,'DO_NOT_EXPOSE' not in json.dumps(p),True)
            a.check('retry only for draft structure '+mode,counts[model],2 if mode=='invalid' else 1)
        model='sse-cancel';modes[model]='slow';before=usage();s=start(body(model));s.next();a.check('cancel after actual outbound',admitted(model),True)
        a.call('legacy shares active user lock','/ai/planner/generate',body()['input'],'POST',token,expected=(200,429))
        blocked=start(body());a.check('SSE shares active user lock',blocked.json.get('code') if blocked.json else None,429)
        cancel(s);finish(s,'cancelled',499);a.check('cancel promptly releases wait',s.events[-1]['atMs']<4000,True)
        a.check('cancel final state',status(s)['state'],'CANCELLED');cancel(s);after=usage()
        a.check('admitted cancel quota/audit',[after['quota']['hourly']['used']-before['quota']['hourly']['used'],after['today']['operations']-before['today']['operations']],[1,1])
        c=circuit(model);a.check('cancel neutral fault sample',[c['total'],c['failed']],[0,0])
        a.check('cancel audit',t.sql(f"SELECT error_msg FROM llm_call_log WHERE user_id={uid} AND model='{model}'"),'CANCELLED')
        finish(start(body()),'done');a.check('generation after cancel succeeds',counts.get('sse-valid'),1)
        model='sse-disconnect';modes[model]='slow';s=start(body(model));s.next();a.check('disconnect after outbound',admitted(model),True);s.close()
        def disconnected():return a.request('/ai/planner/requests/'+s.id,token=token)['body'].get('data',{}).get('state')=='CANCELLED'
        a.check('disconnect clears job in bounded time',wait_for(disconnected,12),True);a.check('disconnect neutral sample',circuit(model)['failed'],0)
        model='sse-logout';modes[model]='slow';s=start(body(model));s.next();a.check('logout after outbound',admitted(model),True)
        a.call('logout active stream session','/auth/logout',{},'POST',token);finish(s,'error',401)
        a.check('revocation terminates while provider waits',s.events[-1]['atMs']<7500,True)
        token=a.login(u,'InitialPass123','login after revocation')['accessToken'];a.check('revocation neutral sample',circuit(model)['failed'],0)
        model='sse-half-open';ks=keys(args.circuit_prefix,uid,{**conn,'model':model})
        redis('HSET',ks[0],'phase','OPEN','generation','fixture-open','retryAt',1,'leaseUntil',0,'probe','');redis('EXPIRE',ks[0],120)
        modes[model]='slow';s=start(body(model));s.next();a.check('half-open outbound probe',admitted(model),True);a.check('half-open during call',circuit(model)['phase'],'HALF_OPEN')
        cancel(s);finish(s,'cancelled',499);c=circuit(model);a.check('abandoned probe no sample/wait',[c['total'],c['retryAfterSeconds']],[0,0])
        modes[model]='ok';finish(start(body(model)),'done');a.check('fresh probe recovers',circuit(model)['phase'],'CLOSED')
        q=a.register('q');qt=a.login(q,'InitialPass123','login quota fixture')['accessToken']
        now=dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).replace(minute=0,second=0,microsecond=0)
        qkey=f'{args.quota_prefix}:{{ai-quota}}:user:{q["id"]}:hour:{int(now.timestamp())}'
        redis('SET',qkey,20,'EX',120);s=start(body('sse-quota'),qt);finish(s,'error',429)
        a.check('quota denial no outbound',counts.get('sse-quota',0),0);a.check('quota denial no fake audit',t.sql(f'SELECT COUNT(*) FROM llm_call_log WHERE user_id={q["id"]}'),'0')
        if args.timeout_base:
            old=a.base;a.base=args.timeout_base;modes['sse-total-timeout']='slow';s=start(body('sse-total-timeout'));finish(s,'error',408)
            a.check('overall deadline before provider finishes',s.events[-1]['atMs']<7000,True);a.base=old
    except Exception as e:a.check('runner completed',str(e),'no error')
    finally:
        for s in active:s.close()
        for u in a.users:
            if not u['id']:continue
            uid=u['id'];clean_sessions(uid);clean_ai_state(uid,args.circuit_prefix,args.quota_prefix);clean_ai_state(uid,args.timeout_circuit_prefix,args.timeout_quota_prefix)
            t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('owned fixture removed '+str(uid),t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        server.shutdown();server.server_close();a.report['phase']='COMPLETE';a.save()
    summary=a.report['summary'];print(f"{summary['passed']}/{summary['total']} passed; {a.out}");return bool(summary['failed'])

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080/api');p.add_argument('--label',default='final')
    p.add_argument('--circuit-prefix',default='trip:ai:circuit');p.add_argument('--quota-prefix',default='trip:ai:quota')
    p.add_argument('--timeout-base',default='');p.add_argument('--timeout-circuit-prefix',default='trip:test:circuit:sse-timeout');p.add_argument('--timeout-quota-prefix',default='trip:test:quota:sse-timeout');p.add_argument('--serve',type=int,default=0)
    args=p.parse_args()
    if args.serve:
        print('Controlled SSE protocol fixture, loopback only',flush=True);ThreadingHTTPServer(('127.0.0.1',args.serve),Provider).serve_forever()
    else:raise SystemExit(main(args))
