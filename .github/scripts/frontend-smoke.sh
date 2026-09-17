#!/usr/bin/env bash
set -euo pipefail

# Смок прод-фронта после заливки: #root, версия и сборка из meta, content-type бандла,
# canonical, JSON-LD на /pricing, честный 404, sitemap.xml равен залитому и каждый его
# URL отвечает 200 с canonical.
# Вход: $1 ожидаемая app-version, $2 ожидаемый app-build;
# опционально BASE (default https://workbit.ru), DIST (default dist).

[ $# -eq 2 ] || { echo "usage: frontend-smoke.sh <expected-version> <expected-build>" >&2; exit 1; }
expected_version="$1"
expected_build="$2"
BASE="${BASE:-https://workbit.ru}"
DIST="${DIST:-dist}"
curl() { command curl --max-time 30 --retry 3 --retry-all-errors --retry-delay 2 "$@"; }

html=$(curl -sf "$BASE/") || { echo "index.html is not served"; exit 1; }
[[ $html == *'id="root"'* ]] || { echo "served HTML has no app root"; exit 1; }
served=$(sed -n 's|.*<meta name="app-version" content="\([^"]*\)".*|\1|p' <<<"$html")
[ "$served" = "$expected_version" ] \
  || { echo "frontend serves version '$served', expected '$expected_version'"; exit 1; }
build=$(sed -n 's|.*<meta name="app-build" content="\([^"]*\)".*|\1|p' <<<"$html")
[ "$build" = "$expected_build" ] \
  || { echo "frontend serves build '$build', expected '$expected_build'"; exit 1; }
echo "frontend version $served build $build"
bundle_re='/assets/[^"]*\.js'
[[ $html =~ $bundle_re ]] || { echo "no js bundle referenced in index.html"; exit 1; }
asset=${BASH_REMATCH[0]}
ctype=$(curl -s -o /dev/null -w '%{content_type}' "$BASE$asset")
case "$ctype" in
  *javascript*) echo "frontend is up ($asset)" ;;
  *) echo "bundle $asset served as '$ctype' instead of javascript"; exit 1 ;;
esac
[[ $html == *'rel="canonical"'* ]] || { echo "no canonical on /"; exit 1; }
pricing=$(curl -s "$BASE/pricing")
[[ $pricing == *'application/ld+json'* ]] || { echo "no json-ld on /pricing"; exit 1; }
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/definitely-missing")
[ "$code" = "404" ] || { echo "soft 404: /definitely-missing answered $code"; exit 1; }
curl -sf "$BASE/sitemap.xml" | cmp -s - "$DIST/sitemap.xml" \
  || { echo "sitemap.xml on prod differs from $DIST/sitemap.xml"; exit 1; }
for loc in $(sed -n 's|.*<loc>\(.*\)</loc>.*|\1|p' "$DIST/sitemap.xml"); do
  page=$(curl -sf "$loc") && [[ $page == *'rel="canonical"'* ]] \
    || { echo "$loc is not served or has no canonical"; exit 1; }
done
echo "seo checks passed"
