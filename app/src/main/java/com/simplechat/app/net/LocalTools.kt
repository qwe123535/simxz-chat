package com.simplechat.app.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 本地工具（function calling）。
 *
 * ### 为什么只有两个
 *
 * 这两个工具**不联网、不要权限、结果确定**：模型调错了最多算错一道题，
 * 不会因为一次幻觉去读用户的相册。搜索类工具需要 API Key 和出网，
 * 文件类工具需要存储权限 —— 那些等这套循环在真机上跑稳了再说。
 *
 * ### 协议
 *
 * OpenAI 兼容的 `tools` 字段：请求里声明，模型回 `tool_calls`，
 * 本地执行后以 `role = "tool"` 回填结果，再发一轮。
 * 只实现了 `type: "function"`，其余类型原样忽略。
 */
object LocalTools {

    const val TIME = "get_current_time"
    const val CALCULATOR = "calculator"

    /** 工具名集合，用来判断模型是不是调了我们认识的工具。 */
    val names: Set<String> = setOf(TIME, CALCULATOR)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 发给服务端的 `tools` 声明。
     *
     * 描述写英文：模型对英文 schema 的理解通常更稳，而工具描述不展示给用户，
     * 不需要翻译。
     */
    val definitions = buildJsonArray {
        add(
            buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TIME)
                    put("description", "Get the current local date and time on the user's phone, including timezone.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {}
                        putJsonArray("required") {}
                    }
                }
            },
        )
        add(
            buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", CALCULATOR)
                    put(
                        "description",
                        "Evaluate an arithmetic expression. Supports + - * / % and parentheses, " +
                            "with decimal numbers. Use this instead of doing arithmetic yourself.",
                    )
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("expression") {
                                put("type", "string")
                                put("description", "The expression to evaluate, e.g. \"(12+3)*4/2\".")
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("expression")) }
                    }
                }
            },
        )
    }

    /**
     * 执行一次调用。
     *
     * **永不抛异常**：工具失败也要以正常结果的形式回给模型（`error: ...`），
     * 让模型自己决定怎么解释 —— 抛出去只会让整轮对话断掉，
     * 而用户看到的是一句莫名其妙的"生成失败"。
     */
    fun execute(name: String, arguments: String?): String = runCatching {
        when (name) {
            TIME -> currentTime()
            CALCULATOR -> {
                val expression = argument(arguments, "expression").orEmpty()
                if (expression.isBlank()) return "error: missing argument 'expression'"
                val value = Calculator.evaluate(expression)
                if (value == null) "error: cannot evaluate \"$expression\"" else formatNumber(value)
            }
            else -> "error: unknown tool \"$name\""
        }
    }.getOrElse { error ->
        "error: ${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
    }

    private fun currentTime(): String {
        val now = Date()
        val zone = TimeZone.getDefault()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.getDefault())
        format.timeZone = zone
        val offsetMinutes = zone.getOffset(now.time) / 60_000
        val sign = if (offsetMinutes >= 0) "+" else "-"
        val hours = Math.abs(offsetMinutes) / 60
        val minutes = Math.abs(offsetMinutes) % 60
        return "${format.format(now)} (UTC$sign${pad(hours)}:${pad(minutes)}, ${zone.id}, " +
            "unix ${now.time / 1000})"
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()

    /** 整数就不带小数点：`4` 比 `4.0` 更像人话。 */
    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite() && Math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    /** 从 JSON 参数里取一个字符串字段；参数不是合法 JSON 时返回 null。 */
    private fun argument(arguments: String?, key: String): String? {
        if (arguments.isNullOrBlank()) return null
        val obj = runCatching { json.parseToJsonElement(arguments) as? JsonObject }.getOrNull() ?: return null
        return (obj[key] as? JsonPrimitive)?.contentOrNull
    }
}

/**
 * 四则运算求值器（递归下降）。
 *
 * 刻意**不用** `javax.script` 或任何 eval：那等于把模型生成的字符串当代码执行。
 * 这里的文法只有数字、括号、`+ - * / %` 和一元负号 —— 越界的东西直接判错。
 */
internal object Calculator {

    fun evaluate(expression: String): Double? {
        val parser = Parser(expression)
        val value = parser.parseExpression() ?: return null
        parser.skipSpaces()
        // 还有没吃完的字符 = 有我们不认识的东西，判错而不是猜
        if (!parser.atEnd()) return null
        if (value.isNaN() || value.isInfinite()) return null
        return value
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return if (pos < text.length) text[pos] else null
        }

        private fun take(): Char = text[pos++]

        /** expression := term (('+'|'-') term)* */
        fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (true) {
                when (peek()) {
                    '+' -> { take(); value += parseTerm() ?: return null }
                    '-' -> { take(); value -= parseTerm() ?: return null }
                    else -> return value
                }
            }
        }

        /** term := unary (('*'|'/'|'%') unary)* */
        private fun parseTerm(): Double? {
            var value = parseUnary() ?: return null
            while (true) {
                when (peek()) {
                    '*' -> { take(); value *= parseUnary() ?: return null }
                    '/' -> {
                        take()
                        val divisor = parseUnary() ?: return null
                        if (divisor == 0.0) return null
                        value /= divisor
                    }
                    '%' -> {
                        take()
                        val divisor = parseUnary() ?: return null
                        if (divisor == 0.0) return null
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        /** unary := ('+'|'-') unary | primary */
        private fun parseUnary(): Double? {
            when (peek()) {
                '+' -> { take(); return parseUnary() }
                '-' -> { take(); return parseUnary()?.let { -it } }
            }
            return parsePrimary()
        }

        /** primary := number | '(' expression ')' */
        private fun parsePrimary(): Double? {
            when (peek()) {
                '(' -> {
                    take()
                    val value = parseExpression() ?: return null
                    if (peek() != ')') return null
                    take()
                    return value
                }
            }
            return parseNumber()
        }

        private fun parseNumber(): Double? {
            skipSpaces()
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            if (pos == start) return null
            return text.substring(start, pos).toDoubleOrNull()
        }
    }
}
