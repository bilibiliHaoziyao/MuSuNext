# MuSu Next 品牌化与兼容性说明

> MuSu Next 是基于上游 [KernelSU](https://github.com/tiann/KernelSU) 的独立发行版（fork）。
> 本文件说明本次改造改了什么、保留了什么，以及你**必须知道的内核编译要求**。

## 1. 为什么不能"只改名字"

上游内核通过**两道锁**识别官方管理器，缺一不可：

| 校验项 | 上游官方值 | 存储位置 |
| --- | --- | --- |
| 管理器包名 | `me.weishu.kernelsu` | `kernel/Kbuild` 的 `KSU_PACKAGE_NAME` |
| APK v2 签名 SHA256 | `c371061b…2d6` | `kernel/Kbuild` 的 `KSU_EXPECTED_HASH` |
| 证书 DER 长度 | `0x033b` (827) | `kernel/Kbuild` 的 `KSU_EXPECTED_SIZE` |

如果只改应用的显示名，而不动这三处，装上去后**内核不会把它识别为管理器**，无法弹出 root 授权对话框。
因此独立品牌版必须走"包名 + 签名 + 内核校验值"三件套全换的路线（这也是 [ReSukiSU](https://github.com/ReSukiSU/ReKernel) 等主流改版的做法）。

## 2. 本次改动清单（身份层 —— 全换）

| 项目 | 上游 | MuSu Next |
| --- | --- | --- |
| 应用显示名 | KernelSU | **MuSu Next** |
| 管理器包名 | `me.weishu.kernelsu` | **`org.musu.next`** |
| PR 构建包名 | `me.weishu.kernelsu.pr` | `org.musu.next.pr` |
| AIDL 接口包 | `me.weishu.kernelsu` | `org.musu.next` |
| JNI 函数名 | `Java_me_weishu_kernelsu_*` | `Java_org_musu_next_*` |
| Application 类 | `KernelSUApplication` | `MuSuNextApplication` |
| 主题资源名 | `Theme.KernelSU` | `Theme.MuSuNext` |
| 签名证书 | CN=weishu（`c371061b…`） | **CN=MuSu Next（`eeed9504…`）** |
| 内核校验 HASH | `c371061b…2d6` | **`eeed9504…ead0`** |
| 内核校验 SIZE | `0x033b` | **`0x0378`** |
| 仓库 / 外链 | tiann/KernelSU | bilibiliHaoziyao/MuSuNext |
| 关于页 | Telegram 频道 | 新增 **B 站主页** 跳转 |

### 新签名证书指纹

```
Owner   : CN=MuSu Next, OU=MuSu, O=MuSu Next, L=Changsha, ST=Hunan, C=CN
SHA256  : EE:ED:95:04:AC:EE:15:43:F8:4E:57:91:6E:F5:0F:88:B6:9B:C9:AB:BC:72:FC:80:F8:03:A4:22:67:A5:EA:D0
DER Size: 888 bytes (0x0378)
```

## 3. 保留不动的部分（兼容层 —— 契约）

以下都是与内核、ksud、模块生态通信的"命脉"，参照上游和 ReSukiSU 的做法**一律不改**，以保证兼容上游所支持的全部内核：

- `/data/adb/ksu/` 数据路径、`/data/adb/modules/` 模块路径
- `ksud`、`libksud.so`、metamodule 机制
- `KSU_*` 内核符号、supercall IOCTL 命令号、uapi 头文件
- 内核文件名 `kernelsu.ko`、`Kconfig` 选项 `CONFIG_KSU_*`
- 上游公共服务地址：`modules.kernelsu.org`（模块仓库）、`mui.kernelsu.org`（WebUI）、`kernelsu.org/templates`（模板）
- Cargo.lock 中 `KernelSU2/*` 第三方依赖组织名

> 说明：`KSU_PACKAGE_NAME` 这个 **property 名**保留（只改其默认值），是因为内核编译脚本、ksud 构建脚本、CI 工作流都引用它；改名只会徒增破坏面，不影响品牌。

## 4. ⚠️ 内核必须配套编译（重要）

**MuSu Next 管理器不能搭配官方 KernelSU 内核使用**（签名哈希不匹配）。你有两种方式让内核识别 MuSu Next 管理器：

### 方式 A：LKM 模块（推荐，需要 GKI 设备）

内核侧 `kernel/Kbuild` 已写入新签名值，直接用本项目源码编译 LKM 即可：

```bash
cd kernel
# 需要配置 KDIR（内核源码）和 CROSS_COMPILE / CLANG_TRIPLE
make -f Kbuild \
  KDIR=/path/to/your/kernel/source \
  KSU_EXPECTED_HASH=eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0 \
  KSU_EXPECTED_SIZE=0x0378 \
  KSU_PACKAGE_NAME=org.musu.next
```

CI 工作流 `build-lkm.yml` / `ddk-lkm.yml` 已内置这些值，可直接触发云端构建。

### 方式 B：集成进内核源码

把 `kernel/` 目录按上游文档 instructions 集成进你的内核源码树，编译出的 `kernelsu.ko` 会自带新的包名与签名校验值。

### 签名私钥

发布签名keystore 位于 `signing/musu-next.jks`（**已被 .gitignore 忽略，不会进仓库**）。请自行妥善保管；如需换签名，务必**同步更新** `kernel/Kbuild` 的两个校验值，否则内核将拒绝管理器。

```bash
# 计算新证书的校验值（换签名后用）
keytool -exportcert -alias musu -keystore signing/musu-next.jks -storepass <密码> -file /tmp/cert.der
python3 -c "import hashlib;d=open('/tmp/cert.der','rb').read();print('HASH:',hashlib.sha256(d).hexdigest());print('SIZE:',hex(len(d)))"
```

## 5. 开源协议（沿用上游双协议，未更改）

- `kernel/` 目录下所有文件：**GPL-2.0-only**
- 其余部分：**GPL-3.0-or-later**

MuSu Next 遵守上游协议继续开源，未变更许可证类型。
