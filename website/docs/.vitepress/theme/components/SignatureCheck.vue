<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  lang: { type: String, default: 'zh_CN' },
})

const EXPECTED = 'eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0'

const T = {
  zh_CN: {
    drop: '把 APK 拖到这里，或者点击选择文件',
    chosen: '已选择',
    checking: '正在读签名…',
    official: '官方正版',
    officialBody: '签名指纹和 MuSu Next 的发布签名一致，这个包没被改过。',
    forged: '签名对不上',
    forgedBody: '这个包的签名和官方不一致。它可能被人重新打包过，别装。',
    noSig: '读不出签名',
    noSigBody: '没在这个文件里找到 APK 签名块，它可能不是正常的 APK，或者用了很旧的签名方式。',
    notZip: '不是 APK',
    notZipBody: '这个文件不是有效的 APK（压缩包结构都不对）。可以试试管理器 APK 或某个 .ko 模块文件。',
    needHttps: '浏览器不支持',
    needHttpsBody: '当前环境拿不到加密接口，换 https 或新版浏览器再试。',
    fp: '签名指纹 SHA-256',
    expectedLabel: '官方指纹',
    again: '换个文件',
    signerIdx: '签名 #',
  },
  zh_TW: {
    drop: '把 APK 拖到這裡，或者點擊選擇檔案',
    chosen: '已選擇',
    checking: '正在讀簽章…',
    official: '官方正版',
    officialBody: '簽章指紋和 MuSu Next 的發佈簽章一致，這個包沒被改過。',
    forged: '簽章不符',
    forgedBody: '這個包的簽章和官方不一致。它可能被人重新打包過，別裝。',
    noSig: '讀不出簽章',
    noSigBody: '沒在這個檔案裡找到 APK 簽章區塊，它可能不是正常的 APK，或者用了很舊的簽章方式。',
    notZip: '不是 APK',
    notZipBody: '這個檔案不是有效的 APK（壓縮包結構都不對）。可以試試管理器 APK 或某個 .ko 模組檔案。',
    needHttps: '瀏覽器不支援',
    needHttpsBody: '目前環境拿不到加密介面，換 https 或新版瀏覽器再試。',
    fp: '簽章指紋 SHA-256',
    expectedLabel: '官方指紋',
    again: '換個檔案',
    signerIdx: '簽章 #',
  },
  en: {
    drop: 'Drop an APK here, or click to choose a file',
    chosen: 'Selected',
    checking: 'Reading signature…',
    official: 'Genuine build',
    officialBody: 'The certificate fingerprint matches the MuSu Next release signing key. This file has not been altered.',
    forged: 'Signature mismatch',
    forgedBody: 'This file is not signed with the official key. It may have been repackaged — do not install it.',
    noSig: 'No signature found',
    noSigBody: 'No APK signing block in this file. It may not be a normal APK, or it uses a very old signing scheme.',
    notZip: 'Not an APK',
    notZipBody: 'This file is not a valid APK (the archive structure is wrong). Try the manager APK or a .ko module.',
    needHttps: 'Browser unsupported',
    needHttpsBody: 'The crypto API is unavailable here. Try https or a newer browser.',
    fp: 'Fingerprint SHA-256',
    expectedLabel: 'Official fingerprint',
    again: 'Choose another file',
    signerIdx: 'Signer #',
  },
  ja_JP: {
    drop: 'APK をここにドロップ、またはクリックして選択',
    chosen: '選択済み',
    checking: '署名を読み取り中…',
    official: '正規ビルド',
    officialBody: '証明書の指紋が MuSu Next のリリース署名と一致しました。改変されていません。',
    forged: '署名が一致しません',
    forgedBody: 'このファイルは公式鍵で署名されていません。再パッケージされた可能性があります。インストールしないでください。',
    noSig: '署名が見つかりません',
    noSigBody: 'APK 署名ブロックが見つかりませんでした。通常の APK でないか、非常に古い署名方式の可能性があります。',
    notZip: 'APK ではありません',
    notZipBody: '有効な APK ではありません（アーカイブ構造が不正）。マネージャーの APK か .ko モジュールを試してください。',
    needHttps: 'ブラウザ非対応',
    needHttpsBody: '暗号 API が使えません。https か新しいブラウザでお試しください。',
    fp: '指紋 SHA-256',
    expectedLabel: '公式の指紋',
    again: '別のファイルを選ぶ',
    signerIdx: '署名 #',
  },
}

const t = computed(() => T[props.lang] || T.zh_CN)

const fileName = ref('')
const status = ref(null) // {type, hashes}
const busy = ref(false)
const dragging = ref(false)
const inputRef = ref(null)

