import type { PageData } from 'vitepress'

export const SITE_URL = 'https://su.novasync.cn/docs'

// Describes the project for search engines and AI agents. SoftwareSourceCode is used
// instead of SoftwareApplication, which Google flags as invalid without ratings.
const projectJsonLd = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'SoftwareSourceCode',
    name: 'MuSu Next',
    description: 'An open-source, kernel-based root solution for Android GKI devices, running as a loadable kernel module.',
    url: `${SITE_URL}/`,
    image: `${SITE_URL}/logo.png`,
    codeRepository: 'https://github.com/bilibiliHaoziyao/MuSuNext',
    license: ['https://www.gnu.org/licenses/gpl-2.0.html', 'https://www.gnu.org/licenses/gpl-3.0.html'],
    programmingLanguage: ['C', 'Rust', 'Kotlin'],
    runtimePlatform: 'Android',
    sameAs: ['https://github.com/bilibiliHaoziyao/MuSuNext', 'https://b23.tv/6UtmCB0'],
})

export function transformPageData(pageData: PageData) {
    if (pageData.frontmatter.layout === 'home') {
        pageData.frontmatter.head = [
            ...(pageData.frontmatter.head ?? []),
            ['script', { type: 'application/ld+json' }, projectJsonLd],
        ]
    }
    // Page descriptions in frontmatter only feed llms.txt for now. Keep the site-wide
    // meta description so existing search snippets stay unchanged.
    return { description: '' }
}
