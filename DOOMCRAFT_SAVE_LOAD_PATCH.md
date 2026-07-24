# DoomCraft 1.0.0 — save/load mínimo

Arquivos substituídos:

- `src/client/java/br/com/chromus/doomcraft/client/input/DoomKeyMappings.java`
- `src/client/java/br/com/chromus/doomcraft/client/DoomClientRuntime.java`
- `src/client/java/br/com/chromus/doomcraft/client/nativebridge/DoomSession.java`
- `src/main/resources/assets/doomcraft/lang/en_us.json`
- `src/main/resources/assets/doomcraft/lang/pt_br.json`
- `README.md`

Controles:

- `Insert`: captura/libera os controles;
- `Home`: cria savegame manual único;
- `End`: carrega o último savegame manual daquela TV;
- `Backspace`: abre/volta nos menus.

O patch usa os comandos `SAVE` e `LOAD` já existentes no bridge nativo.
Portanto, `native/bridge/doomcraft_bridge.cpp` e os executáveis nativos não
foram alterados.

Compilação local:

```bash
./gradlew clean build \
  -x verifyDoomCraftSource \
  --no-configuration-cache \
  --stacktrace
```

Para o JAR multiplataforma final, envie os arquivos ao GitHub e execute
`DoomCraft - Build Multiplataforma` na branch `main`.
