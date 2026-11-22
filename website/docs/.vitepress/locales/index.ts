import { defineConfig } from 'vitepress'
import en from './en'
import zh_CN from './zh_CN'
import zh_TW from './zh_TW'
import ja_JP from './ja_JP'

export default defineConfig({
  locales: {
    root: {
      label: 'English',
      lang: en.lang,
      themeConfig: en.themeConfig,
      description: en.description
    },
    zh_CN: {
      label: '简体中文',
      lang: zh_CN.lang,
      themeConfig: zh_CN.themeConfig,
      description: zh_CN.description
    },
    zh_TW: {
      label: '繁體中文',
      lang: zh_TW.lang,
      themeConfig: zh_TW.themeConfig,
      description: zh_TW.description
    },
    ja_JP: {
      label: '日本語',
      lang: ja_JP.lang,
      themeConfig: ja_JP.themeConfig,
      description: ja_JP.description
    }
  }
})
