# Etapa 6 — integridade e prontidão da instância

## Estado atual: parcial

O dashboard inclui **Verificar integridade da instância**. O diagnóstico lê o perfil privado e, quando há arquivos preparados, confere:

- nome do perfil e identificador de versão;
- se a versão preparada corresponde à versão selecionada;
- existência e tamanho do JSON de versão;
- identificador do JSON, SHA-1 salvo e consistência do hash/tamanho do cliente declarado no próprio manifesto;
- existência do JAR e comparação do tamanho e SHA-1 reais com os valores registrados.

A verificação de SHA-1 acontece em segundo plano para não bloquear a interface durante a leitura do JAR. O relatório separa verificações aprovadas e bloqueios restantes.

## O que este diagnóstico não faz

- Não baixa arquivos, instala bibliotecas, transfere saves, instala Forge/Fabric/OptiFine nem altera os metadados de instalação.
- Não prova compatibilidade do código dos mods nem verifica o carregamento em uma sessão real.
- Não instala ou seleciona um runtime Java, não baixa o índice/assets e não conecta a instância privada ao diretório de jogo do motor Amethyst/Pojav.
- Por esses motivos, `readyToLaunch` é deliberadamente `false` e o dashboard não afirma que a instância está pronta para jogar, mesmo quando o JSON e o JAR passam na verificação.

O diagnóstico é uma verificação de integridade dos arquivos locais e de consistência dos registros, não uma garantia criptográfica contra alguém que consiga alterar tanto os arquivos como os registros privados.

## Próximas ações

- [ ] Confirmar compilação do código Java no GitHub Actions após a correção de uma falha de variável final em `MainActivity.java`.
- [ ] Criar testes que cubram perfil sem download, arquivo ausente, arquivo truncado, hash incorreto, versão divergente e cliente válido.
- [ ] Validar a tela no aparelho Android real e conferir desempenho ao recalcular o hash de um JAR grande.
- [ ] Implementar e testar os downloads verificados de bibliotecas, assets e runtime compatível.
- [ ] Ligar o caminho da instância ao diretório de jogo efetivamente usado pelo motor e comprovar o lançamento num dispositivo.

Nenhum APK foi gerado. O workflow de integração do motor foi atualizado para incluir a nova classe em modo de validação, mas não foi iniciado manualmente.
