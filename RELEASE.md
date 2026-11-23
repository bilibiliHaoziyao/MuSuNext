# 怎么把 MuSu Next 做出来、发出去

> 这份说明是给"啥也不会"的人写的。你不用懂原理，照着复制、粘贴、回车就行。
> 每一步我都告诉你：做什么 → 会看到什么 → 不对怎么办。

---

## 先记住三句话

1. **官方发布的安装包（APK）已经自带模块**：8 个 KMI 版本全在里面，用户选 boot、弹窗选 KMI 就能修补，不需要另外下载 `musu.ko`。
2. 只有**自己编内核**的人，才需要单独产出一个 `musu.ko`；那个文件是给他自己用的，不是给普通用户的。
3. 你自己从源码做 APK 时，要先把编好的模块放进指定目录，再编 ksud —— 否则做出来的 APK 里是空的，弹窗会没有选项。

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

**编之前先检查一件事**：`userspace/ksud/bin/aarch64/` 里得有 8 个 `*_musu.ko`。
这些就是"内置模块"，编进去以后用户才能选 KMI。没有的话，先按第 3 步把模块编出来，再拷进来：

```bash
ls /workspace/MuSuNext/userspace/ksud/bin/aarch64/*_musu.ko | wc -l
```

**看到什么算成功**：输出 `8`。
**不对怎么办**：数字小于 8 就先去做第 3 步，把 `musu.ko` 按 KMI 命名拷进这个目录，再回来。

然后编译 ksud。复制这一整段，粘到终端，回车：

```bash
cd /workspace/MuSuNext/userspace/ksud
rustup target add aarch64-linux-android
cargo ndk -t arm64-v8a build --release
cd /workspace/MuSuNext
mkdir -p target/aarch64-linux-android/release
cp userspace/ksud/target/aarch64-linux-android/release/ksud target/aarch64-linux-android/release/ksud
```

**看到什么算成功**：最后一行命令敲完，屏幕没有任何提示（安静就是成功）。
**不对怎么办**：如果看到 `command not found`，说明缺工具，把报错整段复制给我。

> 这里编出来的 ksud 已经把 `userspace/ksud/bin/aarch64/` 里的 8 个模块压进了二进制。
> 下一步会把它塞进 APK，所以最终安装包自带全部 KMI。

---

## 第 2 步：做出安装包（APK）

签名密钥是 `signing/musu-next.jks`（密码见单独的签名材料备份 zip，**不要**写进 gradle.properties 提交）。
构建时用命令行参数把签名信息传给工具 —— 注意：**别用 sign.properties 文件**，那个插件不读它，会悄悄退回 debug 签名，导致内核不认。

先把密钥复制一份到 manager 目录（和正式 CI 的做法一致）：

```bash
cp /workspace/MuSuNext/signing/musu-next.jks /workspace/MuSuNext/manager/key.jks
```

然后做安装包（把 `你的密码` 换成真实密码）：

```bash
cd /workspace/MuSuNext/manager
./gradlew assembleRelease --no-daemon \
  -PKEYSTORE_FILE=key.jks \
  -PKEYSTORE_PASSWORD=你的密码 \
  -PKEY_ALIAS=musu \
  -PKEY_PASSWORD=你的密码
```

**看到什么算成功**：最后出现 `BUILD SUCCESSFUL`。
> 这一步产出的只是"壳"，里面还没有 ksud。下面把它塞进去，顺便签名。

```bash
cd /workspace/MuSuNext
python3 repack_apk.py repack \
  -b release -t release -a arm64-v8a \
  -K manager/key.jks -A musu \
  -P 你的密码 -S 你的密码 \
  --strip
```

**看到什么算成功**：出现 `dist/` 下的 APK 文件名。
**最终产物在**：`/workspace/MuSuNext/dist/`（这个才是能发出去的）。

**检查模块有没有真的打进去**（关键）：

```bash
python3 scripts/verify_apk_lkm.py dist/*.apk
```

**看到什么算成功**：`arm64-v8a 8/8 OK`，最后一行"内置完整"。
**不对怎么办**：显示 `0/8` 说明第 1 步的模块没进 ksud，回到第 1 步检查 `bin/aarch64/` 里是不是 8 个文件，重新编。

**验证签名对不对**（输出里的 SHA-256 必须等于内核里的 `KSU_EXPECTED_HASH`）：
```bash
apksigner verify --print-certs dist/*.apk
```

---

## 第 3 步：做出内核文件（只有自己编内核才需要）

> 普通用户拿到第 2 步的 APK 就能用，不需要这一步。
> 这一步是给"自己编译了内核"或者"要预编译模块放 Release"的人用的。

复制这段，把 `/path/to/你的内核源码` 换成你内核源码的实际位置，然后回车：

```bash
cd /workspace/MuSuNext/kernel
make -f Kbuild \
  KDIR=/path/to/你的内核源码 \
  KSU_EXPECTED_HASH=eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0 \
  KSU_EXPECTED_SIZE=0x0378 \
  KSU_PACKAGE_NAME=org.musu.next
```

**看到什么算成功**：屏幕上出现 `musu.ko` 相关的生成信息，没有 `Error`。
**不对怎么办**：把报错复制给我。

**做好的文件**：`/workspace/MuSuNext/kernel/musu.ko`

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

发出下载链接（`dist/*.apk` 就是第 2 步做好的安装包，已经内置全部模块）：

```bash
git tag v1.0.0
git push origin v1.0.0
gh release create v1.0.0 \
  --repo bilibiliHaoziyao/MuSuNext \
  --title "MuSu Next v1.0.0" \
  --notes-file CHANGELOG.md \
  dist/*.apk
```

> 想额外附上预编译模块（给自行编内核的人），在最后加一行 `kernel/musu.ko` 即可。

**看到什么算成功**：终端会给出一个网址，打开就能看到下载按钮。
**不对怎么办**：把报错复制给我。

---

## 卡住了怎么办（记住这一条就行）

**任何一步报错，你什么都不用想，把屏幕上的字原样复制发给我。** 我来告诉你下一步点哪。
