<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  lang: { type: String, default: 'zh_CN' },
})

const KMI_LIST = [
  { kmi: 'android12-5.10', kernel: '5.10', android: '12' },
  { kmi: 'android13-5.10', kernel: '5.10', android: '13' },
  { kmi: 'android13-5.15', kernel: '5.15', android: '13' },
  { kmi: 'android14-5.15', kernel: '5.15', android: '14' },
  { kmi: 'android14-6.1', kernel: '6.1', android: '14' },
  { kmi: 'android15-6.6', kernel: '6.6', android: '15' },
  { kmi: 'android16-6.12', kernel: '6.12', android: '16' },
  { kmi: 'android17-6.18', kernel: '6.18', android: '17' },
]

const T = {
  zh_CN: {
    label: '把 uname -r 的输出粘在这里',
    placeholder: '例如 5.10.198-android12-9-g1234abcd',
    or: '或者手动选',
    androidLabel: 'Android 版本',
    kernelLabel: '内核版本',
    pick: '请选择',
    check: '查询',
    empty: '先填点东西再查。',
    hit: '这个内核可以用',
    koLabel: '下载对应模块',
    archHint: '手机装 aarch64 那个，模拟器和 x86 设备用 x86_64。',
    miss: '没对上',
    missBody: '这个内核版本不在预编译列表里。你仍然可以自己用源码编译一份，或者到 QQ 频道里问一下。',
    parseFail: '没从这段文字里认出内核版本，试试只填版本号，比如 5.10.198。',
    tableTitle: '预编译覆盖的内核',
    thAndroid: 'Android',
    thKernel: '内核',
    thKmi: '对应模块',
  },
  zh_TW: {
    label: '把 uname -r 的輸出貼在這裡',
    placeholder: '例如 5.10.198-android12-9-g1234abcd',
    or: '或者手動選',
    androidLabel: 'Android 版本',
    kernelLabel: '內核版本',
    pick: '請選擇',
    check: '查詢',
    empty: '先填點東西再查。',
    hit: '這個內核可以用',
    koLabel: '下載對應模組',
    archHint: '手機裝 aarch64 那個，模擬器和 x86 裝置用 x86_64。',
    miss: '沒對上',
    missBody: '這個內核版本不在預編譯清單裡。你仍然可以自己用原始碼編譯一份，或者到 QQ 頻道裡問一下。',
    parseFail: '沒從這段文字裡認出內核版本，試試只填版本號，比如 5.10.198。',
    tableTitle: '預編譯覆蓋的內核',
    thAndroid: 'Android',
    thKernel: '內核',
    thKmi: '對應模組',
  },
  en: {
    label: 'Paste the output of uname -r',
    placeholder: 'e.g. 5.10.198-android12-9-g1234abcd',
    or: 'Or pick manually',
    androidLabel: 'Android version',
    kernelLabel: 'Kernel version',
    pick: 'Select',
    check: 'Check',
    empty: 'Fill something in first.',
    hit: 'This kernel is covered',
    koLabel: 'Download the matching module',
    archHint: 'Use the aarch64 build on phones; x86_64 for emulators and x86 devices.',
    miss: 'No match',
    missBody: 'This kernel is not in the prebuilt list. You can still build one from source, or ask in the QQ channel.',
    parseFail: 'Could not read a kernel version from that text. Try just the version, e.g. 5.10.198.',
    tableTitle: 'Kernels covered by prebuilt modules',
    thAndroid: 'Android',
    thKernel: 'Kernel',
    thKmi: 'Module',
  },
  ja_JP: {
    label: 'uname -r の出力を貼り付けてください',
    placeholder: '例: 5.10.198-android12-9-g1234abcd',
    or: 'または手動で選択',
    androidLabel: 'Android バージョン',
    kernelLabel: 'カーネルバージョン',
    pick: '選択',
    check: '調べる',
    empty: 'まず何か入力してください。',
    hit: 'このカーネルは対応しています',
    koLabel: '対応モジュールをダウンロード',
    archHint: 'スマートフォンは aarch64、エミュレータや x86 端末は x86_64 を使ってください。',
    miss: '一致しません',
    missBody: 'このカーネルはプリビルドの対象外です。ソースからビルドするか、QQ チャンネルで聞いてください。',
    parseFail: 'カーネルバージョンを読み取れませんでした。5.10.198 のようにバージョンだけを入れてみてください。',
    tableTitle: 'プリビルドが対応するカーネル',
    thAndroid: 'Android',
    thKernel: 'カーネル',
    thKmi: 'モジュール',
  },
}

