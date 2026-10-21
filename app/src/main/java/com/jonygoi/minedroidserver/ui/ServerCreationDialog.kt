package com.jonygoi.minedroidserver.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerCreationDialog(
    onDismiss: () -> Unit,
    onServerCreated: (ServerConfig) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val (totalRam, _) = remember { OptimizationEngine.getDeviceRamInfo(context) }
    val recommendedRam = remember { OptimizationEngine.getRecommendedRam(totalRam) }
    val maxSelectableRam = if (totalRam > 1024) (totalRam - 512).toFloat() else 1024f

    var name by remember { mutableStateOf("Mundo dos Amigos") }
    var selectedType by remember { mutableStateOf(ServerType.PAPER) }
    var selectedVersion by remember { mutableStateOf("1.21.1") }
    var versionExpanded by remember { mutableStateOf(false) }

    var enablePlayit by remember { mutableStateOf(true) }
    var claimLoading by remember { mutableStateOf(false) }
    var activeClaimCode by remember { mutableStateOf<String?>(null) }
    var activeClaimUrl by remember { mutableStateOf<String?>(null) }
    var claimLiveStatus by remember { mutableStateOf("Conectando ao Playit.gg...") }
    var manualSecretKey by remember { mutableStateOf(PlayitManager.getSavedSecretKey(context) ?: "") }

    var ramMb by remember { mutableFloatStateOf(recommendedRam.toFloat()) }
    var enableGeyser by remember { mutableStateOf(true) }
    var enableFloodgate by remember { mutableStateOf(true) }
    var onlineMode by remember { mutableStateOf(false) } // false = Pirata + Original
    var viewDistance by remember { mutableFloatStateOf(6f) }

    val quickVersions = remember { listOf("1.21.1", "1.21.4", "1.20.4", "1.16.5") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            border = BorderStroke(1.dp, MinecraftBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Criar Novo Servidor",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Configuração completa e automatizada",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = TextMuted
                        )
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
                        placeholder = { Text("Ex: Survival com a Galera") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinecraftEmerald,
                            unfocusedBorderColor = MinecraftBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = MinecraftEmerald,
                            unfocusedLabelColor = TextSecondary
                        )
                    )

                    // 2. Motor do Servidor (Software)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Motor do Servidor",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        listOf(
                            Triple(ServerType.PAPER, false, "PaperMC Puro (Apenas Java Edition)"),
                            Triple(ServerType.PAPER, true, "PaperMC + Crossplay (Java + Bedrock Mobile)"),
                            Triple(ServerType.PURPUR, true, "Purpur (Ultra Otimizado + Crossplay)"),
                            Triple(ServerType.VANILLA, false, "Vanilla (Oficial Mojang Puro)"),
                            Triple(ServerType.FABRIC, false, "Fabric (Java Modded)"),
                            Triple(ServerType.GEYSER_STANDALONE, true, "GeyserMC Proxy (Apenas Ponte)")
                        ).forEach { (type, crossplay, label) ->
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
                                            enableGeyser = crossplay
                                            enableFloodgate = crossplay
                                        } else if (type == ServerType.PURPUR) {
                                            enableGeyser = true
                                            enableFloodgate = true
                                        } else {
                                            enableGeyser = false
                                            enableFloodgate = false
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

                        if (selectedType == ServerType.GEYSER_STANDALONE) {
                            Surface(
                                color = MinecraftGold.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MinecraftGold.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MinecraftGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Aviso: GeyserMC Standalone é apenas uma ponte/proxy. Ele NÃO cria um mundo próprio. Para jogar survival no celular e PC, selecione PaperMC.",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 3. Versão do Minecraft
                    if (selectedType != ServerType.GEYSER_STANDALONE) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Versão do Minecraft",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            // Quick version chips
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickVersions.forEach { ver ->
                                    val isSel = selectedVersion == ver
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedVersion = ver },
                                        label = { Text(ver, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MinecraftEmerald.copy(alpha = 0.2f),
                                            selectedLabelColor = MinecraftEmerald
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = if (isSel) MinecraftEmerald else MinecraftBorder,
                                            enabled = true,
                                            selected = isSel
                                        )
                                    )
                                }
                            }

                            // Full dropdown selector
                            ExposedDropdownMenuBox(
                                expanded = versionExpanded,
                                onExpandedChange = { versionExpanded = !versionExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedVersion,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Todas as Versões") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MinecraftEmerald,
                                        unfocusedBorderColor = MinecraftBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = versionExpanded,
                                    onDismissRequest = { versionExpanded = false },
                                    modifier = Modifier.background(MinecraftDarkSurface)
                                ) {
                                    ServerConfig.AVAILABLE_VERSIONS.forEach { ver ->
                                        DropdownMenuItem(
                                            text = { Text(ver, color = TextPrimary) },
                                            onClick = {
                                                selectedVersion = ver
                                                versionExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Integração Playit.gg
                    Surface(
                        color = MinecraftCardSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MinecraftLapis.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Túnel Automático Playit.gg",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MinecraftLapis
                                    )
                                    Text(
                                        text = "Gera um link permanente (ex: servidor.gl.joinmc.link) para jogar online.",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = enablePlayit,
                                    onCheckedChange = { enablePlayit = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MinecraftObsidian,
                                        checkedTrackColor = MinecraftLapis
                                    )
                                )
                            }

                            if (enablePlayit) {
                                if (manualSecretKey.isNotBlank()) {
                                    Surface(
                                        color = MinecraftEmerald.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Dns,
                                                contentDescription = null,
                                                tint = MinecraftEmerald,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Chave Playit.gg salva e ativa!",
                                                color = MinecraftEmerald,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        claimLoading = true
                                        coroutineScope.launch {
                                            val session = PlayitManager.startClaimSession()
                                            claimLoading = false
                                            if (session != null) {
                                                activeClaimCode = session.code
                                                activeClaimUrl = session.claimUrl
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(session.claimUrl))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Não foi possível abrir sessão automática. Use a caixa de texto abaixo para colar sua Secret Key.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftLapis),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Dns, contentDescription = null, tint = MinecraftObsidian)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (claimLoading) "Conectando ao Playit.gg..." else "VINCULAR VIA NAVEGADOR",
                                        fontWeight = FontWeight.Bold,
                                        color = MinecraftObsidian,
                                        fontSize = 12.sp
                                    )
                                }

                                activeClaimCode?.let { code ->
                                    DisposableEffect(code) {
                                        claimLiveStatus = "Aguardando autorização no navegador..."
                                        PlayitManager.startBackgroundClaimPoller(code, context) { status, secret ->
                                            when (status) {
                                                PlayitClaimStatus.WAITING_FOR_BROWSER -> {
                                                    claimLiveStatus = "Aguardando página abrir no navegador..."
                                                }
                                                PlayitClaimStatus.WAITING_FOR_USER_CLICK -> {
                                                    claimLiveStatus = "Clique em 'Add Agent' na página..."
                                                }
                                                PlayitClaimStatus.ACCEPTED -> {
                                                    claimLiveStatus = "Autorizado com sucesso! Conectando agente..."
                                                    if (!secret.isNullOrBlank()) {
                                                        manualSecretKey = secret
                                                        activeClaimCode = null
                                                        activeClaimUrl = null
                                                        try {
                                                            Toast.makeText(context, "Playit.gg vinculado com sucesso!", Toast.LENGTH_LONG).show()
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                                PlayitClaimStatus.REJECTED -> {
                                                    activeClaimCode = null
                                                }
                                                PlayitClaimStatus.ERROR -> {}
                                            }
                                        }
                                        onDispose {
                                            PlayitManager.stopBackgroundClaimPoller()
                                        }
                                    }

                                    Surface(
                                        color = MinecraftDarkSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MinecraftLapis.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = MinecraftLapis,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    claimLiveStatus,
                                                    color = MinecraftLapis,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        try {
                                                            claimLoading = true
                                                            val secret = PlayitManager.exchangeClaim(code)
                                                            claimLoading = false
                                                            if (!secret.isNullOrBlank()) {
                                                                PlayitManager.saveSecretKey(context, secret)
                                                                PlayitTunnelProcess.start(
                                                                    context = context,
                                                                    onLog = { msg -> ServerManager.addLog(msg) },
                                                                    onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) }
                                                                )
                                                                manualSecretKey = secret
                                                                activeClaimCode = null
                                                                activeClaimUrl = null
                                                                Toast.makeText(context, "Playit.gg vinculado com sucesso!", Toast.LENGTH_LONG).show()
                                                            } else {
                                                                Toast.makeText(context, "Ainda não confirmado. Clique em 'Add Agent' na página.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            claimLoading = false
                                                            Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                            ) {
                                                Text(
                                                    "CONFIRMAR VINCULAÇÃO",
                                                    color = MinecraftObsidian,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = manualSecretKey,
                                    onValueChange = {
                                        manualSecretKey = it.trim()
                                        if (manualSecretKey.isNotBlank()) {
                                            PlayitManager.saveSecretKey(context, manualSecretKey)
                                        }
                                    },
                                    label = { Text("Secret Key Playit (Opcional)", fontSize = 11.sp) },
                                    placeholder = { Text("Ex: 1a2b3c4d5e...", fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MinecraftLapis,
                                        unfocusedBorderColor = MinecraftBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = MinecraftLapis,
                                        unfocusedLabelColor = TextSecondary
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 5. Crossplay GeyserMC + Floodgate (Paper ou Purpur)
                    if (selectedType == ServerType.PAPER || selectedType == ServerType.PURPUR) {
                        Surface(
                            color = MinecraftCardSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MinecraftBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Crossplay GeyserMC + Floodgate",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MinecraftLapis
                                        )
                                        Text(
                                            text = "Permite celulares (Bedrock) e PC (Java) jogarem juntos.",
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
                    }

                    // 6. Alocação de Memória RAM
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Alocação de Memória RAM",
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
                    if (selectedType.isJava) {
                        Surface(
                            color = MinecraftCardSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MinecraftBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Modo Pirata + Original",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Permite contas não-oficiais (TLauncher, PojavLauncher, etc.)",
                                        fontSize = 11.sp,
                                        color = TextSecondary
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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create Action Button
                Button(
                    onClick = {
                        val newServer = ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { "Servidor Minecraft" },
                            customDomain = "",
                            enablePlayitTunnel = enablePlayit,
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
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MinecraftObsidian,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CRIAR SERVIDOR",
                        fontWeight = FontWeight.Bold,
                        color = MinecraftObsidian,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
