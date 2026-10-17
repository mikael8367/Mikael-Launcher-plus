# Mikael Launcher Plus — implementação completa (backlog executável)

Este documento é o contrato de implementação. Marcar um item como concluído exige código no repositório e evidência de validação; texto, botão ou perfil salvo não conta como funcionalidade real.

## Regras permanentes

- **Não gerar nem publicar APK sem pedido explícito do usuário.** Validação normal deve compilar/testar sem empacotar APK.
- Nunca afirmar que Minecraft inicia, que um loader funciona, que FPS melhorou ou que o teste físico passou sem evidência.
- Não executar JARs de mods durante a análise estática.
- Usar endpoints e arquivos oficiais, validar HTTPS, tamanhos, hashes, caminhos e redirecionamentos.
- Não copiar credenciais/tokens para logs. Autenticação Microsoft deve seguir o fluxo suportado pelo motor e não deve armazenar senha no launcher.
- Respeitar arquitetura ARM64 e limitações reais do Android; bibliotecas nativas de desktop não são substitutas de bibliotecas Android.
- Downloads grandes precisam de progresso, cancelamento, retentativa segura, verificação e tratamento de espaço insuficiente.

## Etapa A — base verificável e instalação Vanilla

- [ ] Validar build Java/Android pelo GitHub Actions, sem APK, e corrigir todos os erros.
- [ ] Validar integração do motor upstream em modo validate, sem APK.
- [ ] Validar manifesto de versões com tratamento de rede, JSON inválido, versões removidas e cancelamento.
- [ ] Fixar confiança do JSON da versão à cadeia de metadados oficial (não confiar somente no SHA-1 que o próprio JSON declara).
- [ ] Instalar client JAR, JSON, bibliotecas comuns e assets, validando tamanho e hash.
- [ ] Resolver regras de bibliotecas por OS/arquitetura e classificador sem tratar biblioteca nativa desktop como Android.
- [ ] Detectar e informar bibliotecas/classificadores ainda não suportados; não declarar instalação completa se houver bloqueios.
- [ ] Encontrar/provisionar runtime Java ARM64 realmente compatível com o motor e com a versão selecionada.
- [ ] Calcular classpath, argumentos JVM e argumentos do jogo com substituição correta de placeholders, argumentos modernos e antigos.
- [ ] Ligar cada instância ao diretório de jogo real do motor, sem misturar saves, mods, configurações ou versões.
- [ ] Verificar espaço livre antes e durante instalação; cancelar sem corromper artefatos já verificados.
- [ ] Marcar instalação como pronta somente quando todos os componentes obrigatórios estiverem verificados e acessíveis ao motor.

**Critério de aceite A:** iniciar Vanilla a partir da instância selecionada, entrar num mundo e guardar log/evidência de aparelho, versão e resultado. Compilar o launcher ou abrir o motor não basta.

## Etapa B — motor Android e experiência de jogo

- [ ] Integrar o motor Android de forma reproduzível, fixando commit/revisão e documentando licença/atribuição.
- [ ] O botão Jogar deve iniciar a versão/instância selecionada, não apenas abrir outro launcher.
- [ ] Exibir progresso real de download, preparação, autenticação, inicialização e erro.
- [ ] Tratar ausência do motor/runtime, falha de autenticação, falta de espaço, rede indisponível e processo encerrado.
- [ ] Aplicar configurações de RAM e renderizador através das APIs reais do motor; não deixar preferências meramente decorativas.
- [ ] Adicionar diagnóstico de logs com redação de segredos e testes de regressão para tokens/senhas.
- [ ] Verificar layout em retrato e paisagem, estados vazios, carregamento, erros e acessibilidade dos controles.

## Etapa C — loaders reais

