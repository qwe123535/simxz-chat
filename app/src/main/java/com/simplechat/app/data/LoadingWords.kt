package com.simplechat.app.data

/**
 * 把用户输入的一行等待文案洗成能直接显示的形态。
 *
 * 省略号由等待动画自己补（三点逐秒出现），所以这里要**去掉**用户自己带的
 * `.` / `…` / 空白 —— 否则会看到「思考中…....」。
 */
internal fun normalizeLoadingWord(raw: String): String =
    raw.replace(Regex("[\\r\\n]+"), " ")
        .trim()
        .trimEnd { it.isWhitespace() || it == '.' || it == '…' }
