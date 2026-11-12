#!/usr/bin/env python3
"""Helper NT QA: drive ONE chat notification in the open shade (Android 11), with adb.
usage: nt_ui.py find "ROW" TARGET   ROW = "A|B|C", texts that identify the notification (its title or a message line).
TARGET: an action label ("Reply", "Mark as read", "Retry"), "expand" (show it expanded), "body" (tap its title).
At 480x640 the shade's list is ~150 px tall under the quick settings, shorter than one expanded chat, so the title and
the buttons are never on screen together. So: every other expanded chat is collapsed first (collapsed rows show no
buttons), then the list is scrolled in small steps until the button of THIS chat is inside the visible part; it is tapped.
Exit 0 = tapped (or already expanded), 1 = not reachable. Each dump is kept in $OUT/NT-ui-*.xml."""
import sys, re, os, time, subprocess, xml.etree.ElementTree as ET
OUT = os.environ.get('OUT', 'qa/out')
def sh(*a, t=30):
    try: return subprocess.run(['adb', 'shell', *a], capture_output=True, timeout=t).stdout.decode('utf8', 'replace')
    except Exception: return ''
N = [int(os.environ.get('NTN', '0'))]
def dump():
    sh('uiautomator', 'dump', '/sdcard/nt.xml'); raw = sh('cat', '/sdcard/nt.xml')
    N[0] += 1; open(f"{OUT}/NT-ui-{N[0]:02d}.xml", 'w').write(raw)
    raw = raw[raw.find('<'):raw.rfind('>') + 1]
    try: return ET.fromstring(raw)
    except Exception: return None
def b(n):
    m = re.findall(r'\d+', n.get('bounds', '')); return [int(v) for v in m] if len(m) == 4 else None
def ctr(n): x1, y1, x2, y2 = b(n); return (x1 + x2) // 2, (y1 + y2) // 2
def label(n): return (n.get('text') or n.get('content-desc') or '').strip()
def say(m): print(m, flush=True)
def layout(root):
    scr = next((n for n in root.iter() if n.get('resource-id', '').endswith('notification_stack_scroller') and b(n)), None)
    if scr is None: return None
    shelfy = [b(r)[1] for r in scr if b(r) and any(d.get('content-desc', '').startswith(('YID PLUS notification', 'Android System notification')) for d in r.iter())]
    rows = [r for r in scr if b(r) and any(d.get('resource-id', '').endswith('backgroundNormal') for d in r.iter()) and b(r)[1] not in shelfy]
    top = max([b(scr)[1]] + [b(n)[3] for n in root.iter() if n.get('resource-id', '').endswith('quick_settings_container') and b(n)])
    bottom = min([b(scr)[3]] + shelfy)
    return scr, rows, top, bottom
def visible(n, top, bottom):
    if not b(n) or b(n)[3] <= b(n)[1]: return False
    y = ctr(n)[1]; return top + 6 <= y <= bottom - 6
def tap(n, why): x, y = ctr(n); sh('input', 'tap', str(x), str(y)); say(f"tap {why} at {x} {y} (ui {N[0]})"); time.sleep(1.5)
def scroll(scr, top, bottom, d):
    d = max(-110, min(110, d)); x = (b(scr)[0] + b(scr)[2]) // 2
    if d >= 0: y0 = bottom - 8; y1 = y0 - d - 14
    else: y0 = top + 8; y1 = y0 - d + 14
    sh('input', 'swipe', str(x), str(y0), str(x), str(y1), '700'); time.sleep(1.2)
def find(want, target):
    want = [w for w in want.split('|') if w]
    seen = False; dirn = 1; last = None
    for step in range(22):
        root = dump()
        lay = root is not None and layout(root)
        if not lay: say('no shade list'); return 1
        scr, rows, top, bottom = lay
        sig = [tuple(b(r)) + (label(r),) for r in rows]
        if sig == last: dirn = -dirn; say('end of the list, turning back')
        last = sig
        acted = False
        for r in rows:
            labs = [label(d) for d in r.iter() if label(d)]
            mine = any(w in l for l in labs for w in want)
            coll = next((d for d in r.iter() if d.get('content-desc', '').lower() == 'collapse'), None)
            exp = next((d for d in r.iter() if d.get('resource-id', '').endswith('expand_button') and visible(d, top, bottom)), None)
            if not mine and coll is not None and exp is not None: tap(exp, 'collapse other chat'); acted = True; break
            if mine:
                seen = True
                if coll is None and exp is not None:
                    tap(exp, 'expand'); acted = True; break
                if target == 'expand' and coll is not None: say('expanded'); return 0
        if acted: continue
        if target in ('expand', 'body'):
            for r in rows:
                if any(w in label(d) for d in r.iter() for w in want):
                    t = next((d for d in r.iter() if d.get('resource-id', '').endswith(('conversation_text', 'title')) and b(d)), None)
                    if t is not None and visible(t, top, bottom):
                        if target == 'body': tap(t, 'title'); return 0
                    elif t is not None: scroll(scr, top, bottom, ctr(t)[1] - (top + bottom) // 2); break
            else:
                scroll(scr, top, bottom, 60 * dirn)
            continue
        # the button: in the row that shows the chat, else (title scrolled away) the first row with buttons after it
        cands = []
        idx = [i for i, r in enumerate(rows) if any(w in label(d) for d in r.iter() for w in want)]
        for i, r in enumerate(rows):
            if idx and i < idx[0]: continue
            mine = i in idx
            btn = next((d for d in r.iter() if label(d).lower() == target.lower() and b(d) and b(d)[3] > b(d)[1]), None)
            if btn is not None and (mine or seen): cands.append((mine, btn))
        cands.sort(key=lambda c: not c[0])
        if cands:
            btn = cands[0][1]
            if visible(btn, top, bottom): tap(btn, target); return 0
            scroll(scr, top, bottom, ctr(btn)[1] - (top + bottom) // 2); continue
        scroll(scr, top, bottom, 60 * dirn)
    say('not reachable'); return 1
if __name__ == '__main__':
    rc = find(sys.argv[2], sys.argv[3]) if sys.argv[1] == 'find' else 1
    open(f"{OUT}/.ntn", 'w').write(str(N[0])); sys.exit(rc)
