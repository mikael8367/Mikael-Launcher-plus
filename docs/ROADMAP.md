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

- [ ] Implementar instalações reais e compatíveis de Forge, Fabric e OptiFine.
- [ ] Fazer os mods da instância aparecerem na pasta ativa do jogo.
- [ ] Validar duplicados, dependências e incompatibilidades.
- [ ] Testar primeiro com poucos mods antes de packs maiores.

## Etapa 5 — Contas, estabilidade e desempenho

- [ ] Revisar autenticação, armazenamento de sessão e mensagens de erro.
- [ ] Adicionar diagnóstico de falhas e logs acessíveis.
- [ ] Ajustar memória, resolução e renderizador com limites seguros para cada aparelho.
- [ ] Testar em aparelho físico, incluindo Samsung Galaxy A12, sem prometer FPS não medido.

## Regras de validação

- **Sem APK sem autorização explícita.**
- Compilar código não prova que o jogo abre.
- Um botão funcional não prova que o Minecraft inicia.
- Nenhum recurso é considerado concluído sem testes adequados.
- Preservar licenças e avisos do motor e de suas dependências.
