# Mikael Launcher Plus

Launcher Android para Minecraft: Java Edition. Este repositório está na fase inicial de integração e validação do motor Android.

## Estado atual

- [x] Consulta do manifesto oficial da Mojang, com seleção de releases estáveis e consulta de metadados da versão (tipo, data, versão de Java, URL e SHA-1 do client).
- [ ] Implementar download dos arquivos oficiais, validação SHA-1 e instalação real em instância.
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
- [ ] Validar compilação do código Java do app no GitHub Actions sem gerar APK.
- [ ] Executar o modo `validate` do workflow do motor para verificar a integração sem gerar APK.
- [ ] Depois da validação de código, testar o motor integrado em aparelho físico; a geração do APK só pode ocorrer após autorização explícita.
- [ ] Executar o teste ponta a ponta de Minecraft Vanilla em dispositivo físico conforme [docs/VANILLA-TEST.md](docs/VANILLA-TEST.md).
- [ ] Validar versões, loaders, contas, instâncias e mods.

## Instâncias locais

Os perfis são criados dentro do diretório privado do app (`files/instances/`), com um arquivo `instance.json` e diretórios de preparação. A seleção do loader é somente metadado do perfil: Forge/Fabric/OptiFine não são instalados por esse recurso. Arquivos JAR podem ser copiados para `mods/`, listados e removidos, mas ainda não são ligados à pasta real de jogo do motor.

O botão **Iniciar Minecraft** tenta abrir o app Amethyst/Pojav se estiver instalado. No APK integrado preparado pelo workflow manual, o botão chama a tela de inicialização de armazenamento do próprio motor. Isso abre o launcher do motor; autenticação, download da versão e toque em Jogar ainda são necessários para entrar no mundo.

## Etapa 2 — teste Vanilla

O dashboard agora tem o botão **Testar Minecraft Vanilla**. Ele mostra a versão selecionada nesta tela e orienta a continuar no motor. A versão não é transferida automaticamente, e o dashboard ainda não instala o jogo. A etapa só será concluída quando Minecraft Vanilla iniciar e um mundo carregar em um aparelho físico. Consulte [o procedimento de teste](docs/VANILLA-TEST.md).

## Etapa 5 — estabilidade e diagnóstico

O dashboard inclui cópia de um relatório de diagnóstico do dispositivo e visualização da última falha Java registrada. O log é privado ao app, limitado a 256 KiB e filtrado para padrões comuns de segredos; revise qualquer conteúdo antes de compartilhar. Isso não captura todos os tipos de falha, nem significa que preferências de RAM/renderizador já sejam aplicadas ao motor. Consulte [a documentação da etapa 5](docs/ACCOUNTS-STABILITY-PERFORMANCE.md).

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
