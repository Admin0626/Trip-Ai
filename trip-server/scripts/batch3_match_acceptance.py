"""Real HTTP and MySQL tests; isolated route/destination/user fixtures, no model calls."""
import uuid
import sys
import batch3_intent_acceptance as t

t.OUT = t.ROOT / 'docs/dev/evidence/batch3-match' / (t.stamp + '-http.json')
route_ids, destination_ids = [], []
uid = None


def main():
    global uid
    name = 'match_' + uuid.uuid4().hex[:10]
    auth = {'username': name, 'password': 'MatchTest123456'}
    t.call('fixture registration', '/auth/register', auth, record=False)
    uid = int(t.sql(f"SELECT id FROM sys_user WHERE username='{name}'"))
    t.token = t.call('fixture login', '/auth/login', auth, record=False)['data']['accessToken']
    for i in range(3):
        destination_ids.append(int(t.sql(f"""INSERT INTO destination(name,province,city,longitude,latitude,cover_img,status,deleted)
            VALUES ('{name}{i}','{name}','测试城',100,25,'', {0 if i == 1 else 1}, {1 if i == 2 else 0}); SELECT LAST_INSERT_ID();""")))
    fixtures = [
        ('both', 5, 1000, '自然风光,美食', 3, 1, 0, 0),
        ('one', 5, 1500, '美食', 5, 1, 0, 0),
        ('substring', 5, 4000, '美食街', 4, 1, 0, 0),
        ('three-days', 3, 500, '美食', 4, 1, 0, 0),
        ('off-route', 5, 100, '自然风光,美食', 5, 0, 0, 0),
        ('deleted-route', 5, 100, '自然风光,美食', 5, 1, 1, 0),
        ('off-destination', 5, 100, '自然风光,美食', 5, 1, 0, 1),
        ('deleted-destination', 5, 100, '自然风光,美食', 5, 1, 0, 2),
    ]
    for label, days, price, tags, rating, status, deleted, dest in fixtures:
        route_ids.append(int(t.sql(f"""INSERT INTO route(title,destination_id,days,price,tags,avg_score,status,deleted)
            VALUES ('{name}-{label}',{destination_ids[dest]},{days},{price},'{tags}',{rating},{status},{deleted}); SELECT LAST_INSERT_ID();""")))
    endpoint = '/ai/recommend/match'

    def match(label, intent, check=None, top=5, code=200, auth=True, http=200):
        return t.call(label, endpoint, {'intent': intent, 'topN': top}, code=code, auth=auth, predicate=check, http=http)

    def ids(response):
        return [x['routeId'] for x in response['data']['list']]

    base = {'destinations': [name], 'days': 5, 'budget': 2000, 'preferenceTags': ['自然风光', '美食']}
    match('anonymous rejected', base, auth=False, code=401, http=401)
    first = match('hard filters and tag order', base, lambda r: ids(r) == route_ids[:2] and r['data']['totalCandidates'] == 2)
    t.check('no invented model scores', [x['llmScore'] for x in first['data']['list']], [None, None])
    t.check('exact tag fraction', [x['recallScore'] for x in first['data']['list']], [1, .5])
    t.check('honest source', first['data']['source'], 'RULE_BASED')
    match('topN does not change total', base, lambda r: len(ids(r)) == 1 and r['data']['totalCandidates'] == 2, top=1)
    match('default limit with null topN', base, lambda r: ids(r) == route_ids[:2], top=None)
    match('budget boundary inclusive', {**base, 'budget': 1000}, lambda r: ids(r) == route_ids[:1])
    match('price just outside excluded', {**base, 'budget': 999}, lambda r: not ids(r))
    match('exclude exact tags', {'destinations': [name], 'days': 5, 'avoid': ['美食']}, lambda r: ids(r) == [route_ids[2]])
    match('empty preferences sort rating', {'destinations': [name], 'days': 5}, lambda r: ids(r) == [route_ids[1], route_ids[2], route_ids[0]])
    match('duplicates do not inflate score', {**base, 'preferenceTags': ['美食', '美食']}, lambda r: all(x['recallScore'] == 1 for x in r['data']['list']))
    match('unsupported fields explicitly reported', {**base, 'pace': 'relaxed', 'travelMonth': 10}, lambda r: set(r['data']['unsupportedCriteria']) == {'pace', 'travelMonth'})
    match('unknown destination stays empty', {'destinations': ['不存在的目的地' + name]}, lambda r: r['data']['totalCandidates'] == 0 and not ids(r))
    match('SQL syntax is literal data', {'destinations': ["' OR 1=1 --"]}, lambda r: not ids(r))
    match('wildcard is literal data', {'destinations': ['%']}, lambda r: not ids(r))
    match('empty filters allowed', {}, lambda r: r['data']['totalCandidates'] >= 4 and len(ids(r)) <= 5)
    for label, intent, top in [
        ('missing intent', None, 5), ('days zero', {'days': 0}, 5), ('days too large', {'days': 366}, 5),
        ('numeric string', {'days': '5'}, 5), ('fraction', {'days': 5.5}, 5), ('boolean number', {'days': True}, 5),
        ('negative budget', {'budget': -1}, 5), ('huge budget', {'budget': 1000001}, 5),
        ('bad destinations type', {'destinations': '云南'}, 5), ('null list element', {'destinations': [None]}, 5),
        ('too many destinations', {'destinations': ['a', 'b', 'c', 'd']}, 5),
        ('unsupported tag', {'preferenceTags': ['美食街']}, 5),
        ('conflicting tags', {'preferenceTags': ['美食'], 'avoid': ['美食']}, 5),
        ('topN zero', {}, 0), ('topN over limit', {}, 21), ('topN string', {}, '5'),
    ]:
        match(label, intent, top=top, code=400)
    t.check('match does not create model call logs', t.sql(f'SELECT COUNT(*) FROM llm_call_log WHERE user_id={uid}'), '0')


if __name__ == '__main__':
    try:
        main()
    finally:
        if route_ids:
            t.sql('DELETE FROM route WHERE id IN (' + ','.join(map(str, route_ids)) + ')')
        if destination_ids:
            t.sql('DELETE FROM destination WHERE id IN (' + ','.join(map(str, destination_ids)) + ')')
        if uid:
            t.sql(f'DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            t.check('fixture account removed', t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'), '0')
        t.save()
    failed = sum(not row['passed'] for row in t.rows)
    print(f'{len(t.rows)-failed}/{len(t.rows)} passed; evidence: {t.OUT}')
    sys.exit(1 if failed else 0)
