# Etapa 4 — loaders e compatibilidade de mods

## Estado atual

**Parcial. A instalação real de Forge, Fabric e OptiFine ainda não está implementada.** A escolha de um loader no perfil é apenas metadado de configuração; não significa que o loader esteja instalado nem que o motor use o diretório privado da instância.

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

- [ ] Implementar e verificar instalações reais de Forge, Fabric e OptiFine por versão base, incluindo ordem de instalação suportada para Forge + OptiFine.
- [ ] Conectar as pastas de mods privadas ao diretório de jogo realmente usado pelo motor.
- [ ] Validar dependências e intervalos de versão completos por loader, com testes unitários.
- [ ] Executar testes no motor Android: Vanilla, um loader por vez e, depois, poucos mods.
- [ ] Registrar logs e resultados em aparelho físico antes de afirmar compatibilidade.

Nenhum APK foi gerado por esta etapa.
