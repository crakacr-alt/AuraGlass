#!/usr/bin/env python3
"""Tap a visible Android accessibility node by its exact label, not guessed pixels.

Usage: tap_accessibility.py <uiautomator-xml> <label>
The script only talks to the emulator through ADB, not to user devices.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 3:
    raise SystemExit("Expected XML file and accessibility label")
doc = ET.parse(sys.argv[1]).getroot()
label = sys.argv[2]
matches = [
    node for node in doc.iter("node")
    if node.attrib.get("text") == label or node.attrib.get("content-desc") == label
]
if not matches:
    raise SystemExit(f"Accessibility node not found: {label!r}")
node = next((n for n in matches if n.attrib.get("clickable") == "true"), matches[0])
match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
if not match:
    raise SystemExit(f"Invalid bounds for {label!r}")
x1,y1,x2,y2 = map(int,match.groups())
if x2<=x1 or y2<=y1:
    raise SystemExit("Empty accessibility bounds")
x,y=(x1+x2)//2,(y1+y2)//2
print(f"Tapping {label!r} at ({x},{y})")
subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True)
