#!/usr/bin/env bash
# Maintenance helper for this fork: merge upstream, build via GitHub Actions, publish and install the APK.
#
#   scripts/immichtv.sh status            versions + upstream commits not merged yet
#   scripts/immichtv.sh sync              fetch upstream and merge it into branch "upstream-sync"
#   scripts/immichtv.sh bump              version +1 (versionName last number, versionCode)
#   scripts/immichtv.sh release           merge into main, push, wait for CI, publish the APK
#   scripts/immichtv.sh install [ip...]   adb install -r the newest published APK on the sticks
#   scripts/immichtv.sh all               sync -> release -> install (stops on merge conflicts / CI failure)
#
# Needs: git with a stored GitHub login (credential.helper=store), curl, unzip, adb.
set -euo pipefail

REPO="ndlong75/immich-tv"
UPSTREAM_URL="https://github.com/giejay/Immich-Android-TV.git"
UPSTREAM_BRANCH="main"
APK_DIR="/var/www/apk"
APK_URL="http://192.168.10.2:8088"
KEEP_APKS=2
SYNC_BRANCH="upstream-sync"
DEFAULT_STICKS=("192.168.10.81:5555" "100.72.39.64:5555")   # LAN stick, Tailscale stick

cd "$(dirname "$0")/.."
GRADLE="app/build.gradle"

