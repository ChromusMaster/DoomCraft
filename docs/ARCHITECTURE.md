# Decisões de arquitetura

1. **Processo externo em vez de JNI:** reduz o raio de falha e permite descarregar RAM destruindo o processo.
2. **Uma sessão por cliente:** evita multiplicação não controlada de engines.
3. **Save por UUID da BlockEntity:** cada TV mantém continuidade independente.
4. **Modelo cuboidal no runtime:** menor quantidade de vértices e compatibilidade direta com resource packs.
5. **Quad da tela via BlockEntityRenderer:** única parte atualizada por frame.
6. **Protocolo por arquivos atômicos:** implementação simples, auditável e sem compartilhamento de ponteiros.
7. **Sem WAD distribuído:** separação explícita entre engine e conteúdo protegido/licenciado.
