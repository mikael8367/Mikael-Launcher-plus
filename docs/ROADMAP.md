# Mikael Launcher Plus — etapas de implementação

O trabalho é feito por etapas. Uma etapa só é considerada concluída quando existe evidência verificável; preparar código ou compilar classes não prova que o jogo iniciou.

## Etapa 1 — Preparar e validar a integração do motor

**Estado: preparada no repositório; validação ainda pendente.**

- [x] Fixar uma revisão específica do motor Android Amethyst para tornar a integração reproduzível.
- [x] Preparar a cópia da interface Mikael e dos gerenciadores locais para o módulo Android do motor.
- [x] Preparar a alteração da atividade inicial para abrir o dashboard Mikael dentro do app do motor.
- [x] Configurar o workflow manual com modo padrão `validate`, que compila as classes Java integradas sem empacotar APK.
- [ ] Executar o modo `validate` e corrigir erros encontrados.
- [ ] Só depois de autorização explícita, gerar o APK de teste.
- [ ] Instalar e abrir em um aparelho Android; comprovar que a interface abre e que o motor inicia corretamente.

**Critério de conclusão:** integração Java validada, app instalado e aberto em aparelho físico. A etapa ainda não comprova que Minecraft Vanilla carrega um mundo.

## Etapa 2 — Comprovar Minecraft Vanilla

**Estado: fluxo de orientação adicionado; teste real pendente.**

- [x] Adicionar no dashboard o botão **Testar Minecraft Vanilla**.
- [x] Mostrar a versão selecionada e explicar que ela ainda não é transferida automaticamente ao motor.
- [x] Documentar o procedimento e as evidências necessárias em [VANILLA-TEST.md](VANILLA-TEST.md).
- [ ] Executar a validação Java da interface atualizada sem empacotar APK.
- [ ] Com autorização explícita, gerar o APK de teste e instalar em aparelho físico.
- [ ] Selecionar uma versão legítima Vanilla no motor.
- [ ] Confirmar download dos arquivos e runtime compatível.
- [ ] Autenticar por um fluxo suportado pelo motor.
- [ ] Iniciar o jogo, entrar em um mundo e guardar logs da execução.
- [ ] Registrar aparelho, Android, versão do Minecraft e resultado.

**Critério de conclusão:** mundo Vanilla jogável carregado com evidência de teste. Abrir o motor ou compilar o código não basta.

## Etapa 3 — Instalação real por instância

**Estado: implementação parcial adicionada; compilação e teste ainda pendentes.**

- [x] Adicionar instalador de artefatos oficiais Vanilla por instância.
- [x] Baixar metadados oficiais e JAR do cliente, com validação do identificador, tamanho e SHA-1.
- [x] Usar arquivos temporários e descartar downloads incompletos.
- [x] Guardar o estado `client_artifacts_staged` sem marcar o jogo como instalado/jogável.
- [x] Documentar limites e próximos passos em [INSTANCE-INSTALLATION.md](INSTANCE-INSTALLATION.md).
- [ ] Validar compilação Java no GitHub Actions sem gerar APK.
- [ ] Conectar cada instância ao diretório de jogo realmente usado pelo motor.
- [ ] Baixar e verificar bibliotecas, assets e runtime Java.
- [ ] Implementar progresso/cancelamento e retomar downloads com segurança.
- [ ] Testar no motor em aparelho físico e só então marcar instalação completa.

**Critério de conclusão:** o motor inicia a versão da instância selecionada, com todos os arquivos necessários verificados. Baixar apenas o JAR do cliente não conclui a instalação.

## Etapa 4 — Loaders e mods

**Estado: análise estática de mods adicionada; instalações reais dos loaders ainda pendentes.**

- [x] Adicionar análise offline de JARs e leitura dos metadados comuns Fabric/Forge.
- [x] Detectar IDs duplicados quando os metadados permitem e dependências declaradas não encontradas.
- [x] Recusar JARs com conteúdo SHA-256 idêntico já importado, mesmo com outro nome.
- [x] Documentar limites e próximos passos em [LOADERS-AND-MODS.md](LOADERS-AND-MODS.md).
- [ ] Validar compilação Java no GitHub Actions sem empacotar APK.
- [ ] Implementar instalações reais e compatíveis de Forge, Fabric e OptiFine.
- [ ] Fazer os mods da instância aparecerem na pasta de jogo ativa usada pelo motor.
- [ ] Melhorar a verificação completa de intervalos de versão, dependências e conflitos por loader.
- [ ] Testar Vanilla, um loader por vez e poucos mods no aparelho antes de packs maiores.

**Critério de conclusão:** loader instalado na versão-base correta, mods lidos pelo diretório de jogo ativo e teste real concluído. O relatório estático, sozinho, não comprova compatibilidade.

## Etapa 5 — Contas, estabilidade e desempenho

