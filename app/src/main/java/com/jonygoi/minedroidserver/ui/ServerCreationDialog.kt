package com.jonygoi.minedroidserver.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
    var customDomain by remember { mutableStateOf("") }
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
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
                            text = "Configuração completa e automatizada",
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
                        placeholder = { Text("Ex: Survival com a Galera") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MinecraftEmerald,
                            unfocusedBorderColor = MinecraftBorder
                        )
                    )

                    // 2. Motor do Servidor
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
                                        color = if (type == ServerType.GEYSER_STANDALONE) MinecraftDiamond else TextPrimary
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

                    // 3. Versão do Minecraft (Dropdown completo)
                    if (selectedType != ServerType.GEYSER_STANDALONE) {
                        Column {
                            Text(
                                text = "Versão do Minecraft",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            ExposedDropdownMenuBox(
                                expanded = versionExpanded,
                                onExpandedChange = { versionExpanded = !versionExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedVersion,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Versão Selecionada") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MinecraftEmerald,
                                        unfocusedBorderColor = MinecraftBorder
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

                    // 4. Integração Playit.gg (Túnel & Domínio Real)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MinecraftCardSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MinecraftDiamond.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        color = MinecraftDiamond
                                    )
                                    Text(
                                        text = "Cria um endereço nominal grátis (ex: joao.gl.joinmc.link) para jogar pela internet sem abrir portas.",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = enablePlayit,
                                    onCheckedChange = { enablePlayit = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MinecraftObsidian,
                                        checkedTrackColor = MinecraftDiamond
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
                                            Icon(Icons.Default.Dns, contentDescription = null, tint = MinecraftEmerald, modifier = Modifier.size(16.dp))
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
                                                Toast.makeText(context, "Não foi possível abrir sessão automática. Use a caixa de texto abaixo para colar sua Secret Key.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftDiamond),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
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
                                    LaunchedEffect(code) {
                                        claimLiveStatus = "Agente registrado! Aguardando autorização no navegador..."
                                        while (isActive) {
                                            delay(1500)
                                            val status = PlayitManager.checkClaimStatus(code)
                                            when (status) {
                                                PlayitClaimStatus.WAITING_FOR_BROWSER -> {
                                                    claimLiveStatus = "Aguardando página abrir no navegador..."
                                                }
                                                PlayitClaimStatus.WAITING_FOR_USER_CLICK -> {
                                                    claimLiveStatus = "Agente detectado! Clique em 'Add Agent' na página..."
                                                }
                                                PlayitClaimStatus.ACCEPTED -> {
                                                    claimLiveStatus = "Autorizado com sucesso! Salvando credenciais..."
                                                    val secret = PlayitManager.exchangeClaim(code)
                                                    if (!secret.isNullOrBlank()) {
                                                        PlayitManager.saveSecretKey(context, secret)
                                                        manualSecretKey = secret
                                                        activeClaimCode = null
                                                        activeClaimUrl = null
                                                        Toast.makeText(context, "Conta Playit.gg vinculada com sucesso!", Toast.LENGTH_LONG).show()
                                                        break
                                                    }
                                                }
                                                PlayitClaimStatus.REJECTED -> {
                                                    Toast.makeText(context, "Autorização cancelada no site.", Toast.LENGTH_SHORT).show()
                                                    activeClaimCode = null
                                                    break
                                                }
                                                PlayitClaimStatus.ERROR -> {}
                                            }
                                        }
                                    }

                                    Surface(
                                        color = MinecraftDarkSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, MinecraftDiamond.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MinecraftDiamond, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(claimLiveStatus, color = MinecraftDiamond, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text(
                                                text = "O app está mantendo conexão aberta com o site. Quando você clicar em 'Add Agent' na página, ele autorizará sozinho!",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }

                                activeClaimUrl?.let { url ->
                                    Text(
                                        text = "Link de autorização: $url",
                                        fontSize = 10.sp,
                                        color = MinecraftGold
                                    )
                                }

                                // Campo para inserir ou colar chave secreta manualmente
                                OutlinedTextField(
                                    value = manualSecretKey,
                                    onValueChange = {
                                        manualSecretKey = it.trim()
                                        if (manualSecretKey.isNotBlank()) {
                                            PlayitManager.saveSecretKey(context, manualSecretKey)
                                        }
                                    },
                                    label = { Text("Ou cole a Secret Key do Playit (Opcional)", fontSize = 11.sp) },
                                    placeholder = { Text("Ex: 1a2b3c4d5e...", fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MinecraftDiamond,
                                        unfocusedBorderColor = MinecraftBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = MinecraftDiamond,
                                        unfocusedLabelColor = TextSecondary
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 5. Crossplay GeyserMC (se for Java)
                    if (selectedType.isJava) {
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
                                            text = "Crossplay GeyserMC + Floodgate",
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
                    if (selectedType.isJava) {
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Create Action Button
                Button(
                    onClick = {
                        val newServer = ServerConfig(
                            id = UUID.randomUUID().toString(),
                            name = name.ifBlank { "Servidor Minecraft" },
                            customDomain = customDomain,
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
