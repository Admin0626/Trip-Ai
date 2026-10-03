"""Actual app HTTP/MySQL/Redis; --real makes two bounded DeepSeek operations.
Never write keys, tokens or provider reasoning to evidence. --serve is a fixture.
"""
import argparse, datetime as dt, json, os, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from circuit_acceptance import Run
from redis_fixture import clean_sessions, clean_ai_state

received = []
class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        received.append({'singleBearer': self.headers.get('Authorization') == 'Bearer controlled-test-key',
                         'vendorExtensionsAbsent': 'thinking' not in body and 'response_format' not in body})
        if body['max_tokens'] == 16: content = 'OK'
        else:
            days = json.loads(body['messages'][-1]['content'].split('\n')[0])['days']
            content = json.dumps({'title':'旅行助手协议验收行程', 'answer':'这是受控模型的建议：上午散步，下午休息。<script>window.fixtureXss=true</script>',
                'dayList':[{'title':f'第{i+1}天','summary':'非真实模型质量评估', 'items':[{'title':'公园散步','timePoint':'09:00','cost':0}]} for i in range(days)]},ensure_ascii=False)
        raw = json.dumps({'choices':[{'finish_reason':'stop','message':{'content':content}}]},ensure_ascii=False).encode()
        self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(raw))); self.end_headers(); self.wfile.write(raw)

def main(args):
    a=Run(args)
    a.out=Path(__file__).resolve().parents[2]/'docs/dev/evidence/travel-assistant'/f'{dt.datetime.now():%Y%m%d-%H%M%S}-{"real" if args.real else "controlled"}-http.json'
    a.report.update(mode='REAL_APP_HTTP_MYSQL_REDIS_REAL_DEEPSEEK' if args.real else 'REAL_APP_HTTP_MYSQL_REDIS_CONTROLLED_PROVIDER',phase='RUNNING')
    server=None
    try:
        if args.real:
            key=os.environ['TRIP_DEEPSEEK_API_KEY']
            connection={'baseUrl':'https://api.deepseek.com','model':'deepseek-flash','apiKey':'Bearer '+key}
        else:
            server=ThreadingHTTPServer(('127.0.0.1',0),Provider)
            threading.Thread(target=server.serve_forever,daemon=True).start()
            connection={'baseUrl':f'http://127.0.0.1:{server.server_port}/v1','model':'assistant-fixture','apiKey':'Bearer controlled-test-key'}
        user=a.register('asst'); uid=user['id']; token=a.login(user,'InitialPass123','login own fixture')['accessToken']
        a.call('public key required before model call','/ai/planner/test',{'connection':{'baseUrl':'https://api.deepseek.com','model':'deepseek-flash','apiKey':' '}},'POST',token,expected=(200,400))
        a.check('missing key does not consume quota',a.sql(f'SELECT COUNT(*) FROM llm_call_log WHERE user_id={uid}'),'0')
        tested=a.call('actual connection reply','/ai/planner/test',{'connection':connection},'POST',token)
        a.check('connection gives actual final text',bool(tested.get('data',{}).get('reply','').strip()),True)
        body={'connection':connection,'query':'杭州一日游，喜欢西湖散步和本地美食，请安排轻松行程并说明建议。','days':1,'budget':600,'peopleNum':1,'startDate':str(dt.date.today())}
        generated=a.call('actual model suggestions and itinerary','/ai/planner/generate',body,'POST',token)
        draft=generated.get('data',{}).get('draft',{})
        a.check('model answer exists',bool(draft.get('answer','').strip()),True)
        a.check('daily itinerary validated',len(draft.get('dayList',[])),1)
        a.check('generation does not auto save',a.sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uid}'),'0')
        if generated.get('code')!=200: raise RuntimeError('Generation did not return validated preview')
        saved=a.call('explicitly save daily draft','/plan',{**draft,'days':1,'destinationIds':[],'startDate':str(dt.date.today()),'budget':600,'peopleNum':1,'status':0},'POST',token)
        if saved.get('code')!=200: raise RuntimeError('Save failed')
        detail=a.call('read saved itinerary',f'/plan/{saved["data"]}',token=token)['data']
        a.check('saved daily title',detail['dayList'][0]['title'],draft['dayList'][0]['title'])
        a.check('database contains one draft',a.sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uid} AND status=0'),'1')
        a.check('audit stores no key or prompt',a.sql(f"SELECT COUNT(*) FROM llm_call_log WHERE user_id={uid} AND error_msg NOT IN ('','BUSINESS_3004')"),'0')
        if not args.real:
            a.check('Bearer normalized exactly once',all(x['singleBearer'] for x in received),True)
            a.check('other providers unchanged',all(x['vendorExtensionsAbsent'] for x in received),True)
        a.report['phase']='COMPLETED'
    except Exception as e:
        # Avoid including exception request headers/keys.
        a.check('acceptance completed',type(e).__name__,'no exception'); a.report['phase']='FAILED'
    finally:
        for user in a.users:
            uid=user['id']
            if not uid: continue
            a.sql(f'DELETE i FROM user_plan_item i JOIN user_plan_day d ON d.id=i.plan_day_id JOIN user_plan p ON p.id=d.user_plan_id WHERE p.user_id={uid}; DELETE d FROM user_plan_day d JOIN user_plan p ON p.id=d.user_plan_id WHERE p.user_id={uid}; DELETE FROM user_plan WHERE user_id={uid}; DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}')
            clean_sessions(uid); clean_ai_state(uid)
            a.sql(f'DELETE FROM sys_user WHERE id={uid}')
            a.check('own fixtures removed',a.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        if server: server.shutdown(); server.server_close()
        a.save()
    print(json.dumps({'summary':a.report['summary'],'phase':a.report['phase'],'evidence':str(a.out)},ensure_ascii=True))
    return 0 if a.report['phase']=='COMPLETED' and a.report['summary']['failed']==0 else 1

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--serve',action='store_true'); p.add_argument('--real',action='store_true'); p.add_argument('--port',type=int,default=11439); p.add_argument('--base',default='http://127.0.0.1:8080/api'); p.add_argument('--label',default='travel-assistant')
    args=p.parse_args()
    if args.serve:
        print('Controlled fixture only',flush=True); ThreadingHTTPServer(('127.0.0.1',args.port),Provider).serve_forever()
    else: raise SystemExit(main(args))
