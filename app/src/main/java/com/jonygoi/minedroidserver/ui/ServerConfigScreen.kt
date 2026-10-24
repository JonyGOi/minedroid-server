package com.jonygoi.minedroidserver.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    var isPaperPure by remember(currentConfig) { mutableStateOf(currentConfig.serverType == ServerType.PAPER && !currentConfig.enableGeyser) }
    var selectedVersion by remember(currentConfig) { mutableStateOf(currentConfig.version) }
    var ramMb by remember(currentConfig) { mutableFloatStateOf(currentConfig.allocatedRamMb.toFloat()) }
    var enableGeyser by remember(currentConfig) { mutableStateOf(currentConfig.enableGeyser) }
    var enableFloodgate by remember(currentConfig) { mutableStateOf(currentConfig.enableFloodgate) }
    var geyserTransport by remember(currentConfig) { mutableStateOf(currentConfig.geyserTransport) }
    var enableAikar by remember(currentConfig) { mutableStateOf(currentConfig.enableAikarFlags) }
    var onlineMode by remember(currentConfig) { mutableStateOf(currentConfig.onlineMode) }
    var pvp by remember(currentConfig) { mutableStateOf(currentConfig.pvp) }
    var motd by remember(currentConfig) { mutableStateOf(currentConfig.motd) }
    var maxPlayers by remember(currentConfig) { mutableIntStateOf(currentConfig.maxPlayers) }
    var viewDistance by remember(currentConfig) { mutableFloatStateOf(currentConfig.viewDistance.toFloat()) }
    var simulationDistance by remember(currentConfig) { mutableFloatStateOf(currentConfig.simulationDistance.toFloat()) }
    var difficulty by remember(currentConfig) { mutableStateOf(currentConfig.difficulty) }
    var gamemode by remember(currentConfig) { mutableStateOf(currentConfig.gamemode) }

    // Novas configurações
    var levelName by remember(currentConfig) { mutableStateOf(currentConfig.levelName) }
    var levelSeed by remember(currentConfig) { mutableStateOf(currentConfig.levelSeed) }
    var allowFlight by remember(currentConfig) { mutableStateOf(currentConfig.allowFlight) }
    var hardcore by remember(currentConfig) { mutableStateOf(currentConfig.hardcore) }
    var spawnAnimals by remember(currentConfig) { mutableStateOf(currentConfig.spawnAnimals) }
    var spawnMonsters by remember(currentConfig) { mutableStateOf(currentConfig.spawnMonsters) }
    var spawnNpcs by remember(currentConfig) { mutableStateOf(currentConfig.spawnNpcs) }
    var whiteList by remember(currentConfig) { mutableStateOf(currentConfig.whiteList) }
    var enableCommandBlock by remember(currentConfig) { mutableStateOf(currentConfig.enableCommandBlock) }
    var spawnProtection by remember(currentConfig) { mutableFloatStateOf(currentConfig.spawnProtection.toFloat()) }

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
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSaved) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Configurações do Servidor",
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

        // Card 1: Identificação & Conexão
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
                    text = "Identificação & MOTD",
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

                OutlinedTextField(
                    value = motd,
                    onValueChange = { motd = it },
                    label = { Text("Mensagem da Lista de Servidores (MOTD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftEmerald,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )

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
                            if (enableGeyser || currentConfig.serverType == ServerType.GEYSER_STANDALONE) {
                                Text(
                                    text = "Bedrock: ${currentConfig.friendlyBedrockAddress}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (currentConfig.bedrockDomain.isNotBlank()) MinecraftLapis else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Motor e Versão do Servidor (Com Paper Puro em destaque!)
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

                // Opções de Motores com Paper Puro e Paper Crossplay separados
                val engineOptions = listOf(
                    Triple(ServerType.PAPER, false, "PaperMC Puro (Apenas Java Edition)"),
                    Triple(ServerType.PAPER, true, "PaperMC + Crossplay (Java + Bedrock Mobile)"),
                    Triple(ServerType.PURPUR, true, "Purpur (Ultra Otimizado + Crossplay)"),
                    Triple(ServerType.VANILLA, false, "Vanilla (Oficial Mojang Puro)"),
                    Triple(ServerType.FABRIC, false, "Fabric (Java Modded)"),
                    Triple(ServerType.GEYSER_STANDALONE, true, "GeyserMC Proxy (Apenas Ponte)")
                )

                engineOptions.forEach { (type, crossplay, label) ->
                    val isSel = if (type == ServerType.PAPER) {
                        selectedType == ServerType.PAPER && (if (crossplay) enableGeyser else !enableGeyser)
                    } else {
                        selectedType == type
                    }

                    Surface(
                        color = if (isSel) MinecraftCardSurface else MinecraftDarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSel) MinecraftEmerald else MinecraftBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedType = type
                                if (type == ServerType.PAPER) {
                                    if (crossplay) {
                                        enableGeyser = true
                                        enableFloodgate = true
                                        isPaperPure = false
                                    } else {
                                        enableGeyser = false
                                        enableFloodgate = false
                                        isPaperPure = true
                                    }
                                } else if (type == ServerType.PURPUR) {
                                    enableGeyser = true
                                    enableFloodgate = true
                                    isPaperPure = false
                                } else {
                                    enableGeyser = false
                                    enableFloodgate = false
                                    isPaperPure = false
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = {
                                    selectedType = type
                                    if (type == ServerType.PAPER) {
                                        enableGeyser = crossplay
                                        enableFloodgate = crossplay
                                        isPaperPure = !crossplay
                                    }
                                },
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

        // Card 3: Mundo & Geração
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
                    text = "Mundo & Geração",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = levelName,
                    onValueChange = { levelName = it },
                    label = { Text("Nome da Pasta do Mundo (level-name)") },
                    placeholder = { Text("world") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftEmerald,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )

                OutlinedTextField(
                    value = levelSeed,
                    onValueChange = { levelSeed = it },
                    label = { Text("Semente do Mundo (Seed - Opcional)") },
                    placeholder = { Text("Ex: 123456789 ou deixe vazio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftEmerald,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )

                // Distância de Visão (Chunks)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Distância de Renderização (View Distance)", fontSize = 13.sp, color = TextPrimary)
                        Text("${viewDistance.toInt()} chunks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MinecraftEmerald)
                    }
                    Slider(
                        value = viewDistance,
                        onValueChange = { viewDistance = it },
                        valueRange = 3f..16f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = MinecraftEmerald,
                            activeTrackColor = MinecraftEmerald,
                            inactiveTrackColor = MinecraftCardSurface
                        )
                    )
                }

                // Distância de Simulação (Simulation Distance)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Distância de Simulação (Mobs / Chunks Ativos)", fontSize = 13.sp, color = TextPrimary)
                        Text("${simulationDistance.toInt()} chunks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MinecraftLapis)
                    }
                    Slider(
                        value = simulationDistance,
                        onValueChange = { simulationDistance = it },
                        valueRange = 3f..12f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MinecraftLapis,
                            activeTrackColor = MinecraftLapis,
                            inactiveTrackColor = MinecraftCardSurface
                        )
                    )
                }
            }
        }

        // Card 4: Jogabilidade & Regras
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
                    text = "Jogabilidade & Regras",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Modo de Jogo (Chips)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Modo de Jogo Padrão", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("survival" to "Sobrevivência", "creative" to "Criativo", "adventure" to "Aventura", "spectator" to "Espectador").forEach { (code, title) ->
                            val isSel = gamemode == code
                            FilterChip(
                                selected = isSel,
                                onClick = { gamemode = code },
                                label = { Text(title) },
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

                // Dificuldade (Chips)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dificuldade", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("peaceful" to "Pacífico", "easy" to "Fácil", "normal" to "Normal", "hard" to "Difícil").forEach { (code, title) ->
                            val isSel = difficulty == code
                            FilterChip(
                                selected = isSel,
                                onClick = { difficulty = code },
                                label = { Text(title) },
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

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // PvP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Combate PvP", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Permite dano entre jogadores.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = pvp,
                        onCheckedChange = { pvp = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Hardcore
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Hardcore", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Morte permanente (jogador vira espectador).", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = hardcore,
                        onCheckedChange = { hardcore = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftRedstone)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Permitir Voo (allow-flight)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permitir Voo no Survival (allow-flight)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Evita que jogadores com lag ou mods sejam kickados por voar.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = allowFlight,
                        onCheckedChange = { allowFlight = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Blocos de Comando
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blocos de Comando (Command Blocks)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Habilita o funcionamento de command blocks no mapa.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = enableCommandBlock,
                        onCheckedChange = { enableCommandBlock = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }
            }
        }

        // Card 5: Entidades & Spawn
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
                    text = "Entidades & Spawn",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Gerar Monstros
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gerar Monstros (Hostis)", fontSize = 13.sp, color = TextPrimary)
                        Text("Zumbis, esqueletos, creepers, etc.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = spawnMonsters,
                        onCheckedChange = { spawnMonsters = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Gerar Animais
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gerar Animais (Passivos)", fontSize = 13.sp, color = TextPrimary)
                        Text("Vacas, porcos, ovelhas, etc.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = spawnAnimals,
                        onCheckedChange = { spawnAnimals = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Gerar NPCs / Aldeões
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gerar Aldeões (NPCs)", fontSize = 13.sp, color = TextPrimary)
                        Text("Aldeões nas vilas para comércio.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = spawnNpcs,
                        onCheckedChange = { spawnNpcs = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Raio de Proteção do Spawn
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proteção do Ponto de Spawn", fontSize = 13.sp, color = TextPrimary)
                        Text("${spawnProtection.toInt()} blocos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MinecraftEmerald)
                    }
                    Slider(
                        value = spawnProtection,
                        onValueChange = { spawnProtection = it },
                        valueRange = 0f..32f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MinecraftEmerald,
                            activeTrackColor = MinecraftEmerald,
                            inactiveTrackColor = MinecraftCardSurface
                        )
                    )
                }
            }
        }

        // Card 6: Acesso & Segurança
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
                    text = "Acesso & Segurança",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Modo Pirata + Original
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
                            text = if (!onlineMode) "Permite contas TLauncher, PojavLauncher e Bedrock." else "Exige conta oficial Mojang/Microsoft.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = !onlineMode,
                        onCheckedChange = { onlineMode = !it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                    )
                }

                HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))

                // Whitelist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lista Branca (Whitelist)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Apenas jogadores adicionados em whitelist.json podem entrar.", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = whiteList,
                        onCheckedChange = { whiteList = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
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

                if (enableGeyser) {
                    HorizontalDivider(color = MinecraftBorder.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (enableFloodgate) "Floodgate (Exige Conta Microsoft no Bedrock)" else "Modo Bedrock Offline / Pirata",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (enableFloodgate) "Autentica jogadores Bedrock via Microsoft/Xbox." else "Desativa checagem Microsoft no Bedrock, permitindo qualquer versão/APK entrar sem erro de criptografia.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = enableFloodgate,
                            onCheckedChange = { enableFloodgate = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MinecraftObsidian, checkedTrackColor = MinecraftEmerald)
                        )
                    }

                    if (enableFloodgate) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Criptografia Bedrock (Floodgate)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(
                                text = "Se jogadores Bedrock receberem 'Erro ao ler criptografia' (Tag mismatch), clique abaixo para redefinir as chaves de segurança.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            OutlinedButton(
                                onClick = {
                                    val sDir = ServerInstaller.getServerDirectory(context, currentConfig.id)
                                    ServerInstaller.regenerateFloodgateKeys(sDir)
                                    Toast.makeText(context, "Chaves de criptografia redefinidas! O servidor irá gerar novas chaves idênticas ao iniciar.", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MinecraftDiamond),
                                border = BorderStroke(1.dp, MinecraftDiamond.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp), tint = MinecraftDiamond)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reparar Chaves de Criptografia Bedrock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Protocolo de Transporte Bedrock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "O Playit.gg opera via RakNet (UDP). Mantenha em RakNet para que o túnel Playit funcione com Bedrock.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = geyserTransport == "raknet",
                            onClick = { geyserTransport = "raknet" },
                            label = { Text("RakNet (Recomendado Playit)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinecraftEmerald,
                                selectedLabelColor = MinecraftObsidian,
                                containerColor = MinecraftCardSurface,
                                labelColor = TextPrimary
                            )
                        )
                        FilterChip(
                            selected = geyserTransport == "nethernet",
                            onClick = { geyserTransport = "nethernet" },
                            label = { Text("NetherNet") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MinecraftGold,
                                selectedLabelColor = MinecraftObsidian,
                                containerColor = MinecraftCardSurface,
                                labelColor = TextPrimary
                            )
                        )
                    }
                    if (geyserTransport == "nethernet") {
                        Text(
                            text = "⚠️ ATENÇÃO: O túnel gratuito do Playit.gg NÃO suporta NetherNet (pois NetherNet requer sinalização TCP e o Playit Bedrock é apenas UDP). Para jogar via Playit.gg, selecione RakNet!",
                            fontSize = 11.sp,
                            color = MinecraftRedstone,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Card 7: Memória RAM & Desempenho
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
                        text = "Alocação de Memória RAM",
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
                    text = "Celular: ${totalRam}MB totais (Recomendado: ${recommendedRam}MB)",
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
                            text = "Parâmetros do Garbage Collector da JVM para manter 20 TPS estáveis.",
                            fontSize = 11.sp,
                            color = TextSecondary
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

        // Botão Salvar (Full Width, 50dp)
        Button(
            onClick = {
                val updatedConfig = currentConfig.copy(
                    name = serverName.ifBlank { "Servidor Minecraft" },
                    serverType = selectedType,
                    version = selectedVersion,
                    allocatedRamMb = ramMb.toInt(),
                    enableGeyser = enableGeyser,
                    enableFloodgate = enableFloodgate,
                    geyserTransport = geyserTransport,
                    enableAikarFlags = enableAikar,
                    onlineMode = onlineMode,
                    pvp = pvp,
                    motd = motd,
                    maxPlayers = maxPlayers,
                    viewDistance = viewDistance.toInt(),
                    simulationDistance = simulationDistance.toInt(),
                    difficulty = difficulty,
                    gamemode = gamemode,
                    levelName = levelName.ifBlank { "world" },
                    levelSeed = levelSeed.trim(),
                    allowFlight = allowFlight,
                    hardcore = hardcore,
                    spawnAnimals = spawnAnimals,
                    spawnMonsters = spawnMonsters,
                    spawnNpcs = spawnNpcs,
                    whiteList = whiteList,
                    enableCommandBlock = enableCommandBlock,
                    spawnProtection = spawnProtection.toInt()
                )
                ServerManager.updateServer(context, updatedConfig)
                Toast.makeText(context, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()
                onSaved()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
            shape = RoundedCornerShape(12.dp)
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
