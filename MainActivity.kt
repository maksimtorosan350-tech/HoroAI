package ai.horoai.app

import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val Night = Color(0xFF080B17)
private val Glass = Color(0x24FFFFFF)
private val GlassLine = Color(0x42FFFFFF)
private val Violet = Color(0xFFB7A2FF)
private val Aqua = Color(0xFF83E8F2)
private val Muted = Color(0xFFB9C2D8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val secure = SecureStore(applicationContext)
        var unlocked by mutableStateOf(false)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlocked = true
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("HoroAI Private")
                .setSubtitle("Подтверди, что это ты")
                .setAllowedAuthenticators(authenticators)
                .build())
        } else {
            unlocked = true // Device has no supported system authentication; local app data remains encrypted.
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Night, surface = Color(0xFF171B2D),
                primary = Violet, secondary = Aqua,
                onBackground = Color.White, onSurface = Color.White
            )) {
                if (unlocked) HoroApp(secure) else LockScreen { 
                    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
                        })
                    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("HoroAI Private")
                        .setSubtitle("Подтверди, что это ты").setAllowedAuthenticators(authenticators).build())
                }
            }
        }
    }
}

data class ChatMessage(val text: String, val fromUser: Boolean)

class HoroViewModel(private val store: SecureStore, private val appContext: android.content.Context) : ViewModel() {
    var messages by mutableStateOf(store.loadHistory().ifEmpty { listOf(
        ChatMessage("Привет! Я HoroAI Private. Здесь только текстовое общение. Подключи совместимую модель, чтобы включить полноценные AI-ответы.", false)
    ) })
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Приватный режим")

    fun clearHistory() {
        store.clearHistory()
        messages = emptyList()
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return
        messages = (messages + ChatMessage(clean, true)).takeLast(100)
        store.saveHistory(messages)
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { AiClient(appContext).reply(messages) } }
            messages = messages + ChatMessage(
                result.getOrElse { "Не удалось получить ответ: ${it.message ?: "ошибка соединения"}." },
                false
            )
            store.saveHistory(messages)
            status = if (ModelManager.isReady(appContext)) "Локальная модель активна" else "Нужно скачать модель"
            busy = false
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth().padding(24.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = Violet, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text("HoroAI Private", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Личное пространство защищено", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text("Разблокировать") }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Glass, RoundedCornerShape(24.dp))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.42f), Violet.copy(.28f), Aqua.copy(.18f))), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun HoroApp(store: SecureStore) {
    val context = LocalContext.current
    val vm: HoroViewModel = viewModel(factory = HoroFactory(store, context.applicationContext))
    var showSettings by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Night)) {
        // Soft luminous color fields behind translucent glass surfaces.
        Box(Modifier.offset(x = (-75).dp, y = 100.dp).size(230.dp).blur(75.dp).background(Violet.copy(.32f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = 75.dp, y = (-120).dp).size(250.dp).blur(85.dp).background(Aqua.copy(.22f), CircleShape))
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                    .background(Color(0x1EFFFFFF), RoundedCornerShape(22.dp))
                    .border(1.dp, GlassLine, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Violet, Aqua)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Night)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("HoroAI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(vm.status, color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Tune, "Настройки", tint = Color.White) }
            }

            if (showSettings) {
                SettingsPanel(store, { showSettings = false }, { vm.clearHistory() })
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("PERSONAL INTELLIGENCE", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Твоё пространство.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Text("Думай. Создавай. Действуй.", color = Muted, fontSize = 13.sp)
                        }
                    }
                    items(vm.messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                            Column(
                                Modifier.widthIn(max = 320.dp)
                                    .background(
                                        if (msg.fromUser) Brush.linearGradient(listOf(Color(0x664E4388), Color(0x3346A8B5)))
                                        else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(1.dp, GlassLine, RoundedCornerShape(20.dp))
                                    .padding(14.dp)
                            ) {
                                Text(if (msg.fromUser) "ТЫ" else "HOROAI", color = if (msg.fromUser) Aqua else Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text(msg.text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                    if (vm.busy) item { Text("HoroAI думает…", color = Aqua, fontSize = 12.sp) }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(Color(0x2BFFFFFF), RoundedCornerShape(26.dp))
                        .border(1.dp, GlassLine, RoundedCornerShape(26.dp))
                        .padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 13.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) Text("Спроси что-нибудь…", color = Muted)
                            inner()
                        }
                    )
                    IconButton(
                        onClick = { vm.send(draft); draft = "" },
                        enabled = draft.isNotBlank() && !vm.busy,
                        modifier = Modifier.background(Brush.linearGradient(listOf(Violet, Aqua)), CircleShape)
                    ) { Icon(Icons.Default.ArrowUpward, "Отправить", tint = Night) }
                }
                Text("ЗАЩИЩЁННОЕ ПРОСТРАНСТВО • БЕЗ РЕКЛАМЫ", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 9.dp), color = Muted.copy(.8f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun SettingsPanel(store: SecureStore, onClose: () -> Unit, onClearHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(ModelManager.isReady(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Настройки HoroAI", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Color.White)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("ЛОКАЛЬНАЯ НЕЙРОСЕТЬ", color = Aqua, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Text(ModelManager.MODEL_NAME, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Размер: ${ModelManager.MODEL_SIZE}. Загрузка нужна один раз. После неё модель работает на телефоне без отправки переписки в интернет.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            if (ready) {
                Text("Модель установлена на устройстве", color = Aqua, fontSize = 13.sp)
            } else {
                Button(onClick = {
                    downloading = true; downloadError = ""; progress = 0f
                    scope.launch {
                        runCatching { ModelManager.download(context) { done, total -> if (total > 0) progress = done.toFloat() / total } }
                            .onSuccess { ready = true }
                            .onFailure { downloadError = it.message ?: "Ошибка загрузки" }
                        downloading = false
                    }
                }, enabled = !downloading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Night)) {
                    Text(if (downloading) "Загрузка модели…" else "Скачать нейросеть")
                }
                if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Aqua)
                if (downloadError.isNotBlank()) Text(downloadError, color = Color(0xFFFF9A9A), fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) { Text("Очистить историю переписки") }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("ПРИВАТНОСТЬ", color = Aqua, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Text("• Только текст, без микрофона и аудио\n• Модель работает локально через llama.cpp\n• Переписка не отправляется на сервер\n• История шифруется AES-GCM через Android Keystore\n• Вход защищён системной биометрией/блокировкой\n• Модель не работает в фоне", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
fun GlassField(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet, unfocusedBorderColor = GlassLine,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White
            )
        )
    }
}

class HoroFactory(private val store: SecureStore, private val context: android.content.Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HoroViewModel(store, context) as T
}
