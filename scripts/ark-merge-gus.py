#!/usr/bin/env python3
"""Merge a map's [ServerSettings] keys into the live GameUserSettings.ini, in place,
preserving every other section/key (image-managed RCON/session/MOTD, ARK defaults).
Usage: ark-merge-gus.py <live.ini> <map.ini>"""
import sys, re

live_p, map_p = sys.argv[1], sys.argv[2]


def server_settings_keys(path):
    keys, section = {}, None
    for raw in open(path):
        s = raw.split(';', 1)[0].strip()           # drop inline comments
        if s.startswith('[') and s.endswith(']'):
            section = s.lower()
        elif section == '[serversettings]' and '=' in s:
            k, v = s.split('=', 1)
            keys[k.strip()] = v.strip()
    return keys


mk = server_settings_keys(map_p)
if not mk:
    sys.exit(0)

lines = open(live_p).read().splitlines()

start = next((i for i, l in enumerate(lines) if l.strip().lower() == '[serversettings]'), None)
if start is None:                                   # no section yet — append a fresh one
    block = ['', '[ServerSettings]'] + [f'{k}={v}' for k, v in mk.items()]
    open(live_p, 'w').write('\n'.join(lines + block) + '\n')
    print(f'created [ServerSettings] with {len(mk)} keys')
    sys.exit(0)

end = next((i for i in range(start + 1, len(lines))
            if lines[i].strip().startswith('[') and lines[i].strip().endswith(']')), len(lines))

seen, body = set(), []
for l in lines[start + 1:end]:
    m = re.match(r'\s*([A-Za-z0-9_]+)\s*=', l)
    if m and m.group(1) in mk:
        body.append(f'{m.group(1)}={mk[m.group(1)]}')
        seen.add(m.group(1))
    else:
        body.append(l)
for k, v in mk.items():
    if k not in seen:
        body.append(f'{k}={v}')

open(live_p, 'w').write('\n'.join(lines[:start + 1] + body + lines[end:]) + '\n')
print(f'merged {len(mk)} keys into [ServerSettings] ({len(seen)} updated, {len(mk) - len(seen)} added)')
