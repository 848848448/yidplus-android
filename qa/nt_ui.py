#!/usr/bin/env python3
"""Helper NT: act on ONE chat notification in the open shade (uiautomator dump on stdin).
usage: nt_ui.py ROW_TEXT TARGET   -> prints "x y" to tap, or nothing.
ROW_TEXT: a text that appears inside the notification (e.g. its title). TARGET: an action button label
("Reply", "Mark as read", "Retry"), "expand" (the row's expand button, else a point to swipe down from), "body" (the
row's title), "send" (the inline reply send button, anywhere), "rows" (list each notification row's texts)."""
import sys, re, xml.etree.ElementTree as ET
raw = sys.stdin.read(); raw = raw[raw.find('<'):raw.rfind('>') + 1]
try: root = ET.fromstring(raw)
except Exception: sys.exit(0)
want, target = sys.argv[1], sys.argv[2]
def b(n):
    m = re.findall(r'\d+', n.get('bounds', '')); return [int(v) for v in m] if len(m) == 4 else None
def c(n):
    x1, y1, x2, y2 = b(n); return f"{(x1 + x2) // 2} {(y1 + y2) // 2}"
def texts(n): return [d.get('text', '') for d in n.iter() if d.get('text')] + [d.get('content-desc', '') for d in n.iter() if d.get('content-desc')]
rows = [n for n in root.iter() if n.get('resource-id', '').endswith('expandableNotificationRow')] or [root]
if target == 'rows':
    for r in rows: print(b(r), ' | '.join(t for t in texts(r) if t)[:200])
    sys.exit(0)
if target == 'send':
    for n in root.iter():
        if n.get('resource-id', '').endswith('remote_input_send') and b(n): print(c(n)); break
    sys.exit(0)
# innermost row that holds the text (a child row inside a bundle wins over the bundle)
hit = [r for r in rows if any(want in t for t in texts(r))]
hit = [r for r in hit if not any(o is not r and o in list(r.iter()) for o in hit)]
if not hit: sys.exit(0)
row = hit[0]
if target == 'expand':
    for n in row.iter():
        if n.get('resource-id', '').endswith('expand_button') and b(n) and b(n)[2] > b(n)[0]:
            if n.get('content-desc', '').lower() != 'collapse': print(c(n))
            sys.exit(0)
    x1, y1, x2, y2 = b(row); print(f"swipe {(x1 + x2) // 2} {y1 + 20}"); sys.exit(0)
if target == 'body':
    for n in row.iter():
        if want in n.get('text', '') and b(n): print(c(n)); sys.exit(0)
    sys.exit(0)
for n in row.iter():
    if (n.get('text', '') or n.get('content-desc', '')).strip().lower() == target.lower() and b(n) and b(n)[3] > b(n)[1]:
        print(c(n)); sys.exit(0)
