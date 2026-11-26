import { createRequire } from 'module'
import { defineConfig } from 'vitepress'

const require = createRequire(import.meta.url)
const pkg = require('vitepress/package.json')

export default defineConfig({
  lang: 'zh-CN',
  description: '一个基于内核，为安卓 GKI 准备的 root 方案。',

  themeConfig: {
    nav: nav(),

    lastUpdatedText: '最后更新',

    sidebar: {
      '/zh_CN/guide/': sidebarGuide(),
      '/zh_CN/features/': sidebarFeatures()
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/bilibiliHaoziyao/MuSuNext' }
    ],

    footer: {
        message: '在 GPL3 许可证下发布。',
        copyright: 'Copyright © 2022-现在 MuSu Next 开发者。'
    },

    editLink: {
        pattern: 'https://github.com/bilibiliHaoziyao/MuSuNext/edit/main/website/docs/:path',
        text: '在 GitHub 中编辑本页'
    }
  }
})

function nav() {
  return [
    { text: '特色功能', link: '/zh_CN/features/root-access-audit' },
    { text: '指南', link: '/zh_CN/guide/what-is-kernelsu' },
    {
      text: '工具',
      items: [
        { text: '内核模块匹配', link: '/zh_CN/kmi' },
        { text: '签名校验', link: '/zh_CN/verify' },
      ],
    },
  ]
}

function sidebarGuide() {
  return [
    {
        text: 'Guide',
        items: [
          { text: '什么是 MuSu Next？', link: '/zh_CN/guide/what-is-kernelsu' },
          { text: 'MuSu Next 模块与 Magisk 的差异', link: '/zh_CN/guide/difference-with-magisk' },
          { text: '安装', link: '/zh_CN/guide/installation' },
          { text: '如何构建？', link: '/zh_CN/guide/how-to-build' },
          { text: '非官方支持设备', link: '/zh_CN/guide/unofficially-support-devices.md' },
          { text: '模块开发指南', link: '/zh_CN/guide/module.md' },
          { text: '元模块', link: '/zh_CN/guide/metamodule.md' },
          { text: '模块 Web 界面', link: '/zh_CN/guide/module-webui.md' },
          { text: '模块配置', link: '/zh_CN/guide/module-config.md' },
          { text: '一次性脚本', link: '/zh_CN/guide/module-script.md' },
          { text: 'App Profile', link: '/zh_CN/guide/app-profile.md' },
          { text: '救砖', link: '/zh_CN/guide/rescue-from-bootloop.md' },
          { text: 'x86_64 支持', link: '/zh_CN/guide/x86_64-support' },
          { text: '常见问题', link: '/zh_CN/guide/faq' },
          { text: '隐藏功能', link: '/zh_CN/guide/hidden-features' },
        ]
    }
  ]
}


function sidebarFeatures() {
  return [
    {
      text: '特色功能',
      items: [
        { text: 'Root 访问审计', link: '/zh_CN/features/root-access-audit' },
        { text: 'MuZygisk（内建 Zygisk）', link: '/zh_CN/features/muzygisk' },
        { text: 'MuMount（内建元模块）', link: '/zh_CN/features/mumount' },
        { text: '快照备份与恢复', link: '/zh_CN/features/snapshot-backup' },
        { text: '环境检测', link: '/zh_CN/features/environment-check' },
        { text: '内置 DirtyFrag 一键越狱', link: '/zh_CN/features/dirtyfrag-jailbreak' },
      ]
    }
  ]
}
