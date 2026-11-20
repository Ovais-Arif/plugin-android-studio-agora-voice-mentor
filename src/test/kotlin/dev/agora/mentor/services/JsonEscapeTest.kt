package dev.agora.mentor.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for the JSON-escaping logic used when building the ConvoAI POST /join payload.
 * The escapeJson extension is private to AgoraSessionService, so we duplicate the
 * identical logic here to verify its correctness independently.
 */
class JsonEscapeTest {

    private fun String.escapeJson(): String = buildString(length) {
        for (char in this@escapeJson) {
            when (char) {
                '\\'  -> append("\\\\")
                '"'   -> append("\\\"")
                '\b'  -> append("\\b")
                '' -> append("\\f")
                '\n'  -> append("\\n")
                '\r'  -> append("\\r")
                '\t'  -> append("\\t")
                else  -> append(char)
            }
        }
    }

    @Test
    fun `double quotes are escaped`() {
        assertEquals("say \\\"hello\\\"", """say "hello"""".escapeJson())
    }

    @Test
    fun `backslash is escaped`() {
        assertEquals("C:\\\\Users\\\\foo", """C:\Users\foo""".escapeJson())
    }

    @Test
    fun `newlines are escaped`() {
        assertEquals("line1\\nline2", "line1\nline2".escapeJson())
    }

    @Test
    fun `carriage returns are escaped`() {
        assertEquals("line1\\rline2", "line1\rline2".escapeJson())
    }

    @Test
    fun `tabs are escaped`() {
        assertEquals("col1\\tcol2", "col1\tcol2".escapeJson())
    }

    @Test
    fun `plain text is unchanged`() {
        val plain = "Hello, Android developer!"
        assertEquals(plain, plain.escapeJson())
    }

    @Test
    fun `unicode letters are preserved`() {
        val unicode = "café résumé 日本語"
        assertEquals(unicode, unicode.escapeJson())
    }

    @Test
    fun `empty string is unchanged`() {
        assertEquals("", "".escapeJson())
    }

    @Test
    fun `result does not contain unescaped double quote`() {
        val result = """He said "hello"""".escapeJson()
        // The only " chars left should be preceded by backslash
        assertFalse(result.contains(Regex("""(?<!\\)"""")))
    }

    @Test
    fun `prompt with kotlin code escapes correctly`() {
        val code = """fun greet(name: String): String = "Hello, ${'$'}name!""""
        val escaped = code.escapeJson()
        // Verify no raw double quotes remain
        assertFalse(escaped.contains(Regex("""(?<!\\)"""")))
    }
}
