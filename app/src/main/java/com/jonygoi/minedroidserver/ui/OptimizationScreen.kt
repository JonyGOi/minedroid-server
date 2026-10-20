package com.jonygoi.minedroidserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonygoi.minedroidserver.server.*
import com.jonygoi.minedroidserver.theme.*

@Composable
fun OptimizationScreen() {
    val context = LocalContext.current
    val config by ServerManager.config.collectAsState()

    var isIgnoringBattery by remember {
        mutableStateOf(OptimizationEngine.isIgnoringBatteryOptimizations(context))
    }
    var wakeLockEnabled by remember { mutableStateOf(config.enableWakeLock) }
    var wifiLockEnabled by remember { mutableStateOf(config.enableWifiLock) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinecraftObsidian)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Otimizações de Desempenho",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = "Ajustes essenciais para extrair o máximo de poder de celulares antigos sem travamentos nem sono de CPU.",
            fontSize = 13.sp,
            color = TextSecondary
        )

        // Android Battery Optimization Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isIgnoringBattery) MinecraftEmerald else MinecraftRedstone,
                    RoundedCornerShape(14.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Economia de Bateria do Android",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isIgnoringBattery)
                                "Desativada (O app tem permissão para rodar sem ser fechado)"
                            else
                                "Ativa (O Android pode fechar o servidor ao desligar a tela!)",
                            fontSize = 12.sp,
                            color = if (isIgnoringBattery) MinecraftEmerald else MinecraftRedstone
                        )
                    }

                    Icon(
                        imageVector = if (isIgnoringBattery) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isIgnoringBattery) MinecraftEmerald else MinecraftRedstone
                    )
                }

                if (!isIgnoringBattery) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = OptimizationEngine.createBatteryOptimizationIntent(context)
                                context.startActivity(intent)
                                isIgnoringBattery = OptimizationEngine.isIgnoringBatteryOptimizations(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Abra as Configurações do Celular e desative a economia para o MineDroid", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinecraftRedstone),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DESATIVAR ECONOMIA (PERMITIR SEGUNDO PLANO)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Hardware WakeLocks Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Travas de Hardware (Locks)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // CPU WakeLock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CPU WakeLock (PARTIAL_WAKE_LOCK)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MinecraftEmerald
                        )
                        Text(
                            text = "Mantém os núcleos do processador ativos na velocidade máxima mesmo com a tela apagada. Evita que o TPS caia para 0.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = wakeLockEnabled,
                        onCheckedChange = {
                            wakeLockEnabled = it
                            ServerManager.updateConfig(config.copy(enableWakeLock = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MinecraftObsidian,
                            checkedTrackColor = MinecraftEmerald
                        )
                    )
                }

                HorizontalDivider(color = MinecraftBorder)

                // Wi-Fi High Performance Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi High Performance Lock",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MinecraftLapis
                        )
                        Text(
                            text = "Impede que o chip Wi-Fi entre em modo de economia de energia, mantendo o ping dos jogadores ultra-baixo e estável.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = wifiLockEnabled,
                        onCheckedChange = {
                            wifiLockEnabled = it
                            ServerManager.updateConfig(config.copy(enableWifiLock = it))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MinecraftObsidian,
                            checkedTrackColor = MinecraftLapis
                        )
                    )
                }
            }
        }

        // Termux 1-Click Bridge
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
                    Text(
                        text = "Integração Termux (Script 1-Clique)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinecraftGold
                    )
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MinecraftGold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Como você já usou o Termux, criamos um script que automatiza 100% da instalação nele: atualiza pacotes, baixa OpenJDK 21, aceita o EULA, aplica as Aikar's Flags e inicia tudo sem você precisar digitar nada!",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val script = OptimizationEngine.generateTermuxScript(config, "/sdcard/minedroid")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Termux Script", script))
                        Toast.makeText(context, "Script copiado! Abra o Termux e cole para rodar tudo automaticamente.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinecraftGold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MinecraftObsidian)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "COPIAR SCRIPT DE AUTOMAÇÃO DO TERMUX",
                        fontWeight = FontWeight.Bold,
                        color = MinecraftObsidian,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Playit.gg Tunnel Guide (Internet Multiplayer without port forwarding)
        Card(
            colors = CardDefaults.cardColors(containerColor = MinecraftDarkSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MinecraftBorder, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Jogar pela Internet (Sem abrir portas no roteador)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MinecraftDiamond
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Para jogar com amigos fora da sua casa (em redes móveis ou outros Wi-Fi) sem precisar mexer nas portas do roteador:\n\n" +
                            "1. Crie uma conta gratuita no site playit.gg\n" +
                            "2. Baixe o túnel Playit ou configure o plugin Playit no servidor\n" +
                            "3. Ele gera um link como 'meu-servidor.playit.gg' que qualquer amigo seu pode usar para entrar direto!",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
