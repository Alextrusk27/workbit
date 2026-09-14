#!/usr/bin/env bash
# Reads and writes the project version, kept identical in pom.xml and frontend/package.json.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

usage() { echo "usage: version.sh get | set <X.Y.Z-SNAPSHOT> | check" >&2; exit 1; }

pom_version() { sed -n '/<\/parent>/,$ s|.*<version>\(.*\)</version>.*|\1|p' pom.xml | head -1; }
pkg_version() { sed -n 's|^  "version": "\([^"]*\)".*|\1|p' frontend/package.json | head -1; }

project_version() {
  local pom pkg
  pom=$(pom_version)
  pkg=$(pkg_version)
  [ -n "$pom" ] || { echo "no project version in pom.xml" >&2; exit 1; }
  [ "$pom" = "$pkg" ] || { echo "pom.xml says '$pom', frontend/package.json says '$pkg'" >&2; exit 1; }
  echo "$pom"
}

case "${1:-}" in
  get)
    [ $# -eq 1 ] || usage
    project_version
    ;;
  set)
    [ $# -eq 2 ] || usage
    version="$2"
    [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] || { echo "version must look like 1.2.3-SNAPSHOT" >&2; exit 1; }

    awk -v v="$version" '
      !done && after && /<version>[^<]*<\/version>/ {
        sub(/<version>[^<]*<\/version>/, "<version>" v "</version>"); done = 1
      }
      /<\/parent>/ { after = 1 }
      { print }
      END { if (!done) exit 1 }
    ' pom.xml > pom.xml.tmp || { rm -f pom.xml.tmp; echo "project version not found in pom.xml" >&2; exit 1; }
    mv pom.xml.tmp pom.xml

    sed 's|^  "version": "[^"]*"|  "version": "'"$version"'"|' frontend/package.json > frontend/package.json.tmp
    mv frontend/package.json.tmp frontend/package.json

    [ "$(pom_version)" = "$version" ] && [ "$(pkg_version)" = "$version" ] || { echo "version was not applied" >&2; exit 1; }
    ;;
  check)
    [ $# -eq 1 ] || usage
    version=$(project_version)
    case "$version" in
      *-SNAPSHOT) ;;
      *) echo "version '$version' is not a snapshot" >&2; exit 1 ;;
    esac
    release="${version%-SNAPSHOT}"
    latest=$(git ls-remote --tags --refs origin 'v*.*.*' | sed 's|.*refs/tags/v||' | sort -V | tail -1)
    highest=$( { echo "$latest"; echo "$release"; } | sort -V | tail -1)
    if [ -n "$latest" ] && [ "$highest" != "$release" ]; then
      echo "version $release is below the latest release $latest" >&2
      exit 1
    fi
    echo "$release"
    ;;
  *) usage ;;
esac
