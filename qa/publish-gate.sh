#!/bin/bash
# Run on an evidence folder BEFORE it is published to a public branch. Redacts text files in place and never prints matched text.
# Redacts: version-4 UUIDs (real account, media and room ids; fixture ids are version 5), the owner's first name and nickname,
# and email addresses other than example.com / GitHub noreply. PNG frames cannot be scanned: the QA fixture carries neutral
# identity, and frames must be viewed before they are cited.
exec python3 - "${1:-qa/out}" <<'PY'
import os, re, sys
d = sys.argv[1]
if not os.path.isdir(d): sys.exit(0)
U4 = re.compile(r'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}')
NAME = re.compile('[Aa][Vv][Rr][Uu][Mm][Yy]')
NICK = re.compile("\u05de\u05e9\u05d4['\u05d0-\u05ea]*")
MAIL = re.compile(r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}')
ok = lambda m: m.group(0).lower().endswith(('@example.com', '@users.noreply.github.com', '@noreply.github.com'))
n = 0
for root, _, files in os.walk(d):
    if '.git' in root.split(os.sep): continue
    for f in files:
        p = os.path.join(root, f)
        try:
            s = open(p, 'rb').read()
            if b'\0' in s[:4096]: continue
            t = s.decode('utf-8')
        except Exception:
            continue
        u = U4.sub('<uuid4>', t); u = NAME.sub('<name>', u); u = NICK.sub('<nick>', u)
        u = MAIL.sub(lambda m: m.group(0) if ok(m) else '<email>', u)
        if u != t:
            open(p, 'w', encoding='utf-8').write(u); n += 1
print('publish-gate: redacted %d text file(s) in %s' % (n, d))
PY