die()  { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

token() {
  printf 'protocol=https\nhost=github.com\n\n' | GIT_TERMINAL_PROMPT=0 git credential fill 2>/dev/null | sed -n 's/^password=//p'
}

gh_api() { curl -fsS -H "Authorization: Bearer $(token)" -H "Accept: application/vnd.github+json" "$@"; }

rev_version_name() { git show "$1:$GRADLE" | sed -n 's/^[[:space:]]*versionName "\(.*\)"/\1/p' | head -1; }
rev_version_code() { git show "$1:$GRADLE" | sed -n 's/^[[:space:]]*versionCode \([0-9]*\)/\1/p' | head -1; }
version_name() { sed -n 's/^[[:space:]]*versionName "\(.*\)"/\1/p' "$GRADLE" | head -1; }
version_code() { sed -n 's/^[[:space:]]*versionCode \([0-9]*\)/\1/p' "$GRADLE" | head -1; }

# Upstream commits whose change is not already in HEAD. Compared by patch, not by hash,
# because the fork's history was rewritten once and upstream commit hashes no longer match.
pending_upstream() { git log --oneline --no-merges --right-only --cherry-pick "HEAD...upstream/$UPSTREAM_BRANCH"; }

ensure_upstream() {
  git remote get-url upstream >/dev/null 2>&1 || git remote add upstream "$UPSTREAM_URL"
  git fetch -q upstream --tags
}

require_clean() {
  [ -z "$(git status --porcelain)" ] || die "working tree has uncommitted changes; commit or stash them first"
}

cmd_status() {
  ensure_upstream
  info "this fork: $(version_name) (code $(version_code)) on branch $(git rev-parse --abbrev-ref HEAD)"
  local up_name up_code
  up_name=$(git show "upstream/$UPSTREAM_BRANCH:$GRADLE" | sed -n 's/^[[:space:]]*versionName "\(.*\)"/\1/p' | head -1)
  up_code=$(git show "upstream/$UPSTREAM_BRANCH:$GRADLE" | sed -n 's/^[[:space:]]*versionCode \([0-9]*\)/\1/p' | head -1)
  info "upstream:  $up_name (code $up_code)"
  local pending
  pending=$(pending_upstream | wc -l)
  info "upstream commits not merged yet: $pending"
  [ "$pending" -eq 0 ] || pending_upstream | head -15
  info "published APKs in $APK_DIR:"; ls -1 "$APK_DIR"/ImmichTV-*.apk 2>/dev/null || echo "  (none)"
}

set_version() {   # set_version <name> <code>
  sed -i "0,/versionName \".*\"/s//versionName \"$1\"/" "$GRADLE"
  sed -i "0,/versionCode [0-9]*/s//versionCode $2/" "$GRADLE"
}

cmd_bump() {
  local name code last
  name=$(version_name); code=$(version_code)
  last=${name##*.}
  set_version "${name%.*}.$((last + 1))" "$((code + 1))"
  info "version: $name -> $(version_name) (code $(version_code))"
}

cmd_sync() {
  require_clean
  ensure_upstream
  git checkout -q main
  if [ "$(pending_upstream | wc -l)" -eq 0 ]; then
    info "already up to date with upstream"; UP_TO_DATE=1; return 0
  fi
  # Upstream version when it was last merged (before this sync), to tell if upstream bumped its version.
  local old_up_name; old_up_name=$(rev_version_name "$(git merge-base main "upstream/$UPSTREAM_BRANCH")")
  git checkout -q -B "$SYNC_BRANCH" main
  info "merging upstream/$UPSTREAM_BRANCH into $SYNC_BRANCH"
  if ! git merge --no-edit "upstream/$UPSTREAM_BRANCH"; then
    echo
    echo "MERGE CONFLICTS - resolve these files (keep this fork's changes, take upstream elsewhere):"
    git diff --name-only --diff-filter=U | sed 's/^/  /'
    echo "then: git add <files> && git commit, and run: $0 release"
    exit 2
  fi
  fix_version "$old_up_name"
  info "merged cleanly; version $(version_name) (code $(version_code))"
}

# versionCode must always go up (Android refuses downgrades): above upstream's and above the last pushed main.
# versionName: upstream bumped its version -> "<upstream>.1"; otherwise just +1 on our last number.
fix_version() {
  local old_up_name="${1:-}" up_name up_code main_code cur_code need
  up_name=$(rev_version_name "upstream/$UPSTREAM_BRANCH"); up_code=$(rev_version_code "upstream/$UPSTREAM_BRANCH")
  git fetch -q origin main
  main_code=$(rev_version_code origin/main); cur_code=$(version_code)   # last version pushed
  need=$(( (up_code > main_code ? up_code : main_code) + 1 ))
  [ "$cur_code" -ge "$need" ] && [ -z "$old_up_name" ] && return 0
  local name
  if [ -n "$old_up_name" ] && [ "$up_name" != "$old_up_name" ]; then name="${up_name}.1"
  else name=$(version_name); name="${name%.*}.$(( ${name##*.} + 1 ))"; fi
  [ "$cur_code" -ge "$need" ] || cur_code=$need
  set_version "$name" "$cur_code"
  git commit -qam "Bump version to $name after upstream merge" || true
}

cmd_release() {
  require_clean
  local branch; branch=$(git rev-parse --abbrev-ref HEAD)
  if [ "$branch" != "main" ]; then
    info "merging $branch into main"
    git checkout -q main
    git merge --no-edit "$branch"
  fi
  fix_version ""
  info "pushing main (no force)"
  git push origin main
  local sha; sha=$(git rev-parse HEAD)

  info "waiting for the CI run of ${sha:0:7}"
  local run_id=""
  for _ in $(seq 1 18); do
    run_id=$(gh_api "https://api.github.com/repos/$REPO/actions/runs?head_sha=$sha" | grep -m1 '"id"' | tr -dc 0-9 || true)
    [ -n "$run_id" ] && break
    sleep 10
  done
  [ -n "$run_id" ] || die "no CI run found for $sha"
  echo "    https://github.com/$REPO/actions/runs/$run_id"

  local run status conclusion
  while true; do
    run=$(gh_api "https://api.github.com/repos/$REPO/actions/runs/$run_id")
    status=$(echo "$run" | grep -m1 '"status"' | cut -d'"' -f4)
    [ "$status" = "completed" ] && break
    sleep 30
  done
  conclusion=$(echo "$run" | grep -m1 '"conclusion"' | cut -d'"' -f4)
  [ "$conclusion" = "success" ] || die "CI finished with: $conclusion (see the run page above)"

  info "downloading the APK"
  local artifact tmp; tmp=$(mktemp -d)
  artifact=$(gh_api "https://api.github.com/repos/$REPO/actions/runs/$run_id/artifacts" | grep -m1 '"id"' | tr -dc 0-9)
  gh_api -L "https://api.github.com/repos/$REPO/actions/artifacts/$artifact/zip" -o "$tmp/a.zip"
  mkdir -p "$APK_DIR"
  unzip -oq "$tmp/a.zip" -d "$APK_DIR"
  rm -rf "$tmp"

  # keep only the newest $KEEP_APKS APKs
  ls -1 "$APK_DIR"/ImmichTV-*.apk | sort -V | head -n -"$KEEP_APKS" | xargs -r rm -f --
  info "published: $APK_URL/$(basename "$(latest_apk)")"
}

latest_apk() { ls -1 "$APK_DIR"/ImmichTV-*.apk | sort -V | tail -1; }

cmd_install() {
  local apk sticks=("$@")
  apk=$(latest_apk); [ -n "$apk" ] || die "no APK in $APK_DIR"
  [ ${#sticks[@]} -gt 0 ] || sticks=("${DEFAULT_STICKS[@]}")
  for s in "${sticks[@]}"; do
    case "$s" in *:*) ;; *) s="$s:5555" ;; esac
    info "installing $(basename "$apk") on $s"
    if ! timeout 15 adb connect "$s" 2>&1 | grep -q "connected"; then
      echo "    skipped: $s is not reachable (asleep or offline)"; continue
    fi
    adb -s "$s" install -r "$apk" 2>&1 | tail -1
  done
}

case "${1:-help}" in
  status)  cmd_status ;;
  sync)    cmd_sync ;;
  bump)    cmd_bump ;;
  release) cmd_release ;;
  install) shift; cmd_install "$@" ;;
  all)     UP_TO_DATE=0; cmd_sync
           if [ "$UP_TO_DATE" = 1 ]; then info "nothing to do"; else cmd_release; cmd_install; fi ;;
  *)       sed -n '2,12p' "$0" ;;
esac