const t = computed(() => T[props.lang] || T.zh_CN)

const raw = ref('')
const android = ref('')
const kernel = ref('')
const result = ref(null)

const androidOptions = [...new Set(KMI_LIST.map((k) => k.android))]

function pickKernelVersion(text) {
  const s = text.trim()
  if (!s) return null
  // 优先取 uname -r 里常见的 androidNN-x.y 片段
  const androidTag = s.match(/android(\d+)[-_](\d+\.\d+)/i)
  if (androidTag) {
    const kmi = `android${androidTag[1]}-${androidTag[2]}`
    const found = KMI_LIST.find((k) => k.kmi === kmi)
    if (found) return found
    return { kmi, kernel: androidTag[2], android: androidTag[1], unknown: true }
  }
  const ver = s.match(/(\d+\.\d+)(\.\d+)?/)
  if (!ver) return null
  return { kernel: ver[1] }
}

function check() {
  const text = raw.value || `${kernel.value}`
  if (!text.trim() && !android.value && !kernel.value) {
    result.value = { type: 'empty' }
    return
  }

  const parsed = pickKernelVersion(text)
  if (!parsed) {
    result.value = { type: 'parsefail' }
    return
  }

  if (parsed.kmi && !parsed.unknown) {
    result.value = { type: 'hit', item: parsed }
    return
  }

  const kernelVer = parsed.kernel
  const androidVer = android.value || parsed.android || ''
  const candidates = KMI_LIST.filter((k) => k.kernel === kernelVer)
  if (candidates.length === 0) {
    result.value = { type: 'miss', kernel: kernelVer, android: androidVer }
    return
  }
  if (androidVer) {
    const exact = candidates.find((k) => k.android === androidVer)
    if (exact) {
      result.value = { type: 'hit', item: exact }
      return
    }
  }
  if (candidates.length === 1) {
    result.value = { type: 'hit', item: candidates[0] }
    return
  }
  result.value = { type: 'ambiguous', candidates, kernel: kernelVer }
}

function koUrl(kmi, arch) {
  return `https://github.com/bilibiliHaoziyao/MuSuNext/releases/latest/download/lkm-${arch}-${kmi}_musu.ko`
}
</script>

