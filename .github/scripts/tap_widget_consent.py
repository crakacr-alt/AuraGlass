#!/usr/bin/env python3
"""Accept the Android AppWidget bind dialog on an ephemeral QA emulator.

No privileged shell appwidget grants. This is a UI test of a consent dialog,
and must never be used to dismiss unrelated dialogs.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: tap_widget_consent.py <uiautomator-dump.xml>")
root = ET.parse(sys.argv[1]).getroot()
nodes = list(root.iter("node"))
# Never click generic 'Allow' buttons on unrelated screens. The script runs
# only on a disposable emulator but must confirm the target dialog explicitly.
visible_text=" ".join(n.attrib.get("text","") for n in nodes).lower()
if not ("create widget" in visible_text and "aura glass" in visible_text):
    raise SystemExit("Not the expected AURA GLASS AppWidget consent dialog")
positive = {"Create", "Create widget", "Add", "Allow", "OK", "Continue",
            "Создать", "Добавить", "Разрешить", "ОК", "Продолжить"}
candidates = []
for node in nodes:
    text = node.attrib.get("text", "").strip()
    description = node.attrib.get("content-desc", "").strip()
    rid = node.attrib.get("resource-id", "")
    if rid.endswith(":id/button1") or rid.endswith(":id/positive_button"):
        candidates.append((0, node))
    elif text in positive or description in positive:
        candidates.append((1, node))
if not candidates:
    visible = [(n.attrib.get("text",""),n.attrib.get("resource-id",""))
               for n in nodes if n.attrib.get("clickable") == "true"]
    raise SystemExit(f"No AppWidget positive button found. Clickable: {visible!r}")
candidate = sorted(candidates, key=lambda x:x[0])[0][1]
bounds = candidate.attrib.get("bounds", "")
match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
if not match:
    raise SystemExit("AppWidget confirmation has no bounds")
x1,y1,x2,y2=map(int,match.groups())
if x1>=x2 or y1>=y2:
    raise SystemExit("AppWidget confirmation has empty bounds")
x,y=(x1+x2)//2,(y1+y2)//2
print(f"Confirming AppWidget bind through visible Android UI: ({x}, {y})")
subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True)
