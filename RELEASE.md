# MuSu Next 构建与发布指南

本文件列出本地构建步骤与推送到 GitHub（账号 `bilibiliHaoziyao`，仓库 `MuSuNext`）的 CLI 命令清单。
**以下命令需要你在具备 Android SDK / 网络 / GitHub 登录的环境自行执行**（本沙箱未预装 Android SDK，且 github.com 直连超时）。

## 0. 前置条件

- JDK 17+（Gradle 9.7.1 要求 JDK 21，建议安装 JDK 21）
- Android SDK（Platform 35 + Build Tools + NDK）
- Rust + cargo-ndk（构建 ksud）
- GitHub CLI（`gh`）已登录

## 1. 准备 ksud 二进制（管理器依赖）

```bash
cd userspace/ksud
rustup target add aarch64-linux-android
cargo ndk -t arm64-v8a build --release
cd ../..
mkdir -p manager/app/src/main/jniLibs/arm64-v8a
cp userspace/ksud/target/aarch64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/arm64-v8a/libksud.so
```

## 2. 签名配置

签名 keystore 位于 `signing/musu-next.jks`（**不进仓库**）。创建 `manager/sign.properties`：

```properties
KEYSTORE_FILE=../signing/musu-next.jks
KEYSTORE_PASSWORD=<你的密码>
KEY_ALIAS=musu
KEY_PASSWORD=<你的密码>
```

## 3. 构建管理器 APK（CLI）

```bash
cd manager
./gradlew assembleRelease --no-daemon
# 产物: manager/app/build/outputs/apk/release/app-release.apk
```

## 4. 构建内核 LKM

```bash
cd kernel
make -f Kbuild \
  KDIR=/path/to/kernel/source \
  KSU_EXPECTED_HASH=eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0 \
  KSU_EXPECTED_SIZE=0x0378 \
  KSU_PACKAGE_NAME=org.musu.next
# 产物: kernelsu.ko
```

## 5. 推送到 GitHub（CLI，需先登录）

```bash
# 登录（若未登录）
gh auth login

# 在 GitHub 创建仓库（若不存在）
gh repo create bilibiliHaoziyao/MuSuNext --public --description "A kernel-based root solution for Android (MuSu Next fork)" --confirm

# 本地初始化并推送
cd /workspace/MuSuNext
git init
git add -A
git commit -m "feat: initial MuSu Next fork (branding + independent signing)"
git remote add origin https://github.com/bilibiliHaoziyao/MuSuNext.git
git branch -M main
git push -u origin main
```

## 6. 创建 Release（CLI）

```bash
# 打 tag
git tag v1.0.0
git push origin v1.0.0

# 创建 release 并上传产物
gh release create v1.0.0 \
  --repo bilibiliHaoziyao/MuSuNext \
  --title "MuSu Next v1.0.0" \
  --notes-file CHANGELOG.md \
  manager/app/build/outputs/apk/release/app-release.apk \
  kernel/kernelsu.ko
```

> 提醒：`signing/` 与 `*.jks` 已被 `.gitignore` 忽略，私钥不会随代码上传。发布产物前请确认未误提交密钥。
