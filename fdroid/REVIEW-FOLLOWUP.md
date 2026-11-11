# Follow-up for F-Droid request 4754

Update the existing request; do not open a duplicate. This draft describes local
fixes. Publish a new release containing the app artwork/credit changes before
requesting a rebuild of those changes. Do not move or replace `v1.0.3`.

## Fixes prepared

- Regenerated both wrapper scripts and the wrapper JAR with Gradle 8.13.
- Pinned the official distribution SHA-256:
  `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- Verified the wrapper JAR against the official Gradle 8.13 SHA-256:
  `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Added `Translation` pointing to the existing CloudBridge contribution guide.
- Documented maintainer-confirmed AI-generated current artwork and historical
  third-party credits in `ASSETS.md`, without inventing missing license grants.
- Corrected stale launcher attribution and replaced remaining Round-Sync
  branding in the monochrome symbol and standalone Play Store icon.
- Excluded three unused legacy documentation images of uncertain provenance
  from the F-Droid source package. Their existing working-tree deletions and
  other unrelated user changes have not been committed by this task.

## OpenCensus report evidence

The bot's `gradlew-fdroid --write-verification-metadata sha256 help` report
includes build and testing dependencies, not only the APK runtime classpath.
The resolved `ossReleaseRuntimeClasspath` contains neither
`io.opencensus:opencensus-api` nor `io.opencensus:opencensus-proto`.

Cached published POMs show these dependency edges:

- `com.google.testing.platform:core:0.0.9-alpha03` → both flagged libraries.
- `com.android.tools.utp:utp-common:31.13.2` →
  `com.google.testing.platform:launcher:0.0.9-alpha03` → `opencensus-proto`.

This supports requesting build/test classification of those findings rather
than declaring that the app tracks its users. No global dependency exclusions
or blanket scanner ignore rules were introduced to hide these flags. This is
not a license-by-license audit of every transitive Java or Go dependency.

## Before posting

1. Commit/publish the reviewed fixes as a new release when authorized.
2. Update the F-Droid recipe's version, code and full immutable source SHA to
   that release. Preserve `Binaries` and `AllowedAPKSigningKeys`.
3. Repeat the signed-APK versus clean normal-clone unsigned-APK comparison.
4. Update request https://gitlab.com/fdroid/rfp/-/work_items/4754 with the new
   source/release links and this evidence; request another review there.
5. Resolve the independent F-Droid server scan/build and inclusion review before
   enabling distribution. A local comparison does not replace those checks.

## Suggested comment after publication

> I have published the follow-up fixes at **[insert new release and commit]**.
> The Gradle 8.13 wrapper JAR/scripts and distribution checksum are now aligned.
> Translation guidance and artwork provenance are documented, stale branding
> and credits are corrected, and unused legacy documentation artwork is excluded
> from the F-Droid source package. Neither flagged OpenCensus dependency appears
> in the OSS release runtime classpath; the report includes Google testing-tool
> dependencies. Please review that classification and rerun the packaging
> checks against the new immutable release. Developer-signed reproducible
> distribution remains required; the existing certificate pin is unchanged.
