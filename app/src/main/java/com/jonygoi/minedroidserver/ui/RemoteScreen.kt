package com.jonygoi.minedroidserver.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jonygoi.minedroidserver.remote.RemoteWebServer
import com.jonygoi.minedroidserver.server.ServerManager
import com.jonygoi.minedroidserver.theme.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen() {
    val context = LocalContext.current
    val remoteUrl by RemoteWebServer.serverUrl.collectAsState()
    val isRunning by RemoteWebServer.isRunning.collectAsState()
    val activeConfig by remember { derivedStateOf { ServerManager.getActiveConfig() } }

    val prefs = remember { context.getSharedPreferences("minedroid_remote", Context.MODE_PRIVATE) }
    var hostUrlInput by remember {
        mutableStateOf(prefs.getString("last_host_url", "") ?: "")
    }
    var connectedUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val displayUrl = remoteUrl ?: "http://127.0.0.1:8080"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinecraftObsidian)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MinecraftDarkSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MinecraftEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (connectedUrl != null) "Painel Host Remoto" else "Acesso Remoto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (connectedUrl != null) connectedUrl!! else "Gerenciamento Web & Cross-Device",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            if (connectedUrl != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = TextSecondary)
                    }
                    IconButton(onClick = { connectedUrl = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Desconectar", tint = MinecraftRedstone)
                    }
                }
            }
        }

        if (connectedUrl != null) {
            // Exibição da WebView do host conectado
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            loadUrl(connectedUrl!!)
                            webViewInstance = this
                        }
                    },
                    update = { view ->
                        if (view.url != connectedUrl) {
                            view.loadUrl(connectedUrl!!)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Conteúdo principal da aba Remoto
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Servidor Web Deste Aparelho (Host)
                Surface(
                    color = MinecraftCardSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MinecraftBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MinecraftEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Painel Web Deste Celular (Host)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Surface(
                                color = if (isRunning) MinecraftEmerald.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, if (isRunning) MinecraftEmerald.copy(alpha = 0.5f) else TextMuted)
                            ) {
                                Text(
                                    text = if (isRunning) "Servidor Web Ativo" else "Offline",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRunning) MinecraftEmerald else TextMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Abra este endereço no navegador (Chrome, Edge, Safari) de qualquer outro celular, tablet ou computador na mesma rede Wi-Fi para gerenciar o servidor:",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        Surface(
                            color = MinecraftDarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MinecraftBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayUrl,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinecraftEmerald,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = ClipData.newPlainText("URL Remoto", displayUrl)
                                            clipboard?.setPrimaryClip(clip)
                                            Toast.makeText(context, "Endereço copiado!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(displayUrl)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Abrir", tint = MinecraftEmerald, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(displayUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir no Navegador", color = MinecraftObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Painel Remoto MineDroid")
                                            putExtra(Intent.EXTRA_TEXT, "Painel do Servidor MineDroid: $displayUrl")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Painel Remoto"))
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MinecraftBorder),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartilhar Link", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Card 2: Conectar a Outro Celular (Cliente Remoto)
                Surface(
                    color = MinecraftCardSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MinecraftBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = MinecraftDiamond, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Conectar a Outro Celular pelo App",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "Se este celular não é o que está hospedando os servidores, digite o endereço do celular host abaixo para controlá-lo diretamente pelo aplicativo:",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = hostUrlInput,
                            onValueChange = { hostUrlInput = it },
                            label = { Text("Endereço do Celular Host (IP:Porta)") },
                            placeholder = { Text("ex: http://192.168.1.100:8080") },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, tint = MinecraftDiamond)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MinecraftDiamond,
                                unfocusedBorderColor = MinecraftBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Button(
                            onClick = {
                                var target = hostUrlInput.trim()
                                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                    target = "http://$target"
                                }
                                prefs.edit().putString("last_host_url", target).apply()
                                connectedUrl = target
                            },
                            enabled = hostUrlInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MinecraftDiamond),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar ao Celular Host", fontWeight = FontWeight.Bold, color = MinecraftObsidian, fontSize = 13.sp)
                        }
                    }
                }

                // Card 3: Jogando no Mesmo Celular (Host Local)
                activeConfig?.let { cfg ->
                    Surface(
                        color = MinecraftDarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MinecraftBorder.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MinecraftGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Jogando no Próprio Celular que Hospeda",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinecraftGold
                                )
                            }
                            Text(
                                text = "O Android impede que o jogo se conecte ao túnel público do Playit no próprio aparelho que está hospedando. Para jogar neste mesmo celular, use:",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("IP Local Bedrock", "127.0.0.1:${cfg.geyserPort}")
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copiado: 127.0.0.1:${cfg.geyserPort}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    border = BorderStroke(1.dp, MinecraftBorder)
                                ) {
                                    Text("Bedrock: 127.0.0.1:${cfg.geyserPort}", fontSize = 11.sp, color = MinecraftEmerald)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("IP Local Java", "127.0.0.1:${cfg.serverPort}")
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copiado: 127.0.0.1:${cfg.serverPort}", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    border = BorderStroke(1.dp, MinecraftBorder)
                                ) {
                                    Text("Java: 127.0.0.1:${cfg.serverPort}", fontSize = 11.sp, color = MinecraftLapis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
