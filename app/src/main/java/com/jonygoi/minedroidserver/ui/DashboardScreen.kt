package com.jonygoi.minedroidserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val state by ServerManager.state.collectAsState()
    val config by ServerManager.config.collectAsState()
    val stats by ServerManager.stats.collectAsState()
    val downloadProg by ServerManager.downloadProgress.collectAsState()
    val logs by ServerManager.logs.collectAsState()

    val localIp = remember { NetworkHelper.getLocalIpAddress() }

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
                    text = "Hospedagem Minecraft no Celular",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // Status Badge
            StateBadge(state = state)
        }

        // Downloading Progress if active
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

        // Server Profile Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${config.serverType.displayName} ${config.version}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinecraftEmerald
                        )
                        Text(
                            text = if (config.enableGeyser) "Crossplay Bedrock (GeyserMC) Ativado" else "Modo Somente Java",
                            fontSize = 12.sp,
                            color = if (config.enableGeyser) MinecraftLapis else TextMuted
                        )
                    }

                    IconButton(onClick = onNavigateToConfig) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${config.allocatedRamMb} MB RAM") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MinecraftCardSurface,
                            labelColor = TextPrimary
                        )
                    )
                    if (config.enableAikarFlags) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Aikar's Flags") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MinecraftCardSurface,
                                labelColor = MinecraftGold
                            )
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(if (config.onlineMode) "Original" else "Pirata+Original") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MinecraftCardSurface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state == ServerState.STOPPED || state == ServerState.ERROR) {
                Button(
                    onClick = { ServerManager.startServer(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MinecraftObsidian)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "INICIAR SERVIDOR",
                        fontWeight = FontWeight.Bold,
                        color = MinecraftObsidian,
                        fontSize = 15.sp
                    )
                }
            } else {
                Button(
                    onClick = { ServerManager.stopServer(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftRedstone),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "PARAR SERVIDOR",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }

                FilledTonalButton(
                    onClick = { ServerManager.killServer(context) },
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MinecraftCardSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Forçar Parada", tint = MinecraftRedstone)
                }
            }
        }

        // Connection Addresses
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Endereços para Conectar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Seus amigos conectados no mesmo Wi-Fi podem entrar usando:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Java Address
                ConnectionRow(
                    label = "Computador (Java)",
                    address = "$localIp:${config.serverPort}",
                    tagColor = MinecraftEmerald,
                    onCopy = { copyToClipboard(context, "$localIp:${config.serverPort}") }
                )

                if (config.enableGeyser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Bedrock Address
                    ConnectionRow(
                        label = "Celular / Console (Bedrock)",
                        address = "$localIp:${config.geyserPort}",
                        tagColor = MinecraftLapis,
                        onCopy = { copyToClipboard(context, "$localIp:${config.geyserPort}") }
                    )
                }
            }
        }

        // Real-time Statistics
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
                value = "${stats.onlinePlayers}/${config.maxPlayers}",
                subtitle = "Online agora",
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
        }

        // Hardware Gauges
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Uso de Recursos do Celular",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Memória RAM Total / Usada", fontSize = 13.sp, color = TextSecondary)
                    Text(
                        text = "${stats.usedRamMb}MB / ${stats.totalRamMb}MB",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinecraftEmerald
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
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

        // Live Console Preview
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
                        text = "Logs Recentes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = onNavigateToConsole) {
                        Text("Ver Console Completo", color = MinecraftEmerald, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val previewLogs = logs.takeLast(4)
                if (previewLogs.isEmpty()) {
                    Text(
                        text = "Nenhum log no momento. Inicie o servidor para ver a saída.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
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
fun ConnectionRow(
    label: String,
    address: String,
    tagColor: Color,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MinecraftCardSurface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, fontSize = 11.sp, color = tagColor, fontWeight = FontWeight.Bold)
            Text(
                text = address,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary)
        }
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
        colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Minecraft Server IP", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Endereço copiado: $text", Toast.LENGTH_SHORT).show()
}
