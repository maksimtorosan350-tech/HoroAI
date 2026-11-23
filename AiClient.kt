package ai.horoai.app

import android.content.Context

class AiClient(private val context: Context) {
    fun reply(history: List<ChatMessage>): String {
        val model = ModelManager.file(context)
        if (!ModelManager.isReady(context)) return "Чтобы HoroAI начала отвечать, сначала скачай локальную модель в Настройках. После загрузки интернет больше не понадобится."
        return LlamaBridge.generate(model, history)
    }
}
