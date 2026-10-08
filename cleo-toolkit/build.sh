#!/usr/bin/env bash
# 构建 CLEO Builder 单 jar（数据内嵌，自包含）
set -e
cd "$(dirname "$0")"
rm -rf build dist/cleobuilder.jar
mkdir -p build/out
javac -encoding UTF-8 -d build/out src/*.java
mkdir -p build/out/data
cp -r data/* build/out/data/
jar cfe dist/cleobuilder.jar com.sanny.builder.compiler.Main -C build/out .
echo "=== 构建完成: $(pwd)/dist/cleobuilder.jar ($(du -h dist/cleobuilder.jar | cut -f1)) ==="
