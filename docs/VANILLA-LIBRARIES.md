# Etapa 8 — bibliotecas Java Vanilla

## Estado

Código adicionado; a compilação do commit desta etapa, integração com Amethyst e testes no aparelho ainda precisam ser confirmados.

## O que faz

O dashboard apresenta **Baixar e verificar bibliotecas** e **Cancelar download de bibliotecas**. O instalador lê a lista libraries do JSON de versão local e verifica que o JSON ainda corresponde ao SHA-1 registrado quando o cliente oficial foi preparado.

Para cada artefato Java aplicável pelas regras do manifesto, ele valida o caminho relativo, URL HTTPS e host Maven permitido, SHA-1 e tamanho. Limita o manifesto a 1.000 entradas, cada JAR a 512 MiB e o total planejado a 2 GiB, faz uma pré-checagem de espaço livre e usa arquivos .part temporários. Um JAR só é movido ao destino após o tamanho e o SHA-1 coincidirem. JARs completos verificados são reutilizados em novas tentativas.

O manifesto local fica em `libraries/manifest.json` e contém a versão, o SHA-1 do JSON de versão, os artefatos verificados e uma lista de condições ainda não resolvidas. O diagnóstico verifica o SHA-1 e as contagens do manifesto; não relê o hash de todos os JARs a cada checagem.

## Regras e limites importantes

- As regras são avaliadas para o sistema Linux e a arquitetura reportada pelo runtime Android. Não são baixadas bibliotecas explicitamente destinadas a Windows/macOS ou outra arquitetura.
- URLs são limitadas a HTTPS e a hosts de repositórios de bibliotecas conhecidos; redirecionamentos são validados novamente.
- Artefatos sem SHA-1 ou tamanho válidos não são tratados como verificados.
- Quando o manifesto declara classificadores nativos, a instalação é registrada como parcial. Este código não escolhe nem integra binários nativos do desktop ao Android.
- O runtime Java compatível, o classpath final e a associação da pasta privada com o diretório de jogo ativo do Amethyst/Pojav não são implementados nesta etapa.
- Mesmo um estado `libraries_staged` não significa jogo pronto: assets, runtime, nativos e integração real ainda precisam ser confirmados.

## Validação pendente

- [ ] Compilação Android do commit desta etapa.
- [ ] Compilação integrada com o motor no modo `validate`, sem gerar APK.
- [ ] Testes com versões oficiais reais e regras de bibliotecas variadas.
- [ ] Testes de cancelamento, rede interrompida, hash errado, caminho inválido, redirecionamento e falta de espaço.
- [ ] Verificação do classpath/nativos com o motor Android e teste real de inicialização no Galaxy A12.

Nenhum APK foi gerado.
