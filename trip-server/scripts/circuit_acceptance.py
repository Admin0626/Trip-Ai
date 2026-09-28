"""Real HTTP and Redis circuit acceptance; controlled provider, never real LLM quality."""
import argparse, collections, datetime as dt, hashlib, json, subprocess, threading, time, uuid
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import batch3_intent_acceptance as t
import urllib.parse
from password_acceptance import Acceptance
from redis_fixture import clean_sessions
from user_planner_acceptance import redact

def redis(*args):
    return subprocess.check_output(['redis-cli',*map(str,args)],text=True,encoding='utf-8').strip()
def scan(prefix):
    cursor='0'; keys=set()
    while True:
        lines=redis('SCAN',cursor,'MATCH',prefix+'*','COUNT',100).splitlines(); cursor=lines[0]
        for key in lines[1:]:
            if key:
                if not key.startswith(prefix): raise RuntimeError('Unsafe scan result')
                keys.add(key)
        if cursor=='0': return sorted(keys)
def keys(prefix,uid,conn):
    endpoint=conn['baseUrl'].replace('localhost','127.0.0.1').rstrip('/')+'/chat/completions'
    identity=endpoint+'\0'+conn['model']+'\0'+conn.get('apiKey','').strip()
    base=prefix+':{'+str(uid)+':'+hashlib.sha256(identity.encode()).hexdigest()+'}:'
    return [base+k for k in ['state','events','failures']]
LUA=(Path(__file__).resolve().parents[1]/'src/main/resources/redis/ai_circuit.lua').read_text(encoding='utf-8')
def lua(ks,action='status',permit=None,success=False,minimum=4):
    ticket=permit['ticket'] if permit else uuid.uuid4().hex
    generation=permit['generation'] if permit else ''
    raw=redis('EVAL',LUA,3,*ks,action,ticket,generation,int(success),60000,4000,minimum,30,4000,136000)
    return json.loads(raw)

counts = collections.Counter()
modes = {}
class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_GET(self):
        q=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        if self.path.startswith('/__control?'):
            modes[q['model'][0]]=q['mode'][0]; data={'configured':True}
        elif self.path=='/__counts': data=dict(counts)
        else: self.send_error(404); return
        raw=json.dumps(data).encode(); self.send_response(200); self.send_header('Content-Type','application/json'); self.end_headers(); self.wfile.write(raw)
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        model=body['model']; counts[model]+=1
        mode=modes.get(model,'error' if model.startswith('circuit-ui-error') else 'ok')
        if mode=='timeout': time.sleep(3)
        status={'auth':401,'rate':429,'error':500}.get(mode,200)
        content='OK'
        if body['max_tokens']!=16:
            days=json.loads(body['messages'][-1]['content'].split('\n')[0])['days']
            content=json.dumps({'title':'熔断协议夹具','dayList':[{'title':f'第{i+1}天','summary':'受控服务非真实AI','items':[{'title':'散步','cost':0}]} for i in range(days)]},ensure_ascii=False)
        if mode=='invalid': content='invalid itinerary'
        data={'choices':[{'message':{'content':content}}]} if status==200 else {'error':'DO_NOT_EXPOSE_PROVIDER_KEY'}
        if mode=='malformed': data={'choices':[]}
        if mode=='big': data={'large':'x'*1048600}
        raw=json.dumps(data,ensure_ascii=False).encode()
        self.send_response(status); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(raw))); self.end_headers()
        try: self.wfile.write(raw)
        except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError): pass

class Run(Acceptance):
    def save(self):
        self.report['summary']={'total':len(self.report['checks']),'passed':sum(r['passed'] for r in self.report['checks']),'failed':sum(not r['passed'] for r in self.report['checks'])}
        self.out.parent.mkdir(parents=True,exist_ok=True)
        payload=json.dumps(redact(self.report),ensure_ascii=False,indent=2)
        pending=self.out.with_suffix('.writing')
        for attempt in range(3):
            try: pending.write_text(payload,encoding='utf-8'); pending.replace(self.out); return
            except OSError:
                if attempt==2: raise
                time.sleep(0.05)

