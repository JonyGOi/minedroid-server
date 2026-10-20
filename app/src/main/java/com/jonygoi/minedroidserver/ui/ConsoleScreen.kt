package com.jonygoi.minedroidserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*
import kotlinx.coroutines.launch

@Composable
fun ConsoleScreen() {
    val context = LocalContext.current
    val logs by ServerManager.logs.collectAsState()
    val state by ServerManager.state.collectAsState()

    var commandText by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleBg)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MinecraftDarkSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateBadge(state = state)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Terminal ao Vivo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            Row {
                IconButton(onClick = {
                    val cfg = ServerManager.getActiveConfig()
                    if (cfg != null) {
                        TermuxBridge.launchInTermux(context, cfg)
                    } else {
                        Toast.makeText(context, "Nenhum servidor selecionado", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Executar no Termux",
                        tint = MinecraftDiamond
                    )
                }

                IconButton(onClick = {
                    autoScroll = !autoScroll
                    Toast.makeText(
                        context,
                        if (autoScroll) "Auto-rolagem Ativada" else "Auto-rolagem Desativada",
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Icon(
                        imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignCenter,
                        contentDescription = "Auto Scroll",
                        tint = if (autoScroll) MinecraftEmerald else TextMuted
                    )
                }

                IconButton(onClick = {
                    val allText = logs.joinToString("\n") { "[${it.formattedTime}] ${it.text}" }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Minecraft Logs", allText))
                    Toast.makeText(context, "Todos os logs foram copiados!", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar Logs",
                        tint = TextSecondary
                    )
                }

                IconButton(onClick = { ServerManager.clearLogs() }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Limpar Console",
                        tint = TextMuted
                    )
                }
            }
        }

        // High-Performance Termux Banner
        val activeCfg = ServerManager.getActiveConfig()
        if (activeCfg != null && state == ServerState.STOPPED) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = MinecraftCardSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MinecraftBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ Executar com Máximo Processamento",
                            color = MinecraftDiamond,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Inicie via Termux com Java nativo e 100% da CPU",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            TermuxBridge.launchInTermux(context, activeCfg)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MinecraftObsidian, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Termux", color = MinecraftObsidian, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Terminal Log List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Console aguardando inicialização do servidor...",
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        val textColor = when (log.type) {
                            LogType.ERROR -> ConsoleRed
                            LogType.WARN -> ConsoleYellow
                            LogType.JOIN -> ConsoleGreen
                            LogType.LEAVE -> ConsoleYellow
                            LogType.SYSTEM -> ConsoleCyan
                            LogType.COMMAND -> MinecraftGold
                            else -> TextPrimary
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${log.formattedTime} ",
                                color = ConsoleGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = log.text,
                                color = textColor,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Quick Command Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MinecraftDarkSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickCommands = listOf("/tps", "/list", "/op ", "/whitelist on", "/gamemode creative ", "/time set day", "/weather clear", "/stop")
            quickCommands.forEach { cmd ->
                SuggestionChip(
                    onClick = {
                        if (cmd.endsWith(" ")) {
                            commandText = cmd
                        } else {
                            ServerManager.sendCommand(cmd.removePrefix("/"))
                        }
                    },
                    label = { Text(cmd, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MinecraftCardSurface,
                        labelColor = MinecraftEmerald
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MinecraftBorder
                    )
                )
            }
        }

        // Command Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MinecraftDarkSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                placeholder = { Text("Digite um comando (ex: /op nome)", color = TextMuted, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TextPrimary
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MinecraftEmerald,
                    unfocusedBorderColor = MinecraftBorder,
                    focusedContainerColor = ConsoleBg,
                    unfocusedContainerColor = ConsoleBg
                ),
                shape = RoundedCornerShape(8.dp)
            )

            IconButton(
                onClick = {
                    if (commandText.isNotBlank()) {
                        val clean = commandText.trim().removePrefix("/")
                        ServerManager.sendCommand(clean)
                        commandText = ""
                        coroutineScope.launch {
                            if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
                        }
                    }
                },
                modifier = Modifier
                    .background(MinecraftEmerald, RoundedCornerShape(8.dp))
                    .size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = MinecraftObsidian)
            }
        }
    }
}
