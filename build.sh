#!/usr/bin/env bash
# Builds eclipse.jar (the loader) and mods/hello_mod.jar (the example mod). Requires JDK 17+.
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/loader build/mod mods

javac -d build/loader $(find loader/src -name '*.java')
jar --create --file eclipse.jar --manifest loader/MANIFEST.MF -C build/loader .

javac -cp build/loader -d build/mod $(find examplemod/src -name '*.java')
cp examplemod/eclipse.mod.properties build/mod/
jar --create --file mods/hello_mod.jar -C build/mod .

echo "Built eclipse.jar and mods/hello_mod.jar"