def main(args):
    a=Run(args); server=ThreadingHTTPServer(('127.0.0.1',0),Provider)
    second=ThreadingHTTPServer(('127.0.0.1',0),Provider)
    threading.Thread(target=second.serve_forever,daemon=True).start()
    a.out=Path(args.output) if args.output else t.ROOT/'docs/dev/evidence/circuit'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-{args.label}.json'
    a.report.update(mode='REAL_HTTP_MYSQL_REDIS_CONTROLLED_MODEL',phase='RUNNING',base=a.base,circuitPrefix=args.prefix,fixtures=[])
    threading.Thread(target=server.serve_forever,daemon=True).start()
    conn={'baseUrl':f'http://127.0.0.1:{server.server_port}/v1','model':'circuit-main','apiKey':'controlled-test-key'}
    try:
        user=a.register('a'); uid=user['id']; token=a.login(user,'InitialPass123','login fixture')['accessToken']
        a.report['fixtures'].append({'id':uid,'name':user['name']}); a.save()
        def call(name,c=conn,code=200,path='test',owner=token,body=None):
            return a.call(name,'/ai/planner/'+path,body or {'connection':c},'POST',owner,expected=(200,code))
        def status(c=conn,owner=token): return call('read model state',c,path='circuit',owner=owner)['data']
        def usage(owner=token): return a.call('actual usage','/ai/planner/usage',token=owner)['data']
        def measure(c=conn,owner=token):
            u=usage(owner); return [counts[c['model']],u['quota']['hourly']['used'],u['today']['operations']]
        modes[conn['model']]='error'
        for i in range(4): call(f'actual provider failure {i+1}',code=3004)
        result=call('circuit state is available',path='circuit')
        before=counts[conn['model']]
        call('open breaker rejects next call',code=503)
        a.check('blocked request has no upstream call',counts[conn['model']],before)
        if args.label=='baseline': return finish(a)
        a.check('four failures open circuit',result['data']['phase'],'OPEN')
        a.check('outbound failure samples',result['data']['total'],4)
        a.check('configured shortened integration times',[result['data']['windowSeconds'],result['data']['cooldownSeconds'],result['data']['minimumCalls']],[60,4,4])
        before=measure()
        normalized={**conn,'baseUrl':conn['baseUrl'].replace('127.0.0.1','localhost')+'/', 'apiKey':' '+conn['apiKey']+' '}
        call('normalized connection shares open circuit',normalized,503)
        generate={'connection':conn,'query':'希望轻松体验当地美食','days':2,'budget':3000,'peopleNum':2}
        call('generation shares test circuit',code=503,path='generate',body=generate)
        a.check('blocked requests consume no quota or audit',measure(),before)
        a.call('anonymous state denied','/ai/planner/circuit',{'connection':conn},'POST',expected=(401,401))
        call('unsafe state address rejected',{**conn,'baseUrl':'http://169.254.169.254'},400,path='circuit')
        modes[conn['model']]='ok'
        for label,c in [('changed key',{**conn,'apiKey':'other-controlled-key'}),('changed model',{**conn,'model':'other-model'}),('changed path',{**conn,'baseUrl':conn['baseUrl'].replace('/v1','/v2')})]:
            a.check(label+' has fresh state',status(c)['total'],0); call(label+' can call',c)
        pc={**conn,'baseUrl':f'http://127.0.0.1:{second.server_port}/v1'}
        a.check('another actual provider port isolated',status(pc)['total'],0); call('another provider port can call',pc)
        other=a.register('b'); other_id=other['id']; other_token=a.login(other,'InitialPass123','login isolated user')['accessToken']
        a.report['fixtures'].append({'id':other_id,'name':other['name']}); a.save()
        a.check('other user isolated',status(conn,other_token)['total'],0); call('other user reaches healthy provider',owner=other_token)
        # Cooldown really elapses, no production data or deadline is rewritten.
        time.sleep(4.1)
        call('half-open healthy probe recovers')
        recovered=status(); a.check('probe closes circuit',recovered['phase'],'CLOSED'); a.check('recovery starts fresh window',recovered['total'],1)
        modes[conn['model']]='error'
        for i in range(3): call('reopen after recovery '+str(i),code=3004)
        a.check('failed window reopens',status()['phase'],'OPEN')
        time.sleep(4.1); call('failed half-open probe',code=3004)
        a.check('probe failure restarts cooldown',status()['phase'],'OPEN')
        before=measure(); call('failed probe blocks another call',code=503); a.check('no phantom outbound after failed probe',measure(),before)

        # Independent Redis clients exercise the distributed gate beyond the JVM user lock.
        ks=keys(args.prefix,uid,conn); time.sleep(4.1)
        with ThreadPoolExecutor(max_workers=8) as pool: grants=list(pool.map(lambda _:lua(ks,'acquire'),range(8)))
        permits=[r for r in grants if r['allowed']]; a.check('only one of eight independent clients probes',len(permits),1)
        a.check('live probe visible through HTTP',status()['phase'],'HALF_OPEN')
        before=measure(); call('live probe rejects HTTP',code=503); a.check('busy probe no quota or audit',measure(),before)
        old=permits[0]; time.sleep(4.1)
        a.check('expired lease reopens safely',status()['phase'],'OPEN')
        lua(ks,'complete',old,True); a.check('late expired result cannot close',status()['phase'],'OPEN')
        time.sleep(4.1); modes[conn['model']]='ok'; call('real HTTP recovers after crashed probe')
        lua(ks,'complete',old,False); a.check('stale failure cannot reopen recovered circuit',status()['phase'],'CLOSED')

        # Quota rejection must release a reserved probe without adding a sample.
        quota_user=a.register('q'); qid=quota_user['id']; qt=a.login(quota_user,'InitialPass123','login quota fixture')['accessToken']
        a.report['fixtures'].append({'id':qid,'name':quota_user['name']}); a.save()
        qc={**conn,'model':'quota-fixture'}; qkeys=keys(args.prefix,qid,qc)
        for _ in range(4): p=lua(qkeys,'acquire'); lua(qkeys,'complete',p,False)
        time.sleep(4.1)
        epoch=int(dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).replace(minute=0,second=0,microsecond=0).timestamp())
        hourkey=args.quota_prefix+':{ai-quota}:user:'+str(qid)+':hour:'+str(epoch)
        redis('SET',hourkey,20,'EX',3600)
        call('quota denies reserved probe',qc,429,owner=qt)
        a.check('quota release is immediately eligible',status(qc,qt)['retryAfterSeconds'],0)
        a.check('quota rejection adds no sample',status(qc,qt)['total'],4)
        redis('DEL',hourkey); call('released probe can recover',qc,owner=qt)

        # Strictly >30%, not >=30%. HTTP actually forwards all 11 attempts.
        threshold_user=a.register('t'); tid=threshold_user['id']; tt=a.login(threshold_user,'InitialPass123','login threshold fixture')['accessToken']
        a.report['fixtures'].append({'id':tid,'name':threshold_user['name']}); a.save()
        tc={**conn,'model':'threshold-fixture'}
        for i in range(7): call('threshold success '+str(i),tc,owner=tt)
        modes[tc['model']]='error'
        for i in range(3): call('threshold failure '+str(i),tc,3004,owner=tt)
        a.check('exactly thirty percent stays closed',status(tc,tt)['phase'],'CLOSED')
        call('greater than thirty percent failure',tc,3004,owner=tt)
        a.check('four of eleven opens',status(tc,tt)['phase'],'OPEN')

        failure_user=a.register('f'); fid=failure_user['id']; ft=a.login(failure_user,'InitialPass123','login fault matrix')['accessToken']
        a.report['fixtures'].append({'id':fid,'name':failure_user['name']}); a.save()
        for mode in ['auth','rate','error','timeout','malformed','big','invalid']:
            fc={**conn,'model':'fault-'+mode}; modes[fc['model']]=mode
            call('real fault '+mode,fc,3004,path='generate',owner=ft,body={**generate,'connection':fc})
            a.check(mode+' counted by attempt',status(fc,ft)['failed'],2 if mode=='invalid' else 1)
            a.check(mode+' below minimum does not open',status(fc,ft)['phase'],'CLOSED')
        bc={**conn,'model':'bad-input'}
        call('invalid input is not provider failure',bc,400,path='generate',owner=ft,body={**generate,'connection':bc,'days':15})
        a.check('invalid input creates no state',status(bc,ft)['total'],0)
        a.check('no raw provider error persisted',t.sql(f"SELECT COUNT(*) FROM llm_call_log WHERE user_id IN ({uid},{fid}) AND (error_msg LIKE '%DO_NOT_EXPOSE%' OR error_msg LIKE '%controlled-test-key%')"),'0')

        # Real Redis sliding-window expiry, TTL and corrupted-state fail-closed.
        wkeys=keys(args.prefix,uid,{**conn,'model':'window-fixture'})
        p=lua(wkeys,'acquire'); lua(wkeys,'complete',p,False)
        member=redis('ZRANGE',wkeys[1],0,0); oldscore=int(time.time()*1000)-61000
        redis('ZADD',wkeys[1],oldscore,member); redis('ZADD',wkeys[2],oldscore,member)
        state=lua(wkeys); a.check('old samples pruned',[state['total'],state['failed']],[0,0])
        a.check('circuit keys expire',0<int(redis('PTTL',wkeys[0]))<=136000,True)
        corrupt={**conn,'model':'corrupt-fixture'}; ckeys=keys(args.prefix,uid,corrupt)
        redis('HSET',ckeys[0],'phase','BROKEN')
        before=measure(corrupt); call('corrupt state denies outbound',corrupt,503); call('corrupt status safe error',corrupt,503,path='circuit')
        a.check('corrupt state no outbound quota audit',measure(corrupt),before)
        for key in ckeys: redis('DEL',key)
        # Failed structured probe stops the automatic retry before quota admission.
        probe_conn={**conn,'model':'invalid-probe-fixture'}; probe_keys=keys(args.prefix,qid,probe_conn)
        for _ in range(4): p=lua(probe_keys,'acquire'); lua(probe_keys,'complete',p,False)
        time.sleep(4.1); modes[probe_conn['model']]='invalid'; before=measure(probe_conn,qt)
        call('invalid half-open draft blocks structural retry',probe_conn,503,path='generate',owner=qt,body={**generate,'connection':probe_conn})
        after=measure(probe_conn,qt); a.check('one failed probe attempt and one failed operation',[after[i]-before[i] for i in range(3)],[1,1,1])
        # Nonexistent state lookup must not allocate keys.
        a.check('read-only empty lookup does not create keys',len(scan(args.prefix+':{'+str(fid)+':'+hashlib.sha256((bc['baseUrl']+'/chat/completions\0'+bc['model']+'\0'+bc['apiKey']).encode()).hexdigest()+'}:')),0)
        a.check('Redis keys contain no endpoint or key',any('controlled-test-key' in k or '127.0.0.1' in k for k in scan(args.prefix+':')),False)
    except Exception as error:
        a.check('acceptance completed',str(error),'no error')
    finally:
        for user in a.users:
            uid=user['id']
            clean_sessions(uid)
            for prefix in [args.prefix+':{'+str(uid)+':',args.quota_prefix+':{ai-quota}:user:'+str(uid)+':']:
                for key in scan(prefix): redis('DEL',key)
            t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('fixture user and sessions cleaned '+str(uid),t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        server.shutdown(); server.server_close(); second.shutdown(); second.server_close(); a.report['phase']='COMPLETE'; a.save()
    return finish(a)

def finish(a):
    checks=a.report['checks']; failed=sum(not r['passed'] for r in checks)
    print(f'{len(checks)-failed}/{len(checks)} passed; {a.out}')
    return bool(failed)

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--base',default='http://localhost:8080/api'); p.add_argument('--label',default='baseline')
    p.add_argument('--output'); p.add_argument('--prefix',default='trip:ai:circuit'); p.add_argument('--quota-prefix',default='trip:ai:quota')
    p.add_argument('--serve',action='store_true'); p.add_argument('--port',type=int,default=11436)
    args=p.parse_args()
    if args.serve:
        print(f'Controlled provider on 127.0.0.1:{args.port}; NOT real AI',flush=True)
        ThreadingHTTPServer(('127.0.0.1',args.port),Provider).serve_forever()
    else: raise SystemExit(main(args))
