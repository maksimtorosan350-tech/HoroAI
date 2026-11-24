package ai.horoai.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** JNI bridge to llama.cpp. The model stays in app-private storage. */
object LlamaBridge {
    init { System.loadLibrary("horoai_llm") }
    @JvmStatic external fun generateNative(modelPath: String, prompt: String, maxTokens: Int, threads: Int): String

    fun generate(model: File, messages: List<ChatMessage>): String {
        check(model.isFile && model.length() > 100_000_000L) { "Файл модели не найден или повреждён. Скачай модель в настройках." }
        val prompt = buildString {
            append("<|im_start|>system\nТы HoroAI — личный русскоязычный помощник. Отвечай полезно, ясно и честно. Если не знаешь — скажи об этом.\n<|im_end|>\n")
            messages.takeLast(12).forEach { m ->
                append(if (m.fromUser) "<|im_start|>user\n" else "<|im_start|>assistant\n")
                append(m.text).append("\n<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
        return generateNative(model.absolutePath, prompt, 384, 4).trim().ifBlank { "Не удалось получить ответ. Попробуй ещё раз." }
    }
}

object ModelManager {
    const val MODEL_NAME = "Qwen2.5-1.5B-Instruct Q4_K_M"
    const val MODEL_SIZE = "около 1,12 ГБ"
    private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"

    fun file(context: Context) = File(context.filesDir, "models/horoai-qwen2.5-1.5b-q4_k_m.gguf")
    fun isReady(context: Context) = file(context).let { it.exists() && it.length() > 100_000_000L }

    suspend fun download(context: Context, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val target = file(context)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HoroAI-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("Ошибка загрузки модели: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input -> temp.outputStream().buffered().use { output ->
                val buffer = ByteArray(1024 * 1024); var count: Long = 0
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer, 0, n); count += n; onProgress(count, total)
                }
            } }
            if (temp.length() < 100_000_000L) error("Загруженный файл слишком маленький")
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) error("Не удалось сохранить модель")
            target
        } catch (e: Exception) { temp.delete(); throw e }
        finally { connection.disconnect() }
    }
}
