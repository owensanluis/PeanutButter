#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes build/test dist/lib
find src -name '*.java' > build/sources.txt
javac -source 8 -target 8 -encoding UTF-8 -cp sqlite-jdbc-3.23.1.jar -d build/classes @build/sources.txt
jar cfm dist/CSSECDV.jar manifest.mf -C build/classes .
cp sqlite-jdbc-3.23.1.jar dist/lib/
# Preserve an existing runtime database across builds.
if [ ! -f dist/database.db ]; then cp database.db dist/database.db; fi
if [ "${1:-}" = "test" ]; then
    javac -source 8 -target 8 -encoding UTF-8 -cp "build/classes:sqlite-jdbc-3.23.1.jar" -d build/test test/Controller/SecurityRegression.java
    java -Djava.awt.headless=true -cp "build/test:build/classes:sqlite-jdbc-3.23.1.jar" Controller.SecurityRegression
fi
