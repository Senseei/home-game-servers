#!/usr/bin/env python3
"""Fetch Steam Workshop titles for mod IDs. Usage: ark-mods-fetch.py <id>...
Prints one `id<TAB>title` line per resolved mod (for appending to mods.tsv)."""
import sys, urllib.request, urllib.parse, json

ids = [a for a in sys.argv[1:] if a.isdigit()]
if not ids:
    sys.exit(0)

data = [("itemcount", str(len(ids)))] + [(f"publishedfileids[{i}]", m) for i, m in enumerate(ids)]
req = urllib.request.Request(
    "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/",
    data=urllib.parse.urlencode(data).encode(),
)
try:
    resp = json.load(urllib.request.urlopen(req, timeout=20))
except Exception as e:
    print(f"# Steam fetch failed: {e}", file=sys.stderr)
    sys.exit(1)

for d in resp.get("response", {}).get("publishedfiledetails", []):
    title = (d.get("title") or "").strip() or "(unknown — check the ID)"
    print(d["publishedfileid"] + "\t" + title)
