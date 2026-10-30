# Roadmap do ROS Pocket — v0.5 a v0.9

[Projeto](../README.md) · [Documentação](README.md) ·
[Milestones](https://github.com/lamggm/ros2_pocket/milestones) ·
[Estado publicado](PROJECT-STATUS.md)

## Direção e estado

**Exportar → levar a bancada pronta → capturar melhor → ampliar compatibilidade → consolidar estabilidade.**

A [v0.5.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.5.0) publicou CSV
sobre sensores, quatro watches, gráficos, rosout e perfis. A v0.6 acrescenta
perfis portáteis, com host/revisão e smoke físico direcionado aprovados; promoção
e publicação são acompanhadas na issue #23. Consulte a [última release](https://github.com/lamggm/ros2_pocket/releases/latest)
para distinguir APK disponível de código em desenvolvimento. Este documento
não inicia outro ciclo, instala APKs ou autoriza campanhas físicas/rede.

- **Proposto:** direção futura, revisável antes de iniciar o ciclo.
- **Planejado:** prioridade/escopo definidos; implementação ainda não iniciada.
- **Em andamento:** trabalho explicitamente iniciado, com issue/PR vinculadas.
- **Entregue:** critérios verificados e fechamento identificado; merge documental não basta.

**v0.5 publicada; v0.6 implementada e validada no escopo delimitado; v0.7–v0.9 propostas**.
Contratos e evidências: [CSV](FINALIZATION-v0.5.md) e
[perfis portáteis](FINALIZATION-v0.6.md). Na publicação inicial deste roadmap,
todas estavam não iniciadas. Milestones futuros permanecem sem datas.
Revisar este arquivo ao encerrar cada ciclo, preservando o histórico no Git.

## Acompanhamento

- **v0.5 — CSV:** [milestone](https://github.com/lamggm/ros2_pocket/milestone/1) · [issue #22](https://github.com/lamggm/ros2_pocket/issues/22).
- **v0.6 — Perfis portáteis:** [milestone](https://github.com/lamggm/ros2_pocket/milestone/2) · [issue #23](https://github.com/lamggm/ros2_pocket/issues/23).
- **v0.7 — Lotes numéricos:** [milestone](https://github.com/lamggm/ros2_pocket/milestone/3) · [issue #24](https://github.com/lamggm/ros2_pocket/issues/24).
- **v0.8 — Humble/Jazzy:** [milestone](https://github.com/lamggm/ros2_pocket/milestone/4) · [issue #18 existente](https://github.com/lamggm/ros2_pocket/issues/18).
- **v0.9 — Estabilidade:** [milestone](https://github.com/lamggm/ros2_pocket/milestone/5) · [issue #25](https://github.com/lamggm/ros2_pocket/issues/25).

O roadmap explica a direção; milestones agrupam versões; as issues registram
entregas, aceite e evidências. Não duplicar a issue #18, não fechar issues de
funcionalidade com a PR deste documento e não criar tickets especulativos para
cada detalhe. Abrir tarefas menores somente quando iniciar o respectivo ciclo.

<a id="v05"></a>

## v0.5 — Exportar o histórico dos gráficos em CSV

**Estado:** entrega delimitada concluída, merge e release v0.5.0 publicados/verificados.
[Evidências e limites](FINALIZATION-v0.5.md). **Depende de:** histórico de gráficos já existente.

**Objetivo:** tirar os dados retidos do tablet para análise sem bloquear o worker DDS.
O seletor externo pode suspender DDS ao tirar o app do primeiro plano, conforme
o lifecycle herdado; não há promessa de recepção contínua durante o seletor.

**Entra:**
- Botão Exportar gráficos com uma cópia consistente das séries selecionadas.
- CSV UTF-8, números sem locale, identificação de tópico/tipo/slot/campo,
  unidade somente quando conhecida, tempo de recepção com origem declarada e
  timestamp DDS de origem quando disponível. Os relógios continuam distintos.
- Preservar gaps/estado inválido; não inventar zeros, unidades, horário civil ou
  precisão Int64 que o histórico de gráfico já perdeu.
- Pausado: exportar a cópia visível congelada, sem misturar live, sessões ou readers.
- Salvar pelo seletor Android, I/O limitado fora do worker DDS e tratamento de cancelamento/erro.

**Não entra:** mudança da ingestão/retenção, lotes, gravação contínua, scan/imagens ou rosbag.
O gráfico atual usa [latest snapshots](LIVE-PLOTS.md), não todas as mensagens.
O CSV é exportação para o usuário, não o dump interno de QA nem gravador sem perdas.

**Pronto quando:** dado DDS real → gráfico → CSV aberto em Python corresponde ao
snapshot escolhido, preserva tempos/gaps e continua recebendo após salvar,
cancelar ou falhar. Testes de serializador/snapshot/UI e revisão pontual;
smoke Android delimitado quando autorizado.

<a id="v06"></a>

## v0.6 — Bancadas completas e portáteis

**Estado:** implementação, host/revisão e smoke físico 7/7 concluídos;
promoção/release acompanhadas na issue #23. [Evidências e limites](FINALIZATION-v0.6.md).
**Sequência:** depois da v0.5; reutiliza os perfis da v0.3.

**Objetivo:** levar uma configuração pronta entre instalações.

**Entra:** seleção visual scan/câmera nos perfis (tópico, tipo e modo), schema
versionado com leitura dos perfis schema1, importação/exportação JSON pelo seletor
Android, validação de tamanho/schema/campos e tratamento explícito de nomes duplicados.
Aplicar apenas desconectado; Conectar continua explícito. Avisar que o JSON pode
conter peer/IP e nomes de tópicos antes do compartilhamento.

**Delta real:** conexão, watches e gráficos já são persistidos. A novidade é incluir
sensores e transportar configuração, não reimplementar perfis.

**Não entra:** amostras/imagens, handles/gerações DDS, credenciais, cloud/sync,
conexão automática, banco novo ou editor genérico de dashboards.

**Pronto quando:** salvar → exportar → importar → aplicar funciona sem perder perfis
antigos, sobrescrever silenciosamente ou conectar sozinho. Cobrir migração, arquivo
inválido/corrompido, reinício desconectado e tópico visual ausente, com revisão e
smoke direcionado quando autorizado.

<a id="v07"></a>

## v0.7 — Histórico numérico com lotes limitados

**Estado:** proposto. **Depende de:** histórico/CSV; medir antes de alterar o caminho nativo.

**Objetivo:** reduzir a decimação entre polls, transportando mais amostras realmente
tomadas pelo reader até o histórico, em vez de apenas a última.

**Entra:** budgets de taxa/lote/fila/bytes definidos por medição, lotes nativos/JNI
com relógios e identidade originais, consumo limitado de campos necessários pelo
histórico/CSV e descarte local explícito. Renderização continua desacoplada.

**Não entra:** fila infinita, interpolação de amostras, rosbag ou promessa de recepção
sem perdas. Descarte local observado não é contagem universal de perdas DDS/rede.
Não alocar históricos de todos os campos de todos os tipos por conveniência.

**Pronto quando:** amostras entre polls chegam dentro do orçamento acordado;
saturação/recuperação são limitadas e observáveis, sem travar UI ou ressuscitar
reader antigo. Exercitar produtores reais separados em Fast DDS/Cyclone DDS,
rajadas, NaN/Inf, clocks e coexistência com quatro watches/visual/rosout; TDD,
revisão e smoke Android direcionado quando autorizado.

<a id="v08"></a>

## v0.8 — Compatibilidade Humble/Jazzy com alcance declarado

**Estado:** proposto. **Acompanhamento:** issue #18, preservada, sem duplicação.
**Sequência:** após os ciclos anteriores; não depende tecnicamente dos lotes.

**Objetivo:** resolver a pendência herdada de compatibilidade multidistribuição.
Começar por um spike Humble isolado e schemas reais antes de prometer implementação.

**Entra:** matriz Humble/Jazzy × Fast DDS/Cyclone DDS, separando descoberta de
endpoints, leitura de mensagens padrão/rosout e grafo/nós. Implementar somente a
estratégia segura demonstrada, mantendo Jazzy e formato desconhecido explícito.

**Não entra:** inferir compatibilidade por tópico descoberto, inventar nomes de nós,
reescrever stack/RPC, adicionar bridge ou alegar suporte universal. Se a resolução
segura exigir mudança grande ou seleção ambígua do formato, replanejar o ciclo.

**Pronto quando:** critérios da issue #18 e matriz suportada são demonstrados com
versões/RMW/fonte exatos, testes não vazios, regressões Jazzy preservadas e
limitações claras. Distinguir host de eventual validação Android física autorizada.

<a id="v09"></a>

## v0.9 — Consolidação para uso prolongado em bancada

**Estado:** proposto. **Depende de:** candidato integrado e preflight físico viável.

**Objetivo:** tratar pendências reais de rede/reinício e uso prolongado, sem grande
feature nova nem alegação automática de maturidade de produção.

**Entra:** matriz curta e orçamentos de memória/CPU/intake/bateria fixados antes dos
testes, correções reproduzidas por TDD, restart com dados novos/settings preservados
e perda/retorno de rede com recuperação independente e autorização de interrupção.
Soak físico oficial de 30 minutos contínuos somente com preflight real, alimentação
fisicamente retirada e observabilidade dos quatro watches/gráfico conforme os
contratos herdados. Visual/lotes podem exigir cenário próprio, sem substituir o gate antigo.

**Não entra:** mudar thresholds depois de falha, somar rodadas incompletas, simular
bateria ou chamar Home de perda de Wi-Fi. LiDAR físico/matriz de câmeras dependem de
equipamento e escopo combinados, não são validações implícitas deste documento.

**Pronto quando:** jornadas/orçamentos acordados passam no candidato exato, com
revisão/CI e limitações registradas. Timeout/preflight inviável continua incompleto.
Redução de escopo exige decisão explícita, nunca reescrita de resultado antigo.

## Limites e condução comuns

- DDS direto, Android Java/JNI/C++ e operação somente leitura. Preservar buffers,
  clocks, fences de sessão/reader e lifecycle; não criar outro framework ou servidor.
- Uma entrega por vez no principal; delegação apenas de pequenas revisões/probes.
  Detalhar somente o próximo ciclo e revisar o restante conforme uso real.
- TDD para comportamento novo, revisão pontual e regressões afetadas. CI e teste
  host não equivalem a aceite físico; documentação não transfere aprovação de APK.
- Nenhuma data fictícia. Milestone planejado/proposto não é autorização de release,
  instalação, rede/soak ou execução automática de todos os ciclos.
- Humble, RF/Wi-Fi/IP e soak são pendências herdadas não validadas; entrar neste
  calendário não as torna funções novas nem PASS. Ver [validação](VALIDATION.md).
- Escolher identidade interna antes do build a qualificar e preservar os bytes
  aprovados. Não alterar tags/releases antigas ou publicar APK reconstruído como testado.

## Fora até v0.9, salvo decisão explícita de produto

Teleop/publicação de comandos, escrita de parâmetros, serviços/actions genéricos,
rosbag, mapas/TF/SLAM/RViz completo, áudio/gravação de vídeo, tipos customizados
dinâmicos, contas/cloud e descoberta roteada entre sub-redes. Leitura de parâmetros
também depende de RPC ROS e precisaria de spike próprio, não só de uma nova tela.

## Histórico e próxima etapa

Os roadmaps [v0.2](ROADMAP-v0.2.md), [v0.3](ROADMAP-v0.3.md) e
[v0.4](ROADMAP-v0.4.md) conservam o que cada fase autorizou. O
[fechamento v0.4](FINALIZATION-v0.4.md) registra o alcance real entregue.

A publicação inicial foi somente planejamento. O fechamento v0.5 posterior está
registrado na issue #22 e no documento de evidências. A próxima direção proposta
é v0.6 (issue #23); seu início depende de autorização própria.