function toHex(buf) {
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

function readU64(dv, off) {
  const lo = dv.getUint32(off, true)
  const hi = dv.getUint32(off + 4, true)
  return hi * 4294967296 + lo
}

function findEocd(dv) {
  const len = dv.byteLength
  const start = Math.max(0, len - 65557)
  for (let i = len - 22; i >= start; i--) {
    if (dv.getUint32(i, true) === 0x06054b50) return i
  }
  return -1
}

function collectCerts(dv, blockStart, blockEnd) {
  const hashes = []
  let p = blockStart + 8
  const pairsEnd = blockEnd - 24
  while (p + 12 <= pairsEnd) {
    const plen = readU64(dv, p)
    const pid = dv.getUint32(p + 8, true)
    if (!plen || p + 8 + plen > pairsEnd + 12) break
    // v2 = 0x7109871a, v3 = 0xf05368c0, v3.1 = 0x1b93ad61
    if (pid === 0x7109871a || pid === 0xf05368c0 || pid === 0x1b93ad61) {
      // v2/v3 value: [signersLen][signer...]
      const vStart = p + 12
      const vEnd = vStart + (plen - 4)
      const signersLen = dv.getUint32(vStart, true)
      let pos = vStart + 4
      const signersEnd = Math.min(pos + signersLen, vEnd)
      while (pos + 4 <= signersEnd) {
        const signerLen = dv.getUint32(pos, true)
        const s = pos + 4
        const se = s + signerLen
        if (!signerLen || se > vEnd) break
        // signer: [signedDataLen][signedData][sigsLen][sigs][pkLen][pk]
        const sdLen = dv.getUint32(s, true)
        const sdStart = s + 4
        const sdEnd = sdStart + sdLen
        if (sdLen && sdEnd <= se) {
          let q = sdStart
          if (q + 4 <= sdEnd) {
            const digestsLen = dv.getUint32(q, true)
            q += 4 + digestsLen
          }
          if (q + 4 <= sdEnd) {
            const certsLen = dv.getUint32(q, true)
            q += 4
            const certsEnd = Math.min(q + certsLen, sdEnd)
            while (q + 4 <= certsEnd) {
              const clen = dv.getUint32(q, true)
              q += 4
              if (!clen || q + clen > certsEnd) break
              hashes.push(new Uint8Array(dv.buffer, dv.byteOffset + q, clen))
              q += clen
            }
          }
        }
        pos = se
      }
    }
    p += 8 + plen
  }
  return hashes
}

async function handleFile(file) {
  fileName.value = file.name
  busy.value = true
  status.value = null
  try {
    const buf = await file.arrayBuffer()
    const dv = new DataView(buf)

    if (buf.byteLength < 32 || dv.getUint16(0, true) !== 0x4b50) {
      status.value = { type: 'notzip' }
      busy.value = false
      return
    }

    const eocd = findEocd(dv)
    if (eocd < 0) {
      status.value = { type: 'notzip' }
      busy.value = false
      return
    }
    const cdOffset = dv.getUint32(eocd + 16, true)
    const magic = 'APK Sig Block 42'

    let sigEnd = -1
    if (cdOffset - 16 >= 0 && cdOffset - 16 < buf.byteLength) {
      let ok = true
      for (let i = 0; i < 16; i++) {
        if (dv.getUint8(cdOffset - 16 + i) !== magic.charCodeAt(i)) {
          ok = false
          break
        }
      }
      if (ok) sigEnd = cdOffset
    }

    if (sigEnd < 0) {
      status.value = { type: 'nosig' }
      busy.value = false
      return
    }

    const size = readU64(dv, sigEnd - 24)
    const blockStart = sigEnd - 8 - size
    if (blockStart < 0 || blockStart >= sigEnd) {
      status.value = { type: 'nosig' }
      busy.value = false
      return
    }

    const certs = collectCerts(dv, blockStart, sigEnd)
    if (certs.length === 0) {
      status.value = { type: 'nosig' }
      busy.value = false
      return
    }

    if (!globalThis.crypto?.subtle) {
      status.value = { type: 'needhttps' }
      busy.value = false
      return
    }

    const hashes = []
    for (const cert of certs) {
      const digest = await crypto.subtle.digest('SHA-256', cert)
      hashes.push(toHex(digest))
    }

    const ok = hashes.includes(EXPECTED)
    status.value = { type: ok ? 'official' : 'forged', hashes }
  } catch (e) {
    status.value = { type: 'nosig' }
  } finally {
    busy.value = false
  }
}

function onPick(e) {
  const f = e.target.files?.[0]
  if (f) handleFile(f)
}
function onDrop(e) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) handleFile(f)
}
function reset() {
  fileName.value = ''
  status.value = null
  if (inputRef.value) inputRef.value.value = ''
}
</script>

