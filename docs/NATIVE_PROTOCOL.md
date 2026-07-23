# Protocolo nativo DoomCraft

## Diretório por sessão

```text
config/doomcraft/sessions/<uuid>/
├── commands/
├── frame.bin
└── lzdoom.ini
```

## Frame

`frame.bin` é substituído atomicamente pelo processo nativo.

Cabeçalho little-endian de 32 bytes:

| Offset | Tipo | Campo |
|---:|---|---|
| 0 | u32 | magic `0x31464344` (`DCF1`) |
| 4 | u32 | versão `1` |
| 8 | u32 | largura `320` |
| 12 | u32 | altura `200` |
| 16 | u64 | sequência |
| 24 | u32 | formato `1` = RGBA8 |
| 28 | u32 | tamanho dos pixels |

Após o cabeçalho: `320 × 200 × 4` bytes RGBA.

## Comandos

O cliente cria `<sequência>.tmp` e renomeia atomicamente para `<sequência>.cmd`.

Comandos aceitos:

```text
SAVE <slot-seguro>
LOAD <slot-seguro>
PAUSE
RESUME
QUIT
ACTION <forward|back|left|right|attack|use|speed> <0|1>
COMMAND <weapprev|weapnext>
```

O bridge rejeita operações e tokens fora da allowlist.
