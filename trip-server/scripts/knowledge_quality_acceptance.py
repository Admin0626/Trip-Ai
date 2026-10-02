"""Real lexical quality and maintenance HTTP/SQL checks; only owned fixtures are changed."""
import argparse,datetime as dt,uuid,urllib.parse,hashlib,subprocess,json
from concurrent.futures import ThreadPoolExecutor
from circuit_acceptance import Run,t
from redis_fixture import clean_sessions,clean_ai_state

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/knowledge-quality'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-{args.label}.json'
    a.report.update(mode='REAL_HTTP_MYSQL_REDIS_LEXICAL_QUALITY_NO_LLM',phase='RUNNING',fixtures=[],qualityCases=[])
    docs=[];dest=None;route=None;prefix='KQ_'+uuid.uuid4().hex[:8]
    try:
        user=a.register('qr');admin=a.register('qa');t.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={admin['id']}")
        token=a.login(user,'InitialPass123','login reader')['accessToken'];at=a.login(admin,'InitialPass123','login admin')['accessToken']
        def call(name,p,body=None,method='GET',owner=at,code=200,http=200):return a.call(name,p,body,method,owner,expected=(http,code))
        def save(title,content,status=1,kind='GUIDE',sid=None):
            d=call('create owned knowledge','/admin/ai/knowledge',{'title':prefix+title,'content':content,'status':status,'docType':kind,'sourceId':sid},'POST')['data']
            docs.append(d['id']);a.report['fixtures'].append({'docId':d['id'],'title':d['title']});a.save();return d
        def search(q,k=5):return call('actual lexical search','/ai/knowledge/search',{'query':q,'topK':k},'POST',token)['data']
        def page(**params):return call('maintenance filtered page','/admin/ai/knowledge/page?'+urllib.parse.urlencode({'keyword':prefix,**params}))['data']
        def case(name,q,expected):
            r=search(q);ids=list(dict.fromkeys(x['docId'] for x in r['references']));ranks=[ids.index(i)+1 for i in expected if i in ids]
            record={'name':name,'query':q,'expectedDocIds':expected,'returnedDocIds':ids,'hit':bool(ranks) if expected else not ids,'allExpectedPresent':all(i in ids for i in expected),'expectedSourceRecallAt5':len(ranks)/len(expected) if expected else None,'reciprocalRank':1/min(ranks) if ranks else 0}
            a.report['qualityCases'].append(record);a.check('quality '+name,record['allExpectedPresent'] if expected else record['hit'],True);return r
        # Long earlier document must not crowd out the second equally relevant source.
        long=save('霁澜星径甲',('霁澜星径步行指南。'+('甲乙丙丁戊己庚辛壬癸'*45)+'\n')*12)
        short=save('霁澜星径乙','霁澜星径雨天步行需穿防滑鞋。')
        r=case('distinct sources','霁澜星径',[long['id'],short['id']]);a.check('two sources before repeated chunks',[x['docId'] for x in r['references'][:2]],[long['id'],short['id']])
        tail=save('澄汐湖湾','甲乙丙丁戊己庚辛壬癸'*170+'\n澄汐湖湾清晨步行指南。')
        r=case('body-bearing first excerpt','澄汐湖湾',[tail['id']]);a.check('first excerpt contains query','澄汐湖湾' in r['references'][0]['excerpt'],True)
        en=save('QuietLake cycling guide','Cycling beside QuietLake is suitable in the morning.')
        case('fullwidth English','ＣＹＣＬＩＮＧ QUIETLAKE',[en['id']]);case('unknown content','土星雷暴天气',[])
        off=save('停用霜羽谷','霜羽谷步行资料。',0);case('disabled content','霜羽谷',[])
        # Simulate missing index rows while metadata still claims readiness, only on our fixture.
        damaged=save('缺片岚岳桥','岚岳桥步行资料。');t.sql(f"DELETE FROM knowledge_chunk WHERE doc_id={damaged['id']}")
        call('missing chunks must not expose public full text','/ai/knowledge/documents/'+str(damaged['id']),owner=token,code=404)
        if args.label=='baseline':
            call('health endpoint available','/admin/ai/knowledge/health')
        else:
            before=call('quota before maintenance','/ai/planner/usage',owner=token)['data']
            call('anonymous health denied','/admin/ai/knowledge/health',owner='',code=401,http=401)
            call('reader health denied','/admin/ai/knowledge/health',owner=token,code=403,http=403)
            call('reader repair denied','/admin/ai/knowledge/repair',{'documents':[{'id':damaged['id'],'expectedRevision':damaged['revision']}]},'POST',token,403,403)
            health=call('global index health','/admin/ai/knowledge/health')['data'];a.check('health has rebuild deficit',health['needsRebuild']>=1,True)
            bad=page(indexState='NEEDS_REBUILD');a.check('damaged index filtered',[x['id'] for x in bad['records']],[damaged['id']]);a.check('actual chunk count',bad['records'][0]['actualChunkCount'],0)
            for param in [{'indexState':'BAD'},{'sourceState':'BAD'},{'size':101},{'current':0}]:call('invalid maintenance filter','/admin/ai/knowledge/page?'+urllib.parse.urlencode(param),code=400)
            def repair(items):return call('actual bounded per-item repair','/admin/ai/knowledge/repair',{'documents':items},'POST')['data']
            item=lambda d,v=None:{'id':d['id'],'expectedRevision':d['revision'] if v is None else v}
            # Healthy data skipped; stale/missing are repaired; unknown/version conflicts isolated.
            old_chunks=t.sql(f"SELECT GROUP_CONCAT(id ORDER BY id) FROM knowledge_chunk WHERE doc_id={long['id']}")
            mixed=repair([item(damaged),item(long),item(short,999),{'id':999999999,'expectedRevision':1}])
            a.check('mixed results stable order',[x['outcome'] for x in mixed['results']],['REPAIRED','UNCHANGED','FAILED','FAILED'])
            a.check('mixed error codes',[x['code'] for x in mixed['results']],[200,200,409,404]);a.check('mixed summary',[mixed['succeeded'],mixed['unchanged'],mixed['failed']],[1,1,2])
            a.check('healthy chunk ids preserved',t.sql(f"SELECT GROUP_CONCAT(id ORDER BY id) FROM knowledge_chunk WHERE doc_id={long['id']}"),old_chunks)
            a.check('repaired public body',call('public text after repair','/ai/knowledge/documents/'+str(damaged['id']),owner=token)['data']['content'],'岚岳桥步行资料。')
            r=repair([item(damaged)]);a.check('retry unchanged',r['results'][0]['outcome'],'UNCHANGED')
            for body in [{},{'documents':[]},{'documents':[item(long)]*2},{'documents':[{'id':str(long['id']),'expectedRevision':1}]},{'documents':[{'id':long['id'],'expectedRevision':True}]},{'documents':[{'id':-1,'expectedRevision':1}]},{'documents':[{'id':i,'expectedRevision':1} for i in range(1,12)]},{'documents':[None]}]:
                call('strict batch input rejected','/admin/ai/knowledge/repair',body,'POST',code=400)
            t.sql(f"UPDATE knowledge_doc SET revision=revision+1 WHERE id={off['id']}");off['revision']+=1
            r=repair([item(off)]);a.check('disabled repaired but stays disabled',[r['results'][0]['outcome'],int(t.sql(f"SELECT status FROM knowledge_doc WHERE id={off['id']}"))],['REPAIRED',0]);case('still disabled after repair','霜羽谷',[])
            t.sql(f"INSERT INTO destination(name,province,longitude,latitude,cover_img,status) VALUES ('{prefix}来源','夹具',100,25,'/fixture.png',1)");dest=int(t.sql(f"SELECT id FROM destination WHERE name='{prefix}来源'"))
            t.sql(f"INSERT INTO route(title,destination_id,cover_img,days,price,status,create_by) VALUES ('{prefix}路线',{dest},'/fixture.png',2,100,1,{admin['id']})");route=int(t.sql(f"SELECT id FROM route WHERE title='{prefix}路线'"))
            linked=save('霄羽联程','霄羽联程路线资料。',kind='ROUTE',sid=route)
            t.sql(f'UPDATE destination SET status=0 WHERE id={dest}')
            p=page(sourceState='UNAVAILABLE');a.check('hidden parent source filtered',[x['id'] for x in p['records']],[linked['id']]);case('hidden parent absent','霄羽联程',[])
            t.sql(f"UPDATE knowledge_doc SET revision=revision+1 WHERE id={linked['id']}");linked['revision']+=1
            repair([item(linked)]);a.check('repair does not reopen source',int(t.sql(f'SELECT status FROM destination WHERE id={dest}')),0);case('repaired blocked source absent','霄羽联程',[])
            # Legacy no source id remains explicit and no source is guessed.
            t.sql(f"UPDATE knowledge_doc SET source_id=NULL WHERE id={linked['id']}")
            p=page(sourceState='UNLINKED');a.check('legacy missing source visible to admin',[x['id'] for x in p['records']],[linked['id']])
            # Corrupted generation and count are visible and repairable without changing body.
            t.sql(f"UPDATE knowledge_chunk SET doc_revision=0 WHERE doc_id={tail['id']}")
            p=page(indexState='NEEDS_REBUILD');a.check('chunk generation mismatch classified',tail['id'] in [x['id'] for x in p['records']],True)
            def race(_):return a.request('/admin/ai/knowledge/repair',{'documents':[item(tail)]},'POST',at)['body']['data']['results'][0]['outcome']
            with ThreadPoolExecutor(max_workers=2) as pool:outcomes=list(pool.map(race,[1,2]))
            a.check('concurrent repair one rebuild',sorted(outcomes),['REPAIRED','UNCHANGED'])
            t.sql(f"UPDATE knowledge_doc SET chunk_count=chunk_count+1 WHERE id={short['id']}");repair([item(short)])
            a.check('all own indexes healthy',page(indexState='NEEDS_REBUILD')['total'],0)
            h=call('health partition totals','/admin/ai/knowledge/health')['data'];a.check('index health partition',h['indexReady']+h['needsRebuild'],h['total']);a.check('state health partition',h['enabled']+h['disabled'],h['total'])
            after=call('quota after maintenance','/ai/planner/usage',owner=token)['data'];a.check('local maintenance no model quota or audit',after,before)
            # Verify the newly merged optional email feature remains disabled on this live server.
            address='quality-'+uuid.uuid4().hex[:10]+'@trip.invalid'
            call('disabled registration mail','/auth/email/code',{'email':address},'POST',token,503)
            call('disabled password recovery mail','/auth/password/code',{'email':address},'POST',token,503)
            call('disabled binding mail','/user/email/code',{'email':address,'password':'InitialPass123'},'POST',token,503)
            call('unissued reset rejected','/auth/password/reset',{'email':address,'code':'123456','newPassword':'NewQuality123'},'POST',token,400)
            digest=hashlib.sha256(address.encode()).hexdigest();cursor='0';keys=[]
            while True:
                scanned=subprocess.check_output(['redis-cli','--raw','SCAN',cursor,'MATCH','trip:mail:*'+digest,'COUNT','100'],text=True).splitlines();cursor=scanned[0];keys.extend(x for x in scanned[1:] if x)
                if cursor=='0':break
            a.check('disabled mail creates no owned challenge or quota',keys,[])
            health=json.load(__import__('urllib.request',fromlist=['urlopen']).urlopen(a.base+'/actuator/health'));a.check('optional disabled mail leaves app healthy',health['status'],'UP')
    except Exception as e:a.check('run completed',str(e),'no error')
    finally:
        for id in docs:t.sql(f'DELETE FROM knowledge_chunk WHERE doc_id={id}');t.sql(f'DELETE FROM knowledge_doc WHERE id={id}')
        if route:t.sql(f'DELETE FROM route WHERE id={route}')
        if dest:t.sql(f'DELETE FROM destination WHERE id={dest}')
        for u in a.users:t.sql(f"DELETE FROM sys_user WHERE id={u['id']}");clean_sessions(u['id']);clean_ai_state(u['id'])
        a.check('owned fixture docs cleaned',int(t.sql(f"SELECT COUNT(*) FROM knowledge_doc WHERE title LIKE '{prefix}%'")),0)
        cases=a.report['qualityCases'];positive=[x for x in cases if x['expectedDocIds']]
        a.report['qualitySummary']={'samples':len(cases),'allExpectedPresent':sum(x['allExpectedPresent'] and x['hit'] for x in cases),'positiveSamples':len(positive),'meanExpectedSourceRecallAt5':sum(x['expectedSourceRecallAt5'] for x in positive)/len(positive) if positive else 0,'meanReciprocalRankAt5':sum(x['reciprocalRank'] for x in positive)/len(positive) if positive else 0,'scope':'controlled lexical fixtures, not semantic/production benchmark'}
        a.report['phase']='COMPLETE';a.save()
    print(str(a.report['summary'])+'; '+str(a.out));return int(a.report['summary']['failed']>0)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8080/api');p.add_argument('--label',default='final');raise SystemExit(main(p.parse_args()))
