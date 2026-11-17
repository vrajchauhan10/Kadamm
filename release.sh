#!/bin/bash
# Publishes the current version as a GitHub release, so every installed Kadamm (9.2+) offers the update.
# The release carries Kadamm-<version>.apk (what the app looks for) and this version's CHANGELOG section.
#
#   ./release.sh            uses the GitHub CLI (gh) if installed and logged in,
#                           otherwise a token in GITHUB_TOKEN (a fine-grained token with "Contents: read and write"
#                           on vrajchauhan10/Kadamm). The token is read from your environment and never stored.
set -euo pipefail
cd "$(dirname "$0")"
REPO="vrajchauhan10/Kadamm"
VERSION=$(grep '^versionName=' version.properties | cut -d= -f2)
SRC="releases/kadamm-v$VERSION.apk"
[ -f "$SRC" ] || { echo "No $SRC: run ./build.sh first."; exit 1; }
OUT="$(mktemp -d)/Kadamm-$VERSION.apk"; cp "$SRC" "$OUT"
NOTES=$(awk -v v="## $VERSION " 'index($0,v)==1{on=1;next} /^## /{if(on)exit} on' CHANGELOG.md)
[ -n "$NOTES" ] || NOTES="Kadamm $VERSION"

if command -v gh >/dev/null 2>&1; then
  gh release create "v$VERSION" "$OUT" --repo "$REPO" --title "Kadamm $VERSION" --notes "$NOTES"
else
  : "${GITHUB_TOKEN:?Set GITHUB_TOKEN (or install gh) first}"
  BODY=$(python3 -c 'import json,sys; print(json.dumps({"tag_name":"v"+sys.argv[1],"name":"Kadamm "+sys.argv[1],"body":sys.argv[2],"target_commitish":"main"}))' "$VERSION" "$NOTES")
  RESP=$(curl -fsS -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO/releases" -d "$BODY")
  ID=$(printf '%s' "$RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  curl -fsS -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$OUT" "https://uploads.github.com/repos/$REPO/releases/$ID/assets?name=Kadamm-$VERSION.apk" >/dev/null
fi
echo "Published Kadamm $VERSION: https://github.com/$REPO/releases/tag/v$VERSION"
