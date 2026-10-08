"""Read-only dashboard/SQL comparison for donut charts; existing local admin login only.

No fixtures, model calls, data reseeding or business mutations. Credentials stay in memory.
"""
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
BASE = os.environ.get('ANALYTICS_BASE', 'http://127.0.0.1:8080/api')
out = ROOT / 'docs/dev/evidence/analytics-donut/readonly-http-sql.json'
checks, responses = [], []
token = None


def check(name, actual, expected):
    checks.append(dict(name=name, actual=actual, expected=expected, passed=actual == expected))


def sql(query):
    env = {**os.environ, 'MYSQL_PWD': os.environ.get('MYSQL_PASSWORD', '123456')}
    result = subprocess.run(['mysql', '-u', 'root', '-N', '-B', '--default-character-set=utf8mb4', 'trip_llm', '-e', query],
                            capture_output=True, text=True, encoding='utf-8', env=env, check=True)
    return [line.split('\t') for line in result.stdout.strip().splitlines()]


def call(path, body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.status, json.load(response)


try:
    _, login = call('/auth/login', {'username': os.environ.get('ANALYTICS_ADMIN_USER', 'admin'),
                                  'password': os.environ.get('ANALYTICS_ADMIN_PASSWORD', '123456')})
    if login.get('code') != 200:
        raise RuntimeError('Existing admin login failed; configure local ANALYTICS_ADMIN_USER/PASSWORD.')
    token = login['data']['accessToken']
    destinations = sql("SELECT id,name FROM destination WHERE deleted=0 AND name IN ('西安','大理') ORDER BY id")
    scopes = [(30, None), (7, None), (90, None)] + [(30, int(row[0])) for row in destinations]
    for days, did in scopes:
        path = f'/admin/analysis/dashboard?days={days}' + (f'&destinationId={did}' if did else '')
        http, result = call(path)
        check(path + ' HTTP/code', [http, result['code']], [200, 200])
        d = result['data']
        responses.append({'endpoint': path, 'httpStatus': http, 'body': result})
        start = dt.date.fromisoformat(d['startDate']).isoformat()
        until = (dt.date.fromisoformat(d['endDate']) + dt.timedelta(days=1)).isoformat()
        window = f"create_time>='{start} 00:00:00' AND create_time<'{until} 00:00:00'"
        booking_window = window.replace('create_time', 'b.create_time')
        rows = sql("SELECT b.status,COUNT(*) FROM route_booking b JOIN route r ON r.id=b.route_id "
                   "JOIN destination d ON d.id=r.destination_id WHERE r.deleted=0 AND d.deleted=0 AND "
                   + booking_window + (f' AND r.destination_id={did}' if did else '') + ' GROUP BY b.status')
        booking_counts = {int(status): int(count) for status, count in rows}
        rows = sql('SELECT success,COUNT(*) FROM llm_call_log WHERE ' + window + ' GROUP BY success')
        model_counts = {int(status): int(count) for status, count in rows}
        for key, counts, summary in [('bookingStatuses', booking_counts, 'bookings'), ('aiStatuses', model_counts, 'aiCalls')]:
            actual = [[s['status'], s['count']] for s in d[key]]
            expected = [[s['status'], counts.get(s['status'], 0)] for s in d[key]]
            check(path + ' ' + key + ' agrees SQL', actual, expected)
            check(path + ' ' + key + ' reconciles total', sum(s['count'] for s in d[key]), d['summary'][summary])
        check(path + ' trend window', len(d['trend']), days)
finally:
    if token:
        status, result = call('/auth/logout', {})
        check('Only this read-only verification session logged out', [status, result['code']], [200, 200])
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({'mode': 'REAL_READONLY_HTTP_MYSQL_NO_FIXTURES',
                               'boundary': 'Existing local admin login, read-only analytics and SQL. No model calls. Own session logout.',
                               'checks': checks, 'responses': responses,
                               'summary': {'passed': sum(c['passed'] for c in checks), 'total': len(checks)}},
                              ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"{sum(c['passed'] for c in checks)}/{len(checks)} passed; {out}")
if not checks or any(not c['passed'] for c in checks):
    raise SystemExit(1)
