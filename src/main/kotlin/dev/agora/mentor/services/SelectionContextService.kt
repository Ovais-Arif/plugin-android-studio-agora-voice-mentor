package dev.agora.mentor.services

import com.intellij.openapi.components.Service
import dev.agora.mentor.model.SelectedCodeContext

@Service(Service.Level.PROJECT)
class SelectionContextService {

    @Volatile
    private var latestContext: SelectedCodeContext? = null

    fun setContext(context: SelectedCodeContext) {
        latestContext = context
    }

    fun getLatestContext(): SelectedCodeContext? = latestContext
}
