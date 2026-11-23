<script setup lang="ts">
import { useData } from 'vitepress'

const { lang } = useData()

// 按界面语言切换文案，缺省英文
const copy = {
  en: {
    sourceTitle: 'Source code',
    sourceDesc: 'MuSu Next is fully open source. Read the code, follow progress, or open an issue on GitHub.',
    sourceLink: 'View on GitHub',
    creditsTitle: 'Credits',
    creditsDesc: 'MuSu Next is built upon the excellent KernelSU project. Thanks to the upstream team and community.',
    creditsLink: 'KernelSU on GitHub',
  },
  'zh-CN': {
    sourceTitle: '源码',
    sourceDesc: 'MuSu Next 完全开源，欢迎在 GitHub 阅读源码、跟进进度或提交问题。',
    sourceLink: '前往 GitHub',
    creditsTitle: '致谢',
    creditsDesc: 'MuSu Next 基于优秀的 KernelSU 项目发展而来，感谢上游团队与社区。',
    creditsLink: '访问 KernelSU',
  },
  'zh-TW': {
    sourceTitle: '原始碼',
    sourceDesc: 'MuSu Next 完全開源，歡迎在 GitHub 閱讀原始碼、追蹤進度或回報問題。',
    sourceLink: '前往 GitHub',
    creditsTitle: '致謝',
    creditsDesc: 'MuSu Next 基於優秀的 KernelSU 專案發展而來，感謝上游團隊與社群。',
    creditsLink: '造訪 KernelSU',
  },
  ja: {
    sourceTitle: 'ソースコード',
    sourceDesc: 'MuSu Next は完全にオープンソースです。GitHub でコードを読み、進捗を追い、問題を報告できます。',
    sourceLink: 'GitHub を見る',
    creditsTitle: '謝辞',
    creditsDesc: 'MuSu Next は優れた KernelSU プロジェクトを基に開発されています。上流チームとコミュニティに感謝します。',
    creditsLink: 'KernelSU を見る',
  },
}

const t = () => copy[lang.value] || copy.en
</script>

<template>
  <div class="home-links-wrap">
    <div class="home-links">
      <section id="source" class="home-link-card">
        <h2>{{ t().sourceTitle }}</h2>
        <p>{{ t().sourceDesc }}</p>
        <a href="https://github.com/bilibiliHaoziyao/MuSuNext" target="_blank" rel="noopener">
          {{ t().sourceLink }} <span aria-hidden="true">→</span>
        </a>
      </section>
      <section id="credits" class="home-link-card">
        <h2>{{ t().creditsTitle }}</h2>
        <p>{{ t().creditsDesc }}</p>
        <a href="https://github.com/tiann/KernelSU" target="_blank" rel="noopener">
          {{ t().creditsLink }} <span aria-hidden="true">→</span>
        </a>
      </section>
    </div>
  </div>
</template>
