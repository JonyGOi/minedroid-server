package com.jonygoi.minedroidserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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

@Composable
fun DashboardScreen(
    onNavigateToConsole: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val context = LocalContext.current
    val servers by ServerManager.servers.collectAsState()
    val activeServerId by ServerManager.activeServerId.collectAsState()
    val state by ServerManager.state.collectAsState()
    val stats by ServerManager.stats.collectAsState()
    val downloadProg by ServerManager.downloadProgress.collectAsState()
    val logs by ServerManager.logs.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<ServerConfig?>(null) }

    val activeConfig = remember(servers, activeServerId) {
        servers.find { it.id == activeServerId } ?: servers.firstOrNull() ?: ServerConfig()
    }

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

    // Delete confirmation dialog
    serverToDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Excluir Servidor") },
            text = { Text("Deseja realmente excluir o servidor '${s.name}'? Todos os arquivos e mundos deste servidor serão apagados.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ServerManager.deleteServer(context, s.id)
                        serverToDelete = null
                        Toast.makeText(context, "Servidor removido", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Excluir", color = MinecraftRedstone, fontWeight = FontWeight.Bold)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinecraftObsidian)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MineDroid Server",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Gerenciador Global de Servidores",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Novo Servidor", color = MinecraftObsidian, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Active Server Download Progress
        if (state == ServerState.DOWNLOADING || state == ServerState.PREPARING) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MinecraftCardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = downloadProg.first.ifEmpty { "Preparando ambiente..." },
                        color = MinecraftEmerald,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProg.second },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MinecraftEmerald,
                        trackColor = MinecraftDarkSurface
                    )
                }
            }
        }

        // Section: Lista de Servidores
        Text(
            text = "Meus Servidores (${servers.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        servers.forEach { s ->
            val isActive = s.id == activeServerId
            val isServerRunning = isActive && (state == ServerState.RUNNING || state == ServerState.STARTING || state == ServerState.PREPARING || state == ServerState.DOWNLOADING)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MinecraftDarkSurface else MinecraftObsidian
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (isServerRunning) MinecraftEmerald else if (isActive) MinecraftLapis else MinecraftBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        if (!isServerRunning) {
                            ServerManager.selectServer(s.id)
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header of Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isServerRunning) MinecraftEmerald else TextPrimary
                            )
                            Text(
                                text = "${s.serverType.displayName} ${s.version} • ${s.allocatedRamMb}MB RAM",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        // Badge
                        StateBadge(state = if (isActive) state else ServerState.STOPPED)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Friendly Domain Addresses (Real Domains, not just raw IPs!)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MinecraftCardSurface, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Java Address
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Endereço Java (PC / Pojav):",
                                    fontSize = 10.sp,
                                    color = MinecraftEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = s.friendlyJavaAddress,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard(context, s.friendlyJavaAddress) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        if (s.enableGeyser) {
                            HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))
                            // Bedrock Address
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Endereço Bedrock (Celular / Console):",
                                        fontSize = 10.sp,
                                        color = MinecraftLapis,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = s.friendlyBedrockAddress,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard(context, s.friendlyBedrockAddress) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isServerRunning) {
                            Button(
                                onClick = { ServerManager.stopServer(context) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MinecraftRedstone),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PARAR", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = { ServerManager.startServer(context, s.id) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("INICIAR", fontWeight = FontWeight.Bold, color = MinecraftObsidian, fontSize = 13.sp)
                            }
                        }

                        // Terminal Button
                        OutlinedButton(
                            onClick = {
                                ServerManager.selectServer(s.id)
                                onNavigateToConsole()
                            },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinecraftBorder)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "Terminal", modifier = Modifier.size(18.dp))
                        }

                        // Config Button
                        OutlinedButton(
                            onClick = {
                                ServerManager.selectServer(s.id)
                                onNavigateToConfig()
                            },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinecraftBorder)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Configurar", modifier = Modifier.size(18.dp))
                        }

                        // Delete Button (only if not currently running)
                        if (!isServerRunning && servers.size > 1) {
                            IconButton(
                                onClick = { serverToDelete = s },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir", tint = MinecraftRedstone.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }

        // Active Server Live Performance (if running)
        if (state == ServerState.RUNNING || state == ServerState.STARTING) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Desempenho em Tempo Real - ${activeConfig.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            title = "TEMPO ONLINE",
                            value = stats.formattedUptime,
                            subtitle = "Uptime contínuo",
                            icon = Icons.Default.Timer,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "JOGADORES",
                            value = "${stats.onlinePlayers}/${activeConfig.maxPlayers}",
                            subtitle = "Online agora",
                            icon = Icons.Default.People,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Uso de RAM do Celular", fontSize = 12.sp, color = TextSecondary)
                        Text(
                            text = "${stats.usedRamMb}MB / ${stats.totalRamMb}MB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinecraftEmerald
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val ramFraction = if (stats.totalRamMb > 0) {
                        (stats.usedRamMb.toFloat() / stats.totalRamMb.toFloat()).coerceIn(0f, 1f)
                    } else 0.3f
                    LinearProgressIndicator(
                        progress = { ramFraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (ramFraction > 0.85f) MinecraftRedstone else MinecraftEmerald,
                        trackColor = MinecraftCardSurface
                    )
                }
            }

            // Quick Console Preview
            Card(
                colors = CardDefaults.cardColors(containerColor = ConsoleBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Console ao Vivo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = onNavigateToConsole) {
                            Text("Abrir Terminal Completo", color = MinecraftEmerald, fontSize = 13.sp)
                        }
                    }

                    val previewLogs = logs.takeLast(3)
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
                            maxLines = 2
                        )
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
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MinecraftCardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Minecraft Server Address", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Endereço copiado: $text", Toast.LENGTH_SHORT).show()
}
