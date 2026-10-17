# Etapa 2 — teste real de Minecraft Vanilla

## Estado

**Fluxo de orientação adicionado; teste de ponta a ponta ainda não executado.**

O dashboard agora mostra **Testar Minecraft Vanilla**, identifica a versão selecionada nesta tela e explica como continuar no motor. A versão selecionada no dashboard ainda não é transferida automaticamente para o motor.

## Procedimento de teste

1. Consultar as versões oficiais e selecionar uma release estável (não snapshot, beta ou alpha) no Mikael Launcher Plus.
2. Tocar em **Testar Minecraft Vanilla** e depois em **Abrir motor**.
3. No motor Amethyst integrado, confirmar que a tela inicial abre e que a área de versões está acessível.
4. Selecionar a mesma versão Vanilla no motor. Não instalar Forge, Fabric, OptiFine nem mods durante este teste.
5. Usar uma conta/autenticação suportada pelo motor e autorizada para o jogo.
6. Solicitar a instalação e aguardar a conclusão de bibliotecas, arquivos do cliente, assets e runtime compatível.
7. Tocar em **Jogar** no motor e guardar os logs de inicialização.
8. Criar ou abrir um mundo single-player e confirmar que o mundo termina de carregar e aceita entrada do jogador.
9. Fechar o jogo normalmente e reabrir o launcher para confirmar que a instalação continua disponível.

## Evidências a registrar

- Modelo do aparelho e versão do Android.
- Versão exata do Minecraft e tipo de instalação (Vanilla).
- Resultado de cada passo: passou/falhou/não testado.
- Log de instalação e log de inicialização, removendo tokens, nomes de conta e outros dados privados antes de compartilhar.
- Confirmação visual de que o mundo carregou e de que o jogo responde aos controles.

## Limites e segurança

- Compilar código Java não prova que o jogo inicia.
- Abrir o launcher do motor não prova que Minecraft inicia.
- Esta tela consulta metadados; ela ainda não baixa nem instala a versão por conta própria, nem envia automaticamente a versão selecionada ao motor.
- O teste integrado em aparelho físico depende de um APK de teste. **Não gerar APK até Mikael autorizar explicitamente.**
- Não distribuir arquivos proprietários do jogo; os arquivos devem ser obtidos pelo fluxo autorizado do motor.
- Se houver falha, guardar o erro e o log sanitizado antes de marcar a etapa como concluída.
