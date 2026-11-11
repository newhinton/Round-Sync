#!/usr/bin/env python3
"""Prepare only F-Droid packaging inputs; never alter compiled Go sources."""
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RCLONE = ROOT / 'rclone/cache/rclone-src'
MODULES = ROOT / 'rclone/cache/gopath/pkg/mod'
INPUT_FIELDS = (
    'GoFiles', 'CgoFiles', 'CFiles', 'CXXFiles', 'MFiles', 'HFiles', 'FFiles',
    'SFiles', 'SwigFiles', 'SwigCXXFiles', 'SysoFiles', 'EmbedFiles',
)

PRUNE = [
    "rclone/cache/gopath/pkg/mod/github.com/!i!b!m/go-sdk-core/v5@v5.23.1/package.json",
    "rclone/cache/gopath/pkg/mod/github.com/!proton!mail/go-srp@v0.0.7/dist",
    "rclone/cache/gopath/pkg/mod/github.com/!proton!mail/gopenpgp/v3@v3.4.1/crypto/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/!s!t!a!r!r!y-!s/zip@v0.2.3/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/andybalholm/brotli@v1.2.2/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/aws/aws-sdk-go-v2/aws/protocol/eventstream@v1.7.18/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/aws/smithy-go@v1.27.8/eventstream/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/bodgit/sevenzip@v1.6.5/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/buengese/sgzip@v0.1.1/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/abe/cpabe/tkn20/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/blindsign/blindrsa/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/cipher/ascon/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/dh/csidh/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/dh/x25519/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/dh/x448/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/ecc/bls12381/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/expander/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/group/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/hpke/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/kem/mlkem/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/oprf/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/pki/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/sign/bls/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/sign/ed25519/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/sign/ed448/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/sign/mldsa/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/sign/schemes/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/sign/slhdsa/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudflare/circl@v1.6.4/vdaf/prio3/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/cloudsoda/sddl@v0.0.0-20250224235906-926454e91efc/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/diskfs/go-diskfs@v1.9.4/filesystem/squashfs/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/dsnet/compress@v0.0.2-0.20230904184137-39efe44ab707/brotli/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/dsnet/compress@v0.0.2-0.20230904184137-39efe44ab707/bzip2/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/dsnet/compress@v0.0.2-0.20230904184137-39efe44ab707/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/gabriel-vasile/mimetype@v1.4.13/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/google/flatbuffers@v25.12.19+incompatible/dart",
    "rclone/cache/gopath/pkg/mod/github.com/google/flatbuffers@v25.12.19+incompatible/rust",
    "rclone/cache/gopath/pkg/mod/github.com/google/flatbuffers@v25.12.19+incompatible/tests",
    "rclone/cache/gopath/pkg/mod/github.com/hanwen/go-fuse/v2@v2.10.1/zipfs/test.zip",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/flate/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/fse/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/gzip/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/huff0/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/s2/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/snappy/xerial/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/zip/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/compress@v1.19.2/zstd/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/klauspost/pgzip@v1.2.6/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/mholt/archives@v0.1.5/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/minio/minlz@v1.2.0/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/pierrec/lz4/v4@v4.1.28/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/prometheus/common@v0.70.0/expfmt/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/spf13/afero@v1.15.0/zipfs/testdata",
    "rclone/cache/gopath/pkg/mod/github.com/tinylib/msgp@v1.6.4/msgp/testdata",
    "rclone/cache/gopath/pkg/mod/go4.org@v0.0.0-20260112195520-a5071408f32f/ziputil/testdata",
    "rclone/cache/gopath/pkg/mod/golang.org/x/mobile@v0.0.0-20260709172247-6129f5bee9d5/internal/binres/testdata",
    "rclone/cache/gopath/pkg/mod/golang.org/x/sys@v0.48.0/windows/testdata",
    "rclone/cache/gopath/pkg/mod/golang.org/x/tools@v0.49.0/cmd/splitdwarf/internal/macho/testdata",
    "rclone/cache/gopath/pkg/mod/golang.org/x/tools@v0.49.0/go/gccgoexportdata/testdata",
    "rclone/cache/gopath/pkg/mod/golang.org/x/tools@v0.49.0/go/internal/gccgoimporter/testdata",
    "rclone/cache/gopath/pkg/mod/golang.org/x/tools@v0.49.0/internal/gcimporter/testdata",
    "rclone/cache/gopath/pkg/mod/google.golang.org/protobuf@v1.36.12/internal/fuzz/wirefuzz/corpus/20019c4ef10ebf8031c8d204bdd0ae1ec214bf90",
    "rclone/cache/gopath/pkg/mod/moul.io/http2curl/v2@v2.3.0/tool/lint/package.json"
]


def production_inputs():
    """Protect every production Go source/embed on all four packaged ABIs."""
    build_script = (ROOT / 'rclone/build.gradle').read_text()
    match = re.search(r"['\"]-tags['\"]\s*,\s*['\"]([^'\"]+)['\"]", build_script)
    if not match or 'nogui' not in match.group(1).replace(',', ' ').split():
        raise SystemExit('The actual Gradle native build must use the nogui tag')
    tags = match.group(1)
    inputs = set()
    decoder = json.JSONDecoder()
    for arch in ('arm', 'arm64', '386', 'amd64'):
        env = dict(os.environ, GOPATH=str(MODULES.parent.parent), GOMODCACHE=str(MODULES),
                   GOOS='linux', GOARCH=arch, CGO_ENABLED='0',
                   GOTOOLCHAIN='local', GOPROXY='off', GOSUMDB='off')
        if arch == 'arm':
            env['GOARM'] = '7'
        output = subprocess.check_output([
            'go', 'list', '-deps', '-tags', tags,
            '-json=' + ','.join(('ImportPath', 'Dir', *INPUT_FIELDS)), '.',
        ], cwd=RCLONE, env=env, text=True)
        position = 0
        while position < len(output):
            while position < len(output) and output[position].isspace():
                position += 1
            if position == len(output):
                break
            package, position = decoder.raw_decode(output, position)
            if package.get('ImportPath') == 'github.com/rclone/rclone/cmd/gui':
                raise SystemExit('GUI is still a production dependency; select the nogui release')
            for field in INPUT_FIELDS:
                inputs.update((Path(package['Dir']) / name).resolve()
                              for name in package.get(field, []))
    return inputs


def prune_fixtures():
    if not MODULES.is_dir() or MODULES.resolve() != MODULES:
        raise SystemExit('Expected a disposable module cache inside the source checkout')
    protected = production_inputs()
    # Module ZIPs are redundant after extraction; preserve .ziphash provenance.
    targets = [ROOT / relative for relative in PRUNE]
    targets.extend((MODULES / 'cache/download').rglob('*.zip'))
    for target in targets:
        if target == MODULES or not target.is_relative_to(MODULES) or target.resolve() != target:
            raise SystemExit(f'Refusing cleanup outside the module cache or through a symlink: {target}')
        if any(path == target or path.is_relative_to(target) for path in protected):
            raise SystemExit(f'Cleanup would remove a production input: {target}')
    for target in targets:
        if target.is_dir():
            shutil.rmtree(target)
        elif target.exists():
            target.unlink()
    if production_inputs() != protected:
        raise SystemExit('Production Go input list changed during cleanup')
    print('Pruned only listed unused fixtures/foreign-language bindings; all ABI inputs unchanged')


if __name__ == '__main__':
    prune_fixtures()
