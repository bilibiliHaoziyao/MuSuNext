package org.musu.next.ui.util

import android.os.Build

object PinyinUtil {

    private val transliterator: android.icu.text.Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
            }.getOrNull()
        } else null
    }

    @android.annotation.SuppressLint("NewApi") // transliterator 仅在 API29+ 创建，低版本为 null 直接返回
    @Synchronized
    fun toPinyin(input: String): String {
        // Android 10 以下没有 ICU Transliterator，降级为原文（搜索仍能按原名匹配）
        val t = transliterator ?: return input
        return t.transliterate(input).replace(" ", "")
    }
}
