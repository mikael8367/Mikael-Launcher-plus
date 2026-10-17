# Matriz de aceitação

Status possíveis: APROVADA, REPROVADA, NÃO TESTADA, NÃO IMPLEMENTADA.

| Critério | Estado inicial | Evidência necessária |
|---|---|---|
| Projeto Android compila | NÃO TESTADA | Execução concluída do GitHub Actions |
| APK debug é produzido | NÃO TESTADA | Artefato do workflow |
| APK instala e abre | NÃO TESTADA | Teste em dispositivo Android |
| Preferência de versão/filtro | IMPLEMENTADA NO CÓDIGO; teste de fechar/reabrir NÃO TESTADO | Seleção salva em SharedPreferences e restaurada após reinício |
| Manifesto real de versões | IMPLEMENTADA NO CÓDIGO; teste de rede em dispositivo NÃO TESTADO | Consultar manifesto oficial e confirmar conteúdo recebido |
| Download e verificação de arquivos | NÃO IMPLEMENTADA | Hash/tamanho comparado com metadados oficiais |
| Runtime Java Android | NÃO IMPLEMENTADA | Runtime compatível instalado e executado |
| Minecraft Vanilla inicia | NÃO IMPLEMENTADA | Logs e código de saída do processo real |
| Mundo jogável carrega | NÃO TESTADA | Teste em dispositivo e versão identificados |
| Forge / Fabric / OptiFine | NÃO IMPLEMENTADA | Matriz individual por versão |
| Diagnóstico de dispositivo/RAM | IMPLEMENTADO NO CÓDIGO; valores não validados em dispositivo físico | Comparar versão Android, RAM reportada e heap do app |\n| Teste no Galaxy A12 | NÃO TESTADA | Evidência física do dispositivo |

A compilação não prova que o Minecraft funciona. Não marque como aprovada uma funcionalidade sem evidência reproduzível.
