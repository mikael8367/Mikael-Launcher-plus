# Mikael Launcher Plus

Launcher Android para Minecraft: Java Edition. Este repositório está na fase inicial de integração e validação do motor Android.

## Estado atual

- [x] Consulta do manifesto oficial da Mojang, com seleção de releases estáveis e consulta de metadados da versão (tipo, data, versão de Java, URL e SHA-1 do client).
- [ ] Implementar download de arquivos, validação SHA-1 e instalação em instância.\n- [x] Filtrar versões por release, snapshot, beta antiga e alpha antiga; pesquisar por identificador e guardar a seleção.\n- [x] Consultar e copiar metadados de uma versão.\n- [x] Exibir diagnóstico informativo do Android e da memória disponível.\n- [x] Salvar preferências de RAM e renderizador para uso futuro; ainda não aplicadas ao jogo.
- [x] Projeto Android Java mínimo criado.
- [x] Workflow automático para validar o código Java sem empacotar APK.\n- [x] Workflow de APK do motor configurado para execução manual, somente quando solicitado.
- [x] Workflow separado para compilar uma base de motor Minecraft Android upstream, em revisão de compatibilidade/licenças.
- [x] Revisão inicial do erro de CI: a action de setup do Android SDK tentava instalar o pacote legado inexistente `tools`; os workflows foram alterados para usar o SDK pré-instalado no runner e instalar as plataformas necessárias.
- [ ] Validar compilação do código Java no GitHub Actions sem gerar APK.
- [ ] Quando solicitado, validar o build manual do motor upstream.
- [ ] Integrar o motor e a identidade visual Mikael em um único aplicativo.
- [ ] Testar instalação e início de Minecraft em dispositivo físico.
- [ ] Validar versões, loaders, contas, instâncias e mods.

## Workflows

- [Android CI](https://github.com/mikael8367/Mikael-Launcher-plus/actions/workflows/android.yml): compila o app mínimo de desenvolvimento.
- [Minecraft engine base (Amethyst)](https://github.com/mikael8367/Mikael-Launcher-plus/actions/workflows/minecraft-engine-base.yml): clona o código-fonte upstream em uma revisão fixa, ajusta o compileSdk para Android 16 QPR2 (API 36.1), aplica um rótulo de teste Mikael e tenta compilar o módulo `:app_pojavlauncher:assembleDebug`. O APK produzido por este workflow é a base Amethyst upstream, não deve ser confundido com um Mikael Launcher Plus integrado e finalizado.

O workflow automático de validação não gera APK. O workflow do motor pode gerar APK, mas só inicia manualmente; não o execute até Mikael pedir explicitamente.

## Base técnica e licenças

A base de referência é [Amethyst Android](https://github.com/AngelAuraMC/Amethyst-Android), derivado de PojavLauncher. O workflow fixa a revisão upstream `330c6eae3164df64bdc4828e946a9e62cc5169e4` para permitir reproduzir a tentativa de build. O projeto upstream usa LGPL-3.0 e contém dependências/submódulos com licenças próprias. Antes de redistribuir APKs ou incorporar código ao produto, é necessário preservar avisos, fornecer o código-fonte correspondente conforme exigido e revisar cada licença, incluindo submódulos e bibliotecas nativas.

Referências:
- https://github.com/AngelAuraMC/Amethyst-Android
- https://github.com/TeamPojavLauncher/PojavLauncher

## Critério de aceitação

Compilar um APK é apenas uma verificação de build. A conclusão real exige instalar o APK, selecionar uma versão legítima de Minecraft, concluir os downloads, iniciar o processo do jogo, carregar um mundo e guardar logs e identificação do dispositivo. Nenhuma dessas etapas deve ser marcada como aprovada sem evidência.

## Segurança

Não coloque tokens, senhas ou chaves privadas no repositório. Use GitHub Actions Secrets para segredos de CI. O launcher não deve distribuir arquivos proprietários do Minecraft; o usuário deve obter os arquivos do jogo por meios autorizados.
