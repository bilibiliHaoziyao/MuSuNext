import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import HomeLinks from './HomeLinks.vue'
import KmiLookup from './components/KmiLookup.vue'
import SignatureCheck from './components/SignatureCheck.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout: () => {
    return h(DefaultTheme.Layout, null, {
      'home-features-after': () => h(HomeLinks),
    })
  },
  enhanceApp({ app }) {
    app.component('KmiLookup', KmiLookup)
    app.component('SignatureCheck', SignatureCheck)
  },
}
