# DoomCraft 1.0.0

Mod Fabric para Minecraft Java Edition 26.2 que adiciona uma televisão CRT low-poly capaz de executar WADs por meio de um processo LZDoom 4.14.4 isolado da JVM.

## Estado desta entrega

Este repositório contém:

- código-fonte Java completo do mod;
- recursos, modelos, texturas e receita;
- fonte original do LZDoom 4.14.4 fornecida pelo solicitante;
- patch C++ completo do bridge DoomCraft;
- ferramentas para preparar e compilar a engine nativa;
- GLB original da televisão como referência artística;
- validações offline e documentação do protocolo.

Não contém:

- WADs comerciais, shareware ou livres;
- executáveis nativos pré-compilados;
- `gradle-wrapper.jar`, que deve ser gerado localmente pelo Gradle 9.5.1.

## Versões fixadas

| Componente | Versão |
|---|---:|
| Minecraft Java Edition | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.2 |
| Fabric Loom | 1.17.16 |
| Gradle | 9.5.1 |
| LZDoom | 4.14.4 |
| ZMusic | 1.1.12, mesma versão fixada pelo script oficial do pacote LZDoom fornecido |

Não são usadas versões snapshot, alpha, beta ou release candidate.

## Receita

```text
F F F
V R V
T T T
```

- `F`: lingote de ferro;
- `V`: vidraça comum, sem cor;
- `R`: pó de redstone;
- `T`: qualquer item da tag de tábuas.

Quando as três tábuas são do mesmo tipo vanilla reconhecido, o bloco usa a respectiva textura. Tábuas de mods, ou uma mistura de madeiras, produzem a variante padrão de carvalho.

## Estados por distância

| Distância do jogador mais próximo | Bloco | Processo LZDoom |
|---:|---|---|
| 0–4 blocos | `active` | executando em tempo real |
| >4–8 blocos | `paused` | save automático e pausa |
| >8 blocos | `off` | save automático, encerramento e RAM liberada |

A transição usa o sistema de save do próprio LZDoom. Isso preserva o estado jogável da fase, inventário, posição, inimigos e scripts persistidos pelo formato de save. Estado puramente transitório de áudio ou do backend de renderização não é uma fotografia byte a byte da memória do processo.

Somente a televisão mais próxima pode possuir uma sessão nativa no cliente. Essa decisão é deliberada para impedir que dezenas de blocos iniciem dezenas de engines.

### Estado quebrado

Agachar e usar a TV alterna `broken`. Esse controle é administrativo/teste. No estado quebrado, a tela rachada é renderizada e qualquer sessão ativa é salva e descarregada.

## WADs

O mod cria automaticamente:

```text
config/doomcraft/wads/
```

Trinta segundos após cada entrada em um mundo, uma mensagem informa explicitamente que o mod não inclui WADs e mostra o caminho absoluto da pasta.

A seleção segue esta ordem:

1. nomes conhecidos de IWAD, como `doom2.wad`, `doom.wad`, `freedoom2.wad` e equivalentes;
2. os demais arquivos `.wad`, em ordem alfabética, são passados como PWADs com `-file`.

Quando não houver um nome conhecido, o primeiro `.wad` será tratado como IWAD. Para evitar ambiguidade, mantenha um IWAD reconhecido na pasta.

Use apenas arquivos que você tenha direito legal de executar.

## Controles

Pressione `F8` próximo de uma TV ativa para capturar ou liberar os controles.

| Ação Doom | Tecla padrão |
|---|---|
| avançar/recuar | setas para cima/baixo |
| virar | setas esquerda/direita |
| atacar | Ctrl direito |
| usar/abrir | Enter |
| correr | Shift direito |
| arma anterior/próxima | Page Up/Page Down |

As teclas podem ser alteradas no menu de controles do Minecraft.

## Orçamento de memória

O desenho usa:

- uma única engine nativa por cliente;
- framebuffer RGBA de 320×200, aproximadamente 250 KiB;
- máximo de 35 frames por segundo;
- watchdog de RSS em 768 MiB;
- no Linux, `prlimit` com RSS de 768 MiB e espaço de endereçamento de 960 MiB;
- descarregamento completo do processo além de 8 blocos;
- áudio e joystick desabilitados por padrão com `-nosound` e `-nojoy`;
- backend Softpoly solicitado com `+vid_preferbackend 2`, evitando depender do renderer OpenGL para a captura da TV.

O limite se aplica aos recursos atribuíveis ao DoomCraft, não à JVM inteira do Minecraft. No Linux há limite de processo preventivo mais watchdog; no Windows e macOS há watchdog e encerramento quando o limite é detectado. Métricas de RSS e bibliotecas compartilhadas variam por sistema operacional, portanto nenhum processo externo consegue garantir contabilização idêntica em todos os sistemas.

## Pré-requisitos

### Comuns

