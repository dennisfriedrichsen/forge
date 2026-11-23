#!/usr/bin/env bash
# Cut a personal release of this fork from forge-dennis.
#
#   tools/release.sh              # next version, e.g. v2.0.17-dennis.3; creates a draft release to review
#   tools/release.sh --publish    # same, but publishes right away
#   tools/release.sh --dry-run    # only print the tag and release notes
#   tools/release.sh 2.0.18-dennis.1   # pick the version yourself
#
# Publishing the release fires .github/workflows/macos-app.yml, which builds Forge.app for Apple
# Silicon and Intel with the tag as the app version (shown in Help > About Forge) and attaches the
# .dmg files to the release. See tools/RELEASING.md.
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

cd "$(dirname "$0")/.."
git fetch -q origin "$branch" --tags
git fetch -q upstream master 2>/dev/null || echo "note: no 'upstream' remote; skipping the upstream check" >&2

# Version: <pom versionCode>-dennis.<n>, n one past the newest release tag for that versionCode
version_code=$(git show "origin/$branch:pom.xml" | sed -n 's:.*<versionCode>\(.*\)</versionCode>.*:\1:p' | head -1)
if [[ -z "$version" ]]; then
    last_n=$(git tag -l "v${version_code}-dennis.*" | sed "s/^v${version_code}-dennis\.//" | grep -E '^[0-9]+$' | sort -n | tail -1)
    version="${version_code}-dennis.$(( ${last_n:-0} + 1 ))"
fi
tag="v$version"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
    echo "tag $tag already exists" >&2
    exit 1
fi

# Warn when forge-dennis is missing upstream commits (sync first if you want them in the release)
if git rev-parse -q --verify upstream/master >/dev/null; then
    behind=$(git rev-list --count "origin/$branch..upstream/master")
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

notes=$(mktemp)
trap 'rm -f "$notes"' EXIT
{
    echo "Personal build of Forge ${version_code} (upstream master as of $(date +'%b %Y')) with this fork's quality-of-life changes. Help > About Forge shows \`${version}\`."
    echo
    echo "**Download:** \`Forge-${version}-macos-arm64.dmg\` for Apple Silicon Macs, \`Forge-${version}-macos-x64.dmg\` for Intel. A self-contained Forge.app with Java bundled; open the dmg and drag Forge to Applications. (The .dmg files appear a few minutes after publishing, once the build finishes.)"
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

echo "Tag:    $tag (from origin/$branch)"
echo "Title:  Forge $version"
echo "------------------------------------------------------------"
cat "$notes"
echo "------------------------------------------------------------"
if [[ "$dry_run" -eq 1 ]]; then
    exit 0
fi

draft_flag=(--draft)
if [[ "$publish" -eq 1 ]]; then
    draft_flag=()
fi
gh release create "$tag" --repo "$repo_slug" --target "$branch" --title "Forge $version" \
    --notes-file "$notes" ${draft_flag[@]+"${draft_flag[@]}"}

if [[ "$publish" -eq 1 ]]; then
    echo "Published. The macOS build is running: gh run list --repo $repo_slug --workflow macos-app.yml"
else
    echo "Draft created. Review and edit the notes on GitHub, then click Publish to start the macOS build."
fi