<template>
  <div class="kmi-tool">
    <label class="kmi-label">{{ t.label }}</label>
    <input v-model="raw" class="kmi-input" type="text" :placeholder="t.placeholder" />

    <p class="kmi-or">{{ t.or }}</p>

    <div class="kmi-row">
      <div class="kmi-field">
        <label class="kmi-label">{{ t.androidLabel }}</label>
        <select v-model="android" class="kmi-input">
          <option value="">{{ t.pick }}</option>
          <option v-for="a in androidOptions" :key="a" :value="a">{{ a }}</option>
        </select>
      </div>
      <div class="kmi-field">
        <label class="kmi-label">{{ t.kernelLabel }}</label>
        <input v-model="kernel" class="kmi-input" type="text" placeholder="5.10 / 6.1 / 6.6" />
      </div>
    </div>

    <button class="kmi-btn" type="button" @click="check">{{ t.check }}</button>

    <div v-if="result" class="kmi-result">
      <template v-if="result.type === 'empty'">
        <p class="kmi-note">{{ t.empty }}</p>
      </template>

      <template v-else-if="result.type === 'parsefail'">
        <p class="kmi-note kmi-warn">{{ t.parseFail }}</p>
      </template>

      <template v-else-if="result.type === 'hit'">
        <p class="kmi-ok">{{ t.hit }} · {{ result.item.kmi }}</p>
        <p class="kmi-sub">{{ t.koLabel }}</p>
        <ul class="kmi-dl">
          <li>
            <a :href="koUrl(result.item.kmi, 'aarch64')">lkm-aarch64-{{ result.item.kmi }}_musu.ko</a>
          </li>
          <li>
            <a :href="koUrl(result.item.kmi, 'x86_64')">lkm-x86_64-{{ result.item.kmi }}_musu.ko</a>
          </li>
        </ul>
        <p class="kmi-note">{{ t.archHint }}</p>
      </template>

      <template v-else-if="result.type === 'ambiguous'">
        <p class="kmi-sub">{{ t.miss }}</p>
        <ul class="kmi-dl">
          <li v-for="c in result.candidates" :key="c.kmi">
            <a :href="koUrl(c.kmi, 'aarch64')">lkm-aarch64-{{ c.kmi }}_musu.ko</a>
          </li>
        </ul>
      </template>

      <template v-else>
        <p class="kmi-warn">{{ t.miss }}</p>
        <p class="kmi-note">{{ t.missBody }}</p>
      </template>
    </div>

    <h3 class="kmi-table-title">{{ t.tableTitle }}</h3>
    <table class="kmi-table">
      <thead>
        <tr>
          <th>{{ t.thAndroid }}</th>
          <th>{{ t.thKernel }}</th>
          <th>{{ t.thKmi }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="k in KMI_LIST" :key="k.kmi">
          <td>{{ k.android }}</td>
          <td>{{ k.kernel }}</td>
          <td><code>{{ k.kmi }}</code></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.kmi-tool {
  margin: 24px 0;
}
.kmi-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--vp-c-text-1);
}
.kmi-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 15px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-1);
  font-family: var(--vp-font-family-mono);
}
.kmi-input:focus {
  outline: 2px solid var(--vp-c-brand-1);
  outline-offset: -1px;
}
.kmi-or {
  font-size: 13px;
  color: var(--vp-c-text-3);
  margin: 18px 0 10px;
}
.kmi-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.kmi-field {
  min-width: 0;
}
.kmi-btn {
  margin-top: 16px;
  padding: 9px 22px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  border: 1px solid var(--vp-c-brand-1);
  background: var(--vp-c-brand-1);
  color: #fff;
  cursor: pointer;
}
.kmi-btn:hover {
  background: var(--vp-c-brand-2);
  border-color: var(--vp-c-brand-2);
}
.kmi-result {
  margin-top: 20px;
  padding: 18px 20px;
  border-radius: 10px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}
.kmi-ok {
  font-weight: 700;
  font-size: 16px;
  color: var(--vp-c-brand-1);
  margin: 0 0 10px;
}
.kmi-warn {
  font-weight: 600;
  color: var(--vp-c-warning-1);
  margin: 0 0 8px;
}
.kmi-sub {
  font-size: 14px;
  font-weight: 600;
  margin: 8px 0 6px;
}
.kmi-note {
  font-size: 13px;
  color: var(--vp-c-text-2);
  margin: 8px 0 0;
  line-height: 1.6;
}
.kmi-dl {
  margin: 0;
  padding-left: 20px;
  font-size: 14px;
}
.kmi-dl li {
  margin: 4px 0;
}
.kmi-table-title {
  font-size: 15px;
  margin: 32px 0 10px;
  border: 0;
  padding: 0;
}
.kmi-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.kmi-table th,
.kmi-table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--vp-c-divider);
}
.kmi-table th {
  font-weight: 600;
  color: var(--vp-c-text-2);
  font-size: 13px;
}
@media (max-width: 640px) {
  .kmi-row {
    grid-template-columns: 1fr;
  }
}
</style>