<template>
  <div class="sig-tool">
    <div
      v-if="!fileName"
      class="sig-drop"
      :class="{ 'sig-drop-on': dragging }"
      @click="inputRef?.click()"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 16V4m0 0L7 9m5-5 5 5" />
        <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
      </svg>
      <p>{{ t.drop }}</p>
      <input ref="inputRef" type="file" accept=".apk,.ko,application/vnd.android.package-archive" hidden @change="onPick" />
    </div>

    <div v-else class="sig-file">
      <span class="sig-name">{{ t.chosen }}: {{ fileName }}</span>
      <button class="sig-again" type="button" @click="reset">{{ t.again }}</button>
    </div>

    <p v-if="busy" class="sig-note">{{ t.checking }}</p>

    <div v-if="status" class="sig-result" :class="'sig-' + status.type">
      <template v-if="status.type === 'official'">
        <p class="sig-head sig-head-ok">{{ t.official }}</p>
        <p class="sig-body">{{ t.officialBody }}</p>
        <p class="sig-kv">{{ t.fp }}</p>
        <code v-for="(h, i) in status.hashes" :key="i" class="sig-hash">{{ h }}</code>
      </template>

      <template v-else-if="status.type === 'forged'">
        <p class="sig-head sig-head-bad">{{ t.forged }}</p>
        <p class="sig-body">{{ t.forgedBody }}</p>
        <p class="sig-kv">{{ t.fp }}</p>
        <code v-for="(h, i) in status.hashes" :key="i" class="sig-hash">{{ h }}</code>
        <p class="sig-kv">{{ t.expectedLabel }}</p>
        <code class="sig-hash sig-hash-exp">{{ EXPECTED }}</code>
      </template>

      <template v-else-if="status.type === 'nosig'">
        <p class="sig-head sig-head-warn">{{ t.noSig }}</p>
        <p class="sig-body">{{ t.noSigBody }}</p>
      </template>

      <template v-else-if="status.type === 'notzip'">
        <p class="sig-head sig-head-warn">{{ t.notZip }}</p>
        <p class="sig-body">{{ t.notZipBody }}</p>
      </template>

      <template v-else-if="status.type === 'needhttps'">
        <p class="sig-head sig-head-warn">{{ t.needHttps }}</p>
        <p class="sig-body">{{ t.needHttpsBody }}</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.sig-tool {
  margin: 24px 0;
}
.sig-drop {
  border: 2px dashed var(--vp-c-divider);
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  background: var(--vp-c-bg-soft);
  transition: border-color 0.18s, background 0.18s;
}
.sig-drop:hover,
.sig-drop-on {
  border-color: var(--vp-c-brand-1);
  background: var(--vp-c-brand-soft);
}
.sig-drop svg {
  width: 30px;
  height: 30px;
  margin: 0 auto 12px;
  color: var(--vp-c-text-3);
}
.sig-drop p {
  margin: 0;
  font-size: 15px;
  color: var(--vp-c-text-2);
}
.sig-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 10px;
  background: var(--vp-c-bg-soft);
}
.sig-name {
  font-size: 14px;
  font-family: var(--vp-font-family-mono);
  word-break: break-all;
}
.sig-again {
  font-size: 13px;
  padding: 5px 12px;
  border-radius: 7px;
  border: 1px solid var(--vp-c-divider);
  background: transparent;
  color: var(--vp-c-text-1);
  cursor: pointer;
}
.sig-again:hover {
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
}
.sig-note {
  font-size: 14px;
  color: var(--vp-c-text-2);
  margin-top: 14px;
}
.sig-result {
  margin-top: 18px;
  padding: 18px 20px;
  border-radius: 10px;
  border: 1px solid var(--vp-c-divider);
  background: var(--vp-c-bg-soft);
}
.sig-official {
  border-color: var(--vp-c-green-1);
}
.sig-forged {
  border-color: var(--vp-c-danger-1);
}
.sig-head {
  font-size: 17px;
  font-weight: 700;
  margin: 0 0 8px;
}
.sig-head-ok {
  color: var(--vp-c-green-1);
}
.sig-head-bad {
  color: var(--vp-c-danger-1);
}
.sig-head-warn {
  color: var(--vp-c-warning-1);
}
.sig-body {
  font-size: 14px;
  color: var(--vp-c-text-2);
  margin: 0 0 12px;
  line-height: 1.65;
}
.sig-kv {
  font-size: 12px;
  font-weight: 600;
  color: var(--vp-c-text-3);
  margin: 12px 0 4px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.sig-hash {
  display: block;
  font-size: 13px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--vp-c-bg-alt);
  word-break: break-all;
  font-family: var(--vp-font-family-mono);
  margin-bottom: 6px;
}
.sig-hash-exp {
  opacity: 0.85;
}
</style>
