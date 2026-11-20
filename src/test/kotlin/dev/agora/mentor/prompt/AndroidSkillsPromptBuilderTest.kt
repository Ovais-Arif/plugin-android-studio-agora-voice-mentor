package dev.agora.mentor.prompt

import dev.agora.mentor.model.SelectedCodeContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSkillsPromptBuilderTest {

    private val builder = AndroidSkillsPromptBuilder()

    private fun context(
        selectedCode: String = "val x = 1",
        fileName: String = "MainActivity.kt",
        language: String = "Kotlin",
        surroundingContext: String? = null,
    ) = SelectedCodeContext(
        projectName        = "TestProject",
        fileName           = fileName,
        filePath           = "/src/$fileName",
        language           = language,
        selectedCode       = selectedCode,
        surroundingContext = surroundingContext,
        timestamp          = 0L,
    )

    @Test
    fun `prompt contains selected code`() {
        val prompt = builder.buildPrompt(context(selectedCode = "fun hello() = println(\"hi\")"))
        assertTrue(prompt.contains("fun hello() = println(\"hi\")"))
    }

    @Test
    fun `prompt contains file and language metadata`() {
        val prompt = builder.buildPrompt(context(fileName = "Foo.kt", language = "Kotlin"))
        assertTrue(prompt.contains("Foo.kt"))
        assertTrue(prompt.contains("Kotlin"))
    }

    @Test
    fun `prompt contains project name`() {
        val prompt = builder.buildPrompt(context())
        assertTrue(prompt.contains("TestProject"))
    }

    @Test
    fun `prompt includes surrounding context when present`() {
        val prompt = builder.buildPrompt(context(surroundingContext = "class Outer { }"))
        assertTrue(prompt.contains("class Outer { }"))
        assertTrue(prompt.contains("Surrounding context"))
    }

    @Test
    fun `prompt omits surrounding context section when absent`() {
        val prompt = builder.buildPrompt(context(surroundingContext = null))
        assertFalse(prompt.contains("Surrounding context"))
    }

    @Test
    fun `prompt contains voice conversation rules`() {
        val prompt = builder.buildPrompt(context())
        assertTrue(prompt.contains("spoken voice conversation"))
    }

    @Test
    fun `prompt does not contain raw null string`() {
        val prompt = builder.buildPrompt(context(surroundingContext = null))
        assertFalse(prompt.contains("null"))
    }

    @Test
    fun `prompt handles code with special characters`() {
        val code = """data class Foo(val bar: String = "hello & world < > \" '")"""
        val prompt = builder.buildPrompt(context(selectedCode = code))
        assertTrue(prompt.contains(code))
    }

    @Test
    fun `prompt is not blank`() {
        val prompt = builder.buildPrompt(context())
        assertTrue(prompt.isNotBlank())
    }
}
