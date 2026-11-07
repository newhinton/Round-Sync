#!/usr/bin/env bash
# Run in the network-enabled preparation phase, before the offline rebuild.
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"

required_go=$(sed -n 's/^de\.schuelken\.cloudbridge\.goVersion=//p' gradle.properties | tr -d '\r')
actual_go=$(GOTOOLCHAIN=local go version | awk '{print $3}')
if [[ "$actual_go" != "go$required_go" ]]; then
    echo "Use Go $required_go on PATH (found $actual_go); match the GitHub release toolchain." >&2
    exit 1
fi

GOTOOLCHAIN=local ./gradlew :rclone:checkoutRclone -PunsignedRelease=true --no-daemon --console=plain
source_dir="$repo_dir/rclone/cache/rclone-src"
if [[ -n "$(git -C "$source_dir" status --porcelain --untracked-files=all)" ]]; then
    echo 'The pinned rclone checkout must be clean before preparation.' >&2
    exit 1
fi
(
    cd "$source_dir"
    # Keep the compiler outside this cache: downloaded toolchain binaries would
    # otherwise enter F-Droid's source scan and source archive.
    GOPATH="$repo_dir/rclone/cache/gopath" GOTOOLCHAIN=local go mod download
)
echo 'Source and Go modules prepared. Next: an unsigned rebuild with -PrcloneOffline=true.'
