"""Real Trip-AI HTTP/SQL against a controlled local OpenAI-compatible provider.
This is integration testing, NOT a real language-model quality evaluation.
Use --serve to provide a localhost:11435 fixture for browser acceptance.
"""
import argparse
import collections
import datetime as dt
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sys
import threading
import urllib.request
import urllib.error
import uuid
import batch3_intent_acceptance as t
from redis_fixture import clean_sessions,clean_ai_state

counts = collections.Counter()
received = []


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_): pass

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        model = body['model']
        counts[model] += 1
        received.append({'path': self.path, 'authorized': self.headers.get('Authorization') == 'Bearer fixture-key-not-a-real-secret'})
        if model == 'fixture-auth':
            status, data = 401, {'error': 'fixture-key-not-a-real-secret must never reach the browser'}
        elif model == 'fixture-redirect':
            self.send_response(302); self.send_header('Location', 'http://169.254.169.254/latest'); self.end_headers(); return
        elif model == 'fixture-big':
            status, data = 200, {'oversized': 'x' * 1048600}
        else:
            status = 200
            if body['max_tokens'] == 16:
                content = 'OK'
            elif model == 'fixture-invalid' or (model == 'fixture-retry' and counts[model] == 1):
                content = 'this is not valid JSON'
            else:
                user = json.loads(body['messages'][-1]['content'].split('\n', 1)[0])
                content = json.dumps({'title': '可控服务生成的测试行程', 'dayList': [
                    {'title': f'测试第{i+1}天', 'summary': '仅为协议验收夹具，非真实模型内容',
                     'items': [{'title': '古城散步', 'activity': '散步与休息', 'timePoint': '09:00', 'cost': None,
                                'attractionId': 999999, 'sortNo': 999},
                               {'title': '体验当地美食', 'cost': 120, 'meal': '特色餐饮'}]}
                    for i in range(user['days'])]}, ensure_ascii=False)
            data = {'choices': [{'message': {'content': content}}]}
        raw = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(raw))); self.end_headers()
        try: self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError): pass


def start_provider(port=0):
    server = ThreadingHTTPServer(('127.0.0.1', port), Provider)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def redact(value):
    if isinstance(value, dict):
        return {k: '<redacted>' if k.lower() in {'apikey', 'password', 'oldpassword', 'newpassword', 'accesstoken', 'refreshtoken', 'authorization'} else redact(v) for k, v in value.items()}
    if isinstance(value, list): return [redact(v) for v in value]
    return value


