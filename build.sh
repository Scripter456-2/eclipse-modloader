#!/usr/bin/env bash
# Builds eclipse.jar (the loader) and mods/hello_mod.jar (the example mod). Requires JDK 17+ (targets Java 17 so it runs on 17 and 21).
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/loader build/mod mods

javac --release 17 -d build/loader $(find loader/src -name '*.java')
jar --create --file eclipse.jar --manifest loader/MANIFEST.MF -C build/loader .

javac --release 17 -cp build/loader -d build/mod $(find examplemod/src -name '*.java')
cp examplemod/eclipse.mod.properties build/mod/
jar --create --file mods/hello_mod.jar -C build/mod .

echo "Built eclipse.jar and mods/hello_mod.jar"

# Installer (bundles eclipse.jar + example mod)
mkdir -p build/installer installer/res
cp eclipse.jar mods/hello_mod.jar installer/res/
javac --release 17 -d build/installer $(find installer/src -name '*.java')
cp installer/res/*.jar build/installer/
jar --create --file EclipseInstaller.jar --manifest installer/MANIFEST.MF -C build/installer .
echo "Built EclipseInstaller.jar"
