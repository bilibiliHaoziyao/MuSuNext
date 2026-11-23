import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import HomeLinks from './HomeLinks.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout: () => {
    return h(DefaultTheme.Layout, null, {
      'home-features-after': () => h(HomeLinks),
    })
  },
}
