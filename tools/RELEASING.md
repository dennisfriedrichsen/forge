# Releasing the fork

Personal releases are cut from `forge-dennis` and tagged `v<versionCode>-dennis.<n>`, e.g.
`v2.0.17-dennis.2`. The tag becomes the app version, so **Help → About Forge** shows `2.0.17-dennis.2`
instead of an upstream-looking `2.0.17-SNAPSHOT-<date>`.

## Steps

1. **Optional: pick up upstream changes.** Do this first if `tools/release.sh` warns that `forge-dennis`
   is behind:

   ```bash
   git fetch upstream && git checkout master && git merge --ff-only upstream/master && git push origin master
   git checkout forge-dennis && git pull && git merge master && git push origin forge-dennis
   ```

   (`master` mirrors upstream exactly, so never commit to it. Merge it into `forge-dennis`; don't rebase.)

2. **Preview the release:**

   ```bash
   tools/release.sh --dry-run
   ```

   This prints the next tag and the release notes: the PRs merged into `forge-dennis` since the last
   release, plus the full list of fork changes.

3. **Create it:**

   ```bash
   tools/release.sh
   ```

   This creates a **draft** release. Review and edit the notes on GitHub, then click **Publish**.
   (`tools/release.sh --publish` skips the draft.)

4. **Wait for the build.** Publishing starts the **Build macOS App** workflow
   (`.github/workflows/macos-app.yml`). It builds Forge.app for Apple Silicon and Intel and attaches
   `Forge-<version>-macos-arm64.dmg` and `Forge-<version>-macos-x64.dmg` to the release after about ten
   minutes:

   ```bash
   gh run list --repo dennisfriedrichsen/forge --workflow macos-app.yml
   ```

## Notes

- The version number comes from `<versionCode>` in the root `pom.xml`. `<n>` counts up per versionCode,
  so the first release after upstream moves to 2.0.18 is `v2.0.18-dennis.1`. Pass a version to override:
  `tools/release.sh 2.0.18-dennis.1`.
- The workflow passes the tag to Maven as `-Drevision=<tag without v>`. Manual workflow runs with no tag
  keep the pom's `-SNAPSHOT` version.
- macOS's own app info (Finder → Get Info) shows only `2.0.17`, because jpackage requires a purely
  numeric version. Use Help → About Forge to see the full version.
- To rebuild the .dmg files for an existing release, run the workflow from the Actions tab or with
  `gh workflow run macos-app.yml --repo dennisfriedrichsen/forge -f ref=<tag>`. Manual runs keep the files
  as workflow artifacts and use the `-SNAPSHOT` version, so for a correct About version, delete and
  re-publish the release instead.
- To build locally (on a Mac with JDK 17+):
  `mvn -pl forge-gui-desktop -am -P osx package -DskipTests -Drevision=2.0.17-dennis.2`. The output is
  in `forge-gui-desktop/target/osx/`.
