"""Live app/MySQL/Redis source maintenance, with owned fixtures and no model."""
import argparse,datetime as dt,uuid,urllib.parse,hashlib,threading
from concurrent.futures import ThreadPoolExecutor
from circuit_acceptance import Run,t
from redis_fixture import clean_sessions,clean_ai_state

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/knowledge-sources'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-{args.label}.json'
    a.report.update(mode='REAL_HTTP_MYSQL_REDIS_SOURCE_MAINTENANCE_NO_LLM',phase='RUNNING',fixtures=[])
    prefix='KS_'+uuid.uuid4().hex[:8];docs=[];dests=[];routes=[]
    try:
        admin=a.register('sa');reader=a.register('sr');t.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']}")
        at=a.login(admin,'InitialPass123','admin login')['accessToken'];ut=a.login(reader,'InitialPass123','reader login')['accessToken']
        def call(name,p,body=None,method='GET',token=at,code=200,http=200):return a.call(name,p,body,method,token,expected=(http,code))
        def dest(name,status=1,deleted=0):
            t.sql(f"INSERT INTO destination(name,province,longitude,latitude,cover_img,status,deleted) VALUES ('{prefix}{name}','夹具',100,25,'/fixture.png',{status},{deleted})")
            id=int(t.sql(f"SELECT id FROM destination WHERE name='{prefix}{name}'"));dests.append(id);a.report['fixtures'].append({'destinationId':id,'name':prefix+name});a.save();return id
        pub=dest('公开');hidden=dest('未公开',0);gone=dest('已删除',1,1)
        for i in range(22):dest(f'分页{i:02}',i%2)
        literal=dest('字面%_=');duplicate=dest('同名')
        t.sql(f"INSERT INTO destination(name,province,longitude,latitude,cover_img,status) VALUES ('{prefix}同名','另一省夹具',100,25,'/fixture.png',1)")
        dests.extend(int(x) for x in t.sql(f"SELECT id FROM destination WHERE name='{prefix}同名' AND id<>{duplicate}").splitlines())
        for name,parent,status,deleted in [('公开路线',pub,1,0),('未公开路线',pub,0,0),('父级隐藏',hidden,1,0),('父级删除',gone,1,0),('已删路线',pub,1,1)]:
            t.sql(f"INSERT INTO route(title,destination_id,cover_img,days,price,status,deleted,create_by) VALUES ('{prefix}{name}',{parent},'/fixture.png',2,100,{status},{deleted},{admin['id']})")
            routes.append(int(t.sql(f"SELECT id FROM route WHERE title='{prefix}{name}'")))
        a.report['fixtures'].append({'routeIds':routes});a.save()
        def sources(kind='DESTINATION',code=200,**params):return call('source summary page','/admin/ai/knowledge/sources?'+urllib.parse.urlencode({'docType':kind,**params}),code=code).get('data')
        for p in ['/admin/ai/knowledge/sources?docType=DESTINATION','/admin/ai/knowledge/1/source']:
            method='PUT' if p.endswith('/source') else 'GET';body={'docType':'GUIDE','expectedRevision':1} if method=='PUT' else None
            call('anonymous source operation denied',p,body,method,token='',code=401,http=401);call('reader source operation denied',p,body,method,token=ut,code=403,http=403)
        page=sources(keyword=prefix,size=20);a.check('undeleted unpublished entries included',page['total'],len(dests)-1)
        a.check('source projection has no catalog body',sorted(page['records'][0]),sorted(['id','name','status','available','parentName','availabilityReason']))
        p2=sources(keyword=prefix,size=20,current=2);a.check('paging complete and disjoint',len(set(r['id'] for r in page['records']+p2['records'])),len(dests)-1)
        a.check('literal wildcard query escaped', [x['id'] for x in sources(keyword=prefix+'字面%_=')['records']],[literal])
        a.check('duplicate names remain identifiable',len(sources(keyword=prefix+'同名')['records']),2)
        a.check('hidden selected resolved',[sources(sourceId=hidden)['records'][0]['available'],sources(sourceId=hidden)['records'][0]['availabilityReason']],[False,'UNPUBLISHED'])
        a.check('deleted selected omitted',sources(sourceId=gone)['records'],[])
        rr=sources('ROUTE',keyword=prefix)['records'];a.check('deleted route/parent omitted',sorted(r['id'] for r in rr),sorted(routes[:3]))
        a.check('hidden parent distinguished',next(r['availabilityReason'] for r in rr if r['id']==routes[2]),'PARENT_UNAVAILABLE')
        for params in [{'docType':'GUIDE'},{'docType':'BAD'},{'current':0},{'current':1000001},{'size':0},{'size':51},{'sourceId':0},{'sourceId':'x'},{'keyword':'甲'*101},{'keyword':'bad\x00text'}]:
            kind=params.pop('docType','DESTINATION');sources(kind,code=400,**params)
        a.check('empty large page',sources(keyword=prefix,current=1000000)['records'],[])
        def doc(suffix,status=1):
            d=call('create owned knowledge','/admin/ai/knowledge',{'title':prefix+suffix,'content':'澄屿来源核验。'+('正文保持😀。'*90),'docType':'GUIDE','sourceId':None,'status':status},'POST')['data'];docs.append(d['id']);a.report['fixtures'].append({'docId':d['id']});a.save();return d
        def detail(id):return call('current knowledge','/admin/ai/knowledge/'+str(id))['data']
        def change(d,kind='DESTINATION',sid=pub,code=200,rev=None):return call('source-only update',f"/admin/ai/knowledge/{d['id']}/source",{'docType':kind,'sourceId':sid,'expectedRevision':d['revision'] if rev is None else rev},'PUT',code=code).get('data')
        def fingerprint(id):return hashlib.sha256(t.sql(f"SELECT id,chunk_index,start_offset,end_offset,content FROM knowledge_chunk WHERE doc_id={id} ORDER BY id").encode()).hexdigest(),t.sql(f"SELECT t.chunk_id,t.token FROM knowledge_chunk_token t JOIN knowledge_chunk c ON c.id=t.chunk_id WHERE c.doc_id={id} ORDER BY t.chunk_id,t.token")
        def search(code=200):return call('public search','/ai/knowledge/search',{'query':'澄屿来源核验','topK':5},'POST',ut,code)['data']['references']
        def public(id,code=200):return call('public knowledge',f'/ai/knowledge/documents/{id}',token=ut,code=code)
        d=doc('正文保持');t.sql(f"UPDATE knowledge_doc SET source_type='FILE' WHERE id={d['id']}");d=detail(d['id']);before=fingerprint(d['id'])
        session=call('private session','/ai/chat/session',{},'POST',ut)['data']
        call('save original reference','/ai/chat/search',{'sessionId':session['id'],'query':'澄屿来源核验','topK':1,'requestId':str(uuid.uuid4())},'POST',ut)
        def history():return call('refresh private history',f"/ai/chat/message/page?sessionId={session['id']}",token=ut)['data']['records'][-1]['result']
        r=change(d,'ROUTE',routes[0]);new=r['document'];a.check('source change outcome',r['outcome'],'UPDATED')
        for key in ['title','content','status','sourceType','chunkCount','indexMethod','vectorStatus']:a.check('source-only preserves '+key,new[key],d[key])
        a.check('revision moves exactly once',[new['revision'],new['indexedRevision']],[d['revision']+1,d['revision']+1]);a.check('chunks and tokens preserved',fingerprint(d['id']),before)
        a.check('public current source path',search()[0]['sourcePath'],'/route/'+str(routes[0]));a.check('history uses updated source path',history()['references'][0]['sourcePath'],'/route/'+str(routes[0]))
        same=change(new,'ROUTE',routes[0]);a.check('unchanged skips version',[same['outcome'],same['document']['revision']],['UNCHANGED',new['revision']])
        change(d,'ROUTE',routes[0],409);change(new,'GUIDE',pub,400);change(new,'DESTINATION',None,400);change(new,'DESTINATION',gone,400);change(new,'ROUTE',routes[3],400);change(new,'ROUTE',routes[4],400);change(new,'DESTINATION',hidden,400)
        for patch in [{'expectedRevision':None},{'expectedRevision':'2'},{'expectedRevision':0},{'sourceId':'1'},{'sourceId':1.5},{'sourceId':0},{'sourceId':2**65},{'docType':2},{'docType':'OTHER'}]:
            call('strict source payload',f"/admin/ai/knowledge/{d['id']}/source",{**{'docType':'DESTINATION','sourceId':pub,'expectedRevision':new['revision']},**patch},'PUT',code=400)
        call('missing document', '/admin/ai/knowledge/999999999/source',{'docType':'GUIDE','expectedRevision':1},'PUT',code=404)
        a.check('failed writes leave revision',detail(d['id'])['revision'],new['revision'])
        t.sql(f'UPDATE destination SET status=0 WHERE id={pub}');a.check('parent down hides search',search(),[]);public(d['id'],404);a.check('parent down hides history',[history()['references'],history()['unavailableReferenceCount']],[[],1])
        t.sql(f'UPDATE destination SET status=1 WHERE id={pub}');a.check('parent restore history returns preserved chunk',len(history()['references']),1)
        t.sql(f'UPDATE route SET deleted=1 WHERE id={routes[0]}');public(d['id'],404);a.check('deleted source hides history',history()['references'],[]);t.sql(f'UPDATE route SET deleted=0 WHERE id={routes[0]}')
        independent=change(new,'GUIDE',None)['document'];a.check('explicit independent clears link',[independent['docType'],independent['sourceId']],[ 'GUIDE',None]);a.check('independent history source null',history()['references'][0]['sourcePath'],None)
        disabled=doc('停用草稿',0);disabled=change(disabled,'ROUTE',routes[2])['document'];a.check('draft accepts hidden parent',[disabled['status'],disabled['sourceId']],[0,routes[2]]);public(disabled['id'],404)
        call('hidden linked draft cannot enable',f"/admin/ai/knowledge/{disabled['id']}/status",{'status':1,'expectedRevision':disabled['revision']},'PUT',code=400)
        # Legacy non-guide/null source is never guessed or rewritten during listing.
        legacy=doc('旧待关联');t.sql(f"UPDATE knowledge_doc SET doc_type='DESTINATION' WHERE id={legacy['id']}");legacy=detail(legacy['id']);ids=fingerprint(legacy['id']);legacy=change(legacy)['document'];a.check('legacy source explicit and IDs retained',[legacy['sourceId'],fingerprint(legacy['id'])==ids],[pub,True])
        # Future metadata/generation can collide with the next revision; source edits must not heal it.
        for fault in ['indexed_revision=revision+1','chunk_count=chunk_count+1']:
            broken=doc('异常'+str(len(docs)),0);t.sql(f"UPDATE knowledge_doc SET {fault} WHERE id={broken['id']}");broken=detail(broken['id']);broken=change(broken)['document'];a.check('damaged source edit invalidates index '+fault,broken['indexedRevision'],0)
        broken=doc('错代次',0);t.sql(f"UPDATE knowledge_chunk SET doc_revision=doc_revision+1 WHERE doc_id={broken['id']}");broken=change(broken)['document'];a.check('future chunks do not heal',broken['indexedRevision'],0)
        ready=call('damaged item repair','/admin/ai/knowledge/repair',{'documents':[{'id':broken['id'],'expectedRevision':broken['revision']}]},'POST')['data'];a.check('damaged still explicitly repairable',ready['succeeded'],1)
        race=doc('并发',0);barrier=threading.Barrier(2)
        def worker(sid):barrier.wait();return a.request(f"/admin/ai/knowledge/{race['id']}/source",{'docType':'DESTINATION','sourceId':sid,'expectedRevision':race['revision']},'PUT',at)
        with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(worker,[pub,hidden]))
        a.check('concurrent CAS one success one conflict',sorted(r['body']['code'] for r in results),[200,409],responses=results);a.check('CAS advances once',detail(race['id'])['revision'],race['revision']+1)
        deleted=doc('已删');call('delete owned document',f"/admin/ai/knowledge/{deleted['id']}?expectedRevision=1",method='DELETE');change(deleted,code=404)
    except Exception as e:a.check('runner completed',str(e),'no error')
    finally:
        for id in docs:t.sql(f'DELETE FROM knowledge_chunk WHERE doc_id={id}');t.sql(f'DELETE FROM knowledge_doc WHERE id={id}')
        for id in routes:t.sql(f'DELETE FROM route WHERE id={id}')
        for id in dests:t.sql(f'DELETE FROM destination WHERE id={id}')
        for u in a.users:
            if not u['id']:continue
            uid=u['id'];t.sql(f'DELETE FROM llm_chat_message WHERE session_id IN (SELECT id FROM llm_chat_session WHERE user_id={uid}); DELETE FROM llm_chat_session WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}');clean_sessions(uid);clean_ai_state(uid)
        for table,col in [('knowledge_doc','title'),('destination','name'),('route','title')]:a.check('owned cleanup '+table,int(t.sql(f"SELECT COUNT(*) FROM {table} WHERE {col} LIKE '{prefix}%'")),0)
        a.report['phase']='COMPLETE';a.save()
    print(str(a.report['summary'])+'; '+str(a.out));return bool(a.report['summary']['failed'])
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8080/api');p.add_argument('--label',default='final');raise SystemExit(main(p.parse_args()))
