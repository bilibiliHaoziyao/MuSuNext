# 怎么把 MuSu Next 做出来、发出去

> 这份说明是给"啥也不会"的人写的。你不用懂原理，照着复制、粘贴、回车就行。
> 每一步我都告诉你：做什么 → 会看到什么 → 不对怎么办。

---

## 先记住三句话

1. 我们要做出两个文件：一个**安装包（APK）**（装在手机上的），一个**内核文件（kernelsu.ko）**（刷进系统的）。
2. 这俩必须**一起用**，而且都必须用我们这个项目做出来的——用别人家的会认不出来。
3. 你现在什么都不用懂，只要照着下面的步骤敲命令。

---

## 第 0 步：看看你电脑够不够格

需要三样东西：

| 东西 | 干嘛用的 | 没有会怎样 |
|---|---|---|
| **Java（JDK 21）** | 做安装包的工具要用它 | 第 2 步会报错 |
| **Android SDK** | 做安装包必须的零件 | 第 2 步会报错 |
| **GitHub 账号** | 把代码传到网上、发下载链接 | 第 4 步做不了 |

> 沙箱里目前 **没有** Android SDK，所以第 2 步现在还做不了。
> 想让我直接帮你装，就说一句"帮我装"，我来弄。

---

## 第 1 步：准备一个小零件（ksud）

安装包里要塞一个小程序，先把它做出来。

复制这一整段，粘到终端，回车：

```bash
cd /workspace/MuSuNext/userspace/ksud
rustup target add aarch64-linux-android
cargo ndk -t arm64-v8a build --release
cd /workspace/MuSuNext
mkdir -p manager/app/src/main/jniLibs/arm64-v8a
cp userspace/ksud/target/aarch64-linux-android/release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so
```

**看到什么算成功**：最后一行命令敲完，屏幕没有任何提示（安静就是成功）。
**不对怎么办**：如果看到 `command not found`，说明缺工具，把报错整段复制给我。

---

## 第 2 步：做出安装包（APK）

先建一个小文件，告诉工具我们的签名在哪。

复制这段，粘进去，回车（把里面的 `你的密码` 换成真实密码，引号别删）：

```bash
cat > /workspace/MuSuNext/manager/sign.properties <<'EOF'
KEYSTORE_FILE=../signing/musu-next.jks
KEYSTORE_PASSWORD=你的密码
KEY_ALIAS=musu
KEY_PASSWORD=你的密码
EOF
```

然后做安装包：

```bash
cd /workspace/MuSuNext/manager
./gradlew assembleRelease --no-daemon
```

**看到什么算成功**：最后出现 `BUILD SUCCESSFUL`。
**不对怎么办**：把最后 20 行报错复制给我，别自己改。

**做好的文件在这里**（记住这个位置）：
```
/workspace/MuSuNext/manager/app/build/outputs/apk/release/app-release.apk
```

---

## 第 3 步：做出内核文件

复制这段，把 `/path/to/你的内核源码` 换成你内核源码的实际位置，然后回车：

```bash
cd /workspace/MuSuNext/kernel
make -f Kbuild \
  KDIR=/path/to/你的内核源码 \
  KSU_EXPECTED_HASH=eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0 \
  KSU_EXPECTED_SIZE=0x0378 \
  KSU_PACKAGE_NAME=org.musu.next
```

**看到什么算成功**：屏幕上出现 `kernelsu.ko` 相关的生成信息，没有 `Error`。
**不对怎么办**：把报错复制给我。

**做好的文件**：`/workspace/MuSuNext/kernel/kernelsu.ko`

> 看不懂这一大串命令？没关系，你只要知道：这串命令是告诉编译器"这是 MuSu Next 自己的东西"，照粘就行，一个字都别改（除了内核源码路径）。

---

## 第 4 步：传到网上，生成下载链接

先登录 GitHub（会跳出一个网页让你确认，按提示点同意）：

```bash
gh auth login
```

建一个放代码的仓库（如果还没有）：

```bash
gh repo create bilibiliHaoziyao/MuSuNext --public --confirm
```

把代码传上去：

```bash
cd /workspace/MuSuNext
git remote add origin https://github.com/bilibiliHaoziyao/MuSuNext.git
git push -u origin main
```

发出下载链接（这一步会把刚才做的两个文件传上去）：

```bash
git tag v1.0.0
git push origin v1.0.0
gh release create v1.0.0 \
  --repo bilibiliHaoziyao/MuSuNext \
  --title "MuSu Next v1.0.0" \
  --notes-file CHANGELOG.md \
  manager/app/build/outputs/apk/release/app-release.apk \
  kernel/kernelsu.ko
```

**看到什么算成功**：终端会给出一个网址，打开就能看到下载按钮。
**不对怎么办**：把报错复制给我。

---

## 卡住了怎么办（记住这一条就行）

**任何一步报错，你什么都不用想，把屏幕上的字原样复制发给我。** 我来告诉你下一步点哪。
