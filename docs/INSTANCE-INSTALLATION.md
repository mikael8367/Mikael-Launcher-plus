# Etapa 3 — instalação por instância

## Estado atual

**Implementado parcialmente: download dos metadados oficiais e do JAR do cliente Vanilla, com verificação SHA-1. A instância ainda não é jogável.**

No dashboard, selecione uma instância criada, selecione `Vanilla` como perfil do carregador e escolha uma versão após consultar o manifesto oficial. O botão **Baixar arquivos oficiais Vanilla** grava os arquivos em:

`files/instances/<instância>/versions/<versão>/<versão>.json`

`files/instances/<instância>/versions/<versão>/<versão>.jar`

Os arquivos temporários recebem extensão `.part` e são descartados quando o download falha. O JSON precisa corresponder ao identificador selecionado. O JAR precisa ter tamanho igual ao informado no manifesto e SHA-1 correspondente. Os hosts são limitados a domínios HTTPS esperados da Mojang.

## O que ainda falta

- [ ] Conectar o caminho de cada instância ao parâmetro de diretório de jogo realmente usado pelo motor Amethyst.
- [ ] Baixar/verificar todas as bibliotecas nativas e Java do manifesto, respeitando regras por sistema operacional/arquitetura.
- [ ] Baixar/verificar o índice de assets e os assets necessários.
- [ ] Provisionar ou selecionar um runtime Java compatível, incluindo arquitetura ARM64.
- [ ] Tratar espaço livre antes de downloads grandes, progresso detalhado, cancelamento e retomada segura.
- [ ] Testar com uma versão Vanilla real e confirmar que o motor lê os arquivos da instância correta.
- [ ] Atualizar o estado de instalação apenas depois de verificar todos os componentes.

## Sem falsas indicações

O estado `client_artifacts_staged` significa **somente** que os metadados e o JAR do cliente foram baixados e verificados. `gameInstalled` continua `false`. Não significa que bibliotecas, assets ou runtime estejam presentes e não garante que o Minecraft possa iniciar.

O código foi adicionado ao repositório, mas ainda precisa passar pela validação de compilação. Nenhum APK foi gerado nesta etapa.
