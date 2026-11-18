#!/bin/bash
# Publishes the current version as a GitHub release, so every installed Kadamm (9.2+) offers the update.
# The release carries Kadamm-<version>.apk (what the app looks for) and a description with screenshots and this
# version's CHANGELOG section.
#
#   ./release.sh          publish the current version
#   ./release.sh notes    rewrite the current version's release description (screenshots + changelog)
#
# Uses the GitHub CLI (gh) if installed and logged in; otherwise a token from the Keychain (asked for once, hidden
# typing) or GITHUB_TOKEN from the environment: a fine-grained token with "Contents: read and write" on vrajchauhan10/Kadamm.
# The token is never stored.
set -euo pipefail
cd "$(dirname "$0")"
REPO="vrajchauhan10/Kadamm"
MODE="${1:-publish}"
VERSION=$(grep '^versionName=' version.properties | cut -d= -f2)
CHANGES=$(awk -v v="## $VERSION " 'index($0,v)==1{on=1;next} /^## /{if(on)exit} on' CHANGELOG.md)
IMG="https://raw.githubusercontent.com/$REPO/main/docs/screenshots"
NOTES=$(cat <<EOF
<p>
  <img src="$IMG/daily-goals.png" width="32%" />
  <img src="$IMG/trainer.png" width="32%" />
  <img src="$IMG/effort-zones.png" width="32%" />
</p>

**Kadamm $VERSION**: your walks, analysed like a premium tracker, from your phone alone.

### What's new in $VERSION
$CHANGES

### Highlights
- **Daily goals capsule:** steps, brisk minutes and distance, layered into one day's progress
- **Session analysis:** relative effort, intensity and pace zones, splits, cadence, stride, best efforts
- **Kadamm Trainer:** your stage, today's workout and a 4-week forecast
- **Smart sessions:** start after 100 steps, rides left out, treadmill walking detected
- **Sitting breaks, steps through the day, 45 badges, reports**

<p>
  <img src="$IMG/steps-through-the-day.png" width="32%" />
  <img src="$IMG/splits-cadence.png" width="32%" />
  <img src="$IMG/achievements.png" width="32%" />
</p>

### Install
Download **Kadamm-$VERSION.apk** below and open it. Already on 9.2 or later? Kadamm offers this update by itself; your sessions and settings stay.
EOF
)

api(){ curl -fsS -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$@"; }
# The token lives in the macOS Keychain ("kadamm-release"): asked for once, then read from there every time.
need_token(){
  if [ -z "${GITHUB_TOKEN:-}" ]; then GITHUB_TOKEN=$(security find-generic-password -s kadamm-release -a github -w 2>/dev/null || true); fi
  if [ -z "${GITHUB_TOKEN:-}" ]; then
    echo "Paste a GitHub token with Contents: read and write on $REPO (typing is hidden), then press Return."
    echo "It's saved in your Mac's Keychain, so you won't be asked again:"
    read -rs GITHUB_TOKEN; echo
    [ -n "$GITHUB_TOKEN" ] || { echo "No token given."; exit 1; }
    security add-generic-password -U -s kadamm-release -a github -w "$GITHUB_TOKEN" && echo "Saved to Keychain."
  fi
}

if [ "$MODE" = "notes" ]; then
  if command -v gh >/dev/null 2>&1; then gh release edit "v$VERSION" --repo "$REPO" --title "Kadamm $VERSION" --notes "$NOTES"
  else
    need_token
    ID=$(api "https://api.github.com/repos/$REPO/releases/tags/v$VERSION" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
    BODY=$(python3 -c 'import json,sys; print(json.dumps({"name":"Kadamm "+sys.argv[1],"body":sys.argv[2]}))' "$VERSION" "$NOTES")
    api -X PATCH "https://api.github.com/repos/$REPO/releases/$ID" -d "$BODY" >/dev/null
  fi
  echo "Updated the description: https://github.com/$REPO/releases/tag/v$VERSION"; exit 0
fi

SRC="releases/kadamm-v$VERSION.apk"
[ -f "$SRC" ] || { echo "No $SRC: run ./build.sh first."; exit 1; }
OUT="$(mktemp -d)/Kadamm-$VERSION.apk"; cp "$SRC" "$OUT"
if command -v gh >/dev/null 2>&1; then
  gh release create "v$VERSION" "$OUT" --repo "$REPO" --title "Kadamm $VERSION" --notes "$NOTES"
else
  need_token
  BODY=$(python3 -c 'import json,sys; print(json.dumps({"tag_name":"v"+sys.argv[1],"name":"Kadamm "+sys.argv[1],"body":sys.argv[2],"target_commitish":"main"}))' "$VERSION" "$NOTES")
  ID=$(api -X POST "https://api.github.com/repos/$REPO/releases" -d "$BODY" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  curl -fsS -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$OUT" "https://uploads.github.com/repos/$REPO/releases/$ID/assets?name=Kadamm-$VERSION.apk" >/dev/null
fi
echo "Published Kadamm $VERSION: https://github.com/$REPO/releases/tag/v$VERSION"
