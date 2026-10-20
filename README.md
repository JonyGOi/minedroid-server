# ⛏️ MineDroid Server - Hospedagem de Minecraft no Celular Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%207.0+-green?style=for-the-badge&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Minecraft-Java%20%7C%20Bedrock%20%7C%20GeyserMC-blue?style=for-the-badge&logo=minecraft" alt="Minecraft" />
  <img src="https://img.shields.io/badge/Engine-PaperMC%20%7C%20Purpur-orange?style=for-the-badge" alt="Engines" />
  <img src="https://img.shields.io/badge/Optimization-Aikar's%20Flags%20%2B%20WakeLock-emerald?style=for-the-badge" alt="Optimization" />
</p>

O **MineDroid Server** é um aplicativo Android desenvolvido em Kotlin + Jetpack Compose para automatizar 100% o processo de transformar qualquer celular Android (inclusive modelos antigos) em um servidor dedicado de Minecraft de alta performance.

Antes, para rodar um servidor no celular, era necessário instalar o Termux manualmente, configurar PRoot, instalar pacotes Linux, digitar linhas de comando complexas e lidar com o Android matando o processo ao desligar a tela. O **MineDroid Server** resolve tudo isso com interface gráfica moderna, automação de downloads e otimizações extremas de hardware.

---

## 🚀 Principais Recursos

### 1. 🔄 Multi-Versões e Motores de Servidor
- **PaperMC** (Recomendado): Motor Java de altíssimo desempenho com suporte completo a plugins Spigot/Bukkit.
- **Purpur**: Fork otimizado do Paper com ajustes avançados de jogabilidade e ticks.
- **Fabric**: Ideal para servidores modded leves com mods de performance (Lithium, FerriteCore).
- **Vanilla**: Servidor oficial Mojang Java.
- **Bedrock Dedicated Server (BDS) & PocketMine-MP**: Servidores nativos para Minecraft Bedrock.

### 2. 🌐 Cross-Play Nativo com GeyserMC & Floodgate
- **Jogue com amigos em qualquer dispositivo**: Celular (Android/iOS), Consoles (Nintendo Switch, PlayStation 4/5, Xbox One/Series) e PC (Windows 10/11) podem entrar juntos no mesmo servidor Java!
- **Download Automatizado**: O app baixa e instala automaticamente o `Geyser-Spigot.jar` e o `floodgate-spigot.jar` na pasta `plugins`.
- **Entrada sem Conta Java**: Com o Floodgate ativo, jogadores de celular/console não precisam comprar o Minecraft Java para jogar.

### 3. ⚡ Otimização Extrema de Hardware (Nível Termux)
- **CPU WakeLock (`PARTIAL_WAKE_LOCK`)**: Garante que os núcleos do processador continuem operando em frequência máxima mesmo quando a tela do celular apagar, impedindo quedas de TPS (Ticks Per Second).
- **Wi-Fi High Performance Lock**: Força o modem Wi-Fi a desativar o modo de economia de energia, mantendo a latência e o ping dos jogadores estáveis e baixos.
- **Serviço em Primeiro Plano (Foreground Service)**: Notificação contínua no sistema operacional com controle de parada que protege o processo contra o LMK (Low Memory Killer) do Android.
- **Bypass de Economia de Bateria**: Acesso direto às configurações do Android para conceder permissão de execução irrestrita em segundo plano.
- **Aikar's Garbage Collection Flags**: Parâmetros de JVM especialmente formulados para servidores Minecraft eliminando engasgos de limpeza de memória:
  ```bash
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions
  -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
  ```
- **Slider Inteligente de Memória RAM**: Detecta a memória física total do dispositivo e calcula a quantidade recomendada para alocação de heap (`-Xms` e `-Xmx`).

### 4. 💻 Terminal & Console Interativo
- Visualização de logs em tempo real com coloração sintática ANSI (verde para entradas de jogadores, vermelho para erros, amarelo para avisos).
- Barra de envio de comandos no console (`/op`, `/whitelist`, `/gamemode`, `/stop`, `/say`).
- Chips de atalho rápido para comandos frequentes (`/tps`, `/list`, `/time set day`, `/weather clear`).
- Botão de auto-rolagem e cópia integral de logs para diagnóstico.

### 5. ⚙️ Editor Visual de `server.properties`
- Configuração simplificada de MOTD (nome visível do servidor).
- Alternância entre **Modo Pirata** (permite contas não oficiais / TLauncher / Pojav) e **Modo Original** (autenticação oficial Microsoft).
- Ajuste de Distância de Renderização (View Distance) — recomendado entre 4 e 6 chunks para manter o celular frio.
- Limite de jogadores simultâneos, PvP e nível de dificuldade.
- Aceitação automática do EULA (`eula=true`).

### 6. 🔗 Compartilhamento e Conexão Fácil
- Exibe os IPs locais prontos para copiar:
  - **PC (Java)**: `192.168.x.x:25565`
  - **Celular / Console (Bedrock)**: `192.168.x.x:19132`
- Guia integrado para jogar pela internet sem abrir portas no roteador via **Playit.gg**.

---

## 📱 Estrutura do Aplicativo

```
app/src/main/java/com/example/minedroidserver/
├── MainActivity.kt               # Navegação principal e permissões (Android 13+)
├── server/
│   ├── ServerConfig.kt           # Modelos de dados e configurações
│   ├── ServerManager.kt          # Gerenciamento de estado, estatísticas e processos
│   ├── ServerInstaller.kt        # Download automatizado de Paper, Purpur, Geyser, etc.
│   ├── ProcessRunner.kt          # Execução de processo, pipe de logs e comandos
│   ├── OptimizationEngine.kt     # Aikar's flags, WakeLocks, script Termux
│   ├── MinecraftServerService.kt # Foreground service e wake locks persistentes
│   └── NetworkHelper.kt          # Detecção de IP Wi-Fi e formatação de conexões
├── ui/
│   ├── DashboardScreen.kt        # Painel de controle, status, botões Iniciar/Parar e IP
│   ├── ConsoleScreen.kt          # Terminal interativo com logs e envio de comandos
│   ├── ServerConfigScreen.kt     # Seletor de motor, versão, RAM e propriedades
│   └── OptimizationScreen.kt     # Gerenciador de bateria, travas e script Termux
└── theme/
    ├── Color.kt                  # Paleta temática de Minecraft (Obsidian, Emerald, Redstone)
    ├── Theme.kt                  # Material 3 Dark Theme
    └── Type.kt                   # Tipografia
```

---

## 🛠️ Como Compilar o APK

Requisitos:
- Java JDK 17
- Android SDK 36 (ou compatível com minSdk 24)

Execute no terminal:
```bash
# Compilar versão Debug
./gradlew assembleDebug

# O APK será gerado em:
# app/build/outputs/apk/debug/app-debug.apk
```

O repositório já inclui um fluxo de **GitHub Actions** (`.github/workflows/build-apk.yml`) que compila o APK automaticamente a cada commit ou release!

---

## 📄 Licença

Distribuído sob a licença MIT. Sinta-se livre para usar, modificar e distribuir.
