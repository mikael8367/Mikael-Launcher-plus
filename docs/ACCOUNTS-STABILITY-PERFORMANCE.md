# Etapa 5 — contas, estabilidade e desempenho

## Estado atual: parcial

Esta etapa adiciona diagnóstico local e registro limitado de falhas do próprio launcher. Ela não representa um teste de jogo concluído.

### Implementado nesta etapa

- Um handler de falhas não tratadas tenta salvar o último erro em files/diagnostics/last-crash.txt, dentro do armazenamento privado do app, e então chama o handler original do Android.
- O arquivo tem limite de 256 KiB e não inclui intencionalmente preferências nem dados de autenticação.
- O texto é filtrado para ocultar padrões comuns de tokens, Bearer e senhas antes de ser gravado/exibido.
- O dashboard oferece **Copiar diagnóstico seguro** com aparelho, versão do Android, memória reportada, limite de RAM preferido e renderizador preferido.
- O dashboard oferece **Ver última falha** para consultar e copiar o log salvo.
- O workflow de integração copia as classes de diagnóstico e análise de mods para o módulo do motor.

### Limitações e cuidados

- O relatório de dispositivo é informativo. Preferências de RAM/renderizador ainda não são necessariamente aplicadas ao motor.
- Um log pode conter informações técnicas sensíveis mesmo após a filtragem. Revise o conteúdo antes de compartilhar publicamente.
- O handler só registra exceções não tratadas que passam por ele; encerramentos forçados, falhas nativas, ANRs e crashes do processo do jogo podem não ser capturados.
- Não há login Microsoft próprio nesta etapa. A autenticação continua delegada ao motor integrado; não armazenar tokens de sessão no código do dashboard.
- Não há benchmark de FPS ainda. Nenhum ganho de desempenho pode ser alegado sem teste repetível no aparelho.
- As preferências de RAM não são prova de alocação real. Escolher um valor alto pode reduzir a memória disponível para o Android e provocar encerramentos; mantenha valores conservadores e valide no dispositivo.

### Trabalho restante

- [ ] Validar a compilação do código Android sem empacotar APK.
- [ ] Executar o modo validate do workflow de integração do motor, sem APK.
- [ ] Confirmar no dispositivo que logs são criados, filtrados e apresentados corretamente.
- [ ] Documentar autenticação e sessão com o fluxo oficial do motor, sem copiar tokens para logs.
- [ ] Aplicar configurações de memória/renderizador através das APIs reais do motor e validar opções suportadas.
- [ ] Fazer testes repetíveis de inicialização, uso de memória e FPS no Galaxy A12 e registrar os resultados reais.
- [ ] Testar falhas, falta de espaço, rede indisponível e cancelamento sem corromper instâncias.

**Critério de conclusão:** compilação validada, diagnósticos seguros confirmados em aparelho físico, configurações efetivamente aplicadas pelo motor e resultados de estabilidade/desempenho medidos. Nenhum APK foi gerado por esta etapa.
