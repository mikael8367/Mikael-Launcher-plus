# Mikael Launcher Plus

Launcher Android para Minecraft: Java Edition. O assistente de criação de versões está conectado aos instaladores nativos do motor Amethyst. A compilação integrada passou no GitHub Actions; o teste em aparelho físico e a entrada em um mundo ainda estão pendentes.

## Organização da interface

A navegação inferior separa as funções em quatro áreas, pensadas para telas pequenas:

1. **Início** — versão selecionada e acesso ao fluxo de teste do motor.
2. **Versões** — manifesto oficial, pesquisa/seleção e metadados de versões.
3. **Instâncias** — perfis locais e arquivos de mods associados ao perfil selecionado.
4. **Ajustes** — RAM/renderizador preferidos, idioma, tipo de conta e diagnóstico do dispositivo.

**Importante:** uma opção visível não é prova de integração completa. Preferências de RAM/renderizador/idioma e o tipo de conta ainda precisam ser conectados ao motor para produzir efeito real; escolher Microsoft ou ely.by não autentica a conta por si só. Os arquivos de mods ficam armazenados nos perfis e ainda não são carregados pelo jogo.

## Mapa do código

- `app/src/main/java/com/mikael/launcherplus/MainActivity.java` — interface e coordenação das ações do dashboard.
- `InstanceManager.java` — criação, seleção e gerenciamento de instâncias locais.
- `InstanceModManager.java` e `ModCompatibilityChecker.java` — armazenamento e análise estática dos mods.
- `VanillaAssetInstaller.java`, `VanillaLibraryInstaller.java` e `VersionArtifactInstaller.java` — preparação/verificação de componentes Vanilla.
- `InstanceReadinessChecker.java` — diagnóstico de prontidão; não substitui um teste real de inicialização.
- `DiagnosticLogManager.java` — relatório e registro de falhas.
- `.github/workflows/` — validação Android e validação manual da integração do motor.
- `docs/` — critérios de aceite, procedimentos de teste e backlog de implementação.


## Estado atual

- [x] Consulta do manifesto oficial da Mojang, com seleção de releases estáveis e consulta de metadados da versão (tipo, data, versão de Java, URL e SHA-1 do client).
- [ ] Completar a ligação do instalador próprio ao perfil real do motor. O assistente Criar versão e baixar já encaminha a seleção para os fluxos nativos de instalação do motor.
- [x] Criar perfis locais privados com diretórios separados para mods, resourcepacks, shaderpacks, saves, config e logs.
- [x] Persistir nome, versão escolhida e perfil de loader em `instance.json`, com seleção e exclusão do perfil.
- [x] Importar e remover arquivos `.jar` na pasta privada de mods do perfil, com limite de 250 MB e SHA-256 informativo; ainda é armazenamento, não ativação no jogo.
- [x] Botão **Iniciar Minecraft** tenta abrir uma instalação Amethyst/Pojav detectada no aparelho.
- [x] Workflow manual preparado para empacotar a interface Mikael junto ao código do motor Amethyst.
- [x] Filtrar versões por release, snapshot, beta antiga e alpha antiga; pesquisar por identificador e guardar a seleção.
- [x] Consultar e copiar metadados de uma versão.
- [x] Exibir diagnóstico informativo do Android e da memória disponível.
- [x] Salvar preferências de RAM e renderizador para uso futuro; ainda não aplicadas ao jogo.
- [x] Projeto Android Java mínimo criado.
- [x] Workflow automático para validar o código Java sem empacotar APK.
- [x] Workflow de APK do motor configurado para execução manual, somente quando solicitado.
- [x] Workflow separado para preparar a integração do motor Android upstream; o modo padrão valida o código integrado sem empacotar APK.
- [x] Revisão inicial do erro de CI: a action de setup do Android SDK tentava instalar o pacote legado inexistente `tools`; os workflows foram alterados para usar o SDK pré-instalado no runner e instalar as plataformas necessárias.
- [x] Validar compilação do código Java do app no GitHub Actions sem gerar APK.
- [x] Executar o modo `validate` do workflow do motor; a compilação Java integrada passou sem gerar APK.
- [ ] Depois da validação de código, testar o motor integrado em aparelho físico; a geração do APK só pode ocorrer após autorização explícita.
- [ ] Executar o teste ponta a ponta de Minecraft Vanilla em dispositivo físico conforme [docs/VANILLA-TEST.md](docs/VANILLA-TEST.md).
- [ ] Testar no aparelho as versões, loaders, contas e instâncias; conferir logs de instalação e inicialização.

## Instâncias locais

Os perfis em `files/instances/` são registros auxiliares do Mikael Launcher, com diretórios preparados para mods, saves, configurações e logs. Esses diretórios ainda não são a pasta de jogo ativa do motor, então importar um JAR ali não garante que o jogo o carregará.

O assistente **Criar versão e baixar**, na aba **Versões**, passa a versão escolhida, o loader e o nome do perfil ao motor integrado. Vanilla solicita a inicialização da versão selecionada; Forge, Fabric, Quilt, NeoForge e OptiFine usam os instaladores nativos do motor, selecionando uma release disponível para a versão-base antes de solicitar a inicialização. A compilação integrada passou no CI, mas o fluxo ainda precisa ser testado em aparelho físico. A compatibilidade depende das releases disponíveis: nem todo loader aceita toda versão do Minecraft, e Forge + OptiFine pode exigir combinações específicas.

## Etapa 2 — teste Vanilla

