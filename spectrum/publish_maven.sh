#!/usr/bin/env bash
# Publishes this fork's akit-java to the Maven repository on the fork's GitHub Pages
# (https://spectrum3847.github.io/AdvantageKit/), served from the orphan `maven` branch.
#
#   spectrum/publish_maven.sh 27.0.0-alpha-7-spectrum.1
#
# Only akit-java is published. Robot projects keep upstream's akit-wpilibio (native) and
# akit-autolog at the upstream version, so the fork must not change the JNI or the annotations.
# A published version is never overwritten: bump the last number instead.
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?usage: spectrum/publish_maven.sh VERSION (e.g. 27.0.0-alpha-7-spectrum.1)}
case $version in
    *-spectrum.*) ;;
    *) echo "version must end in -spectrum.N so it never collides with upstream" >&2; exit 1 ;;
esac
if [ -n "$(git status --porcelain akit)" ]; then
    echo "akit/ has uncommitted changes; publish from a commit" >&2
    exit 1
fi

(cd akit && ../gradlew -p . clean test jar -PpublishingVersion="$version" -q)

site=$(mktemp -d)
trap 'git worktree remove --force "$site" 2>/dev/null || true' EXIT
if git ls-remote --exit-code origin maven >/dev/null; then
    git fetch -q origin maven
    git worktree add -q "$site" origin/maven
    git -C "$site" checkout -q -B maven
else
    git worktree add -q --detach "$site"
    git -C "$site" checkout -q --orphan maven
    git -C "$site" rm -rqf .
    printf 'Maven repository for Spectrum 3847'"'"'s AdvantageKit fork. See spectrum/ on its branches.\n' > "$site/README.md"
    touch "$site/.nojekyll"
fi

dir=$site/org/littletonrobotics/akit/akit-java/$version
if [ -e "$dir" ]; then
    echo "$version is already published" >&2
    exit 1
fi
mkdir -p "$dir"
cp akit/build/libs/akit.jar "$dir/akit-java-$version.jar"
cat > "$dir/akit-java-$version.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.littletonrobotics.akit</groupId>
  <artifactId>akit-java</artifactId>
  <version>$version</version>
</project>
EOF
for f in "$dir"/*; do
    sha1sum "$f" | cut -d' ' -f1 > "$f.sha1"
    sha256sum "$f" | cut -d' ' -f1 > "$f.sha256"
done

git -C "$site" add -A
git -C "$site" commit -q -m "akit-java $version from $(git rev-parse --short HEAD)"
git -C "$site" push -q origin maven
echo "published akit-java $version"
