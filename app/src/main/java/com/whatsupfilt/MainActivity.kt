package com.whatsupfilt

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var accessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationLogStore.load(applicationContext)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Phase0Screen(
                        accessGranted = accessGranted,
                        onGrant = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onExport = ::share,
                        onClear = { NotificationLogStore.clear(applicationContext) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    private fun share(redact: Boolean) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "WhatsUpFilt notification dump")
            putExtra(Intent.EXTRA_TEXT, NotificationLogStore.export(redact))
        }
        startActivity(Intent.createChooser(send, "Export log"))
    }
}

@Composable
private fun Phase0Screen(
    accessGranted: Boolean,
    onGrant: () -> Unit,
    onExport: (redact: Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val entries by NotificationLogStore.entries.collectAsState()
    val connected by NotificationLogStore.listenerConnected.collectAsState()

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        Text("WhatsUpFilt · Phase 0 probe", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Notification access: ${if (accessGranted) "granted" else "NOT granted"} · listener: ${if (connected) "connected" else "not connected"}")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!accessGranted) Button(onClick = onGrant) { Text("Grant access") }
            OutlinedButton(onClick = { onExport(true) }) { Text("Export (redacted)") }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }
        TextButton(onClick = { onExport(false) }) { Text("Export full (includes message text)") }
        HorizontalDivider()
        if (entries.isEmpty()) {
            Text(
                "No WhatsApp notifications yet. Ask someone to message you in a group (keep WhatsApp closed or on another chat).",
                Modifier.padding(top = 16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(entries) { EntryCard(it) }
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun EntryCard(e: LoggedNotification) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "${timeFmt.format(Date(e.time))} · ${e.pkg.removePrefix("com.")}" +
                    (if (e.isGroupSummary) " · SUMMARY" else "") + " · ch=${e.channelId}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                if (e.resolvedGroup != null) "Group: ${e.resolvedGroup}  ←  ${e.resolvedSender}"
                else "Not a group (${e.conversationTitle ?: e.rawExtras["android.title"] ?: "?"})",
                fontWeight = FontWeight.Bold,
            )
            Text("parsed via ${e.resolvedFrom} · isGroupConversation=${e.isGroupConversation}", style = MaterialTheme.typography.labelSmall)
            e.messages.takeLast(5).forEach { m ->
                Text("• ${m.sender ?: "(me)"}: ${m.text}", maxLines = 2)
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                e.rawExtras.forEach { (k, v) ->
                    Text("$k = $v", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            } else {
                Text("tap for raw extras", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
