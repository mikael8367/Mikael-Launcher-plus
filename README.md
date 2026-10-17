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
- [x] Workflow separado para compilar uma base de motor Minecraft Android upstream, em revisão de compatibilidade/licenças.
- [x] Revisão inicial do erro de CI: a action de setup do Android SDK tentava instalar o pacote legado inexistente `tools`; os workflows foram alterados para usar o SDK pré-instalado no runner e instalar as plataformas necessárias.
- [ ] Validar compilação do código Java no GitHub Actions sem gerar APK.
- [ ] Quando solicitado, validar o build manual do motor integrado.
- [ ] Executar e validar o workflow manual de integração do motor (somente quando Mikael autorizar a geração de APK).
- [ ] Testar instalação e início de Minecraft em dispositivo físico.
- [ ] Validar versões, loaders, contas, instâncias e mods.

## Instâncias locais

Os perfis são criados dentro do diretório privado do app (`files/instances/`), com um arquivo `instance.json` e diretórios de preparação. A seleção do loader é somente metadado do perfil: Forge/Fabric/OptiFine não são instalados por esse recurso. Arquivos JAR podem ser copiados para `mods/`, listados e removidos, mas ainda não são ligados à pasta real de jogo do motor.

O botão **Iniciar Minecraft** tenta abrir o app Amethyst/Pojav se estiver instalado. No APK integrado preparado pelo workflow manual, o botão chama a tela de inicialização de armazenamento do próprio motor. Isso abre o launcher do motor; autenticação, download da versão e toque em Jogar ainda são necessários para entrar no mundo.

## Workflows

- [Android CI](https://github.com/mikael8367/Mikael-Launcher-plus/actions/workflows/android.yml): valida a compilação do código Java e não empacota APK.
- [Minecraft engine base (Amethyst)](https://github.com/mikael8367/Mikael-Launcher-plus/actions/workflows/minecraft-engine-base.yml): workflow **manual** que clona uma revisão fixa do motor, copia a interface Mikael e as ferramentas de perfis/mods para o módulo Android do motor, troca a atividade inicial pelo dashboard Mikael e tenta compilar `:app_pojavlauncher:assembleDebug`. Não execute até Mikael pedir para gerar APK.

O workflow automático de validação compila o código Java sem empacotar APK. O workflow do motor pode gerar o APK integrado, mas só inicia manualmente; ele não foi executado nesta etapa.

## Base técnica e licenças

A base de referência é [Amethyst Android](https://github.com/AngelAuraMC/Amethyst-Android), derivado de PojavLauncher. O workflow fixa a revisão upstream `330c6eae3164df64bdc4828e946a9e62cc5169e4` para permitir reproduzir a tentativa de build. O projeto upstream usa LGPL-3.0 e contém dependências/submódulos com licenças próprias. Antes de redistribuir APKs ou incorporar código ao produto, é necessário preservar avisos, fornecer o código-fonte correspondente conforme exigido e revisar cada licença, incluindo submódulos e bibliotecas nativas.

Referências:
- https://github.com/AngelAuraMC/Amethyst-Android
- https://github.com/TeamPojavLauncher/PojavLauncher

## Critério de aceitação

Compilar um APK é apenas uma verificação de build. A conclusão real exige instalar o APK, selecionar uma versão legítima de Minecraft, concluir os downloads, iniciar o processo do jogo, carregar um mundo e guardar logs e identificação do dispositivo. Nenhuma dessas etapas deve ser marcada como aprovada sem evidência.

## Segurança

Não coloque tokens, senhas ou chaves privadas no repositório. Use GitHub Actions Secrets para segredos de CI. O launcher não deve distribuir arquivos proprietários do Minecraft; o usuário deve obter os arquivos do jogo por meios autorizados.