- [ ] Fabric: descobrir instalador oficial/metadata por versão base, baixar perfil e bibliotecas necessárias, validar artefatos e criar versão executável.
- [ ] Forge: resolver instalador oficial compatível com a versão base; implementar a estrutura de bibliotecas e perfil que o motor suporte.
- [ ] OptiFine: instalar apenas versões compatíveis, respeitando distribuição/licença e o formato do instalador; não simular instalação apenas mudando o nome do perfil.
- [ ] Forge + OptiFine: aplicar a ordem de instalação suportada para a combinação específica e recusar combinações incompatíveis.
- [ ] Mostrar versões disponíveis por loader e versão base, com motivo explícito quando não houver compatibilidade.
- [ ] Tornar a instalação transacional: falha/cancelamento não deve deixar uma instância marcada como pronta.
- [ ] Cobrir parsing de metadados, regras de versões, falhas de rede e incompatibilidades com testes automatizados.

**Critério de aceite C:** cada loader deve aparecer como instalado somente depois de produzir um perfil real que o motor consiga iniciar. Testar cada loader separadamente antes de testar combinações.

## Etapa D — mods, recursos e instâncias

- [ ] Conectar mods importados à pasta `mods` efetivamente usada pelo jogo naquela instância.
- [ ] Importar/instalar mods por fonte oficial com filtros de versão Minecraft/loader, dependências e paginação.
- [ ] Verificar hashes, arquivos duplicados, ZIP/JAR inválido e nomes/caminhos maliciosos antes de importar.
- [ ] Separar mods, resource packs, shader packs, saves, config e logs por instância.
- [ ] Detectar dependências obrigatórias e conflitos conhecidos sem prometer que análise estática garante compatibilidade.
- [ ] Permitir ativar/desativar/remover mods e desfazer importações sem apagar dados de outras instâncias.
- [ ] Confirmar que versões/instâncias selecionadas persistem após reiniciar o app.
- [ ] Evitar executar automaticamente qualquer conteúdo importado.

**Critério de aceite D:** iniciar uma instância com loader real e um mod de teste compatível; comprovar nos logs que o mod foi carregado. Repetir com dependência ausente e confirmar mensagem útil.

## Etapa E — contas e segurança

- [ ] Definir explicitamente os provedores de conta realmente suportados pelo motor; não apresentar offline/ely.by como funcional se não houver suporte real.
- [ ] Implementar login Microsoft somente por fluxo oficial suportado, com retorno de erro claro e sem guardar senha.
- [ ] Tratar logout, expiração de sessão, cancelamento e troca de conta.
- [ ] Garantir que logs/relatórios não incluam access tokens, refresh tokens, códigos OAuth ou dados privados.
- [ ] Adicionar testes de regressão de redação de segredos e caminhos de autenticação.

## Etapa F — desempenho e compatibilidade Android

- [ ] Validar no Galaxy A12 (ARM64, Android 12, PowerVR GE8320) sem prometer suporte antes dos testes.
- [ ] Medir tempo de início, memória e FPS com a mesma versão, mundo, distância de renderização e cena; registrar condições.
- [ ] Aplicar limites de memória seguros para RAM física e não confundir RAM virtual/RAM Plus com RAM real.
- [ ] Expor opções de renderizador apenas quando suportadas pelo motor/dispositivo, com fallback seguro.
- [ ] Investigar crashes com logs reproduzíveis; não desativar verificações para mascarar falhas.
- [ ] Testar pouca memória, rotação, app em segundo plano, retomada, downloads interrompidos e armazenamento quase cheio.

## Etapa G — testes e release

- [ ] Testes unitários de manifesto, regras de versão, hashing, caminhos, dependências, cancelamento e estado da instância.
- [ ] Testes instrumentados para fluxos de UI, seleção de instância e mensagens de erro.
- [ ] CI obrigatório para compilação/testes sem APK; falha deve bloquear conclusão da etapa.
- [ ] Validar o workflow do motor em modo `validate` sem gerar APK.
- [ ] Teste ponta a ponta no aparelho: Vanilla → Fabric → Forge → OptiFine → combinação compatível → mods.
- [ ] Registrar limitações conhecidas e versões testadas.
- [ ] Gerar/publicar APK **somente depois de pedido explícito do usuário**.

## Estado inicial deste backlog

Este backlog registra o trabalho restante; sua criação não implementa os itens acima. Conforme a documentação existente, instalação Vanilla, runtime, integração de diretório do motor, loaders reais e testes físicos ainda não podem ser considerados concluídos.
