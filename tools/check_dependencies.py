"""Check resolved runtime dependencies without uploading source or private app data."""
import argparse
import json
from pathlib import Path
import sys
import urllib.request

# OSV's maintained Maven advisory index complements Android lint; no dependency changes are automatic.
# https://google.github.io/osv.dev/api/#querybatch

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    packages = set()
    for lock in (root / "app/gradle.lockfile", root / "core/gradle.lockfile"):
        for line in lock.read_text().splitlines():
            if line.startswith("#") or "=" not in line:
                continue
            coordinate, scopes = line.split("=", 1)
            if "releaseRuntimeClasspath" not in scopes and "runtimeClasspath" not in scopes:
                continue
            parts = coordinate.split(":")
            if len(parts) == 3:
                packages.add(tuple(parts))
    findings = []
    ordered = sorted(packages)
    for offset in range(0, len(ordered), 50):
        batch = ordered[offset:offset + 50]
        payload = {"queries": [{"package": {"ecosystem": "Maven", "name": f"{g}:{a}"}, "version": v} for g, a, v in batch]}
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=45) as response:
            results = json.load(response)["results"]
        if len(results) != len(batch):
            raise RuntimeError("Incomplete advisory response; verification failed.")
        for coordinate, result in zip(batch, results):
            for vulnerability in result.get("vulns", []):
                findings.append({"dependency": ":".join(coordinate), "id": vulnerability["id"]})
    result = {"checked_packages": len(ordered), "findings": findings}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))
    return 1 if findings else 0

if __name__ == "__main__":
    sys.exit(main())
