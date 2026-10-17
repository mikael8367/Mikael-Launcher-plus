# Etapa 4 — loaders e compatibilidade de mods

## Estado atual

**Parcial — fluxo nativo conectado e compilação integrada aprovada no CI; teste em aparelho físico pendente.** O assistente na aba **Versões** primeiro seleciona o Minecraft, depois o loader, e encaminha a solicitação para os instaladores nativos do motor para **Vanilla, Forge, Fabric, Quilt, NeoForge, OptiFine e Forge + OptiFine**. O fluxo tenta escolher automaticamente uma release compatível com a versão-base e, após a instalação, seleciona o perfil real do motor e solicita a inicialização. Nem todo loader tem release para toda versão do Minecraft; combinações sem release compatível devem ser recusadas. A compilação comprova que o código Java integra, não que cada loader foi testado em um celular nem que um mundo carregou.

Esta etapa adiciona uma análise offline e conservadora dos JARs importados:

- reconhece metadados comuns de fabric.mod.json, mcmod.info e META-INF/mods.toml;
- lista os JARs cujo formato ZIP não pode ser aberto ou cujos metadados não são reconhecidos;
- detecta IDs de mod duplicados quando ambos os arquivos expõem IDs legíveis;
- verifica algumas dependências declaradas por metadados Fabric e requiredMods do Forge antigo;
- avisa quando há mods numa instância configurada como Vanilla;
- recusa importar novamente o mesmo conteúdo JAR, mesmo que o nome do arquivo seja diferente, comparando SHA-256.

Use **Analisar compatibilidade** na área Gerenciador de Mods. O resultado é um diagnóstico estático; não executa o código dos JARs.

## Limitações conhecidas

- Nem todos os mods incluem metadados padronizados, e alguns empacotam vários módulos num único JAR.
- A análise não interpreta integralmente os intervalos de versão do Forge/Fabric, nem todas as regras de dependências opcionais, conflitos, mixins ou bibliotecas nativas.
- Um ID ausente no relatório não prova que o mod não tenha dependências.
- O scanner não verifica compatibilidade binária com Android/ARM64, GL4ES ou o renderizador escolhido.
- A análise não instala loaders, não move os JARs para o diretório de jogo usado pelo motor e não garante que o Minecraft inicie.

## Trabalho restante

- [x] Conectar o assistente ao fluxo dos instaladores nativos e passar Minecraft, loader e nome de perfil; compilação integrada validada no GitHub Actions.
- [ ] Confirmar em aparelho real a instalação e inicialização de Vanilla, Forge, Fabric, Quilt, NeoForge e OptiFine, com logs de cada teste.
- [ ] Validar a combinação Forge + OptiFine por versões-base compatíveis; não assumir que todas as releases combinam entre si.
- [ ] Ligar o gerenciador de mods auxiliar ao diretório de jogo real do motor, para que os JARs importados sejam usados pela instalação ativa.
- [ ] Validar dependências e intervalos de versão completos por loader, com testes automatizados e testes de falha de rede/cancelamento.
- [ ] Registrar logs e resultados em aparelho físico antes de afirmar compatibilidade.

O CI compila o código Java integrado, mas não empacota APK nesse modo e não substitui testes em dispositivo. Nenhum APK foi gerado nesta etapa.
