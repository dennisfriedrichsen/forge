#!/usr/bin/env bash
# Build and publish a personal release of this fork from forge-dennis, on this Mac.
#
#   tools/release.sh                   # next version, e.g. v2.0.17-dennis.3; builds the .dmg, creates a draft release
#   tools/release.sh --publish         # same, but publishes right away
#   tools/release.sh --dry-run         # only print the tag and release notes; no build, nothing on GitHub
#   tools/release.sh 2.0.18-dennis.1   # pick the version yourself
#
# Builds Forge.app (Java bundled) for this Mac's architecture from origin/forge-dennis in a temporary
# worktree, with the tag as the app version (shown in Help > About Forge). The .dmg is attached to
# the release and copied to ~/Downloads. Needs macOS, a JDK 17+ and gh. See tools/RELEASING.md.
set -euo pipefail

repo_slug="dennisfriedrichsen/forge"
branch="forge-dennis"
publish=0
dry_run=0
version=""

for arg in "$@"; do
    case "$arg" in
        --publish) publish=1 ;;
        --dry-run) dry_run=1 ;;
        -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*) echo "unknown option: $arg" >&2; exit 1 ;;
        *) version="${arg#v}" ;;
    esac
done

repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo"
git fetch -q origin "$branch" --tags
git fetch -q upstream master 2>/dev/null || echo "note: no 'upstream' remote; skipping the upstream check" >&2
sha=$(git rev-parse "origin/$branch")

# Version: <pom versionCode>-dennis.<n>, n one past the newest release tag for that versionCode
version_code=$(git show "$sha:pom.xml" | sed -n 's:.*<versionCode>\(.*\)</versionCode>.*:\1:p' | head -1)
if [[ -z "$version" ]]; then
    last_n=$(git tag -l "v${version_code}-dennis.*" | sed "s/^v${version_code}-dennis\.//" | grep -E '^[0-9]+$' | sort -n | tail -1)
    version="${version_code}-dennis.$(( ${last_n:-0} + 1 ))"
fi
tag="v$version"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "tag $tag already exists" >&2
    exit 1
fi

case "$(uname -m)" in
    arm64) arch=arm64; arch_desc="Apple Silicon Macs" ;;
    x86_64) arch=x64; arch_desc="Intel Macs" ;;
    *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac
dmg_name="Forge-${version}-macos-${arch}.dmg"

# Warn when forge-dennis is missing upstream commits (sync first if you want them in the release)
if git rev-parse -q --verify upstream/master >/dev/null; then
    behind=$(git rev-list --count "$sha..upstream/master")
    if [[ "$behind" -gt 0 ]]; then
        echo "warning: $branch is $behind commit(s) behind upstream/master (see tools/RELEASING.md to sync first)" >&2
    fi
fi

# Release notes: PRs merged into forge-dennis since the previous release, then the full list
prev_tag=$(git tag -l 'v*-dennis.*' --sort=-creatordate | head -1)
prs=$(gh pr list --repo "$repo_slug" --base "$branch" --state merged --limit 500 \
    --json number,title,mergedAt --jq 'sort_by(.number) | .[] | "\(.mergedAt)\t- \(.title) (#\(.number))"')
since=""
if [[ -n "$prev_tag" ]]; then
    since=$(git log -1 --format=%cI "$prev_tag")
fi
new_prs=$(awk -F'\t' -v since="$since" 'since == "" || $1 > since { print $2 }' <<<"$prs")
all_prs=$(cut -f2 <<<"$prs")

work=$(mktemp -d)
notes="$work/notes.md"
build="$work/build"
cleanup() {
    git -C "$repo" worktree remove --force "$build" 2>/dev/null || true
    rm -rf "$work"
}
trap cleanup EXIT
{
    echo "Personal build of Forge ${version_code} (upstream master as of $(date +'%b %Y')) with this fork's quality-of-life changes. Help > About Forge shows \`${version}\`."
    echo
    echo "**Download:** \`${dmg_name}\`. A self-contained Forge.app for ${arch_desc}, with Java bundled. Open the dmg and drag Forge to Applications."
    echo
    echo "The app isn't signed or notarized. If macOS blocks it, right-click Forge.app → Open, or run:"
    echo "\`xattr -dr com.apple.quarantine /Applications/Forge.app\`"
    echo
    echo "Your settings and decks in \`~/Library/Application Support/Forge\` are shared with any other Forge install."
    if [[ -n "$prev_tag" ]]; then
        echo
        echo "### New since ${prev_tag}"
        echo "${new_prs:-- (no new fork changes; upstream updates only)}"
    fi
    echo
    echo "### Changes vs. upstream"
    echo "$all_prs"
} >"$notes"

echo "Tag:    $tag (origin/$branch at ${sha:0:11})"
echo "Title:  Forge $version"
echo "Asset:  $dmg_name"
echo "------------------------------------------------------------"
cat "$notes"
echo "------------------------------------------------------------"
if [[ "$dry_run" -eq 1 ]]; then
    exit 0
fi

# Build in a throwaway worktree so the current checkout is left alone
echo "Building Forge.app and .dmg (takes a few minutes)..."
git worktree add -q --detach "$build" "$sha"
(cd "$build" && mvn -B -q -pl forge-gui-desktop -am -P osx package -DskipTests -Drevision="$version")
dmg="$work/$dmg_name"
mv "$build"/forge-gui-desktop/target/osx/*.dmg "$dmg"
cp "$dmg" "$HOME/Downloads/"
echo "Copied $dmg_name to ~/Downloads"

draft_flag=(--draft)
if [[ "$publish" -eq 1 ]]; then
    draft_flag=()
fi
echo "Creating the release and uploading the .dmg..."
gh release create "$tag" "$dmg" --repo "$repo_slug" --target "$sha" --title "Forge $version" \
    --notes-file "$notes" ${draft_flag[@]+"${draft_flag[@]}"}

if [[ "$publish" -eq 1 ]]; then
    echo "Published $tag."
else
    echo "Draft created with the .dmg attached. Review the notes on GitHub, then click Publish."
fi
