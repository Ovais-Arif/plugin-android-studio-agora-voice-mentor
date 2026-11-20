package dev.agora.mentor.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import dev.agora.mentor.model.SelectedCodeContext
import dev.agora.mentor.services.SelectionContextService
import dev.agora.mentor.toolwindow.AgoraAssistantPanel

class AskAgoraAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT)
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isVisible = project != null
        event.presentation.isEnabled = project != null && editor != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(CommonDataKeys.PROJECT) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText

        if (selectedText.isNullOrBlank()) {
            showSelectionWarning(project)
            return
        }

        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile == null) {
            showSelectionWarning(project)
            return
        }

        val context = buildContext(project, editor, selectedText, virtualFile)
        project.service<SelectionContextService>().setContext(context)

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Agora Assistant")
        if (toolWindow != null) {
            toolWindow.activate({
                refreshToolWindowPanel(toolWindow)
            }, true)
        }
    }

    private fun buildContext(
        project: Project,
        editor: Editor,
        selectedText: String,
        virtualFile: VirtualFile
    ): SelectedCodeContext {
        val document = editor.document
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        val language = psiFile?.language?.displayName ?: virtualFile.fileType.name

        return SelectedCodeContext(
            projectName = project.name,
            fileName = virtualFile.name,
            filePath = virtualFile.path,
            language = language,
            selectedCode = selectedText,
            surroundingContext = buildSurroundingContext(document, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd),
            timestamp = System.currentTimeMillis()
        )
    }

    private fun buildSurroundingContext(
        document: Document,
        selectionStart: Int,
        selectionEnd: Int
    ): String? {
        val startLine = document.getLineNumber(selectionStart)
        val endLine = document.getLineNumber((selectionEnd - 1).coerceAtLeast(selectionStart))
        val contextStartLine = (startLine - 2).coerceAtLeast(0)
        val contextEndLine = (endLine + 2).coerceAtMost(document.lineCount - 1)

        if (contextStartLine > contextEndLine) {
            return null
        }

        val startOffset = document.getLineStartOffset(contextStartLine)
        val endOffset = document.getLineEndOffset(contextEndLine)
        return document.getText(TextRange(startOffset, endOffset)).takeIf { it.isNotBlank() }
    }

    private fun refreshToolWindowPanel(toolWindow: ToolWindow) {
        val panel = toolWindow.contentManager.contents
            .asSequence()
            .mapNotNull { it.component as? AgoraAssistantPanel }
            .firstOrNull()
        panel?.refreshContext()
    }

    private fun showSelectionWarning(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Agora Assistant Notifications")
            .createNotification(
                "Select code first to ask Agora Conversational AI.",
                NotificationType.WARNING
            )
            .notify(project)
    }
}
