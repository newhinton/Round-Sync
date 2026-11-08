# Artwork provenance and licenses

## Current CloudBridge artwork

The maintainer confirmed on 8 November 2026 that the current CloudBridge
launcher icon and store graphics were generated using AI by the maintainer.
They are contributed under this repository's GPLv3 terms to the extent the
maintainer holds applicable rights. This statement does not assert exclusive
copyright in AI-generated material or identify an unspecified generation service.

This covers the current launcher PNGs under `app/src/main/res/mipmap-*`,
the launcher foreground PNGs, current store icon and feature graphics under
`fastlane/metadata/android/*/images/`, and the copy at
`app/src/main/ic_launcher-playstore.png`. Store screenshots depict CloudBridge
and retain any depicted third-party names or marks.

`app/src/main/res/drawable/appicon.xml` is an original monochrome cloud/link
symbol contributed under GPL-3.0-only. It replaces the inherited Round-Sync
symbol in the navigation header and update notification.

## Historical credits

The original rcloneExplorer launcher was credited to Smashicons via Flaticon.
Commit `65212bc6` included an explicit Creative Commons Attribution 3.0 notice;
commit `c69c0fd1` added the corresponding README credit. This is historical
attribution, not the origin of the current CloudBridge launcher.

- Smashicons: https://www.flaticon.com/authors/smashicons
- Flaticon: https://www.flaticon.com/
- CC BY 3.0: https://creativecommons.org/licenses/by/3.0/

Commit `7b89d0ee` added `docs/cloud-computing.png`, `docs/locked-padlock.png`
and `docs/smartphone.png` and expanded the README attribution to include Freepik.
Exact item/license URLs for those legacy images have not been established.
They are no longer referenced by current documentation and are not app resources.
The F-Droid recipe removes them from the source package rather than assuming
that the project's GPL grants rights to third-party artwork.

- Freepik: https://www.flaticon.com/authors/freepik

This file does not relicense third-party dependencies, provider logos or marks.
Inherited source assets remain subject to their upstream notices and applicable
trademark rights. Preserve the upstream GPL notices and contributor history.
