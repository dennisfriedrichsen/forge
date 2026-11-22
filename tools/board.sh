#!/usr/bin/env bash
# Compile the desktop client and launch it straight into the board described by a .pzl file.
#
#   tools/board.sh testboards/savannah-birds.pzl
#   SKIP_BUILD=1 tools/board.sh testboards/savannah-birds.pzl   # reuse the last compile
#
# Runs from compiled classes rather than the packaged jar, since packaging needs launch4j.
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "usage: $0 <file.pzl>" >&2
    exit 1
fi

repo="$(cd "$(dirname "$0")/.." && pwd)"
puzzle="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
if [[ ! -f "$puzzle" ]]; then
    echo "no such puzzle file: $1" >&2
    exit 1
fi

desktop="$repo/forge-gui-desktop"
cp_file="$desktop/target/board-classpath.txt"

if [[ -z "${SKIP_BUILD:-}" || ! -f "$cp_file" ]]; then
    # Must run from the repo root: building from forge-gui-desktop alone trips on .mvn/local-settings.xml.
    (cd "$repo" && mvn -q -pl forge-gui-desktop -am compile dependency:build-classpath \
        -Dmdep.outputFile="$cp_file")
fi

# Same --add-opens flags the packaged launcher uses; without them XStream crashes at startup.
addopens="$(sed -n 's:.*<addopen.java.args>\(.*\)</addopen.java.args>.*:\1:p' "$desktop/pom.xml")"

cd "$desktop"
# shellcheck disable=SC2086 # addopens is a list of flags
exec java -Xmx4096m -Dio.netty.tryReflectionSetAccessible=true -Dfile.encoding=UTF-8 $addopens \
    -cp "target/classes:$(cat "$cp_file")" forge.view.Main puzzle "$puzzle"
