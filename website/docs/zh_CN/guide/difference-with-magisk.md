# MuSu Next 模块与 Magisk 的差异 {#title}

虽然 MuSu Next 模块与 Magisk 模块有很多相似之处，但由于它们的实现机制完全不同，因此不可避免地会有一些差异；如果你希望你的模块能同时在 Magisk 与 MuSu Next 中运行，那么你必须了解这些差异。

## 相同之处 {#similarities}

- 模块文件格式: 都以 zip 的方式组织模块，并且模块的格式几乎相同
- 模块安装目录: 都在 `/data/adb/modules`
- systemless: 都支持通过模块的形式以 systemless 修改 /system
- `post-fs-data.sh`: 执行时机完全一致，语义也完全一致
- `service.sh`: 执行时机完全一致，语义也完全一致
- `system.prop`: 完全相同
- `sepolicy.rule`: 完全相同
- BusyBox：脚本都在 BusyBox 中以“独立模式”运行

## 不同之处 {#differences}

在了解不同之处之前，你需要知道如何区分你的模块是运行在 MuSu Next 还是运行在 Magisk 之中；在所有你可以运行模块脚本的地方（`customize.sh`, `post-fs-data.sh`, `service.sh`)，你都可以通过环境变量`KSU` 来区分，在 MuSu Next 中，这个环境变量将被设置为 `true`。

以下是一些不同之处：

1. MuSu Next 的模块不支持在 Recovery 中安装。
2. MuSu Next 已内建 [MuZygisk](../features/muzygisk.md)（源自 ReZygisk），默认禁用，在「设置 → MuZygisk」中开启后即可使用 Zygisk 模块；也可使用第三方实现 [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext)，二者互斥。
3. **模块挂载架构**：MuSu Next 使用 [metamodule 系统](metamodule.md)，挂载由元模块负责（内建的 [MuMount](../features/mumount.md)，基于 Hybrid Mount，或第三方元模块如 `meta-overlayfs`），而 Magisk 将挂载内置在其核心中。MuSu Next 已内建元模块，默认禁用，在「设置 → MuMount」中开启；也可安装第三方元模块，二者互斥。
4. MuSu Next 模块替换或者删除文件与 Magisk 完全不同。MuSu Next 不支持 `.replace` 方式，相反，你需要通过 `mknod filename c 0 0` 创建同名文件夹来删除对应文件。
5. BusyBox 的目录不同；MuSu Next 内置的 BusyBox 在 `/data/adb/ksu/bin/busybox` 而 Magisk 在 `/data/adb/magisk/busybox`；**注意此为 MuSu Next 内部行为，未来可能会更改！**
6. MuSu Next 不支持 `.replace` 文件；但 MuSu Next 支持 `REPLACE` 和 `REMOVE` 变量。
7. MuSu Next 新增了一种脚本 `boot-completed.sh`，以便在 Android 系统启动后运行某些任务。
8. MuSu Next 新增了一种脚本 `post-mount.sh`，以便在模块挂载完成后运行某些任务。
