"""Reproducible smoke, configuration and frame checks on the fictional demo only."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

# Windows native ADB may miss a present Pixel interface; restarting with ADB_LIBUSB=1
# selects Android's alternate USB transport without changing the phone or installed driver.
# https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md
PACKAGE = 'jm.yardmoney.demo'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--configurations', action='store_true')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*command):
        return subprocess.check_output([args.adb, *command], text=True, encoding='utf-8').strip()

    def launch():
        output = adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/jm.yardmoney.MainActivity')
        # Display overrides can redisplay the keyguard on an already trusted/unlocked test phone.
        # Android dismisses it only when permitted; the foreground guard still fails on a secure lock.
        adb('shell', 'wm', 'dismiss-keyguard')
        return output

    def tree():
        adb('shell', 'uiautomator', 'dump', '/data/local/tmp/yardmoney-quality.xml')
        doc = ET.fromstring(adb('shell', 'cat', '/data/local/tmp/yardmoney-quality.xml'))
        if not any(n.get('package') == PACKAGE for n in doc.iter('node')):
            adb('shell', 'wm', 'dismiss-keyguard')
            time.sleep(.5)
            adb('shell', 'uiautomator', 'dump', '/data/local/tmp/yardmoney-quality.xml')
            doc = ET.fromstring(adb('shell', 'cat', '/data/local/tmp/yardmoney-quality.xml'))
        if not any(n.get('package') == PACKAGE for n in doc.iter('node')):
            raise RuntimeError('The fictional demo must be foreground; personal data is never inspected.')
        return doc

    def tap(label):
        doc = tree()
        nodes = [n for n in doc.iter('node') if n.get('content-desc') == label]
        if not nodes:
            nodes = [n for n in doc.iter('node') if n.get('text') == label]
        if not nodes:
            raise AssertionError(f'Unreachable control: {label}')
        parents = {child: parent for parent in doc.iter() for child in parent}
        node = nodes[-1]
        original = node
        while node.get('clickable') != 'true' and node in parents:
            node = parents[node]
        # Compose may expose a labelled child and selected parent without a native clickable flag.
        if not node.get('bounds'):
            node = original
        box = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        adb('shell', 'input', 'tap', str((box[0] + box[2]) // 2), str((box[1] + box[3]) // 2))

    def menus(name):
        launch()
        for menu in ('Home', 'Activity', 'Plan', 'Shop', 'More'):
            tap(menu)
            time.sleep(.35)
            doc = tree()
            parents = {child: parent for parent in doc.iter() for child in parent}
            def selected(node):
                while node is not None:
                    if node.get('selected') == 'true':
                        return True
                    node = parents.get(node)
                return False
            if not any((n.get('content-desc') == menu or n.get('text') == menu) and selected(n) for n in doc.iter('node')):
                raise AssertionError(f'Menu did not settle: {name}/{menu}')
        adb('shell', 'screencap', '-p', '/data/local/tmp/yardmoney-quality.png')
        adb('pull', '/data/local/tmp/yardmoney-quality.png', str(args.output / f'{name}.png'))
        print(f'PASS {name}: all main destinations', flush=True)

    result = {'package': PACKAGE, 'configurations': [], 'cold_start_ms': []}
    # Official am/gfxinfo measurements are smoke evidence, not a Macrobenchmark distribution.
    # https://developer.android.com/topic/performance/vitals/render
    for _ in range(3):
        output = adb('shell', 'am', 'start', '-S', '-W', '-n', f'{PACKAGE}/jm.yardmoney.MainActivity')
        match = re.search(r'TotalTime: (\d+)', output)
        if not match:
            raise RuntimeError('Startup timing unavailable')
        result['cold_start_ms'].append(int(match[1]))
    adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'reset')
    menus('baseline')
    for _ in range(2):
        for menu in ('Home', 'Shop', 'Activity', 'Plan', 'More'):
            tap(menu)
    frames = adb('shell', 'dumpsys', 'gfxinfo', PACKAGE)
    result['frames'] = [line.strip() for line in frames.splitlines() if re.search(r'Total frames|Janky frames|percentile|Missed Vsync|Slow UI thread|Slow bitmap uploads|Slow issue draw|Frame deadline missed', line)]
    memory = adb('shell', 'dumpsys', 'meminfo', PACKAGE)
    result['memory'] = [line.strip() for line in memory.splitlines() if re.search(r'TOTAL PSS:|TOTAL RSS:|TOTAL SWAP PSS:|Views:|Activities:', line)]

    if args.configurations:
        original_size = adb('shell', 'wm', 'size')
        original_density = adb('shell', 'wm', 'density')
        keys = [('system', 'font_scale'), ('system', 'accelerometer_rotation'), ('system', 'user_rotation'), ('global', 'animator_duration_scale')]
        original = {(space, key): adb('shell', 'settings', 'get', space, key) for space, key in keys}

        def setting(space, key, value):
            adb('shell', 'settings', 'put', space, key, value)

        try:
            setting('system', 'accelerometer_rotation', '0')
            for name, size, density, font, rotation, motion in (
                ('small-large-font', '960x1920', '480', '2.0', '0', '1.0'),
                ('tablet-window', '1800x2400', '240', '1.0', '0', '1.0'),
                ('landscape-reduced-motion', '1344x2992', '480', '1.3', '1', '0.0'),
            ):
                print(f'Checking {name}', flush=True)
                adb('shell', 'wm', 'size', size)
                adb('shell', 'wm', 'density', density)
                setting('system', 'font_scale', font)
                setting('system', 'user_rotation', rotation)
                setting('global', 'animator_duration_scale', motion)
                time.sleep(1)
                menus(name)
                result['configurations'].append(name)
        finally:
            # Restore overrides and accessibility settings even when a test raises.
            for kind, output in [('size', original_size), ('density', original_density)]:
                override = re.search(r'Override (?:size|density): (\S+)', output)
                adb('shell', 'wm', kind, override[1] if override else 'reset')
            for (space, key), value in original.items():
                if value == 'null':
                    adb('shell', 'settings', 'delete', space, key)
                else:
                    setting(space, key, value)
            launch()
    (args.output / 'device-quality.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