def acceptance():
    rows = []
    out = t.ROOT / 'docs/dev/evidence/user-planner' / (dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '-http.json')
    token, uid, plan_id = None, None, None
    server = start_provider()
    connection = {'baseUrl': f'http://127.0.0.1:{server.server_port}/v1', 'model': 'fixture-valid', 'apiKey': 'fixture-key-not-a-real-secret'}
    base = {'connection': connection, 'query': '去大理旅行，喜欢美食，不要太赶', 'days': 2, 'budget': 3000, 'peopleNum': 2}

    def save():
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({'mode': 'REAL_APP_HTTP_WITH_CONTROLLED_PROVIDER_NOT_REAL_LLM', 'fixtures':[] if uid is None else [{'id':uid}], 'checks': rows}, ensure_ascii=False, indent=2), encoding='utf-8')

    def verify(name, actual, expected):
        rows.append({'name': name, 'actual': actual, 'expected': expected, 'passed': actual == expected}); save()

    def call(name, path, body=None, code=200, auth=True, method='POST', http=200):
        req = urllib.request.Request(t.BASE + path, None if method == 'GET' else json.dumps(body).encode(),
                                     {'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token and auth else {})}, method=method)
        try: response = urllib.request.urlopen(req, timeout=125)
        except urllib.error.HTTPError as e: response = e
        result = json.loads(response.read())
        rows.append({'name': name, 'path': path, 'request': redact(body), 'http': response.status,
                     'expectedCode': code, 'expectedHttp': http, 'response': redact(result), 'passed': result.get('code') == code and response.status == http}); save()
        return result

    try:
        name = 'up_' + uuid.uuid4().hex[:12]
        credentials = {'username': name, 'password': 'TestPlanner123'}
        call('register fixture', '/auth/register', credentials)
        uid = int(t.sql(f"SELECT id FROM sys_user WHERE username='{name}'"))
        token = call('login fixture', '/auth/login', credentials)['data']['accessToken']
        call('anonymous denied', '/ai/planner/generate', base, code=401, http=401, auth=False)
        opts = call('endpoint options', '/ai/planner/options', method='GET')['data']
        verify('local development endpoint available', opts['allowLoopback'], True)
        call('connection actually reaches provider', '/ai/planner/test', {'connection': connection})
        verify('key forwarded only to selected fixture', received[-1]['authorized'], True)
        verify('path appended once', received[-1]['path'], '/v1/chat/completions')
        result = call('generate valid preview', '/ai/planner/generate', base)['data']
        verify('number of days', len(result['draft']['dayList']), 2)
        verify('untrusted attraction id removed', result['draft']['dayList'][0]['items'][0]['attractionId'], 0)
        verify('unknown cost remains null', result['draft']['dayList'][0]['items'][0]['cost'], None)
        verify('preview does not auto-save', t.sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uid}'), '0')
        retried = call('invalid JSON retries once', '/ai/planner/generate', {**base, 'connection': {**connection, 'model': 'fixture-retry'}})['data']
        verify('retry count returned', retried['attempts'], 2)
        verify('exactly two upstream retry requests', counts['fixture-retry'], 2)
        for model in ['fixture-invalid', 'fixture-auth', 'fixture-redirect', 'fixture-big']:
            result = call(model, '/ai/planner/generate', {**base, 'connection': {**connection, 'model': model}}, code=3004)
            verify(model + ' hides upstream secret/error body', 'fixture-key-not-a-real-secret' in json.dumps(result), False)
        verify('invalid JSON capped at two', counts['fixture-invalid'], 2)
        verify('auth error not retried', counts['fixture-auth'], 1)
        for label, update in [('days over limit', {'days': 15}), ('blank query', {'query': '     '}), ('budget invalid', {'budget': -1}), ('invalid people', {'peopleNum': 11})]:
            call(label, '/ai/planner/generate', {**base, **update}, code=400)
        for label, update in [('fractional days rejected', {'days': 1.5}), ('numeric string days rejected', {'days': '2'}),
                              ('numeric query rejected', {'query': 12345}), ('string budget rejected', {'budget': '3000'})]:
            call(label, '/ai/planner/generate', {**base, **update}, code=400)
        for date in ['yesterday', '2000-01-01']:
            call('invalid or past start date ' + date, '/ai/planner/generate', {**base, 'startDate': date}, code=400)
        for url in ['http://169.254.169.254/latest', 'http://localhost:80/v1', 'https://untrusted.example/v1', connection['baseUrl'] + '/chat/completions', connection['baseUrl'] + '?key=abc']:
            call('reject endpoint ' + url, '/ai/planner/test', {'connection': {**connection, 'baseUrl': url}}, code=400)
        call('header injection rejected', '/ai/planner/test', {'connection': {**connection, 'apiKey': 'bad\r\nHeader: injected'}}, code=400)
        # Persist only after explicit user confirmation (simulated by this separate request).
        draft = call('generate for confirmed save', '/ai/planner/generate', base)['data']['draft']
        saved = call('confirm draft save', '/plan', {**draft, 'days': 2, 'destinationIds': [], 'startDate': str(dt.date.today()), 'budget': 3000, 'peopleNum': 2, 'status': 0})
        plan_id = saved['data']
        detail = call('read own saved plan', f'/plan/{plan_id}', method='GET')['data']
        verify('saved days preserved', len(detail['dayList']), 2)
        verify('save creates one draft', t.sql(f'SELECT COUNT(*) FROM user_plan WHERE user_id={uid} AND status=0'), '1')
        verify('audit never stores key or query', t.sql(f"SELECT COUNT(*) FROM llm_call_log WHERE user_id={uid} AND (error_msg LIKE '%fixture-key%' OR error_msg LIKE '%大理%')"), '0')
        verify('provider failures recorded', t.sql(f"SELECT COUNT(*) FROM llm_call_log WHERE user_id={uid} AND scene='USER_PLANNER' AND success=0"), '4')
    finally:
        if plan_id:
            t.sql(f'DELETE i FROM user_plan_item i JOIN user_plan_day d ON d.id=i.plan_day_id WHERE d.user_plan_id={plan_id}; DELETE FROM user_plan_day WHERE user_plan_id={plan_id}; DELETE FROM user_plan WHERE id={plan_id}')
        if uid:
            verify('fixture sessions removed',clean_sessions(uid)>=1,True)
            verify('fixture AI state removed',clean_ai_state(uid)>=1,True)
            t.sql(f'DELETE FROM llm_call_log WHERE user_id={uid}; DELETE FROM user_preference WHERE user_id={uid}; DELETE FROM sys_user WHERE id={uid}')
            verify('fixtures cleaned', t.sql(f'SELECT COUNT(*) FROM sys_user WHERE id={uid}'), '0')
        server.shutdown(); server.server_close(); save()
    failed = sum(not row['passed'] for row in rows)
    print(f'{len(rows)-failed}/{len(rows)} passed; {out}')
    return 1 if failed else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--serve', action='store_true')
    parser.add_argument('--port', type=int, default=11435)
    args = parser.parse_args()
    if args.serve:
        server = ThreadingHTTPServer(('127.0.0.1', args.port), Provider)
        print(f'Controlled test provider listening on 127.0.0.1:{args.port}; NOT a real model', flush=True)
        server.serve_forever()
    else:
        sys.exit(acceptance())
