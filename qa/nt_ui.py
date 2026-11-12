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
if target == 'scroller':  # the shade's notification list: "x y_from y_to" for one upward scroll inside it
    for n in root.iter():
        if n.get('resource-id', '').endswith('notification_stack_scroller') and b(n):
            x1, y1, x2, y2 = b(n)
            rows_top = min([b(r)[1] for r in root.iter() if r.get('resource-id', '').endswith('backgroundNormal') and b(r)] or [y1])
            yf = y2 - 12; yt = max(rows_top + 5, yf - max(120, (y2 - y1) // 3))
            print(f"{(x1 + x2) // 2} {yf} {yt}"); break
    sys.exit(0)
if target == 'send':
    for n in root.iter():
        if n.get('resource-id', '').endswith('remote_input_send') and b(n): print(c(n)); break
    sys.exit(0)
parent = {ch: p for p in root.iter() for ch in p}
def is_exp(n): return n.get('resource-id', '').endswith('expand_button')
def nexp(n): return sum(1 for d in n.iter() if is_exp(d))
# the notification row = the LARGEST ancestor of the title node that holds exactly one expand button (one per notification)
titles = [n for n in root.iter() if want in n.get('text', '') and b(n)]
if not titles: sys.exit(0)
t0 = titles[0]; row = t0; cur = t0
while cur in parent:
    cur = parent[cur]
    k = nexp(cur)
    if k == 1: row = cur
    elif k > 1: break
if target == 'expand':
    if any(n.get('content-desc', '').lower() == 'collapse' for n in row.iter()): sys.exit(0)  # already expanded
    for n in row.iter():
        if n.get('resource-id', '').endswith('expand_button') and b(n) and b(n)[2] > b(n)[0]:
            print(c(n)); sys.exit(0)
    x1, y1, x2, y2 = b(row); print(f"swipe {(x1 + x2) // 2} {y1 + 10}"); sys.exit(0)
if target == 'body':
    for n in row.iter():
        if want in n.get('text', '') and b(n): print(c(n)); sys.exit(0)
    sys.exit(0)
for n in row.iter():
    if (n.get('text', '') or n.get('content-desc', '')).strip().lower() == target.lower() and b(n) and b(n)[3] > b(n)[1]:
        print(c(n)); sys.exit(0)
