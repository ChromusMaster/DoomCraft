# DoomCraft — build multiplataforma

O workflow `.github/workflows/build-multiplatform.yml` produz um único JAR com:

- `natives/linux-x86_64/`
- `natives/windows-x86_64/`
- `natives/macos-x86_64/`
- `natives/macos-arm64/`

## Execução manual

1. Envie todos os arquivos deste pacote ao repositório.
2. Abra **Actions** no GitHub.
3. Selecione **DoomCraft - Build Multiplataforma**.
4. Clique em **Run workflow**.
5. Quando o job `Montar JAR multiplataforma` terminar, baixe o artefato
   `DoomCraft-1.0.0-multiplatform`.

## Publicação por tag

```bash
git tag v1.0.0
git push origin v1.0.0
```

O workflow compila os quatro pacotes e cria uma GitHub Release com o JAR final.

## Primeiro diagnóstico de falha

- Falha apenas no Windows: abra o job `Native windows-x86_64`.
- Falha apenas em um Mac: abra `Native macos-x86_64` ou `Native macos-arm64`.
- Todos os nativos passam, mas o JAR falha: abra `Montar JAR multiplataforma`.

Não inclua WADs no repositório nem no JAR.

## Aviso de distribuição

O macOS bundle copia bibliotecas dinâmicas instaladas pelo Homebrew para tornar o
JAR testável sem exigir Homebrew na máquina do jogador. Antes da publicação
pública final, revise `THIRD_PARTY_NOTICES.md` e inclua os avisos/licenças das
bibliotecas efetivamente empacotadas no artefato da execução.

A primeira execução do workflow é uma validação real de compilação, não apenas
um teste de runners. Caso um job nativo falhe, use o log daquele sistema como
base para o próximo ajuste; os outros jobs continuam por causa de
`fail-fast: false`.
