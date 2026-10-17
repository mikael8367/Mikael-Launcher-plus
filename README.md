# Mikael Launcher Plus

Launcher Android para Minecraft: Java Edition. O projeto começa com uma base Android mínima e um pipeline reproduzível; a integração do motor de jogo será feita por etapas e validada com evidências.

## Estado atual

- [x] Repositório inicializado com documentação.
- [ ] Aplicativo Android mínimo compilável.
- [ ] Workflow de build no GitHub Actions.
- [ ] Integração do motor de inicialização compatível com Android.
- [ ] Teste real de instalação e execução do Minecraft.

## Base técnica

A avaliação inicial considera o código aberto do PojavLauncher/Amethyst como referência para o motor Android. PojavLauncher é LGPL-3.0 e foi descontinuado no repositório original; qualquer reutilização deve preservar avisos, cumprir a licença e auditar as licenças de cada componente. Não se copia código nem se declara integração antes de uma revisão dos arquivos e da compatibilidade.

Referências:
- https://github.com/TeamPojavLauncher/PojavLauncher
- https://github.com/AngelAuraMC/Amethyst-Android

## Build

O workflow em `.github/workflows/android.yml` compila o APK debug e publica o APK como artefato de workflow. Um build bem-sucedido não prova que Minecraft iniciou.

## Segurança

Não coloque tokens, senhas ou chaves de assinatura no repositório. Use GitHub Actions Secrets para segredos de CI. Não distribua arquivos proprietários do Minecraft; o usuário deve obter os arquivos do jogo por meios autorizados.
