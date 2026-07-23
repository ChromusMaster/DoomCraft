# Relatório de validação da entrega

Data da validação: 22 de julho de 2026.

## Verificações concluídas

- 64 arquivos JSON de recursos e metadados analisados com parser JSON.
- versões fixadas conferidas: Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.155.2+26.2, Fabric Loom 1.17.16, Gradle 9.5.1, Java 25 e LZDoom 4.14.4;
- ausência de versões snapshot, alpha, beta e release candidate em `gradle.properties`;
- receita customizada e todas as 12 variantes vanilla de madeira conferidas;
- ausência de qualquer arquivo WAD na distribuição;
- GLB de referência validado como glTF Binary 2.0 íntegro;
- ZIP original do LZDoom validado, inclusive contra entradas de caminho inseguras;
- SHA-256 do ZIP do LZDoom: `1637381919bc104c8c5cf3e0d0af67bdd0eebdfc0ff1569719e022d29faa484a`;
- scripts Python compilados pelo `py_compile` sem erro;
- preparação do LZDoom executada e os três pontos de integração confirmados:
  - `doomcraft_bridge.cpp` registrado no CMake;
  - `doomcraft_bridge.h` incluído em `d_main.cpp`;
  - `DoomCraftBridge::OnFrame(screen)` inserido após a atualização do framebuffer;
- configuração CMake iniciada com GCC 14.2 e Ninja 1.12.1; detecção do compilador, threads e bibliotecas básicas concluída.

## Limite do ambiente de validação

A configuração nativa parou na detecção do SDL2 porque este ambiente não possui os arquivos de desenvolvimento `SDL2_LIBRARY` e `SDL2_INCLUDE_DIR`.

O ambiente também contém apenas OpenJDK 21 e não possui Gradle instalado nem resolução DNS externa. Como o projeto exige Java 25, Gradle 9.5.1 e download das dependências Fabric/ZMusic, não foi possível gerar ou executar aqui:

- o executável nativo final do LZDoom;
- o JAR final do DoomCraft;
- um teste dentro do cliente Minecraft 26.2.

Isso significa que a entrega foi validada estruturalmente e nos pontos de patch, mas ainda precisa da compilação integral e do teste de execução no sistema-alvo descrito no `README.md`.

## Comandos de validação utilizados

```bash
python3 -m py_compile native/tools/*.py scripts/*.py
python3 scripts/verify_project.py
python3 native/tools/prepare_lzdoom.py
cmake -S native/work/lzdoom-l4.14.4 -B /tmp/doomcraft-cmake-check -G Ninja
```