**Estado: diagnóstico seguro e log local de falha adicionados; validação em aparelho ainda pendente.**

- [x] Adicionar captura limitada de exceções não tratadas do launcher, mantendo o handler original do Android.
- [x] Adicionar visualização da última falha e cópia do relatório.
- [x] Adicionar cópia de diagnóstico sem incluir intencionalmente dados de sessão/autenticação.
- [x] Documentar limites e trabalho restante em [ACCOUNTS-STABILITY-PERFORMANCE.md](ACCOUNTS-STABILITY-PERFORMANCE.md).
- [x] Incluir as novas classes no workflow de integração do motor.
- [ ] Validar compilação Java no GitHub Actions sem gerar APK.
- [ ] Confirmar logs e filtragem no aparelho físico.
- [ ] Confirmar o fluxo de autenticação oficial no motor; não guardar tokens no dashboard nem nos logs.
- [ ] Aplicar RAM e renderizador através das APIs reais do motor e verificar limites seguros.
- [ ] Medir inicialização, memória e FPS de forma repetível no Galaxy A12; não afirmar resultados não medidos.

**Critério de conclusão:** código validado, diagnóstico verificado no dispositivo e configurações de desempenho realmente aplicadas e medidas.

## Etapa 6 — integridade e prontidão da instância

**Estado: diagnóstico implementado parcialmente; compilação do app isolado passou antes da etapa 7, validação integrada e teste em aparelho pendentes.**

- [x] Adicionar o botão **Verificar integridade da instância**.
- [x] Validar o perfil local, a correspondência entre versão selecionada e preparada, e a presença do JSON e do JAR do cliente.
- [x] Recalcular SHA-1 dos artefatos e comparar metadados, tamanho e hash declarado do cliente.
- [x] Mostrar os bloqueios restantes sem marcar o jogo como instalado ou jogável.
- [x] Incluir a classe de diagnóstico no workflow de validação do código integrado ao motor.
- [x] Corrigir a variável final em `MainActivity.java` e confirmar compilação Java do app isolado no GitHub Actions (execução 39041576841, antes das alterações da etapa 7).
- [ ] Adicionar testes de regressão para arquivos ausentes, alterados, truncados e versão divergente.
- [ ] Validar o diagnóstico em aparelho físico; verificar que ele não altera nem corrompe instâncias.
- [ ] Completar o download de bibliotecas/assets/runtime e conectar a instância ao diretório real do motor.

**Limite explícito:** mesmo que o JSON e o JAR do cliente sejam verificados, a checagem sempre informa que a instância não está pronta para iniciar enquanto as dependências de execução e a ligação ao diretório do motor não estiverem implementadas e testadas.

## Etapa 7 — baixar e verificar assets oficiais Vanilla

**Estado: implementação adicionada; a nova compilação CI e o teste em aparelho ainda estão pendentes.**

- [x] Ler o índice de assets indicado pelo JSON oficial da versão Vanilla.
- [x] Validar a URL HTTPS por uma lista de domínios oficiais permitidos.
- [x] Verificar o SHA-1 e o tamanho do índice e de cada objeto baixado.
- [x] Deduplicar objetos pelo hash e verificar arquivos locais antes de baixá-los novamente.
- [x] Fazer pré-verificação de espaço livre, limitar quantidade/tamanho e rejeitar caminhos de asset inválidos.
- [x] Implementar cancelamento cooperativo, apagar downloads .part incompletos e manter objetos verificados para continuar depois.
- [x] Gerar caminhos legados quando o índice marca virtual ou map_to_resources.
- [x] Persistir o estado dos assets por instância e exibir progresso, cancelamento e resultado no dashboard.
- [x] Atualizar o diagnóstico de prontidão para verificar o índice e o último registro de conclusão dos assets.
- [x] Incluir a classe nova no workflow de validação do motor sem APK.
- [ ] Confirmar compilação Java do código atualizado no GitHub Actions.
- [ ] Testar os downloads com versões reais, rede interrompida, cancelamento, espaço insuficiente e retomada.
- [ ] Medir duração e uso de armazenamento no Galaxy A12.
- [ ] Baixar/verificar as bibliotecas de execução, selecionar runtime Java compatível e conectar a instância ao diretório efetivamente usado pelo motor.

**Limite explícito:** a conclusão desta etapa significa somente que os assets foram baixados e verificados. Bibliotecas, runtime Java, integração ao diretório do motor e teste real de inicialização continuam pendentes; gameInstalled e readyToLaunch permanecem falsos.

## Regras de validação

- **Sem APK sem autorização explícita.**
- Compilar código não prova que o jogo abre.
- Um botão funcional não prova que o Minecraft inicia.
- Nenhum recurso é considerado concluído sem testes adequados.
- Preservar licenças e avisos do motor e de suas dependências.
