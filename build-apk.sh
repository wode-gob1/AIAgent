#!/bin/bash
set -e

# AIAgent APK 构建脚本
# 用法: 在项目根目录执行 ./build-apk.sh

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export PATH=$JAVA_HOME/bin:$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "正在打包 Release APK..."

./gradlew clean assembleRelease --no-daemon

echo "打包完成!"
echo "APK 路径: app/build/outputs/apk/release/app-release.apk"
ls -lh app/build/outputs/apk/release/app-release.apk
