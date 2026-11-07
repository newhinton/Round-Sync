# Release channels

- **Stable:** numbered tags such as `v1.0.2`, short notes, signed APKs and AAB,
  and `SHA256SUMS`. The newest stable release is GitHub's **Latest** release.
- **Development:** one automatic prerelease at `development`, updated in place
  after checks pass on `master`. Its tag and source archives move; use the full
  commit SHA in its notes for reproducible source. Old development assets are
  removed after replacement uploads succeed. Its checksum filename includes the
  build version so a failed upload cannot replace the previous checksum file.

The in-app updater checks stable releases only. Both channels use the same
release signing certificate.

## Publishing

For a stable release, bump the patch version and default version code together
in `app/build.gradle`, add `docs/releases/<version>.md`, and tag the release
commit as `v<version>`. Push the commit to `master`, wait for its development
build to pass, then push the stable tag. Stable uploads remain drafts until all
assets have uploaded. Published stable assets are never replaced by a rerun.

Version codes reserve the final digit for ABI offsets. With the full release
commit history, development uses `commit_count * 20` and stable uses
`commit_count * 20 + 10`; set the app's default code to the latter for the
release commit. Thus stable can replace the development build at that commit,
and the next development commit can replace stable. Do not rewrite `master`
history or move stable tags.

Manual workflow runs default to validation without publication. Select
`publish` only on `master` or a matching stable version tag to publish. All
release runs share a concurrency group; publishing runs are never interrupted
mid-upload by a newer run.

Select `inspect_signing` for a diagnostic-only run that prints the configured
public certificate fingerprint without building or publishing anything.
