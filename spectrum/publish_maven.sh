#!/usr/bin/env bash
# Publishes this fork to the Maven repository on the fork's GitHub Pages
# (https://spectrum3847.github.io/AdvantageKit/), served from the orphan `maven` branch:
# akit-java and akit-autolog, plus the vendordep AdvantageKit-Spectrum.json at the site root, which
# is the vendordep's jsonUrl, so WPILib's "update vendordeps" updates to the fork, not upstream.
#
#   spectrum/publish_maven.sh 27.0.0-alpha-7-spectrum.2
#
# The native akit-wpilibio is not rebuilt: the vendordep takes upstream's, at the upstream version
# the fork version starts with, so the fork must not change the JNI.
# A published version is never overwritten: bump the last number instead.
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?usage: spectrum/publish_maven.sh VERSION (e.g. 27.0.0-alpha-7-spectrum.2)}
case $version in
    *-spectrum.*) ;;
    *) echo "version must end in -spectrum.N so it never collides with upstream" >&2; exit 1 ;;
esac
upstream_version=${version%-spectrum.*}
if [ -n "$(git status --porcelain akit spectrum)" ]; then
    echo "akit/ or spectrum/ has uncommitted changes; publish from a commit" >&2
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

# The runtime dependencies a module's build file declares, as POM <dependency> elements, so a
# consumer gets them as it would from upstream (autolog needs JavaPoet).
pom_dependencies() { # build.gradle.kts
    grep -oE 'implementation\("[^"]+"\)' "$1" | sed -E 's/implementation\("([^:]+):([^:]+):([^"]+)"\)/      <dependency><groupId>\1<\/groupId><artifactId>\2<\/artifactId><version>\3<\/version><scope>runtime<\/scope><\/dependency>/'
}

publish() { # artifactId jar [build.gradle.kts whose implementation dependencies the POM lists]
    local dir=$site/org/littletonrobotics/akit/$1/$version
    if [ -e "$dir" ]; then
        echo "$1 $version is already published" >&2
        exit 1
    fi
    mkdir -p "$dir"
    cp "$2" "$dir/$1-$version.jar"
    local deps=""
    if [ -n "${3:-}" ]; then
        deps=$(pom_dependencies "$3")
    fi
    cat > "$dir/$1-$version.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.littletonrobotics.akit</groupId>
  <artifactId>$1</artifactId>
  <version>$version</version>
  <dependencies>
$deps
  </dependencies>
</project>
EOF
    for f in "$dir"/*; do
        sha1sum "$f" | cut -d' ' -f1 > "$f.sha1"
        sha256sum "$f" | cut -d' ' -f1 > "$f.sha256"
    done
}
publish akit-java akit/build/libs/akit.jar
publish akit-autolog akit/autolog/build/libs/autolog.jar akit/autolog/build.gradle.kts

# The vendordep: latest at the root (its jsonUrl), and a copy per version.
sed -e "s/\$VERSION/$version/g" -e "s/\$UPSTREAM_VERSION/$upstream_version/g" \
    spectrum/AdvantageKit-Spectrum.json > "$site/AdvantageKit-Spectrum.json"
mkdir -p "$site/vendordeps"
cp "$site/AdvantageKit-Spectrum.json" "$site/vendordeps/AdvantageKit-Spectrum-$version.json"
"$(command -v python3 || command -v python)" -m json.tool "$site/AdvantageKit-Spectrum.json" > /dev/null

git -C "$site" add -A
git -C "$site" commit -q -m "AdvantageKit $version from $(git rev-parse --short HEAD)"
git -C "$site" push -q origin maven
echo "published AdvantageKit $version"
