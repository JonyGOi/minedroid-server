package com.jonygoi.minedroidserver.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Text(
                    text = "Nenhum Servidor Selecionado",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Crie seu primeiro servidor na aba 'Servidores' para poder configurá-lo aqui.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var serverName by remember(currentConfig) { mutableStateOf(currentConfig.name) }
    var domainAddress by remember(currentConfig) { mutableStateOf(currentConfig.customDomain) }
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
        // Header
        Text(
            text = "Configurações: $serverName",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // Nome e Domínio
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Identificação & Endereço Amigável",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinecraftDiamond
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
                    value = domainAddress,
                    onValueChange = { domainAddress = it },
                    label = { Text("Endereço Amigável (Domínio)") },
                    placeholder = { Text("Ex: meu-mundo.craft.gg ou servidor.local") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinecraftDiamond,
                        unfocusedBorderColor = MinecraftBorder
                    )
                )
            }
        }

        // Section: Tipo de Servidor
        Text(
            text = "Motor do Servidor (Software)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MinecraftEmerald
        )

        ServerType.values().forEach { type ->
            val isSelected = selectedType == type
            val isRecommended = type == ServerType.PAPER
            val isProxyOnly = type == ServerType.GEYSER_STANDALONE
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MinecraftCardSurface else MinecraftDarkSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isSelected) MinecraftEmerald else if (isRecommended) MinecraftEmerald.copy(alpha = 0.5f) else MinecraftBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { selectedType = type }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedType = type },
                        colors = RadioButtonDefaults.colors(selectedColor = MinecraftEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = type.displayName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isRecommended -> MinecraftEmerald
                                    isProxyOnly -> TextMuted
                                    else -> TextPrimary
                                }
                            )
                            if (isRecommended) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MinecraftEmerald.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "RECOMENDADO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinecraftEmerald,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = type.category,
                            fontSize = 12.sp,
                            color = if (isProxyOnly) MinecraftGold else TextSecondary
                        )
                    }
                }
            }
        }

        // Section: Versão do Minecraft (se não for Geyser Standalone)
        if (selectedType != ServerType.GEYSER_STANDALONE) {
            Text(
                text = "Versão do Minecraft",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MinecraftEmerald
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServerConfig.AVAILABLE_VERSIONS.take(4).forEach { ver ->
                    val isVerSelected = selectedVersion == ver
                    FilterChip(
                        selected = isVerSelected,
                        onClick = { selectedVersion = ver },
                        label = { Text(ver) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinecraftEmerald,
                            selectedLabelColor = MinecraftObsidian,
                            containerColor = MinecraftDarkSurface,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServerConfig.AVAILABLE_VERSIONS.drop(4).take(4).forEach { ver ->
                    val isVerSelected = selectedVersion == ver
                    FilterChip(
                        selected = isVerSelected,
                        onClick = { selectedVersion = ver },
                        label = { Text(ver) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MinecraftEmerald,
                            selectedLabelColor = MinecraftObsidian,
                            containerColor = MinecraftDarkSurface,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
        }

        // Section: Crossplay GeyserMC ou Aviso de Proxy
        if (selectedType == ServerType.GEYSER_STANDALONE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MinecraftGold.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MinecraftGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GeyserMC Standalone (Sem Mundo Próprio)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinecraftGold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O modo Geyser Standalone é apenas um proxy/ponte para conectar celulares a outro servidor Java externo. Ele NÃO gera blocos, mundo nem mobs.\n\nPara ter um servidor completo onde você e seus amigos possam jogar juntos no mesmo mapa gerado pelo app, mude para PaperMC abaixo:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            selectedType = ServerType.PAPER
                            enableGeyser = true
                            enableFloodgate = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Converter para PaperMC (Mundo + Crossplay) ⭐",
                            fontWeight = FontWeight.Bold,
                            color = MinecraftObsidian,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if (selectedType == ServerType.PAPER || selectedType == ServerType.PURPUR) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Crossplay GeyserMC (Bedrock + Java)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinecraftLapis
                            )
                            Text(
                                text = "Permite que celulares, consoles (Switch, PS, Xbox) e Win10 joguem juntos no mesmo servidor Java!",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = enableGeyser,
                            onCheckedChange = { enableGeyser = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MinecraftObsidian,
                                checkedTrackColor = MinecraftLapis
                            )
                        )
                    }

                    if (enableGeyser) {
                        Spacer(modifier = Modifier.height(10.dp))
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
                                    text = "Jogadores Bedrock entram direto, sem precisar de conta de PC Java.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Checkbox(
                                checked = enableFloodgate,
                                onCheckedChange = { enableFloodgate = it },
                                colors = CheckboxDefaults.colors(checkedColor = MinecraftLapis)
                            )
                        }
                    }
                }
            }
        }

        // Section: Alocação de Memória RAM
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Memória RAM do Servidor",
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
                    text = "RAM Total do Celular: ${totalRam}MB (Recomendado: ${recommendedRam}MB)",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                // Aikar's GC Flags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Otimizações de JVM (Aikar's Flags)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MinecraftGold
                        )
                        Text(
                            text = "Elimina travamentos ajustando a coleta de lixo da JVM no celular.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = enableAikar,
                        onCheckedChange = { enableAikar = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MinecraftObsidian,
                            checkedTrackColor = MinecraftGold
                        )
                    )
                }
            }
        }

        // Section: Configurações do Jogo (server.properties se for Java)
        if (selectedType.isJava) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Opções do Jogo (server.properties)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // MOTD
                    OutlinedTextField(
                        value = motd,
                        onValueChange = { motd = it },
                        label = { Text("Nome do Servidor (MOTD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinecraftEmerald,
                            unfocusedBorderColor = MinecraftBorder
                        )
                    )

                    // Modo Pirata / Original
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!onlineMode) "Modo Pirata + Original (Recomendado)" else "Somente Contas Originais",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (!onlineMode) "Permite contas TLauncher, Pojav, etc." else "Exige conta oficial da Microsoft",
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

                    // PvP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Combate PvP Ativado", fontSize = 13.sp, color = TextPrimary)
                        Switch(
                            checked = pvp,
                            onCheckedChange = { pvp = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MinecraftObsidian,
                                checkedTrackColor = MinecraftEmerald
                            )
                        )
                    }

                    // Distancia de Renderizacao (View Distance)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Distância de Renderização (Chunks)", fontSize = 13.sp, color = TextPrimary)
                            Text(text = "${viewDistance.toInt()} chunks", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MinecraftEmerald)
                        }
                        Text(
                            text = "Valores entre 4 e 6 são recomendados para evitar superaquecimento no celular.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Slider(
                            value = viewDistance,
                            onValueChange = { viewDistance = it },
                            valueRange = 2f..12f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = MinecraftEmerald,
                                activeTrackColor = MinecraftEmerald,
                                inactiveTrackColor = MinecraftCardSurface
                            )
                        )
                    }

                    // Max Players
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Limite de Jogadores: $maxPlayers", fontSize = 13.sp, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { if (maxPlayers > 2) maxPlayers -= 2 },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("-")
                            }
                            FilledTonalButton(
                                onClick = { if (maxPlayers < 50) maxPlayers += 2 },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("+")
                            }
                        }
                    }
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                val updatedConfig = currentConfig.copy(
                    name = serverName.ifBlank { "Servidor Minecraft" },
                    customDomain = domainAddress,
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
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = MinecraftObsidian)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "SALVAR CONFIGURAÇÕES",
                fontWeight = FontWeight.Bold,
                color = MinecraftObsidian,
                fontSize = 15.sp
            )
        }
    }
}