- JDK 25 estável;
- Python 3.11 ou superior;
- Git;
- CMake;
- Ninja;
- compilador C/C++ com suporte a C++17;
- Gradle 9.5.1 para gerar inicialmente o wrapper.

### Linux Debian/Ubuntu

O próprio pacote LZDoom fornecido cita como base `nasm`, `autoconf`, `libtool`, `libsystemd-dev`, Clang/GCC, `libx11-dev`, `libsdl2-dev` e `libgtk-3-dev`. Uma instalação típica é:

```bash
sudo apt update
sudo apt install -y \
  git python3 cmake ninja-build build-essential clang nasm autoconf libtool \
  libsystemd-dev libx11-dev libsdl2-dev libgtk-3-dev \
  libopenal-dev libvpx-dev libbz2-dev zlib1g-dev libjpeg-dev
```

A lista exata pode variar conforme a distribuição e as opções do CMake.

## Compilação passo a passo

### 1. Gerar o Gradle Wrapper

O ZIP não distribui binários de wrapper. Com Gradle 9.5.1 instalado:

```bash
gradle wrapper --gradle-version 9.5.1
```

Depois disso, use sempre `./gradlew` no Linux/macOS ou `gradlew.bat` no Windows.

### 2. Validar e preparar o LZDoom

```bash
./gradlew verifyDoomCraftSource prepareLzDoom
```

O código é extraído em `native/work/lzdoom-l4.14.4/` e recebe apenas duas alterações:

- registro de `doomcraft_bridge.cpp` no CMake;
- chamada `DoomCraftBridge::OnFrame(screen)` depois de `screen->Update()`.

### 3. Compilar a engine nativa

```bash
./gradlew buildNative
```

A tarefa:

1. baixa o ZMusic 1.1.12 do repositório oficial, se necessário;
2. compila e instala o ZMusic localmente;
3. configura e compila o LZDoom 4.14.4 com o bridge;
4. copia executável, PK3s e bibliotecas necessárias para `src/main/resources/natives/<plataforma>/`;
5. gera `native-manifest.txt`.

Variáveis opcionais:

```bash
export DOOMCRAFT_CMAKE_GENERATOR=Ninja
export DOOMCRAFT_CMAKE_ARGS='-DUMA_OPCAO=VALOR;-DOUTRA_OPCAO=VALOR'
export DOOMCRAFT_ZMUSIC_CMAKE_ARGS='-DUMA_OPCAO=VALOR'
```

Para usar uma instalação de ZMusic já disponível pelo sistema:

```bash
export DOOMCRAFT_SKIP_ZMUSIC=1
export DOOMCRAFT_CMAKE_ARGS='-DCMAKE_PREFIX_PATH=/caminho/do/zmusic'
./gradlew buildNative
```

### 4. Compilar o mod

```bash
./gradlew clean build
```

Saída esperada:

```text
build/libs/doomcraft-1.0.0.jar
```

### 5. Instalar

1. instale Fabric Loader 0.19.3 para Minecraft 26.2;
2. coloque Fabric API 0.155.2+26.2 na pasta `mods`;
3. coloque `doomcraft-1.0.0.jar` na pasta `mods`;
4. inicie o jogo uma vez;
5. coloque o IWAD e eventuais PWADs em `config/doomcraft/wads/`.

## Compilação para várias plataformas

Executáveis nativos não são portáveis. Execute `buildNative` em cada sistema/arquitetura desejado:

- `linux-x86_64`;
- `linux-arm64`;
- `windows-x86_64`;
- `windows-arm64`;
- `macos-x86_64`;
- `macos-arm64`.

Copie os diretórios produzidos para `src/main/resources/natives/` e só então gere o JAR final. O carregador escolhe exclusivamente o diretório correspondente à plataforma atual.

## Estrutura técnica

```text
Minecraft/Fabric
  └─ DoomClientRuntime
      ├─ proximidade e estados
      ├─ DoomSession: processo nativo único
      ├─ arquivo de comandos atômico
      ├─ framebuffer RGBA atômico
      └─ DynamicTexture + BlockEntityRenderer

LZDoom modificado
  └─ DoomCraftBridge
      ├─ captura GetScreenshotBuffer()
      ├─ escala para 320×200
      ├─ SAVE/LOAD/PAUSE/RESUME/QUIT
      └─ comandos de controle permitidos por allowlist
```

Não há JNI. Uma falha nativa não corrompe diretamente a heap da JVM.

## Modelo 3D

`reference/minedoom_lowpoly_crt_tv.glb` é preservado como referência. O runtime usa um modelo JSON cuboidal significativamente mais leve e um quad dinâmico separado para a tela. O pipeline padrão de modelos de blocos do Minecraft/Fabric não consome GLB diretamente.

## Licença

O projeto e a modificação da engine são distribuídos sob GPL-3.0-only. Consulte `LICENSE` e `THIRD_PARTY_NOTICES.md`.
