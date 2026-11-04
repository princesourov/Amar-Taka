package com.hisab.app.data.backup

/**
 * A tiny, dependency-free JSON reader/writer used only for backup files.
 *
 * Why not org.json: it is part of the Android framework and is NOT available (it is stubbed) in plain
 * JVM unit tests, so the backup format could not be tested. This implementation is deterministic
 * (objects keep insertion order, no whitespace), which the backup checksum relies on.
 *
 * Values: null, Boolean, Long, Double, String, List<Any?>, Map<String, Any?>.
 */
object MiniJson {

    class JsonException(message: String) : Exception(message)

    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(v: Any?, out: StringBuilder) {
        when (v) {
            null -> out.append("null")
            is Boolean -> out.append(v.toString())
            is Int -> out.append(v.toString())
            is Long -> out.append(v.toString())
            is Double -> {
                require(!v.isNaN() && !v.isInfinite()) { "NaN/Infinity cannot be written as JSON" }
                out.append(v.toString())
            }
            is String -> writeString(v, out)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) out.append(',')
                    first = false
                    writeString(k as String, out)
                    out.append(':')
                    write(value, out)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                v.forEachIndexed { i, item -> if (i > 0) out.append(','); write(item, out) }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value: ${v::class.simpleName}")
        }
    }

    private fun writeString(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
            }
        }
        out.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Unexpected data after the end of the JSON document")
        return v
    }

    private class Parser(private val s: String) {
        private var i = 0
        fun atEnd() = i >= s.length
        fun skipWs() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }

        fun value(): Any? {
            if (atEnd()) throw JsonException("Unexpected end of file")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw JsonException("Unexpected character '$c' at position $i")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("Unexpected token at position $i")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                if (atEnd() || s[i] != '"') throw JsonException("Expected a property name at position $i")
                val k = str()
                skipWs()
                if (atEnd() || s[i] != ':') throw JsonException("Expected ':' at position $i")
                i++
                skipWs()
                if (m.containsKey(k)) throw JsonException("Duplicate property '$k'")
                m[k] = value()
                skipWs()
                if (atEnd()) throw JsonException("Unexpected end of file")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                throw JsonException("Expected ',' or '}' at position $i")
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                skipWs()
                l += value()
                skipWs()
                if (atEnd()) throw JsonException("Unexpected end of file")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                throw JsonException("Expected ',' or ']' at position $i")
            }
        }

        private fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonException("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("Bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw JsonException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            var isDouble = false
            if (i < s.length && s[i] == '.') { isDouble = true; i++; while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true; i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val t = s.substring(start, i)
            return try { if (isDouble) t.toDouble() else t.toLong() } catch (e: NumberFormatException) { throw JsonException("Bad number '$t'") }
        }
    }
}
