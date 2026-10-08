#!/usr/bin/env bash
# 构建 cleo-compiler.jar（纯 Java，无需 Gradle）
set -e
cd "$(dirname "$0")"
rm -rf build out
mkdir -p build out
javac -encoding UTF-8 -d out src/*.java
mkdir -p out/data
cp -r data/* out/data/
jar cfe build/cleo-compiler.jar com.sanny.builder.compiler.Main -C out .
echo "built: $(pwd)/build/cleo-compiler.jar ($(du -h build/cleo-compiler.jar | cut -f1))"
