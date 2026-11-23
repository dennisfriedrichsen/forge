# Releasing the fork

Personal releases are cut from `forge-dennis` and tagged `v<versionCode>-dennis.<n>`, e.g.
`v2.0.17-dennis.2`. The tag becomes the app version, so **Help → About Forge** shows `2.0.17-dennis.2`
instead of an upstream-looking `2.0.17-SNAPSHOT-<date>`.

GitHub Actions is off on this fork, so releases are built on this Mac by `tools/release.sh`. You need
macOS, a JDK 17+ (`java -version`) and the `gh` CLI, signed in.

## Steps

1. **Optional: pick up upstream changes.** Do this first if `tools/release.sh` warns that `forge-dennis`
   is behind:

   ```bash
   git fetch upstream && git checkout master && git merge --ff-only upstream/master && git push origin master
   git checkout forge-dennis && git pull && git merge master && git push origin forge-dennis
   ```

   (`master` mirrors upstream exactly, so never commit to it. Merge it into `forge-dennis`; don't rebase.)
   Only what's pushed to `origin/forge-dennis` goes into the release.

2. **Preview the release:**

   ```bash
   tools/release.sh --dry-run
   ```

   This prints the next tag and the release notes: the PRs merged into `forge-dennis` since the last
   release, plus the full list of fork changes. Nothing is built or created.

3. **Build and create it:**

   ```bash
   tools/release.sh
   ```

   This builds Forge.app from `origin/forge-dennis` in a temporary worktree (a few minutes), copies
   `Forge-<version>-macos-arm64.dmg` to `~/Downloads`, and creates a **draft** release with the .dmg
   attached. Uploading the ~300 MB file takes a while. Review the notes on GitHub, then click
   **Publish**. (`tools/release.sh --publish` skips the draft.)

## Notes

- The version number comes from `<versionCode>` in the root `pom.xml`. `<n>` counts up per versionCode,
  so the first release after upstream moves to 2.0.18 is `v2.0.18-dennis.1`. Pass a version to override:
  `tools/release.sh 2.0.18-dennis.1`.
- The build is for this Mac's architecture: Apple Silicon gives an `arm64` .dmg, an Intel Mac gives `x64`.
- macOS's own app info (Finder → Get Info) shows only `2.0.17`, because jpackage requires a purely
  numeric version. Use Help → About Forge to see the full version.
- To build without releasing:
  `mvn -pl forge-gui-desktop -am -P osx package -DskipTests -Drevision=2.0.17-dennis.2`. The output is
  in `forge-gui-desktop/target/osx/`.
- To replace the .dmg on an existing release, build it as above, rename it to
  `Forge-<version>-macos-arm64.dmg`, and run
  `gh release upload <tag> <file> --repo dennisfriedrichsen/forge --clobber`.
- If Actions is ever turned on for the fork, publishing a release also runs
  `.github/workflows/macos-app.yml`, which builds both arm64 and x64 .dmg files with the same tag version
  and attaches them.
