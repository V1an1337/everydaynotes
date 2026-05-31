package xyz.v1an.everydaynotes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var incomingShareText by mutableStateOf<String?>(null)
    private var incomingImageUri by mutableStateOf<Uri?>(null)

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        incomingImageUri = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIncomingIntent(intent)
        requestRuntimePermissions()
        stopService(Intent(this, ScreenshotObserverService::class.java))
        startFloatingButtonIfAllowed()

        setContent {
            EverydayNotesScreen(
                incomingShareText = incomingShareText,
                incomingImageUri = incomingImageUri,
                onIncomingConsumed = {
                    incomingShareText = null
                    incomingImageUri = null
                },
                onPickImage = { imagePicker.launch("image/*") },
                onOpenOverlaySettings = {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        startFloatingButtonIfAllowed()
    }

    private fun startFloatingButtonIfAllowed() {
        if (Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(this, Intent(this, FloatingCaptureButtonService::class.java))
        }
    }

    private fun readIncomingIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        incomingShareText = intent.getStringExtra(Intent.EXTRA_TEXT)
        incomingImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 17)
        }
    }
}

@Composable
private fun EverydayNotesScreen(
    incomingShareText: String?,
    incomingImageUri: Uri?,
    onIncomingConsumed: () -> Unit,
    onPickImage: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SettingsStore(context) }
    var token by remember { mutableStateOf(store.token) }
    var apiBase by remember { mutableStateOf(store.apiBase) }
    var password by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf<List<NoteDto>>(emptyList()) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun api(): EverydayNotesApi = EverydayNotesApi(context, apiBase, token)
    fun refresh() {
        if (token == null) return
        scope.launch {
            loading = true
            error = null
            runCatching { api().listNotes(query) }
                .onSuccess { notes = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(token) {
        if (token != null) refresh()
    }
    LaunchedEffect(incomingShareText, incomingImageUri) {
        pendingText = incomingShareText
        pendingImage = incomingImageUri
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF365F53),
            surface = Color(0xFFFFFDF9),
            background = Color(0xFFF7F4EF)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F4EF)) {
            if (token == null) {
                LoginScreen(
                    apiBase = apiBase,
                    password = password,
                    error = error,
                    onApiBaseChange = {
                        apiBase = it
                        store.apiBase = it
                    },
                    onPasswordChange = { password = it },
                    onLogin = {
                        scope.launch {
                            loading = true
                            error = null
                            runCatching { EverydayNotesApi(context, apiBase).login(password) }
                                .onSuccess {
                                    store.token = it
                                    token = it
                                }
                                .onFailure { error = it.message }
                            loading = false
                        }
                    }
                )
            } else {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Header(
                        loading = loading,
                        onRandom = {
                            scope.launch {
                                runCatching { api().randomNote() }
                                    .onSuccess { notes = listOf(it) + notes.filter { note -> note.id != it.id } }
                                    .onFailure { error = it.message }
                            }
                        },
                        onLogout = {
                            store.clearToken()
                            token = null
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            label = { Text("搜索") }
                        )
                        Button(onClick = { refresh() }) { Text("刷新") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val candidates = CaptureCandidateDetector.detect(context)
                                CaptureOverlayService.showCandidates(context, candidates)
                            }
                        ) { Text("立即识别") }
                        OutlinedButton(onClick = onPickImage) { Text("选择截图") }
                        OutlinedButton(onClick = onOpenOverlaySettings) { Text("悬浮窗") }
                    }
                    error?.let { Text(it, color = Color(0xFFA23B35)) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(notes, key = { it.id }) { note ->
                            NoteRow(note = note, api = api())
                        }
                    }
                }
            }

            if (pendingText != null || pendingImage != null) {
                CaptureDialog(
                    title = if (pendingText != null) "保存抖音" else "保存截图",
                    onDismiss = {
                        pendingText = null
                        pendingImage = null
                        onIncomingConsumed()
                    },
                    onSave = { remark ->
                        scope.launch {
                            runCatching {
                                if (pendingText != null) api().captureDouyin(pendingText.orEmpty(), remark)
                                else api().uploadScreenshot(pendingImage ?: kotlin.error("missing image"), remark)
                            }.onSuccess {
                                notes = listOf(it) + notes
                            }.onFailure {
                                error = it.message
                            }
                            pendingText = null
                            pendingImage = null
                            onIncomingConsumed()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    apiBase: String,
    password: String,
    error: String?,
    onApiBaseChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFFDF9)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("EverydayNotes", style = MaterialTheme.typography.labelLarge, color = Color(0xFF7F6148))
            Text("私人日记库", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(apiBase, onApiBaseChange, label = { Text("API") }, singleLine = true)
            OutlinedTextField(password, onPasswordChange, label = { Text("密码") }, singleLine = true)
            error?.let { Text(it, color = Color(0xFFA23B35)) }
            Button(onClick = onLogin, enabled = password.isNotBlank()) { Text("进入") }
        }
    }
}

@Composable
private fun Header(loading: Boolean, onRandom: () -> Unit, onLogout: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("EverydayNotes", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7F6148))
            Text("今日以前", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Button(onClick = onRandom) { Text("盲盒") }
            OutlinedButton(onClick = onLogout) { Text("退出") }
        }
    }
}

@Composable
private fun NoteRow(note: NoteDto, api: EverydayNotesApi) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF9)), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val asset = note.assets.firstOrNull { it.kind == "cover" || it.kind == "screenshot" } ?: note.assets.firstOrNull()
            if (asset != null && asset.mimeType?.startsWith("image") != false) {
                AsyncImage(
                    model = api.assetUrl(asset),
                    contentDescription = note.title,
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE8E2))
                )
            } else {
                Box(Modifier.size(86.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDFE8E2)), contentAlignment = Alignment.Center) {
                    Text(if (note.type == "douyin") "视频" else "截图")
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(note.title ?: "Untitled", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(note.author, note.status).joinToString(" · "), color = Color(0xFF776B60))
                note.remark?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (note.tags.isNotEmpty()) Text(note.tags.joinToString(" #", prefix = "#"), color = Color(0xFF315D50))
            }
        }
    }
}

@Composable
private fun CaptureDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var remark by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(remark, { remark = it }, label = { Text("备注") })
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(remark) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("丢弃") }
        }
    )
}
