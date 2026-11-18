#!/usr/bin/env bash
# Run a built buzz-parse binary through each mode, so a static link that
# didn't take or a library the archive is missing fails here and not on a
# user's machine.  Usage: ci/smoke.sh ./buzz-parse
set -u
bin="$1"
out=$(mktemp -d)
fail() { echo "smoke: $*"; exit 1; }
page='<!DOCTYPE html><ul id=nav><li class=a>One &amp; only</li><li>Two <b>2</b></li></ul>'

"$bin" --help | grep -q "Usage: buzz-parse" || fail "--help printed no usage"

printf '%s' "$page" > "$out/page.html"
"$bin" "$out/page.html" | head -c 12 | grep -q '{:tag :html' || fail "no EDN tree from a file"
echo "smoke: parse a file"

printf '%s' "$page" | "$bin" --format json | grep -q '"tag":"html"' || fail "no JSON tree from stdin"
echo "smoke: parse stdin as JSON"

[ "$(printf '%s' "$page" | "$bin" -s 'li:has(b)' --as html)" = '<li>Two <b>2</b></li>' ] \
  || fail "selector match isn't the expected HTML"
[ "$(printf '%s' "$page" | "$bin" -s '#nav .a' --as text)" = 'One & only' ] \
  || fail "selector match isn't the expected text"
echo "smoke: CSS selectors"

printf '%s' 'a <i>b</i>' | "$bin" --fragment | grep -q '\["a " {:tag :i' || fail "no fragment"
echo "smoke: fragment"

printf '%s' "$page" | "$bin" -s 'li[' > /dev/null 2>&1
[ $? -eq 2 ] || fail "a bad selector didn't exit 2"
"$bin" "$out/no-such-file" > /dev/null 2>&1
[ $? -eq 2 ] || fail "a missing file didn't exit 2"
echo "smoke: bad usage exits 2"
