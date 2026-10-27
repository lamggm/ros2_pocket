# ROS Pocket v0.2 — entrega de protótipo de bancada

## Decisão de escopo

O operador autorizou o merge da [PR #12](https://github.com/lamggm/ros2_pocket/pull/12)
e a redução explícita do escopo da v0.2 para **protótipo de bancada**.
Esta decisão substitui o uso da qualificação física extensa como bloqueio da
entrega limitada; não converte testes pendentes em aprovados.

O produto continua somente leitura, DDS direto, IPv4 na mesma LAN, com buffers
limitados, dados obsoletos identificados e hooks OFF no APK de uso normal.
Não há alteração de código, guardas, orçamento de recursos ou política de segurança
para implementar esta decisão. Uma tarefa pequena por vez, conforme `AGENTS.md`.

## Critérios de entrega limitada

- Revisão do código e testes/lint/build/scan aplicáveis aprovados; CI do head da PR.
- APK DEV normal identificado por fonte, hash, ABI e assinatura; atualização
  in-place sem uninstall/clear data e sem contornar certificado incompatível.
- Teste curto real de duas séries finitas no gráfico, pausa visual enquanto a
  recepção continua e retomada com desenho novo; cleanup e preferências conferidos.
- Limitações documentadas e backlog explícito, sem alegar endurance ou recuperação
  física de rede não exercitados.

O merge da #12 integra esse trabalho em `integration/v0.2`. A promoção para `main`
na PR #1 exige autorização própria; ela continua draft e sem auto-merge.
Tag, GitHub Release, assinatura de produção e loja não fazem parte desta decisão.

## Evidência host existente — sem reatribuir SHA

Código `496b9bb9053f915f9e249de675dec3f44d25fc23`:

- Nove gates locais aprovados: build offline, lint, empacotamento
  ordinary/diagnóstico/nondebug e inspeção dos dois runners.
- 293 Python sem skips; 277 Java/Robolectric com um skip intencional JNI opt-in.
  O gate Linux JNI real passou separadamente.
- DDS real em fixtures isoladas Fast DDS e Cyclone DDS, com replay Canvas host.
- Correção do orçamento e cleanup do launcher revisados independentemente.
- Fonte e histórico examinados pelo scanner de segredos, sem findings.

Head documental `30008c95675b121ca6c56e717dbf08074dab9c28`:

- Build local final aprovado: 293 Python sem skips, Java/lint/build ordinary OFF.
- [CI Verify 40407163597](https://github.com/lamggm/ros2_pocket/actions/runs/40407163597)
  aprovado; artefato reinspecionado. No CI, 287 Python e seis GraphProvenance
  skipped por cache `org.json` ausente; os seis passaram localmente.
- A árvore do merge de teste desse CI foi conferida contra o head da PR.

O registro `ci/finalization-gates.json` mantém os três gates host registered.
Os testes e validadores de qualificação extensa continuam fail-closed e inalterados.
O CI de cada novo head deve ser consultado separadamente; evidência anterior não
se torna teste do novo commit por mera troca do nome do arquivo.

## Teste curto no tablet — concluído

No APK DEV ordinary `0.2.0`, code `2`, ARM64, hooks OFF, fonte `30008c9`:

- SHA-256: `d1577e6a484bda95d33d6f9c49d254fd700b95760468ff39d1378ccbd2c339c8`.
- Atualização in-place preservou UID e preferências.
- Fixture própria Fast DDS: duas séries selecionadas com pontos finitos e
  desenho concluído; recortes reais inspecionados.
- Pausa: pixels idênticos com intake avançando. Retomada: pixels novos.
- Readback final confirmou fixture ausente, app parado, preferências restauradas
  byte a byte e configuração de tela restaurada.

Isso comprova somente esse fluxo curto. Não prova descoberta multicast automática,
qualificação gráfica completa nos dois RMWs, instrumentação, rede ou soak.
As evidências brutas ficam privadas, fora do Git. Alterações documentais posteriores
não reidentificam esse APK como build do novo head. Se mudar código, gere nova
identidade e reexecute os comportamentos afetados, sem importar passes históricos.

## Qualificação estendida — adiada, não bloqueia este protótipo

O [plano extenso](DEVICE-QUALIFICATION-v0.2.md) permanece disponível para tarefas
futuras. Seus gates e thresholds não foram relaxados. Continuam não comprovados
para um candidato integrado final nesse plano:

- Regressão física completa e gráficos completos nos dois RMWs.
- Perda/retorno real de Wi-Fi e mudança real de IP. Exigem autorização de
  interrupção e recuperação alternativa previamente testada.
- Soak oficial de 30 minutos, quatro watches e gráfico, sem alimentação externa.
- Aceite completo e relatório ready da qualificação estendida.

`ready`, `full12` e `physicalAccepted` dos relatórios extensos permanecem falsos
quando os respectivos requisitos não foram cumpridos. A entrega limitada usa os
critérios acima, não um relatório extenso falsamente aprovado. Não alterar JSON,
reduzir thresholds, simular bateria nem executar campanhas automaticamente para
produzir uma aparência de encerramento.

## Backlog e exclusões

- Robustez/endurance, recuperação física de rede e compatibilidade com outros
  dispositivos: tarefas futuras explícitas, sem migração fictícia de evidências.
- Harness: otimizar somente quando bloquear um teste concreto.
- Cosmética/localização e avisos conhecidos de lint sem impacto funcional demonstrado.
- Parâmetros/RPC, serviços/actions, teleop/publicação, rosbag, RViz/TF e tipos
  customizados dinâmicos continuam fora da v0.2.
