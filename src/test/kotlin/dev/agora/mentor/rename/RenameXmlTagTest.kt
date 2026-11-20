package dev.agora.mentor.rename

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RenameXmlTagTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testRenameXmlTagAtCaret() {
        myFixture.configureByFile("rename/foo.xml")

        myFixture.renameElementAtCaret("a2")

        myFixture.checkResultByFile("rename/foo_after.xml")
    }
}
