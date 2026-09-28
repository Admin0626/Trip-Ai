"""Remove only explicitly owned fixture sessions; work with older Windows redis-cli."""
import subprocess


def session_keys(user_id):
    if not isinstance(user_id, int) or user_id <= 0:
        raise ValueError('Invalid fixture user ID')
    prefix = 'trip:auth:session:{' + str(user_id) + '}:'
    cursor, keys = '0', set()
    while True:
        lines = subprocess.check_output(['redis-cli', 'SCAN', cursor, 'MATCH', prefix + '*', 'COUNT', '100'], text=True).splitlines()
        if not lines or not lines[0].isdigit():
            raise RuntimeError('Invalid Redis SCAN response')
        cursor = lines[0]
        for key in lines[1:]:
            if not key:
                continue
            if not key.startswith(prefix):
                raise RuntimeError('Unexpected fixture session key')
            keys.add(key)
        if cursor == '0':
            return sorted(keys)


def clean_sessions(user_id):
    keys = session_keys(user_id)
    for key in keys:
        subprocess.check_output(['redis-cli', 'DEL', key], text=True)
    if session_keys(user_id):
        raise RuntimeError('Fixture sessions remain')
    return len(keys)
