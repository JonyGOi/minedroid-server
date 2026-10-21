package com.jonygoi.minedroidserver.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val servers by ServerManager.servers.collectAsState()
    val activeServerId by ServerManager.activeServerId.collectAsState()
    val currentConfig = remember(servers, activeServerId) { ServerManager.getActiveConfig() }

    if (currentConfig == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MinecraftObsidian)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Nenhum servidor selecionado",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Button(onClick = onSaved, colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald)) {
                    Text("Voltar ao Painel", color = MinecraftObsidian, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    var serverName by remember(currentConfig) { mutableStateOf(currentConfig.name) }
    var selectedType by remember(currentConfig) { mutableStateOf(currentConfig.serverType) }
    var selectedVersion by remember(currentConfig) { mutableStateOf(currentConfig.version) }
    var ramMb by remember(currentConfig) { mutableFloatStateOf(currentConfig.allocatedRamMb.toFloat()) }
    var enableGeyser by remember(currentConfig) { mutableStateOf(currentConfig.enableGeyser) }
    var enableFloodgate by remember(currentConfig) { mutableStateOf(currentConfig.enableFloodgate) }
    var enableAikar by remember(currentConfig) { mutableStateOf(currentConfig.enableAikarFlags) }
    var onlineMode by remember(currentConfig) { mutableStateOf(currentConfig.onlineMode) }
    var pvp by remember(currentConfig) { mutableStateOf(currentConfig.pvp) }
    var motd by remember(currentConfig) { mutableStateOf(currentConfig.motd) }
    var maxPlayers by remember(currentConfig) { mutableIntStateOf(currentConfig.maxPlayers) }
    var viewDistance by remember(currentConfig) { mutableFloatStateOf(currentConfig.viewDistance.toFloat()) }
    var simulationDistance by remember(currentConfig) { mutableFloatStateOf(currentConfig.simulationDistance.toFloat()) }
    var difficulty by remember(currentConfig) { mutableStateOf(currentConfig.difficulty) }
    var gamemode by remember(currentConfig) { mutableStateOf(currentConfig.gamemode) }

    val (totalRam, _) = remember { OptimizationEngine.getDeviceRamInfo(context) }
    val maxSelectableRam = if (totalRam > 1024) (totalRam - 512).toFloat() else 1024f
    val recommendedRam = remember { OptimizationEngine.getRecommendedRam(totalRam) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinecraftObsidian)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Bar com botão Voltar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSaved) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Configurações",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = serverName,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Card 1: Identificação do Servidor
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MinecraftBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Identificação",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Nome do Servidor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftEmerald,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )

                // Informações dos endereços Playit (somente-leitura)
                if (currentConfig.enablePlayitTunnel) {
                    Surface(
                        color = MinecraftCardSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MinecraftDiamond, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Endereço Playit.gg (Automático)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MinecraftDiamond
                                )
                            }
                            if (currentConfig.customDomain.isNotBlank()) {
                                Text(
                                    text = "Java: ${currentConfig.friendlyJavaAddress}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MinecraftEmerald,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (currentConfig.bedrockDomain.isNotBlank()) {
                                Text(
                                    text = "Bedrock: ${currentConfig.friendlyBedrockAddress}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MinecraftLapis,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Motor e Versão do Servidor
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MinecraftBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Motor & Versão",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Seleção compacta dos motores principais
                listOf(
                    ServerType.PAPER to "PaperMC (Recomendado com Mundo)",
                    ServerType.PURPUR to "Purpur (Ultra Otimizado)",
                    ServerType.VANILLA to "Vanilla (Oficial Mojang)",
                    ServerType.FABRIC to "Fabric (Modded)",
                    ServerType.GEYSER_STANDALONE to "GeyserMC Proxy (Apenas Ponte)"
                ).forEach { (type, label) ->
                    val isSel = selectedType == type
                    Surface(
                        color = if (isSel) MinecraftCardSurface else MinecraftDarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSel) MinecraftEmerald else MinecraftBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = MinecraftEmerald),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) TextPrimary else TextSecondary
                            )
                        }
                    }
                }

                // Alerta se Geyser Standalone estiver selecionado
                if (selectedType == ServerType.GEYSER_STANDALONE) {
                    Surface(
                        color = MinecraftGold.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MinecraftGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Atenção: Modo Proxy não gera mundo!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinecraftGold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ele apenas repassa conexões para outro servidor Java externo.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Seleção de Versão (Chips roláveis horizontais com versões reais)
                if (selectedType != ServerType.GEYSER_STANDALONE) {
                    Text(
                        text = "Versão do Minecraft",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ServerConfig.AVAILABLE_VERSIONS.forEach { ver ->
                            val isVerSel = selectedVersion == ver
                            FilterChip(
                                selected = isVerSel,
                                onClick = { selectedVersion = ver },
                                label = { Text(ver) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MinecraftEmerald,
                                    selectedLabelColor = MinecraftObsidian,
                                    containerColor = MinecraftCardSurface,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Card 3: Crossplay Bedrock + Java
        if (selectedType == ServerType.PAPER || selectedType == ServerType.PURPUR) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
                shape = RoundedCornerShape(14.dp),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Crossplay Bedrock (GeyserMC)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Permite jogadores de Celular, Console e PC no mesmo mundo.",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = enableGeyser,
                            onCheckedChange = { enableGeyser = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MinecraftObsidian,
                                checkedTrackColor = MinecraftEmerald
                            )
                        )
                    }

                    if (enableGeyser) {
                        HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ativar Floodgate",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Jogadores de celular entram direto sem precisar de conta de PC.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Checkbox(
                                checked = enableFloodgate,
                                onCheckedChange = { enableFloodgate = it },
                                colors = CheckboxDefaults.colors(checkedColor = MinecraftEmerald)
                            )
                        }
                    }
                }
            }
        }

        // Card 4: Memória RAM & Otimização
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MinecraftBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Memória RAM",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${ramMb.toInt()} MB",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinecraftEmerald
                    )
                }

                Text(
                    text = "Aparelho: ${totalRam}MB totais (Recomendado: ${recommendedRam}MB)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Slider(
                    value = ramMb,
                    onValueChange = { ramMb = it },
                    valueRange = 512f..maxSelectableRam,
                    steps = ((maxSelectableRam - 512f) / 256f).toInt(),
                    colors = SliderDefaults.colors(
                        thumbColor = MinecraftEmerald,
                        activeTrackColor = MinecraftEmerald,
                        inactiveTrackColor = MinecraftCardSurface
                    )
                )

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Aikar's Flags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aikar's Flags (Otimização JVM)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ajusta o Garbage Collector da JVM para manter TPS estável.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = enableAikar,
                        onCheckedChange = { enableAikar = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MinecraftObsidian,
                            checkedTrackColor = MinecraftEmerald
                        )
                    )
                }
            }
        }

        // Card 5: Regras de Jogo
        if (selectedType.isJava) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MinecraftBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Regras do Jogo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Modo Pirata / Original
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!onlineMode) "Modo Pirata + Original" else "Apenas Contas Oficiais",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (!onlineMode) "Permite contas TLauncher, Pojav e Bedrock." else "Exige conta oficial Microsoft.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = !onlineMode,
                            onCheckedChange = { onlineMode = !it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MinecraftObsidian,
                                checkedTrackColor = MinecraftEmerald
                            )
                        )
                    }

                    HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                    // PvP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Combate PvP Habilitado", fontSize = 13.sp, color = TextPrimary)
                        Switch(
                            checked = pvp,
                            onCheckedChange = { pvp = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MinecraftObsidian,
                                checkedTrackColor = MinecraftEmerald
                            )
                        )
                    }

                    HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                    // Distância de Renderização
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Distância de Visão", fontSize = 13.sp, color = TextPrimary)
                            Text("${viewDistance.toInt()} chunks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MinecraftEmerald)
                        }
                        Slider(
                            value = viewDistance,
                            onValueChange = { viewDistance = it },
                            valueRange = 2f..10f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = MinecraftEmerald,
                                activeTrackColor = MinecraftEmerald,
                                inactiveTrackColor = MinecraftCardSurface
                            )
                        )
                    }

                    HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                    // Limite de Jogadores
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Limite de Jogadores: $maxPlayers", fontSize = 13.sp, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (maxPlayers > 2) maxPlayers -= 2 },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, MinecraftBorder)
                            ) {
                                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { if (maxPlayers < 50) maxPlayers += 2 },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = BorderStroke(1.dp, MinecraftBorder)
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Botão Salvar
        Button(
            onClick = {
                val updatedConfig = currentConfig.copy(
                    name = serverName.ifBlank { "Servidor Minecraft" },
                    customDomain = currentConfig.customDomain,
                    bedrockDomain = currentConfig.bedrockDomain,
                    serverType = selectedType,
                    version = selectedVersion,
                    allocatedRamMb = ramMb.toInt(),
                    enableGeyser = enableGeyser,
                    enableFloodgate = enableFloodgate,
                    enableAikarFlags = enableAikar,
                    onlineMode = onlineMode,
                    pvp = pvp,
                    motd = motd,
                    maxPlayers = maxPlayers,
                    viewDistance = viewDistance.toInt(),
                    simulationDistance = simulationDistance.toInt(),
                    difficulty = difficulty,
                    gamemode = gamemode
                )
                ServerManager.updateServer(context, updatedConfig)
                Toast.makeText(context, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()
                onSaved()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = MinecraftObsidian)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "SALVAR CONFIGURAÇÕES",
                fontWeight = FontWeight.Bold,
                color = MinecraftObsidian,
                fontSize = 14.sp
            )
        }
    }
}
