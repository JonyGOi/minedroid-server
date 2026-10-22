package com.jonygoi.minedroidserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*
import kotlinx.coroutines.*

@Composable
fun DashboardScreen(
    onNavigateToConsole: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onNavigateToFiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val servers by ServerManager.servers.collectAsState()
    val activeServerId by ServerManager.activeServerId.collectAsState()
    val state by ServerManager.state.collectAsState()
    val stats by ServerManager.stats.collectAsState()
    val downloadProg by ServerManager.downloadProgress.collectAsState()
    val logs by ServerManager.logs.collectAsState()
    val liveClaimUrl by PlayitManager.liveClaimUrl.collectAsState()
    val agentUrl by PlayitManager.agentUrl.collectAsState()
    val isPlayitRunning by PlayitTunnelProcess.isRunningFlow.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val secret = PlayitManager.getSavedSecretKey(context)
                if (!secret.isNullOrBlank() && isPlayitRunning) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val tunnels = PlayitManager.fetchTunnelsViaApi(secret)
                        withContext(Dispatchers.Main) {
                            val javaTunnel = tunnels.find { !it.isBedrock }
                            val bedrockTunnel = tunnels.find { it.isBedrock }
                            ServerManager.updateActiveServerDomain(javaTunnel?.domain ?: "")
                            ServerManager.updateActiveServerBedrockDomain(bedrockTunnel?.domain ?: "")
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<ServerConfig?>(null) }
    var playitSessionToConfirm by remember { mutableStateOf<PlayitClaimSession?>(null) }
    var isExchangingPlayit by remember { mutableStateOf(false) }
    var playitLiveStatus by remember { mutableStateOf("Conectando ao Playit.gg...") }
    var manualPlayitInput by remember { mutableStateOf("") }

    val activeConfig = remember(servers, activeServerId) {
        servers.find { it.id == activeServerId } ?: servers.firstOrNull()
    }
    val savedSecret = remember(playitSessionToConfirm, isPlayitRunning) { PlayitManager.getSavedSecretKey(context) }
    val isRunning = state == ServerState.RUNNING
    val isStarting = state == ServerState.STARTING || state == ServerState.PREPARING || state == ServerState.DOWNLOADING

    // Dialog: Criar Novo Servidor
    if (showCreateDialog) {
        ServerCreationDialog(
            onDismiss = { showCreateDialog = false },
            onServerCreated = { newServer ->
                ServerManager.createServer(context, newServer)
                showCreateDialog = false
                Toast.makeText(context, "Servidor '${newServer.name}' criado com sucesso!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Excluir Servidor
    serverToDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Excluir Servidor") },
            text = { Text("Deseja realmente apagar '${s.name}'? Todos os arquivos do mundo e configurações serão excluídos.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ServerManager.deleteServer(context, s.id)
                        serverToDelete = null
                        Toast.makeText(context, "Servidor excluído com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Sim, Excluir", color = MinecraftRedstone, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = MinecraftDarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Dialog: Vinculação Playit.gg
    playitSessionToConfirm?.let { session ->
        var hasAutoOpenedBrowser by remember { mutableStateOf(false) }

        DisposableEffect(session.code) {
            if (session.code.isNotBlank()) {
                playitLiveStatus = "Agente registrado! Aguardando autorização no navegador..."
                PlayitManager.startBackgroundClaimPoller(session.code, context) { status, secret ->
                    when (status) {
                        PlayitClaimStatus.WAITING_FOR_BROWSER -> {
                            playitLiveStatus = "Aguardando página abrir no navegador..."
                        }
                        PlayitClaimStatus.WAITING_FOR_USER_CLICK -> {
                            playitLiveStatus = "Agente detectado! Clique em 'Add Agent' na página..."
                        }
                        PlayitClaimStatus.ACCEPTED -> {
                            playitLiveStatus = "Autorizado com sucesso! Conectando agente..."
                            if (!secret.isNullOrBlank()) {
                                PlayitManager.saveSecretKey(context, secret)
                                PlayitTunnelProcess.start(
                                    context = context,
                                    onLog = { msg -> ServerManager.addLog(msg) },
                                    onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) },
                                    onBedrockDomainFound = { bDomain -> ServerManager.updateActiveServerBedrockDomain(bDomain) }
                                )
                                playitSessionToConfirm = null
                                try {
                                    Toast.makeText(context, "Conta Playit.gg vinculada com sucesso!", Toast.LENGTH_LONG).show()
                                } catch (_: Exception) {}
                            }
                        }
                        PlayitClaimStatus.REJECTED -> {
                            try {
                                Toast.makeText(context, "Vinculação cancelada no navegador.", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                            playitSessionToConfirm = null
                        }
                        PlayitClaimStatus.ERROR -> {}
                    }
                }
            } else {
                playitLiveStatus = "Iniciando motor nativo Playit.gg..."
            }
            onDispose {
                PlayitManager.stopBackgroundClaimPoller()
            }
        }

        LaunchedEffect(session.claimUrl, liveClaimUrl) {
            val urlToOpen = if (!liveClaimUrl.isNullOrBlank()) liveClaimUrl else session.claimUrl
            if (!urlToOpen.isNullOrBlank() && !hasAutoOpenedBrowser) {
                hasAutoOpenedBrowser = true
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        AlertDialog(
            onDismissRequest = { playitSessionToConfirm = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = MinecraftDiamond)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vincular Playit.gg", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Abra a página do Playit.gg e clique em 'Add Agent' para autorizar o túnel deste celular:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    val targetUrl = if (!liveClaimUrl.isNullOrBlank()) liveClaimUrl!! else session.claimUrl
                    if (targetUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MinecraftDiamond),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ABRIR PÁGINA DO PLAYIT", color = MinecraftObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Surface(
                        color = MinecraftCardSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MinecraftEmerald, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = playitLiveStatus, fontSize = 11.sp, color = TextPrimary)
                        }
                    }

                    HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                    Text("Ou cole sua Secret Key manualmente:", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = manualPlayitInput,
                        onValueChange = { manualPlayitInput = it },
                        placeholder = { Text("Chave secreta do playit.toml") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinecraftEmerald,
                            unfocusedBorderColor = MinecraftBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualPlayitInput.isNotBlank()) {
                            PlayitManager.saveSecretKey(context, manualPlayitInput.trim())
                            PlayitTunnelProcess.start(
                                context = context,
                                onLog = { msg -> ServerManager.addLog(msg) },
                                onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) },
                                onBedrockDomainFound = { bDomain -> ServerManager.updateActiveServerBedrockDomain(bDomain) }
                            )
                            playitSessionToConfirm = null
                            Toast.makeText(context, "Chave salva com sucesso!", Toast.LENGTH_SHORT).show()
                        } else {
                            isExchangingPlayit = true
                            coroutineScope.launch {
                                val secret = PlayitManager.exchangeClaim(session.code)
                                isExchangingPlayit = false
                                if (!secret.isNullOrBlank()) {
                                    PlayitManager.saveSecretKey(context, secret)
                                    PlayitTunnelProcess.start(
                                        context = context,
                                        onLog = { msg -> ServerManager.addLog(msg) },
                                        onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) },
                                        onBedrockDomainFound = { bDomain -> ServerManager.updateActiveServerBedrockDomain(bDomain) }
                                    )
                                    playitSessionToConfirm = null
                                    Toast.makeText(context, "Conectado ao Playit.gg com sucesso!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Ainda aguardando autorização no site...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isExchangingPlayit) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MinecraftObsidian, strokeWidth = 2.dp)
                    } else {
                        Text("CONFIRMAR", color = MinecraftObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { playitSessionToConfirm = null }) {
                    Text("Fechar")
                }
            },
            containerColor = MinecraftDarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Main Scaffold Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinecraftObsidian)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Top Bar: Clean title & primary add action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MineDroid Server",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (servers.isEmpty()) "Nenhum servidor configurado" else "${servers.size} servidor(es) cadastrado(s)",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Novo Servidor", color = MinecraftObsidian, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Active Download / Preparation Bar
        if (state == ServerState.DOWNLOADING || state == ServerState.PREPARING) {
            Surface(
                color = MinecraftDarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MinecraftBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = downloadProg.first.ifEmpty { "Preparando ambiente..." },
                            color = MinecraftEmerald,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(downloadProg.second * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProg.second },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MinecraftEmerald,
                        trackColor = MinecraftCardSurface
                    )
                }
            }
        }

        // Empty State (quando não há servidores)
        if (servers.isEmpty()) {
            Surface(
                color = MinecraftDarkSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MinecraftBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MinecraftCardSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MinecraftEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Nenhum Servidor Criado",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Crie seu primeiro servidor para jogar com amigos pelo celular ou computador.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MinecraftObsidian)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Criar Primeiro Servidor",
                            color = MinecraftObsidian,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // HERO CARD: O Servidor Ativo (Unificado, sem caixas aninhadas)
            activeConfig?.let { s ->
                val isServerRunning = isRunning || isStarting

                Card(
                    colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (isRunning) MinecraftEmerald else if (isStarting) MinecraftGold else MinecraftBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Header do Servidor: Nome, Motor, Versão e Estado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = s.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRunning) MinecraftEmerald else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${s.serverType.displayName} ${if (s.serverType != ServerType.GEYSER_STANDALONE) s.version else ""}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (s.serverType == ServerType.GEYSER_STANDALONE) MinecraftGold else MinecraftDiamond
                                    )
                                    Text(
                                        text = " • ${s.allocatedRamMb}MB RAM",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            StateBadge(state = state)
                        }

                        // 2. HUD de Desempenho em Tempo Real (Apenas quando Rodando)
                        if (isRunning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MinecraftCardSurface, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TEMPO ATIVO", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(stats.formattedUptime, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MinecraftBorder))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("JOGADORES", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text("${stats.onlinePlayers}/${s.maxPlayers}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MinecraftEmerald)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MinecraftBorder))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("RAM CELULAR", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text("${stats.usedRamMb} MB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                        }

                        // 3. Área de Endereços de Conexão (Limpa e Direta)
                        Surface(
                            color = MinecraftCardSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Endereço Java (se não for proxy puro)
                                if (s.serverType != ServerType.GEYSER_STANDALONE) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Computer, contentDescription = null, tint = MinecraftEmerald, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Java (PC / Pojav)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = s.friendlyJavaAddress,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        if (s.customDomain.isNotBlank()) {
                                            IconButton(
                                                onClick = { copyToClipboard(context, s.friendlyJavaAddress) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // Endereço Bedrock (se crossplay ativo)
                                if (s.enableGeyser || s.serverType == ServerType.GEYSER_STANDALONE) {
                                    if (s.serverType != ServerType.GEYSER_STANDALONE) {
                                        HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))
                                    }
                                    if (s.bedrockDomain.isNotBlank()) {
                                        val parts = s.bedrockDomain.split(":")
                                        val bHost = parts.getOrNull(0) ?: s.bedrockDomain
                                        val bPort = parts.getOrNull(1) ?: s.geyserPort.toString()

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = MinecraftLapis, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text("Bedrock (Celular / Console)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            text = "$bHost : $bPort",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        copyToClipboard(context, "$bHost:$bPort")
                                                        Toast.makeText(context, "Endereço Bedrock copiado!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Tudo", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            // Atalhos rápidos para copiar IP e Porta separadamente (exigido pelo Minecraft Bedrock)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        copyToClipboard(context, bHost)
                                                        Toast.makeText(context, "IP Bedrock copiado!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(28.dp),
                                                    border = BorderStroke(1.dp, MinecraftBorder)
                                                ) {
                                                    Text("📋 Copiar IP", fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        copyToClipboard(context, bPort)
                                                        Toast.makeText(context, "Porta copiada: $bPort", Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(0.6f).height(28.dp),
                                                    border = BorderStroke(1.dp, MinecraftBorder)
                                                ) {
                                                    Text("📋 Porta: $bPort", fontSize = 10.sp, color = MinecraftEmerald, maxLines = 1)
                                                }
                                            }
                                        }
                                    } else {
                                        // Túnel Bedrock ainda não configurado no Playit.gg
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MinecraftRedstone.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = MinecraftGold, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Túnel Bedrock (UDP :19132) Não Detectado",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MinecraftGold
                                                )
                                            }
                                            Text(
                                                text = "Seus túneis no Playit estão apontados apenas para Java (TCP 25565). Para que amigos entrem pelo celular/Bedrock, é necessário um túnel Minecraft Bedrock (UDP 19132).",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        try {
                                                            val target = agentUrl ?: "https://playit.gg/account/tunnels"
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {}
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftCardSurface),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(30.dp),
                                                    border = BorderStroke(1.dp, MinecraftBorder)
                                                ) {
                                                    Text("Abrir Playit.gg", fontSize = 11.sp, color = TextPrimary)
                                                }

                                                var isCreatingTunnel by remember { mutableStateOf(false) }
                                                Button(
                                                    onClick = {
                                                        val secret = savedSecret
                                                        if (!secret.isNullOrBlank()) {
                                                             isCreatingTunnel = true
                                                             coroutineScope.launch(Dispatchers.IO) {
                                                                 val ok = PlayitManager.createTunnelViaApi(secret, "minecraft-bedrock", 19132)
                                                                 withContext(Dispatchers.Main) {
                                                                     isCreatingTunnel = false
                                                                     if (ok) {
                                                                         Toast.makeText(context, "Túnel Bedrock criado com sucesso! Reiniciando agente...", Toast.LENGTH_SHORT).show()
                                                                         PlayitTunnelProcess.stop()
                                                                         PlayitTunnelProcess.start(
                                                                             context = context,
                                                                             onLog = { msg -> ServerManager.addLog(msg) },
                                                                             onDomainFound = { d -> ServerManager.updateActiveServerDomain(d) },
                                                                             onBedrockDomainFound = { bd -> ServerManager.updateActiveServerBedrockDomain(bd) }
                                                                         )
                                                                     } else {
                                                                         Toast.makeText(context, "Não foi possível criar automaticamente. Abra o painel playit.gg", Toast.LENGTH_LONG).show()
                                                                     }
                                                                 }
                                                             }
                                                        } else {
                                                            Toast.makeText(context, "Vincule sua conta Playit primeiro!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    enabled = !isCreatingTunnel,
                                                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(30.dp)
                                                ) {
                                                    if (isCreatingTunnel) {
                                                         CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MinecraftObsidian)
                                                    } else {
                                                         Text("Criar Automático", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinecraftObsidian)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Status do Túnel Playit.gg
                                if (s.enablePlayitTunnel) {
                                    HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))
                                    var isSyncingTunnels by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(if (isPlayitRunning) MinecraftEmerald else TextMuted, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isPlayitRunning) "Túnel Playit.gg ativo" else "Túnel Playit.gg offline",
                                                fontSize = 11.sp,
                                                color = if (isPlayitRunning) MinecraftEmerald else TextSecondary
                                            )
                                            if (isPlayitRunning && !savedSecret.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = {
                                                        if (!isSyncingTunnels) {
                                                            isSyncingTunnels = true
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                val tunnels = PlayitManager.fetchTunnelsViaApi(savedSecret)
                                                                withContext(Dispatchers.Main) {
                                                                    isSyncingTunnels = false
                                                                    val javaTunnel = tunnels.find { !it.isBedrock }
                                                                    val bedrockTunnel = tunnels.find { it.isBedrock }
                                                                    ServerManager.updateActiveServerDomain(javaTunnel?.domain ?: "")
                                                                    ServerManager.updateActiveServerBedrockDomain(bedrockTunnel?.domain ?: "")
                                                                    Toast.makeText(context, "Túneis sincronizados!", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    if (isSyncingTunnels) {
                                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MinecraftEmerald)
                                                    } else {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar Túneis", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }

                                        if (savedSecret.isNullOrBlank()) {
                                            TextButton(
                                                onClick = {
                                                    PlayitTunnelProcess.start(
                                                        context = context,
                                                        onLog = { msg -> ServerManager.addLog(msg) },
                                                        onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) },
                                                        onBedrockDomainFound = { bDomain -> ServerManager.updateActiveServerBedrockDomain(bDomain) }
                                                    )
                                                    playitSessionToConfirm = PlayitClaimSession(code = "", claimUrl = "")
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Vincular Conta", color = MinecraftDiamond, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Alerta: Converter Geyser Standalone para PaperMC
                        if (s.serverType == ServerType.GEYSER_STANDALONE) {
                            Surface(
                                color = MinecraftGold.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MinecraftGold.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Modo Proxy (Sem mapa)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinecraftGold
                                        )
                                        Text(
                                            text = "Gere terreno e blocos completos mudando para PaperMC.",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val updated = s.copy(serverType = ServerType.PAPER, enableGeyser = true, enableFloodgate = true)
                                            ServerManager.updateServer(context, updated)
                                            Toast.makeText(context, "Convertido para PaperMC com sucesso!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Converter", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinecraftObsidian)
                                    }
                                }
                            }
                        }

                        // 4. Ações Principais do Servidor
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Botão Primário: Iniciar / Parar (FULL WIDTH, 52dp)
                            if (isRunning) {
                                Button(
                                    onClick = { ServerManager.stopServer(context) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftRedstone),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PARAR SERVIDOR",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else if (isStarting) {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = MinecraftGold.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MinecraftObsidian,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INICIANDO SERVIDOR...",
                                        fontWeight = FontWeight.Bold,
                                        color = MinecraftObsidian,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { ServerManager.startServer(context, s.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MinecraftObsidian,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INICIAR SERVIDOR",
                                        fontWeight = FontWeight.Bold,
                                        color = MinecraftObsidian,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Barra de Ferramentas Rápidas (Terminal, Arquivos, Configurar, Excluir)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Botão Terminal
                                OutlinedButton(
                                    onClick = {
                                        ServerManager.selectServer(s.id)
                                        onNavigateToConsole()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = BorderStroke(1.dp, MinecraftBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MinecraftEmerald
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Terminal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                // Botão Arquivos / Explorador de Diretórios
                                OutlinedButton(
                                    onClick = {
                                        ServerManager.selectServer(s.id)
                                        onNavigateToFiles()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = BorderStroke(1.dp, MinecraftBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MinecraftLapis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Arquivos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                // Botão Configurar
                                OutlinedButton(
                                    onClick = {
                                        ServerManager.selectServer(s.id)
                                        onNavigateToConfig()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = BorderStroke(1.dp, MinecraftBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Config", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                // Botão Excluir (apenas quando parado)
                                if (!isServerRunning) {
                                    IconButton(
                                        onClick = { serverToDelete = s },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Excluir Servidor",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Prévia do Terminal (Apenas quando Rodando)
                        if (isRunning && logs.isNotEmpty()) {
                            Surface(
                                color = ConsoleBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MinecraftBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToConsole() }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Console em Tempo Real", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                        Text("Abrir Terminal →", fontSize = 11.sp, color = MinecraftEmerald, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val previewLogs = logs.takeLast(2)
                                    previewLogs.forEach { log ->
                                        Text(
                                            text = "[${log.formattedTime}] ${log.text}",
                                            fontSize = 11.sp,
                                            color = when (log.type) {
                                                LogType.ERROR -> ConsoleRed
                                                LogType.WARN -> ConsoleYellow
                                                LogType.JOIN -> ConsoleGreen
                                                LogType.SYSTEM -> ConsoleCyan
                                                else -> TextSecondary
                                            },
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lista de Outros Servidores (se houver mais de um cadastrado)
            val otherServers = servers.filter { it.id != activeServerId }
            if (otherServers.isNotEmpty()) {
                Text(
                    text = "Outros Servidores (${otherServers.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                otherServers.forEach { s ->
                    Surface(
                        color = MinecraftDarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MinecraftBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isRunning && !isStarting) {
                                    ServerManager.selectServer(s.id)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    text = "${s.serverType.displayName} ${s.version} • ${s.allocatedRamMb}MB",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!isRunning && !isStarting) {
                                        ServerManager.selectServer(s.id)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Ativar", fontSize = 11.sp, color = MinecraftEmerald)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateBadge(state: ServerState) {
    val (color, label) = when (state) {
        ServerState.STOPPED -> MinecraftRedstone to "PARADO"
        ServerState.PREPARING -> MinecraftGold to "PREPARANDO"
        ServerState.DOWNLOADING -> MinecraftGold to "BAIXANDO"
        ServerState.STARTING -> MinecraftGold to "INICIANDO"
        ServerState.RUNNING -> MinecraftEmerald to "RODANDO"
        ServerState.STOPPING -> MinecraftRedstone to "PARANDO"
        ServerState.ERROR -> MinecraftRedstone to "ERRO"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Minecraft Server Address", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Endereço copiado: $text", Toast.LENGTH_SHORT).show()
}
