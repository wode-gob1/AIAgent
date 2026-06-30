# AIAgent APK 构建指南

## 方式一：Docker 一键构建（推荐）

```bash
cd AIAgent
docker build -t aiagent-builder .
docker run --rm -v $(pwd)/output:/output aiagent-builder
# APK 将输出到 ./output/AIAgent-v1.0.apk
```

## 方式二：本地 Android Studio 构建

1. 打开 Android Studio
2. File → Open → 选择 AIAgent 目录
3. 等待 Gradle Sync
4. Build → Build Bundle(s) / APK(s) → Build APK(s)

## 方式三：命令行构建

确保已安装：
- JDK 17
- Android SDK (API 34, Build Tools 34.0.0)
- 配置环境变量 ANDROID_HOME

```bash
./build-apk.sh
```

## 安装测试

```bash
adb install app/build/outputs/apk/release/app-release.apk
```
