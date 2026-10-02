"""Real local HTTP/MySQL/Redis private retrieval history; only owned fixtures are mutated.
Capacity boundaries use explicitly recorded owned SQL fixtures, not a load-test claim.
"""
import argparse,datetime as dt,json,uuid,threading,subprocess,os
from concurrent.futures import ThreadPoolExecutor
from circuit_acceptance import Run,t
from redis_fixture import clean_sessions,clean_ai_state

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/knowledge-sessions'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-{args.label}.json'
    a.report.update(mode='REAL_LOCAL_KNOWLEDGE_SESSION_HTTP_MYSQL_REDIS_NO_LLM',phase='RUNNING',fixtures=[],capacityFixtures='99 local sessions and 398 message rows for exact boundary checks, not a load test')
    docs=[];dest=None;route=None;prefix='KSS_'+uuid.uuid4().hex[:10]
    try:
        with __import__('urllib.request',fromlist=['urlopen']).urlopen(a.base+'/actuator/health',timeout=5) as r:
            if json.load(r)['status']!='UP':raise RuntimeError('Application not ready')
        user=a.register('ks');other=a.register('ko');admin=a.register('kad');limit=a.register('klim')
        t.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']}")
        ut=a.login(user,'InitialPass123','login reader')['accessToken'];ot=a.login(other,'InitialPass123','login second reader')['accessToken'];at=a.login(admin,'InitialPass123','login owned admin')['accessToken'];lt=a.login(limit,'InitialPass123','login limit reader')['accessToken']
        def call(name,path,body=None,method='GET',token=ut,code=200,http=200):return a.call(name,path,body,method,token,expected=(http,code))
        def create(title=None,token=ut):
            d=call('create own local session','/ai/chat/session',{} if title is None else {'title':title},'POST',token)['data'];a.report['fixtures'].append({'sessionId':d['id'],'userId':user['id'] if token==ut else limit['id']});a.save();return d
        def detail(id,token=ut,code=200):return call('owned session detail','/ai/chat/session/'+str(id),token=token,code=code).get('data')
        def page(id,size=20,before=None,token=ut,code=200):return call('owned chronological message page',f'/ai/chat/message/page?sessionId={id}&size={size}'+('' if before is None else '&beforeId='+str(before)),token=token,code=code).get('data')
        def search(s,q='霁澜星径',rid=None,top=1,token=ut,code=200):return call('persisted actual local retrieval','/ai/chat/search',{'sessionId':s['id'],'query':q,'topK':top,'requestId':rid or str(uuid.uuid4())},'POST',token,code).get('data')
        def docsave(body,id=None):
            d=call('owned knowledge fixture save','/admin/ai/knowledge'+('' if id is None else '/'+str(id)),body,'POST' if id is None else 'PUT',at)['data']
            if id is None:docs.append(d['id']);a.report['fixtures'].append({'docId':d['id'],'title':d['title']});a.save()
            return d
        for method,path,body in [('GET','/ai/chat/session/page',None),('POST','/ai/chat/session',{}),('POST','/ai/chat/search',{})]:call('anonymous session operation denied',path,body,method,token='',code=401,http=401)
        for query in ['current=0','size=51','current=abc']:call('session page bounds '+query,'/ai/chat/session/page?'+query,code=400)
        call('create title wrong type','/ai/chat/session',{'title':2},'POST',code=400)
        call('create title over bound','/ai/chat/session',{'title':'甲'*101},'POST',code=400)
        call('create title control rejected','/ai/chat/session',{'title':'bad\u0000title'},'POST',code=400)
        session=create();a.check('new session mode/counters',[session['mode'],session['title'],session['revision'],session['messageCount']],['LOCAL_SEARCH','新资料会话',1,0])
        a.check('empty history',page(session['id']),{'records':[],'hasMore':False,'nextBeforeId':None})
        for actor in [ot,at]:
            detail(session['id'],actor,404);page(session['id'],token=actor,code=404)
            call('cross owner rename denied','/ai/chat/session/'+str(session['id']),{'title':'stolen','expectedRevision':1},'PUT',actor,404)
            call('cross owner delete denied','/ai/chat/session/'+str(session['id'])+'?expectedRevision=1',method='DELETE',token=actor,code=404)
            search(session,token=actor,code=404)
        foreign=call('principal overrides userId query','/ai/chat/session/page?userId='+str(user['id']),token=ot)['data'];a.check('no other owner sessions',foreign['records'],[])
        # Legacy compatibility: this fixture is preserved by two idempotent upgrades and excluded from local views.
        t.sql(f"INSERT INTO llm_chat_session(user_id,title) VALUES ({user['id']},'{prefix}legacy')")
        legacy=int(t.sql(f"SELECT id FROM llm_chat_session WHERE user_id={user['id']} AND title='{prefix}legacy'"));t.sql(f"INSERT INTO llm_chat_message(session_id,role,content,references_json) VALUES ({legacy},'assistant','legacy fixture','[]')")
        before=t.sql(f'SELECT title,mode,revision,message_count FROM llm_chat_session WHERE id={legacy}')
        for n in [1,2]:
            result=subprocess.run(['mysql','-u','root','--default-character-set=utf8mb4','trip_llm'],input=(t.ROOT/'trip-server/src/main/resources/sql/upgrade_knowledge_sessions.sql').read_text(encoding='utf-8'),text=True,encoding='utf-8',capture_output=True,env={**os.environ,'MYSQL_PWD':os.getenv('MYSQL_PASSWORD','123456')});a.check('upgrade with legacy fixture '+str(n),result.returncode,0)
        a.check('legacy data/mode preserved',t.sql(f'SELECT title,mode,revision,message_count FROM llm_chat_session WHERE id={legacy}'),before)
        a.check('legacy message untouched',t.sql(f'SELECT content FROM llm_chat_message WHERE session_id={legacy}'),'legacy fixture');detail(legacy,code=404)
        a.check('legacy excluded from page',call('local sessions only','/ai/chat/session/page')['data']['total'],1)
        maximum=create('😀'*100);a.check('100 Unicode title supported',len(maximum['title']),100)
        body={'title':prefix+'原文旧标题','docType':'GUIDE','sourceId':None,'status':1,'content':'霁澜星径，旧历史私密片段。<img src=x onerror="alert(1)">'};doc=docsave(body)
        rid=str(uuid.uuid4());usage=call('local quota baseline','/ai/planner/usage')['data'];log_count=t.sql(f"SELECT COUNT(*) FROM llm_call_log WHERE user_id={user['id']}")
        turn=search(session,rid=rid);session=turn['session'];msgs=turn['messages'];result=msgs[1]['result']
        a.check('two atomic messages',[len(msgs),[m['role'] for m in msgs],[m['messageType'] for m in msgs]], [2,['user','assistant'],['LOCAL_QUERY','LOCAL_RESULT']])
        a.check('fresh request not replayed',turn['replayed'],False);a.check('session count/title/revision',[session['messageCount'],session['title'],session['revision']],[2,'霁澜星径',2])
        a.check('original reference actual body',[result['mode'],result['references'][0]['docId'],result['references'][0]['excerpt']],['LOCAL_NGRAM',doc['id'],body['content']])
        a.check('tokens are zero',[m['tokensUsed'] for m in msgs],[0,0]);a.check('persisted pair/count SQL',t.sql(f"SELECT COUNT(*),SUM(tokens_used) FROM llm_chat_message WHERE session_id={session['id']}"),'2\t0')
        replay=search(session,rid=rid);a.check('same request replay id/count',[replay['replayed'],[m['id'] for m in replay['messages']],replay['session']['messageCount']],[True,[m['id'] for m in msgs],2])
        search(session,'different question',rid=rid,code=409);search(session,rid=rid,top=2,code=409)
        # Simultaneous duplicate requests: one stored turn, one replay.
        barrier=threading.Barrier(2);race_id=str(uuid.uuid4());request={'sessionId':session['id'],'query':'霁澜星径','topK':1,'requestId':race_id}
        def simultaneous(_):barrier.wait();return a.request('/ai/chat/search',request,'POST',ut)['body']
        with ThreadPoolExecutor(max_workers=2) as pool:responses=list(pool.map(simultaneous,range(2)))
        a.check('duplicate concurrent actual codes',[r['code'] for r in responses],[200,200]);a.check('one concurrent replay',sorted(r['data']['replayed'] for r in responses),[False,True]);a.check('same concurrent message IDs',[m['id'] for m in responses[0]['data']['messages']],[m['id'] for m in responses[1]['data']['messages']])
        a.check('duplicate pair persisted once',t.sql(f"SELECT COUNT(*) FROM llm_chat_message WHERE session_id={session['id']} AND request_id='{race_id}'"),'2')
        # Distinct concurrent turns serialize while preserving exact counters.
        barrier=threading.Barrier(2)
        def distinct(_):barrier.wait();return a.request('/ai/chat/search',{**request,'requestId':str(uuid.uuid4())},'POST',ut)['body']['code']
        with ThreadPoolExecutor(max_workers=2) as pool:a.check('distinct concurrent appends',list(pool.map(distinct,range(2))),[200,200])
        session=detail(session['id']);a.check('all appends counted',[session['messageCount'],session['revision']],[8,5])
        all_ids=[int(x) for x in t.sql(f"SELECT id FROM llm_chat_message WHERE session_id={session['id']} ORDER BY id").splitlines()];first=page(session['id'],3);a.check('latest window ascending',[m['id'] for m in first['records']],all_ids[-3:])
        collected=[];cursor=None
        while True:
            window=page(session['id'],1,cursor);collected.extend(m['id'] for m in window['records'])
            if not window['hasMore']:break
            cursor=window['nextBeforeId']
        a.check('cursor complete without duplicates',sorted(collected),all_ids)
        for size,before in [(0,None),(51,None),(2,0),(2,-1)]:page(session['id'],size,before,code=400)
        for patch in [{'sessionId':str(session['id'])},{'sessionId':0},{'query':2},{'query':'😀!?'},{'query':'\ud800'},{'query':'甲'*201},{'topK':'2'},{'topK':True},{'topK':6},{'requestId':'bad'},{'requestId':42}]:call('strict search input rejected','/ai/chat/search',{**request,'requestId':str(uuid.uuid4()),**patch},'POST',code=400)
        a.check('invalid requests did not append',detail(session['id'])['messageCount'],8)
        # Historical snapshots stay in DB but the API must suppress changed/private reference text.
        doc=call('hide own knowledge','/admin/ai/knowledge/'+str(doc['id'])+'/status',{'status':0,'expectedRevision':doc['revision']},'PUT',at)['data']
        hidden=page(session['id']);raw=json.dumps(hidden,ensure_ascii=False);a.check('hidden old title/body/link omitted',any(x in raw for x in [body['title'],'旧历史私密片段','/knowledge/'+str(doc['id'])]),False)
        a.check('all hidden references marked',[m['result']['unavailableReferenceCount'] for m in hidden['records'] if m['result']],[1,1,1,1]);a.check('replay also revalidates',search(session,rid=rid)['messages'][1]['result']['references'],[])
        doc=call('restore unchanged reference','/admin/ai/knowledge/'+str(doc['id'])+'/status',{'status':1,'expectedRevision':doc['revision']},'PUT',at)['data'];a.check('restored unchanged current reference',page(session['id'])['records'][1]['result']['references'][0]['excerpt'],body['content'])
        doc=docsave({**body,'title':prefix+'当前标题','content':'晴岚星径，新的资料原文。','expectedRevision':doc['revision']},doc['id']);changed=page(session['id']);a.check('edited history avoids stale excerpts',any('旧历史私密片段' in json.dumps(m,ensure_ascii=False) for m in changed['records']),False)
        fresh=search(session,'晴岚星径');a.check('new retrieval uses current source',fresh['messages'][1]['result']['references'][0]['excerpt'],'晴岚星径，新的资料原文。');session=fresh['session']
        nohit=search(session,'土星雷暴天气');a.check('no match persisted explicitly',[nohit['messages'][1]['result']['matchedAtSearch'],nohit['messages'][1]['result']['references']],[False,[]]);session=nohit['session']
        follow=search(session,'那里呢');a.check('no inferred conversational context',follow['messages'][1]['result']['matchedAtSearch'],False);session=follow['session']
        # Real linked catalog parent visibility must also apply to old history.
        t.sql(f"INSERT INTO destination(name,province,longitude,latitude,cover_img,status) VALUES ('{prefix}目的地','夹具',100,25,'/fixture.png',1)");dest=int(t.sql(f"SELECT id FROM destination WHERE name='{prefix}目的地'"))
        t.sql(f"INSERT INTO route(title,destination_id,cover_img,days,price,status,create_by) VALUES ('{prefix}路线',{dest},'/fixture.png',2,100,1,{admin['id']})");route=int(t.sql(f"SELECT id FROM route WHERE title='{prefix}路线'"))
        linked=docsave({**body,'title':prefix+'目录引用','docType':'ROUTE','sourceId':route,'content':'澄岳联程，目录来源片段。'})
        linked_turn=search(session,'澄岳联程');session=linked_turn['session'];a.check('catalog history verified link',linked_turn['messages'][1]['result']['references'][0]['sourcePath'],'/route/'+str(route))
        t.sql(f'UPDATE destination SET status=0 WHERE id={dest}');last=page(session['id'],2)['records'][-1];a.check('hidden parent strips historical link/body',[last['result']['references'],last['result']['unavailableReferenceCount']],[[],1]);t.sql(f'UPDATE destination SET status=1 WHERE id={dest}')
        # Rename/delete CAS protects new turns and cannot act on stale lists.
        call('stale rename conflicts','/ai/chat/session/'+str(session['id']),{'title':'lost','expectedRevision':1},'PUT',code=409)
        call('stale delete conflicts','/ai/chat/session/'+str(session['id'])+'?expectedRevision=1',method='DELETE',code=409)
        for bad in [{'title':2,'expectedRevision':session['revision']},{'title':'甲'*101,'expectedRevision':session['revision']},{'title':'new','expectedRevision':str(session['revision'])}]:call('strict rename invalid','/ai/chat/session/'+str(session['id']),bad,'PUT',code=400)
        renamed=call('rename current session','/ai/chat/session/'+str(session['id']),{'title':prefix+'_100%','expectedRevision':session['revision']},'PUT')['data'];a.check('rename increments only revision',[renamed['revision'],renamed['messageCount']],[session['revision']+1,session['messageCount']]);session=renamed
        from urllib.parse import quote
        filtered=call('literal title wildcard filter','/ai/chat/session/page?keyword='+quote(prefix+'_100%'))['data'];a.check('escaped title literal',filtered['total'],1)
        call('delete version mandatory','/ai/chat/session/'+str(session['id']),method='DELETE',code=400)
        # Capacity fixtures belong only to separate temporary accounts/sessions.
        values=','.join(f"({limit['id']},'{prefix}limit{i}','LOCAL_SEARCH')" for i in range(99));t.sql('INSERT INTO llm_chat_session(user_id,title,mode) VALUES '+values)
        barrier=threading.Barrier(2)
        def cap_create(_):barrier.wait();return a.request('/ai/chat/session',{},'POST',lt)['body']['code']
        with ThreadPoolExecutor(max_workers=2) as pool:a.check('100-session cap serialized',sorted(pool.map(cap_create,range(2))),[200,400])
        a.check('exact session cap',t.sql(f"SELECT COUNT(*) FROM llm_chat_session WHERE user_id={limit['id']} AND mode='LOCAL_SEARCH' AND deleted=0"),'100')
        capped=create(prefix+'round-cap');first_cap=search(capped,'晴岚星径');capped=first_cap['session'];caprid=first_cap['requestId']
        values=','.join(f"({capped['id']},'{('user' if i%2==0 else 'assistant')}','{('LOCAL_QUERY' if i%2==0 else 'LOCAL_RESULT')}','capacity fixture')" for i in range(398));t.sql('INSERT INTO llm_chat_message(session_id,role,message_type,content) VALUES '+values);t.sql(f"UPDATE llm_chat_session SET message_count=400 WHERE id={capped['id']}")
        search(capped,'晴岚星径',code=400);a.check('cap still permits replay',search(capped,'晴岚星径',rid=caprid)['replayed'],True);a.check('exact message cap persists',t.sql(f"SELECT COUNT(*) FROM llm_chat_message WHERE session_id={capped['id']}"),'400')
        a.check('local history no AI quota',call('local quota after','/ai/planner/usage')['data'],usage);a.check('no model audit fabricated',t.sql(f"SELECT COUNT(*) FROM llm_call_log WHERE user_id={user['id']}"),log_count)
        call('delete own session and messages','/ai/chat/session/'+str(session['id'])+'?expectedRevision='+str(session['revision']),method='DELETE');detail(session['id'],code=404);page(session['id'],code=404);search(session,code=404)
        a.check('messages physically removed',t.sql(f"SELECT COUNT(*) FROM llm_chat_message WHERE session_id={session['id']}"),'0');a.check('deleted session hidden from list',any(s['id']==session['id'] for s in call('own sessions after delete','/ai/chat/session/page')['data']['records']),False)
    except Exception as e:a.check('runner completed',str(e),'no error')
    finally:
        for id in docs:t.sql(f'DELETE FROM knowledge_doc WHERE id={id}')
        if route:t.sql(f'DELETE FROM route WHERE id={route}')
        if dest:t.sql(f'DELETE FROM destination WHERE id={dest}')
        for u in a.users:
            if not u['id']:continue
            uid=u['id'];t.sql(f'DELETE FROM llm_chat_message WHERE session_id IN (SELECT id FROM llm_chat_session WHERE user_id={uid}); DELETE FROM llm_chat_session WHERE user_id={uid}');clean_sessions(uid);clean_ai_state(uid);t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('owned sessions/users cleaned '+str(uid),[t.sql(f'SELECT COUNT(*) FROM llm_chat_session WHERE user_id={uid}'),t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}')],['0','0'])
        a.check('owned knowledge cleaned',sum(int(t.sql(f'SELECT COUNT(*) FROM knowledge_doc WHERE id={id}')) for id in docs),0);a.report['phase']='COMPLETE';a.save()
    s=a.report['summary'];print(f"{s['passed']}/{s['total']} passed; {a.out}");return bool(s['failed'])
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080/api');p.add_argument('--label',default='final');raise SystemExit(main(p.parse_args()))
