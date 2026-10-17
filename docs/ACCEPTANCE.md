# Matriz de aceitação

Status possíveis: APROVADA, REPROVADA, NÃO TESTADA, NÃO IMPLEMENTADA.

| Critério | Estado inicial | Evidência necessária |
|---|---|---|
| Código Java compila | CORREÇÃO ENVIADA; validação atual pendente | Workflow de compilação Java sem empacotar APK |
| APK integrado de teste | NÃO GERADO; workflow manual preparado | Executar somente quando Mikael pedir a geração do APK |
| APK instala e abre | NÃO TESTADA | Teste em dispositivo Android |
| Preferência de versão/filtro | IMPLEMENTADA NO CÓDIGO; teste de fechar/reabrir NÃO TESTADO | Seleção salva em SharedPreferences e restaurada após reinício |
| Manifesto real de versões | IMPLEMENTADA NO CÓDIGO; teste de rede em dispositivo NÃO TESTADO | Consultar manifesto oficial e confirmar conteúdo recebido |
| Perfis locais de instância | IMPLEMENTADO NO CÓDIGO; teste em Android NÃO TESTADO | Criar, fechar/reabrir, selecionar, excluir e verificar diretórios privados |\n| Importação/listagem/remoção de JARs | IMPLEMENTADA NO CÓDIGO; teste em Android NÃO TESTADO | Importar JAR confiável, rejeitar extensão inválida/duplicada/maior que 250 MB, conferir SHA-256 e excluir |\n| Botão Iniciar Minecraft | PONTE IMPLEMENTADA; não testada em dispositivo | Abrir motor Amethyst/Pojav instalado; exibir orientação quando não instalado |\n| Interface Mikael integrada ao motor | SCRIPT MANUAL PREPARADO; NÃO EXECUTADO | Build manual, instalar APK, iniciar motor e comprovar fluxo de conta/versão/Jogar |
| Download e verificação de arquivos | NÃO IMPLEMENTADA | Hash/tamanho comparado com metadados oficiais |
| Runtime Java Android | NÃO IMPLEMENTADA | Runtime compatível instalado e executado |
| Minecraft Vanilla inicia | AINDA NÃO COMPROVADO | No motor integrado, concluir autenticação/download, tocar Jogar, verificar processo e logs reais |
| Mundo jogável carrega | NÃO TESTADA | Teste em dispositivo e versão identificados |
| Forge / Fabric / OptiFine | NÃO IMPLEMENTADA | Matriz individual por versão |
| Diagnóstico de dispositivo/RAM | IMPLEMENTADO NO CÓDIGO; valores não validados em dispositivo físico | Comparar versão Android, RAM reportada e heap do app |
| Teste no Galaxy A12 | NÃO TESTADA | Evidência física do dispositivo |

A compilação não prova que o Minecraft funciona. Não marque como aprovada uma funcionalidade sem evidência reproduzível.
