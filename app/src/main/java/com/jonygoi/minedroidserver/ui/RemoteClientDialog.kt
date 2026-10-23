package com.jonygoi.minedroidserver.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jonygoi.minedroidserver.theme.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteClientDialog(
    initialUrl: String = "",
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("minedroid_remote", Context.MODE_PRIVATE) }
    var hostUrlInput by remember {
        mutableStateOf(
            if (initialUrl.isNotBlank()) initialUrl
            else prefs.getString("last_host_url", "") ?: ""
        )
    }
    var connectedUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MinecraftDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MinecraftBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MinecraftCardSurface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MinecraftEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (connectedUrl != null) "Painel Remoto Host" else "Conectar a Celular Remoto",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectedUrl != null) {
                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Recarregar", tint = TextSecondary)
                            }
                            IconButton(onClick = { connectedUrl = null }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Desconectar", tint = MinecraftRedstone)
                            }
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                        }
                    }
                }

                if (connectedUrl == null) {
                    // Tela de Conexão
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MinecraftCardSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinecraftBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "📱 Acessar o Celular que Hospeda o Servidor",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinecraftEmerald
                                )
                                Text(
                                    text = "Você pode usar este aplicativo para gerenciar e controlar outro celular Android que esteja rodando seus servidores Minecraft na mesma rede Wi-Fi.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "1. No celular que hospeda o servidor, abra o MineDroid.\n" +
                                            "2. No painel, veja o endereço IP do 'Acesso Remoto' (ex: http://192.168.1.50:8080).\n" +
                                            "3. Digite o endereço abaixo e clique em Conectar.",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        OutlinedTextField(
                            value = hostUrlInput,
                            onValueChange = { hostUrlInput = it },
                            label = { Text("Endereço do Celular Host (IP:Porta)") },
                            placeholder = { Text("ex: http://192.168.1.100:8080") },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, tint = MinecraftEmerald)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MinecraftEmerald,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MinecraftObsidian)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar ao Celular Host", fontWeight = FontWeight.Bold, color = MinecraftObsidian)
                        }

                        // Conexão rápida com localhost (se o usuário quiser testar no próprio aparelho)
                        OutlinedButton(
                            onClick = {
                                val local = "http://127.0.0.1:8080"
                                hostUrlInput = local
                                connectedUrl = local
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinecraftBorder)
                        ) {
                            Text("Conectar neste próprio aparelho (127.0.0.1:8080)", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                } else {
                    // WebView conectada
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
                }
            }
        }
    }
}
