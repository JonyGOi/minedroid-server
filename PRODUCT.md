# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Jogadores e entusiastas de Minecraft que desejam hospedar servidores de Minecraft diretamente em smartphones Android (antigos ou principais), jogando com amigos pela internet (via túnel Playit.gg) ou em rede local, permitindo que amigos no celular (Bedrock) e no computador (Java) joguem juntos no mesmo mundo.

## Product Purpose
Transformar o dispositivo Android em um servidor dedicado de Minecraft de alta performance e consumo otimizado, 100% autônomo (executando OpenJDK 21 e PRoot nativos sem necessidade de Termux), com configuração automática de portas, crossplay integrado e console em tempo real.

## Positioning
Ao contrário de tutoriais manuais e arriscados no Termux ou serviços de hospedagem pagos em nuvem, o MineDroid é um host nativo e visual com automação total: gera o mundo, conecta os túneis Playit.gg sem abrir portas no roteador, instala Geyser/Floodgate e gerencia os recursos de CPU/RAM em um único painel.

## Operating Context
Execução contínua como Foreground Service do Android com proteção de bateria (WakeLock e Wi-Fi High Performance Lock). Uso em telas móveis verticais de diferentes tamanhos (5" a 7"), exigindo alta legibilidade em ambientes claros ou escuros.

## Capabilities and Constraints
- Motores suportados: PaperMC (recomendado com mundo e plugins), Purpur, Vanilla, Fabric e Geyser Proxy.
- Conectividade automática: Playit.gg nativo (CLI embutido) sem requerer plugins Spigot extras no jogo.
- Crossplay nativo: GeyserMC + Floodgate + ViaVersion para permitir qualquer versão Bedrock ou Java no mesmo mundo.
- Limitações de hardware móvel: Consumo de memória e processamento devem ser monitorados; a interface deve ser leve, fluida e não consumir ciclos extras de GPU.

## Brand Commitments
- Nome: MineDroid Server.
- Estética: Minecraft moderno e profissional — superfícies escuras limpas (ardósia/obsidiana), acentos esmeralda (sucesso/ativo), lápis-lazúli (Bedrock/rede), ouro (avisos) e redstone (parada/erro).
- Tipografia: Escala clara de leitura em dp/sp com pesos bem definidos, eliminando poluição visual de emojis e textos repetitivos.

## Product Principles
1. **Clareza Imediata (Glanceability):** O usuário deve saber se o servidor está online, qual o endereço de IP/porta e quem está jogando em um único olhar.
2. **Hierarquia Sem Caixas Aninhadas:** Eliminar caixas dentro de caixas. Usar espaçamento, tipografia e divisores sutis para separar grupos lógicos.
3. **Ações Decisivas:** Botões de alta relevância (Iniciar, Parar, Copiar IP) devem ser evidentes, táteis e com feedback instantâneo.
4. **Android Material 3 Elegante:** Alvos de toque de no mínimo 48dp, bordas suaves, contraste de texto rigoroso (mínimo 4.5:1) e respeito ao ecossistema Android.
