# Etapa 7 — assets oficiais Vanilla

## Estado atual: código implementado; validação pendente

O dashboard contém **Baixar e verificar assets** e **Cancelar download de assets** para uma instância configurada como Vanilla, depois de preparar os arquivos oficiais do cliente. O instalador lê o JSON da versão, usa o campo assetIndex para obter o índice oficial e baixa os objetos referenciados por hash SHA-1.

### Garantias implementadas

- HTTPS e lista limitada de hosts oficiais da Mojang/Minecraft, inclusive durante redirecionamentos permitidos.
- Limites para JSON, quantidade de caminhos, tamanho de cada objeto e tamanho total.
- Verificação do tamanho e SHA-1 do índice e de cada objeto baixado.
- Caminhos de objetos baseados no hash, deduplicação por hash e reutilização apenas de arquivos locais que passam na verificação.
- Pré-verificação do espaço livre e reserva de 16 MiB quando o Android informa o espaço disponível.
- Downloads temporários .part; arquivos incompletos são removidos, enquanto objetos já concluídos e verificados são mantidos ao cancelar, falhar a rede ou tentar novamente.
- Suporte para projetar arquivos em assets/virtual/<id> e/ou resources/ quando o índice oficial define virtual ou map_to_resources.
- Estado por instância em instance.json, incluindo índice, SHA-1, contagens e total de bytes.

### Limites conhecidos

- A conclusão só atesta o índice e os assets. Não instala bibliotecas Java/Minecraft, executáveis nativos ou runtime Java.
- Para não repetir o custo de ler todos os assets em cada toque do diagnóstico, a checagem rápida recalcula o SHA-1 do índice e confere o estado/contagens do último download completo; ela não recalcula todos os objetos em cada execução. Se houver suspeita de alteração posterior dos objetos, execute novamente **Baixar e verificar assets**.
- Os diretórios são privados da instância do dashboard. O motor Amethyst/Pojav ainda não está configurado para usar essa pasta.
- Índices com volume excepcional, espaço livre insuficiente ou falhas de rede podem exigir liberar espaço e tentar novamente.
- O código foi escrito para não marcar gameInstalled como verdadeiro nem declarar a instância pronta para jogar.

### Validação pendente

- [ ] Confirmar compilação Java do app atualizado no GitHub Actions.
- [ ] Executar o workflow de integração do motor em modo validate (não empacota APK).
- [ ] Testar no aparelho Android, incluindo cancelamento, retomada, rede interrompida, asset adulterado, índice inválido e espaço insuficiente.
- [ ] Concluir dependências de execução e associar a instância ao diretório do motor.
- [ ] Iniciar Minecraft e carregar um mundo antes de declarar a instalação concluída.

Nenhum APK foi gerado nesta etapa.
