"""Index only the ten unchanged repository demonstration documents through real admin HTTP.
Requires local MySQL fixture privileges. Never edits document bodies, publishes drafts,
guesses catalog IDs, or indexes arbitrary user documents. Admin UI rebuild is the usual path.
"""
import argparse,datetime as dt,hashlib,json,re,uuid
from circuit_acceptance import Run,t
from redis_fixture import clean_sessions,clean_ai_state

def main(args):
    a=Run(args);a.out=t.ROOT/'docs/dev/evidence/knowledge'/f'{dt.datetime.now():%Y%m%d-%H%M%S-%f}-demo-index.json'
    a.report.update(mode='REAL_HTTP_VERIFIED_DEMO_LOCAL_INDEX',phase='RUNNING',documents=[])
    try:
        seed=(t.ROOT/'trip-server/src/main/resources/sql/data.sql').read_text(encoding='utf-8')
        seeds=re.findall(r"^\((\d+), '([^']*)', '(GUIDE|DESTINATION|ROUTE)', 'MANUAL', '((?:[^']|'')*)', '', 0, 0, 1,",seed,re.M)
        if len(seeds)!=10:raise RuntimeError('Expected exactly ten known knowledge seeds')
        user=a.register('kseed');t.sql(f"UPDATE sys_user SET role='ADMIN' WHERE id={user['id']}")
        token=a.login(user,'InitialPass123','local index admin login')['accessToken']
        for sid,title,kind,content in seeds:
            id=int(sid);content=content.replace("''", "'")
            row=json.loads(t.sql(f"SELECT JSON_OBJECT('title',title,'docType',doc_type,'content',content,'sourceId',source_id,'status',status,'deleted',deleted) FROM knowledge_doc WHERE id={id}"))
            expected={'title':title,'docType':kind,'content':content,'sourceId':None,'status':1,'deleted':0}
            if row!=expected:
                a.report['documents'].append({'docId':id,'action':'SKIP_CHANGED_SEED'});a.save();continue
            current=a.call('read verified seed','/admin/ai/knowledge/'+str(id),method='GET',token=token)['data']
            result=a.call('rebuild verified seed','/admin/ai/knowledge/rebuild',{'id':id,'expectedRevision':current['revision']},'POST',token)['data']
            a.check('seed body and revision preserved '+str(id),[result['content'],result['revision'],result['vectorStatus']],[content,current['revision'],0])
            a.report['documents'].append({'docId':id,'title':title,'action':'INDEXED_LOCAL','contentSha256':hashlib.sha256(content.encode()).hexdigest(),'revision':result['revision'],'indexedRevision':result['indexedRevision'],'chunks':result['chunkCount']});a.save()
        for query in ['香格里拉十月','洱海骑行']:
            result=a.call('demo query '+query,'/ai/knowledge/search',{'query':query},'POST',token)['data']
            a.check('demo actual source references '+query,result['matched'],True)
    except Exception as e:a.check('seed index completed',str(e),'no error')
    finally:
        for u in a.users:
            if not u['id']:continue
            clean_sessions(u['id']);clean_ai_state(u['id']);t.sql(f"DELETE FROM sys_user WHERE id={u['id']}")
            a.check('owned seed index account cleaned',t.sql(f"SELECT COUNT(*) FROM sys_user WHERE id={u['id']}"),'0')
        a.report['phase']='COMPLETE';a.save()
    s=a.report['summary'];print(f"{s['passed']}/{s['total']} passed; {a.out}");return bool(s['failed'])
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080/api');p.add_argument('--label',default='demo-index');raise SystemExit(main(p.parse_args()))