O botão **Testar Minecraft Vanilla** serve para verificar o caminho básico do motor. Para criar uma instalação nomeada, use **Criar versão e baixar** na aba Versões: selecione primeiro a versão-base do Minecraft, depois o loader. No fluxo Vanilla, o motor recebe a versão e solicita a inicialização para baixar os componentes pendentes; para loaders, o motor abre o instalador nativo. A etapa ponta a ponta só será concluída quando Minecraft Vanilla iniciar e um mundo carregar em aparelho físico. Consulte [o procedimento de teste](docs/VANILLA-TEST.md).

## Etapa 5 — estabilidade e diagnóstico

O dashboard inclui cópia de um relatório de diagnóstico do dispositivo e visualização da última falha Java registrada. O log é privado ao app, limitado a 256 KiB e filtrado para padrões comuns de segredos; revise qualquer conteúdo antes de compartilhar. Isso não captura todos os tipos de falha, nem significa que preferências de RAM/renderizador já sejam aplicadas ao motor. Consulte [a documentação da etapa 5](docs/ACCOUNTS-STABILITY-PERFORMANCE.md).

## Etapa 6 — integridade e prontidão da instância

O botão **Verificar integridade da instância** confere os metadados do perfil, se a versão preparada corresponde à escolhida, o JSON oficial, o SHA-1 e tamanho esperado do JAR e o hash real do arquivo local. O resultado lista os bloqueios restantes para a instalação.

A verificação **não inicia o jogo nem marca a instância como jogável**. Bibliotecas, runtime Java compatível e ligação da instância ao diretório real usado pelo motor continuam pendentes. A compilação do app isolado passou antes das alterações da etapa 7; teste físico e validação integrada continuam pendentes. Consulte [a documentação da etapa 6](docs/INSTANCE-READINESS.md).

## Etapa 7 — baixar e verificar assets Vanilla

O botão **Baixar e verificar assets** lê o índice oficial da versão Vanilla, verifica SHA-1 e tamanho do índice e dos objetos, evita baixar novamente arquivos já íntegros e prepara caminhos legados quando o índice solicita. Há progresso e cancelamento; arquivos completos verificados ficam disponíveis para continuar em outra tentativa, enquanto arquivos .part incompletos são descartados.

O diagnóstico de instância passa a consultar o registro dos assets e recalcular o SHA-1 do índice. Ele não recalcula cada asset na checagem rápida, e o resultado **não significa que o Minecraft já pode iniciar**: ainda faltam bibliotecas, runtime Java e integração à pasta real de jogo do motor. Consulte [a documentação da etapa 7](docs/VANILLA-ASSETS.md).

## Etapa 8 — bibliotecas Java Vanilla

O botão **Baixar e verificar bibliotecas** resolve os artefatos Java declarados no manifesto oficial da versão Vanilla, aplica as regras de plataforma, baixa os JARs com SHA-1/tamanho obrigatórios e grava um manifesto local com o que foi verificado e o que ficou pendente. O download possui progresso e cancelamento e preserva arquivos completos verificados para novas tentativas.

A etapa **não instala classificadores nativos nem runtime Java** e não liga ainda a pasta privada ao diretório ativo do motor. Por isso, o relatório pode indicar bibliotecas parciais e nunca marca a instância como jogável. Consulte [a documentação da etapa 8](docs/VANILLA-LIBRARIES.md).

## Workflows

- [Android CI](https://github.com/mikael8367/Mikael-Launcher-plus/actions/workflows/android.yml): valida a compilação do código Java e não empacota APK.
- [Minecraft engine base (Amethyst)](https://github.com/mikael8367/Mikael-Launcher-plus/actions/workflows/minecraft-engine-base.yml): workflow **manual** que clona uma revisão fixa do motor e integra a interface Mikael. O modo padrão `validate` compila somente as classes Java (`compileDebugJavaWithJavac`) e não empacota APK. O modo `build` gera um APK e só deve ser escolhido depois de autorização explícita de Mikael.

O workflow automático de validação compila o código Java sem empacotar APK. O workflow do motor também é manual e agora inicia no modo `validate`, sem gerar APK; o modo `build` é separado e condicionado à autorização explícita. A validação integrada ainda precisa ser executada.

## Base técnica e licenças

A base de referência é [Amethyst Android](https://github.com/AngelAuraMC/Amethyst-Android), derivado de PojavLauncher. O workflow fixa a revisão upstream `330c6eae3164df64bdc4828e946a9e62cc5169e4` para permitir reproduzir a tentativa de build. O projeto upstream usa LGPL-3.0 e contém dependências/submódulos com licenças próprias. Antes de redistribuir APKs ou incorporar código ao produto, é necessário preservar avisos, fornecer o código-fonte correspondente conforme exigido e revisar cada licença, incluindo submódulos e bibliotecas nativas.

Referências:
- https://github.com/AngelAuraMC/Amethyst-Android
- https://github.com/TeamPojavLauncher/PojavLauncher

## Critério de aceitação

Compilar um APK é apenas uma verificação de build. A conclusão real exige instalar o APK, selecionar uma versão legítima de Minecraft, concluir os downloads, iniciar o processo do jogo, carregar um mundo e guardar logs e identificação do dispositivo. Nenhuma dessas etapas deve ser marcada como aprovada sem evidência.

## Segurança

Não coloque tokens, senhas ou chaves privadas no repositório. Use GitHub Actions Secrets para segredos de CI. O launcher não deve distribuir arquivos proprietários do Minecraft; o usuário deve obter os arquivos do jogo por meios autorizados.


## Plano de implementação completa

O backlog executável com as etapas restantes, critérios de aceite, segurança, loaders, runtime, contas, mods e testes está em [docs/FULL-IMPLEMENTATION-BACKLOG.md](docs/FULL-IMPLEMENTATION-BACKLOG.md). Esse documento é um plano de trabalho; não significa que os itens já estejam implementados.
