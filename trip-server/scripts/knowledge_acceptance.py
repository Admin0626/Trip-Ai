"""Real local HTTP/MySQL knowledge retrieval. Only explicitly owned fixtures are mutated."""
import argparse,datetime as dt,json,os,threading,uuid,urllib.request,urllib.error
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from circuit_acceptance import Run,t
from redis_fixture import clean_sessions,clean_ai_state

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/knowledge'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-{args.label}.json'
    a.report.update(mode='REAL_LOCAL_HTTP_MYSQL_REDIS_KNOWLEDGE_NO_LLM',phase='RUNNING',fixtures=[])
    docs=[];dest=None;route=None;prefix='KN_'+uuid.uuid4().hex[:8]
    try:
        user=a.register('ku');admin=a.register('ka');t.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']}")
        token=a.login(user,'InitialPass123','login reader')['accessToken'];at=a.login(admin,'InitialPass123','login owned admin')['accessToken']
        def call(name,path,body=None,method='GET',owner=at,code=200,http=200):return a.call(name,path,body,method,owner,expected=(http,code))
        def search(q='霁澜秘境',top=5):return call('actual local retrieval','/ai/knowledge/search',{'query':q,'topK':top},'POST',token)['data']
        def save(body,id=None,code=200):
            r=call('create/edit document','/admin/ai/knowledge'+('/'+str(id) if id else ''),body,'PUT' if id else 'POST',code=code)
            if r.get('data') and id is None:docs.append(r['data']['id']);a.report['fixtures'].append({'docId':r['data']['id'],'title':body['title']});a.save()
            return r.get('data')
        def state(d,status,code=200):return call('change document state','/admin/ai/knowledge/'+str(d['id'])+'/status',{'expectedRevision':d['revision'],'status':status},'PUT',code=code).get('data')
        def rebuild(d,code=200):return call('rebuild current local chunks','/admin/ai/knowledge/rebuild',{'id':d['id'],'expectedRevision':d['revision']},'POST',code=code).get('data')
        def detail(id,owner=at,code=200):return call('document detail','/admin/ai/knowledge/'+str(id),owner=owner,code=code).get('data')
        def public(id,code=200):return call('public source document','/ai/knowledge/documents/'+str(id),owner=token,code=code).get('data')
        call('anonymous retrieval denied','/ai/knowledge/search',{'query':'霁澜秘境'},'POST',owner='',http=401,code=401)
        call('reader cannot administer','/admin/ai/knowledge/page',owner=token,http=403,code=403)
        for query in ['current=0','size=101','status=4','current=abc']:call('invalid pagination '+query,'/admin/ai/knowledge/page?'+query,code=400)
        call('missing document denied','/admin/ai/knowledge/99999999',code=404)
        ready=int(t.sql("SELECT COUNT(*) FROM knowledge_doc WHERE deleted=0 AND status=1 AND index_method='LOCAL_NGRAM' AND indexed_revision=revision AND source_id IS NULL"))
        if ready==0:
            call('initial unindexed corpus reports maintenance','/ai/knowledge/search',{'query':'霁澜秘境'},'POST',token,503)
        # Keep an unrelated ready document so visibility tests exercise successful no-match responses.
        save({'title':prefix+'石径资料','docType':'GUIDE','sourceId':None,'content':'石径步行资料，需要穿舒适鞋。','status':1})
        body={'title':prefix+'霁澜秘境指南','docType':'GUIDE','sourceId':None,'content':'霁澜秘境晨间步行，注意休息。😀'+('甲乙丙丁戊己庚辛壬癸'*120),'status':1}
        d=save(body)
        if not d:raise RuntimeError('Document creation failed')
        a.check('local index not fake vector',[d['indexMethod'],d['indexedRevision'],d['revision'],d['vectorStatus']],['LOCAL_NGRAM',1,1,0])
        expected_chunks=[];content=d['content']
        for start in range(0,len(content),450):
            end=min(start+500,len(content));expected_chunks.append([start,end]);
            if end==len(content):break
        rows=t.sql(f"SELECT start_offset,end_offset,CHAR_LENGTH(content) FROM knowledge_chunk WHERE doc_id={d['id']} ORDER BY chunk_index").splitlines()
        a.check('persisted Unicode500 overlap50',[[int(x) for x in r.split('\t')] for r in rows],[[s,e,e-s] for s,e in expected_chunks])
        a.check('persisted chunk count',d['chunkCount'],len(expected_chunks));a.check('public content exact',public(d['id'])['content'],content)
        found=search();a.check('query mode/source',[found['mode'],found['matched'],found['references'][0]['docId']],['LOCAL_NGRAM',True,d['id']])
        a.check('topK bounded',len(search(top=1)['references']),1)
        a.check('stable ordering',search()['references'],found['references'])
        a.check('no source made up for guide',found['references'][0]['sourcePath'],None)
        a.check('score is lexical coverage',found['references'][0]['keywordCoverage'],1)
        before=call('quota before local search','/ai/planner/usage',owner=token)['data'];search();after=call('quota after local search','/ai/planner/usage',owner=token)['data'];a.check('no model quota/audit',after,before)
        page=call('title filter only own docs','/admin/ai/knowledge/page?keyword='+prefix)['data'];a.check('list excludes body','content' in page['records'][0],False)
        for bad in [{'query':2},{'query':None},{'query':'霁澜秘境','topK':'2'},{'query':'霁澜秘境','topK':True},{'query':'霁澜秘境','topK':1.5},{'query':'霁澜秘境','topK':0},{'query':'霁澜秘境','topK':6},{'query':' '},{'query':'😀!?'},{'query':'甲'*201}]:call('strict query invalid','/ai/knowledge/search',bad,'POST',token,400)
        for patch in [{'title':'x'*201},{'content':'x'*20001},{'content':'bad\u0000text'},{'content':'\ud800'},{'docType':'LINK'},{'status':'1'},{'status':None},{'sourceId':'1'},{'sourceId':-1},{'sourceId':2**65},{'sourceId':1},{'expectedRevision':1},{'title':2}]:save({**body,**patch},code=400)
        maximum=save({**body,'title':'😀'*200,'content':'😀'*20000,'status':0})
        a.check('Unicode exact title/body limits',[len(maximum['title']),len(maximum['content']),maximum['chunkCount']],[200,20000,45])
        english=save({**body,'title':prefix+'英文攻略','content':'Cycling guide beside QuietLake.','status':1})
        a.check('NFKC English word normalization',search('ＣＹＣＬＩＮＧ')['references'][0]['docId'],english['id'])
        call('rebuild integer type required','/admin/ai/knowledge/rebuild',{'id':str(d['id']),'expectedRevision':d['revision']},'POST',code=400)
        d=state(d,0);a.check('disabled absent from retrieval',search()['references'],[]);public(d['id'],404)
        d=state(d,1);a.check('reenable preserves index generation',[d['revision'],d['indexedRevision']],[3,3]);a.check('reenabled searchable',search()['matched'],True)
        old=d.copy();d=save({**body,'title':prefix+'晴岚秘境新指南','content':'晴岚秘境步行适合清晨。','expectedRevision':d['revision']},d['id'])
        a.check('edit replaces stale chunks',search()['references'],[]);a.check('new text searchable',search('晴岚秘境')['matched'],True)
        save({**body,'expectedRevision':old['revision']},d['id'],409);state(old,0,409);rebuild(old,409)
        call('stale delete conflicts','/admin/ai/knowledge/'+str(d['id'])+'?expectedRevision='+str(old['revision']),method='DELETE',code=409)
        d=rebuild(d);a.check('rebuild stable doc revision',d['revision'],4)
        barrier=threading.Barrier(2)
        def edit_race(index):
            barrier.wait();return a.request('/admin/ai/knowledge/'+str(d['id']),{**body,'title':prefix+'竞态'+str(index),'content':'晴岚秘境并发编辑','expectedRevision':d['revision']},'PUT',at)['body']['code']
        with ThreadPoolExecutor(max_workers=2) as pool:codes=list(pool.map(edit_race,[1,2]))
        a.check('exactly one concurrent writer',sorted(codes),[200,409]);d=detail(d['id'])
        t.sql(f"UPDATE knowledge_doc SET revision=revision+1 WHERE id={d['id']}")
        a.check('stale document excluded from ready corpus',search('晴岚秘境')['references'],[]);public(d['id'],404)
        d=detail(d['id']);d=rebuild(d);a.check('rebuild repairs stale revision',[d['revision'],d['indexedRevision']],[6,6])
        # Rebuild must synchronize normalized source text and codepoint offsets, including legacy CRLF/BOM.
        t.sql(f"UPDATE knowledge_doc SET content=CONCAT(CHAR(13),CHAR(10),'晴岚秘境',CHAR(13),CHAR(10),'正文'),revision=revision+1 WHERE id={d['id']}")
        d=detail(d['id']);d=rebuild(d);a.check('rebuild canonicalizes legacy body',[d['content'],d['revision'],d['indexedRevision']],['晴岚秘境\n正文',8,8])
        # Source isolation uses new SQL catalog fixtures, never existing user's content.
        t.sql(f"INSERT INTO destination(name,province,longitude,latitude,cover_img,status) VALUES ('{prefix}目的地','夹具',100,25,'/fixture.png',1)");dest=int(t.sql(f"SELECT id FROM destination WHERE name='{prefix}目的地'"))
        t.sql(f"INSERT INTO route(title,destination_id,cover_img,days,price,status,create_by) VALUES ('{prefix}路线',{dest},'/fixture.png',2,100,1,{admin['id']})");route=int(t.sql(f"SELECT id FROM route WHERE title='{prefix}路线'"))
        linked=save({**body,'title':prefix+'澄岳联程','content':'澄岳联程路线资料','docType':'ROUTE','sourceId':route})
        a.check('verified route link',search('澄岳联程')['references'][0]['sourcePath'],'/route/'+str(route))
        t.sql(f'UPDATE destination SET status=0 WHERE id={dest}');a.check('hidden parent excludes associated text',search('澄岳联程')['references'],[]);public(linked['id'],404)
        state(linked,1,400);linked=state(linked,0);t.sql(f'UPDATE destination SET status=1 WHERE id={dest}');linked=state(linked,1)
        t.sql(f'UPDATE route SET status=0 WHERE id={route}');a.check('hidden route excludes text',search('澄岳联程')['references'],[]);t.sql(f'UPDATE route SET status=1 WHERE id={route}')
        destination_doc=save({**body,'title':prefix+'澄岳目的地','content':'澄岳目的地资料','docType':'DESTINATION','sourceId':dest})
        a.check('verified destination link',search('澄岳目的地')['references'][0]['sourcePath'],'/destination/'+str(dest))
        save({**body,'docType':'ROUTE','sourceId':999999999},code=400);save({**body,'docType':'DESTINATION'},code=400)
        # Real multipart uploads, including failures. Files are never persisted.
        def upload(name,raw,code=200):
            boundary='KN-'+uuid.uuid4().hex;fields={'title':prefix+'晴岚导入','docType':'GUIDE','status':'1'}
            data=b''
            for k,v in fields.items():data+=f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode()
            data+=f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()+raw+f'\r\n--{boundary}--\r\n'.encode()
            req=urllib.request.Request(a.base+'/admin/ai/knowledge/upload',data,{'Authorization':'Bearer '+at,'Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
            try:r=urllib.request.urlopen(req,timeout=30)
            except urllib.error.HTTPError as e:r=e
            with r:value=json.load(r)
            a.check('actual multipart '+name,value['code'],code,response=value)
            if value.get('data'):docs.append(value['data']['id']);return value.get('data')
        imported=upload('guide.MD','\ufeff晴岚导入\r\n原文资料'.encode());a.check('UTF8 BOM/CRLF imported',[imported['sourceType'],imported['content']],['FILE','晴岚导入\n原文资料'])
        boundary=upload('exact-limit.txt',b'x'+b' '*262143);a.check('exact 256KiB accepted and stripped',boundary['content'],'x')
        for name,raw in [('bad.pdf',b'text'),('bad.txt',b'\xff\xfe'),('empty.txt',b''),('control.md',b'hello\x00world'),('large.txt',b'x'*262145),('long.txt',b'x'*20001)]:upload(name,raw,400)
        call('reader cannot upload','/admin/ai/knowledge/upload',{},'POST',token,403,403)
        call('delete version required','/admin/ai/knowledge/'+str(d['id']),method='DELETE',code=400)
        call('delete own document','/admin/ai/knowledge/'+str(d['id'])+'?expectedRevision='+str(d['revision']),method='DELETE')
        public(d['id'],404);detail(d['id'],code=404);a.check('deleted chunks/tokens cleaned',t.sql(f"SELECT COUNT(*) FROM knowledge_chunk WHERE doc_id={d['id']}"),'0')
        a.check('out of corpus no invented answer',[search('今天土星雷暴天气')['matched'],search('今天土星雷暴天气')['references']],[False,[]])
    except Exception as e:a.check('runner completed',str(e),'no error')
    finally:
        for id in docs:t.sql(f'DELETE FROM knowledge_doc WHERE id={id}')
        if route:t.sql(f'DELETE FROM route WHERE id={route}')
        if dest:t.sql(f'DELETE FROM destination WHERE id={dest}')
        a.check('all owned knowledge removed',sum(int(t.sql(f'SELECT COUNT(*) FROM knowledge_doc WHERE id={id}')) for id in docs),0)
        for u in a.users:
            if not u['id']:continue
            uid=u['id'];clean_sessions(uid);clean_ai_state(uid);t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            a.check('owned account removed '+str(uid),t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'),'0')
        a.report['phase']='COMPLETE';a.save()
    s=a.report['summary'];print(f"{s['passed']}/{s['total']} passed; {a.out}");return bool(s['failed'])
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080/api');p.add_argument('--label',default='final');raise SystemExit(main(p.parse_args()))
