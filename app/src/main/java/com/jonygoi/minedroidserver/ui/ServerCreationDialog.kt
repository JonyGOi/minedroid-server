package com.jonygoi.minedroidserver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*
import java.util.UUID

@Composable
fun ServerCreationDialog(
    onDismiss: () -> Unit,
    onServerCreated: (ServerConfig) -> Unit
) {
    val context = LocalContext.current
    val (totalRam, _) = remember { OptimizationEngine.getDeviceRamInfo(context) }
    val recommendedRam = remember { OptimizationEngine.getRecommendedRam(totalRam) }
    val maxSelectableRam = if (totalRam > 1024) (totalRam - 512).toFloat() else 1024f

    var name by remember { mutableStateOf("Mundo dos Amigos") }
    var domainName by remember { mutableStateOf("meu-servidor.craft.gg") }
    var domainType by remember { mutableStateOf(DomainType.PLAYIT) }
    var selectedType by remember { mutableStateOf(ServerType.PAPER) }
    var selectedVersion by remember { mutableStateOf("1.21.1") }
    var ramMb by remember { mutableFloatStateOf(recommendedRam.toFloat()) }
    var enableGeyser by remember { mutableStateOf(true) }
    var enableFloodgate by remember { mutableStateOf(true) }
    var onlineMode by remember { mutableStateOf(false) } // false = Pirata + Original
    var viewDistance by remember { mutableFloatStateOf(6f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .border(1.dp, MinecraftBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Criar Novo Servidor",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Configuração guiada e automatizada",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Nome do Servidor
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Servidor") },
                        placeholder = { Text("Ex: Survival dos Amigos") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinecraftEmerald,
                            unfocusedBorderColor = MinecraftBorder
                        )
                    )

                    // 2. Endereço Amigável (Nome de Domínio)
                    Column {
                        Text(
                            text = "Endereço Amigável (Domínio para Conexão)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinecraftDiamond
                        )
                        Text(
                            text = "Em vez de um número de IP difícil, use um endereço nominal para passar aos amigos.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = domainName,
                            onValueChange = { domainName = it },
                            label = { Text("Endereço / Hostname") },
                            placeholder = { Text("Ex: amigos.craft.gg ou servidor.local") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MinecraftDiamond,
                                unfocusedBorderColor = MinecraftBorder
                            )
                        )
                    }

                    // 3. Motor do Servidor
                    Column {
                        Text(
                            text = "Motor do Servidor (Software)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinecraftEmerald
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ServerType.values().forEach { type ->
                            val isSel = selectedType == type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSel) MinecraftCardSurface else MinecraftObsidian,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSel) MinecraftEmerald else MinecraftBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedType = type }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = { selectedType = type },
                                    colors = RadioButtonDefaults.colors(selectedColor = MinecraftEmerald)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = type.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = type.category,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // 4. Versão
                    Column {
                        Text(
                            text = "Versão do Minecraft",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ServerConfig.AVAILABLE_VERSIONS.take(4).forEach { ver ->
                                FilterChip(
                                    selected = selectedVersion == ver,
                                    onClick = { selectedVersion = ver },
                                    label = { Text(ver, fontSize = 11.sp) },
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

                    // 5. Crossplay GeyserMC
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MinecraftCardSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Crossplay GeyserMC (Bedrock + Java)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinecraftLapis
                                    )
                                    Text(
                                        text = "Permite celulares, consoles e Win10 no servidor Java.",
                                        fontSize = 11.sp,
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
                        }
                    }

                    // 6. Alocação de Memória RAM
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Alocação de RAM",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${ramMb.toInt()} MB",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MinecraftEmerald
                            )
                        }
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
                    }

                    // 7. Modo Pirata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Pirata + Original",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Permite contas não-oficiais (TLauncher, Pojav, etc.)",
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create Action Button
                Button(
                    onClick = {
                        val newServer = ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { "Servidor Minecraft" },
                            customDomain = domainName.ifBlank { "meu-mundo.craft.gg" },
                            domainType = domainType,
                            serverType = selectedType,
                            version = selectedVersion,
                            allocatedRamMb = ramMb.toInt(),
                            enableGeyser = enableGeyser,
                            enableFloodgate = enableFloodgate,
                            onlineMode = onlineMode,
                            viewDistance = viewDistance.toInt()
                        )
                        onServerCreated(newServer)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "CRIAR SERVIDOR",
                        fontWeight = FontWeight.Bold,
                        color = MinecraftObsidian,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
