# Versões, desenvolvimento e histórico

[Início](../README.md) · [Índice da documentação](README.md)

## v1.2 — promoção e release autorizadas

Modo LAN/VPN, perfis schema4, transporte imutável até o sensor e anúncios de
leitores próprios para bridge ROS/Zenoh existente implementados. Commit de app
`da1281d`, conteúdo igual ao snapshot físico; produção1.2.0/code12/upstream e
DEV1.2.0-dev/code12/debug separados. [Uso](VPN-DDS.md) · [Proveniência](RELEASE-v1.2.md).

DEV recebeu telemetria/scan do runtime existente. Bancada análoga Ryzen com
ROS Jazzy e duas bridges Zenoh1.10.2 pela Tailscale: rodada final10/10 e
replace/remove auditados em rodada anterior, sem costurar campanha contínua.
Produção passou build/lint/assinatura no host, sem instalação física.
Merge/CI/release GitHub são autorizados; publicação só é concluída após readback
do merge, CI pós-merge e download dos assets. Não atualiza F-Droid.
Issue38 permanece aberta para ausência/retorno real da VPN; RF/IP na issue31.
Os registros anteriores não descrevem a identidade dos APKs v1.2.

## v1.1 — histórico ROS Graph, promoção e release autorizadas

Grafo nó→tópico→nó touch-first implementado; seleção destaca conversas e esmaece
contexto, com busca, pan/zoom e detalhes de associações anunciadas. Fonte dos
APKs `8ca6785`; produção **1.1.0/code11/upstream** e DEV **1.1.0-dev/code11/debug**.
O principal executa PR/CI/merge/release GitHub autorizados pelo operador.
Os registros abaixo preservam o histórico, não a identidade do APK v1.1.

Host:538 casos Java/1skip/zero falhas ou erros,309 Python e21 CTests aprovados;
lintDebug/lintRelease sem erros/fatais. Smoke físico DEV **11/11** com produtor
DDS sintético local Android. Produção passou build/manifesto/assinatura/hooks
OFF; não foi instalada/testada fisicamente nesta rodada. Tentativa ROS pela
LAN sem descoberta confirmada; RF/IP permanece na issue31. F-Droid independente
e catálogo oficial não são atualizados por uma release GitHub.
[Uso](ROS-GRAPH.md) · [Proveniência e limites](RELEASE-v1.1.md) ·
[Última release](https://github.com/lamggm/ros2_pocket/releases/latest).

## v0.9 — estabilidade de bancada, aceite técnico aprovado

[Fechamento, identidade e limites](FINALIZATION-v0.9.md) · [Histórico](STABILITY-v0.9.md).
Fonte do app073821c, harnessca69285, **0.9.0-dev/code9/ARM64/debug/hooks OFF**.
Correções de timeout, primeira janelaCPU e observação de desenho pendente,
reproduzidas e revisadas sem relaxar aceitação. Python303 testes frescos sem
falhas/skips; wrapper offline aprovado e Java497/1skip retidos como UP-TO-DATE.

**Soak físico oficial PASS:**121 amostras/1800,694606s, quatro watches Float64
DDS nativos locais Android e gráfico visível, desplugado, dentro dos budgets.
**Lifecycle2/2 PASS:** Home/retorno65→115 e restart real115→175, configuração
preservada, conexão explícita após restart. Mesmo APK, cleanup/readback e cópias
de evidência verificados; tentativas incompletas preservadas, sem somar rodadas.

SHA-256 do APK físico:
`fce2b53d6842e5ba13ac51de47a7a2d346c49b150d8ba3e51bb6d05f7c4cbed0`.
Promoção, CI do head/pós-merge e releasev0.9.0 são registrados na
[issue25](https://github.com/lamggm/ros2_pocket/issues/25) e manifesto público,
sem substituir pelos bytes do CI. **RF/IP não executado**, separado do aceite
proporcional e aberto na [issue31](https://github.com/lamggm/ros2_pocket/issues/31).
Sem nova matriz Android/ROS real pela LAN ou certificação de produção.

## v0.8 — Humble/Jazzy com formato manual

[Contrato e resultados](HUMBLE-JAZZY.md). Fonte do APK `096a7d5`,
**0.8.0-dev/code8**, ARM64/debug compatível/hooks OFF. Seletor manual de formato
persistido no perfil, schemas antigos legíveis e aplicação apenas desconectada.
Matriz ROS real Humble/Jazzy × Fast DDS/Cyclone e lifecycle Humble aprovados
em contêineres privados; 497 testes Java, zero falhas/erros e um skip legado,
21 CTests e revisão independente aprovados, lint zero erros/123 avisos.

Smoke físico direcionado **4/4** em 2026-11-04: ambos os grafos com DDS nativo
sintético local Android e contadores avançando, bloqueio de alteração durante
conexão e perfil Humble restaurado desconectado após reinício. Instalação
in-place, configurações originais restauradas e APK/UID/cleanup conferidos por
readback separado. Não é ROS real pela LAN nem soak. APK físico:
`66d1f97ef0a6a7eb08fc367981e37aa1717b6fd09e1eb36d4eb17c6b79ab8958`.
Promoção concluída: PR#30/main `d3096b8`, CI PR41823327092 e main41826471322
success com logs/artefatos/árvores conferidos. [Releasev0.8.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.8.0)
Latest com cinco assets baixados anonimamente/hash comparados; seis releases
anteriores preservadas, issue#18 e milestonev0.8 fechadas.
[Fechamento e identidade do artefato](FINALIZATION-v0.8.md). O APK publicado deve
ser o mesmo físico, nunca a recompilação do CI.

## v0.7 — histórico numérico em lotes

[Contrato/budgets](NUMERIC-BATCHES.md) · [Fechamento](FINALIZATION-v0.7.md).
Fonte limpa do APK `dc5e772`, **0.7.0-dev/code7**, ARM64/debug compatível/hooks OFF.
490 testes Java sem falhas/erros, um skip legado; lint zero erros/121 avisos,
20 CTests e matriz host Fast DDS/Cyclone aprovados. Revisão independente aprovada
após corrigir a perda de gaps por timestamp coincidente. Quatro watches host a
50 Hz transportaram 800 amostras sem descarte local na rodada medida.

Smoke físico delimitado **4/4**, com produtor DDS local Android separado;
CSV live e pausado com 600 pontos/150 gaps cada, pausa/cancel/retomada e cleanup
verificados. Não é uma nova matriz LAN ou soak. SHA-256 do APK físico:
`2437dfbd53a55618a17680874e1e9cfa08ecc978e912808db9ac7ee57fba9b88`.
PR, CI exato/pós-merge e release acompanham a [issue #24](https://github.com/lamggm/ros2_pocket/issues/24).
Não substituir pelos bytes do CI. A v0.8 e o início da v0.9 estão descritos acima.

## v0.6 — perfis portáteis

Seleção scan/câmera e modo nos perfis; importação/exportação de um perfil JSON
pelo seletor Android, com confirmação, validação e UUID novo. Schema1 permanece
legível sem regravação no load; Aplicar e Conectar continuam separados.

[Contrato](PORTABLE-PROFILES.md) · [Fechamento](FINALIZATION-v0.6.md).
Fonte limpa do APK `153e3ee`, interno **0.6.0-dev/code6**, ARM64/debug/hooks OFF.
457 casos Java sem falhas/erros, um skip legado; build/lint aprovados e revisão
independente sem blockers. Smoke físico de configuração/documentos **7/7**,
sem produtor DDS. Promoção, CI e release acompanhados na issue #23.
O download da [última release](https://github.com/lamggm/ros2_pocket/releases/latest)
inclui a identidade exata do APK; não confundir build do CI com candidato físico.
A v0.6 foi publicada na [release v0.6.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.6.0),
PR #28/main `a365079`, CI da PR/pós-merge aprovados e assets baixados/conferidos.

## v0.5 — fechamento delimitado de CSV

A v0.5 implementa [exportação CSV dos gráficos](CSV-EXPORT.md). O
[fechamento](FINALIZATION-v0.5.md) registra fonte/APK, revisão independente,
418 casos Java (zero falhas/erros, 1 skip), build/lint e smoke físico CSV **4/4**
com produtor DDS local Android. O reteste curto Jazzy/LAN passou separadamente
após o operador reconectar ambos os dispositivos ao Wi-Fi.

A [release v0.5.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.5.0)
foi publicada e verificada após merge da PR #27/main `aa7e39d`, com CI da PR e
pós-merge aprovados. O APK físico 0.5.0-dev/code5 foi preservado sem rebuild e não
se confunde com APK host/CI. Issue #22 e milestone v0.5 encerrados.

## v0.4 — fechamento de sensores visuais

A [release v0.4.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.4.0)
acrescenta LaserScan 2D e câmera ROS alternados no Monitor, preservando watches,
gráficos, logs e perfis. [Uso e limites](SENSOR-VIEWS.md).

- Host/build ARM64 e revisão independente concluídos; smoke físico curto **6/6**
  no SM-X400 / Android 16, com webcam real JPEG e LaserScan sintético.
- APK distribuído: **0.4.0-dev**, versionCode **4**, package
  `com.lamggm.rospocket.dev`, ARM64, debug v2, hooks OFF. Mantém exatamente os
  bytes testados no tablet, sem rebuild para alterar a etiqueta da versão.
- Fonte: implementação local baseada em `c55fbb3`; a integração incorpora apenas
  reconciliação documental e ligação do replay de sensores ao CI após o smoke.
- [Identidade do APK, critérios e limites](FINALIZATION-v0.4.md). O manifesto da
  release distingue o snapshot de build/teste da árvore Git publicada.
- `main` recebe a v0.4 pelo fechamento autorizado; nenhuma campanha extra é
  iniciada por este documento. As tags e os APKs v0.3 permanecem intactos.

## Histórico publicado v0.3

A [release v0.3.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0)
foi publicada da main `c55fbb3`, reutilizando o APK da
[pré-release v0.3.0-rc1](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0-rc1).
Fonte original do APK: `90191840826a30c3757f09fa8b33361def91d9f5`, code3,
assinatura debug de bancada. Seu [smoke físico](SMOKE-v0.3.md) passou **8/8**.
A evidência v0.3 não é usada como aceite físico da v0.4.

## Histórico da v0.3

- **Base:** PR #14, merge `a5fc28d`, CI 40523750784 aprovado.
- **Navegação:** [PR #15](https://github.com/lamggm/ros2_pocket/pull/15), merge
  `de3c254`, CI 40537386529; smoke físico delimitado **11/11**, fonte `a86cf7d`.
- **Perfis:** [PR #16](https://github.com/lamggm/ros2_pocket/pull/16), merge
  `e655da3`, CI 40569878837; revisão independente e regressões de lifecycle
  aprovadas. Aceite físico posterior no candidato publicado: **8/8**.
- **Candidato:** [PR #17](https://github.com/lamggm/ros2_pocket/pull/17), merge
  `6012a05c378c684e34859812c01b73ea701b3134`, árvore idêntica ao head `9019184`.
  [CI 40574783991](https://github.com/lamggm/ros2_pocket/actions/runs/40574783991)
  aprovado. Na fonte do APK, a validação local teve 348 Java aprovados, dois
  skips opt-in, 295 Python, 14 CTests, lint e build ordinary aprovados.

- **Documentação:** PR #19, merge `88e5e11`, README curto e guias por assunto.
- **Aceite físico posterior:** [smoke 8/8](SMOKE-v0.3.md), no APK público da fonte
  `9019184`, sem mudar o aplicativo; cleanup e apresentação conferidos.
- **Promoção:** autorizada pelo operador após o smoke, preservando a ancestralidade
  das duas branches e sem mover a tag ou publicar outra release.

Cada resultado conserva seu SHA/APK. O teste anterior da navegação não foi usado
como aceite do novo candidato. [FINALIZATION-v0.3](FINALIZATION-v0.3.md) separa o
resultado atual do plano histórico anterior à instalação/publicação.

## Histórico da v0.2

A [PR #1](https://github.com/lamggm/ros2_pocket/pull/1) promoveu o protótipo de
bancada para `main` em `c659b8521c093aacc3ccecd0855c07727398db2b`, com
[CI 40515533393](https://github.com/lamggm/ros2_pocket/actions/runs/40515533393)
aprovado. A entrega limitada e suas identidades de fonte/APK estão em
[FINALIZATION-v0.2](FINALIZATION-v0.2.md); notas em
[RELEASE-NOTES-v0.2](RELEASE-NOTES-v0.2.md). Não foi qualificação física extensa.

O smoke físico curto cobriu duas séries finitas, pausa com intake avançando,
retomada com desenho novo e cleanup/preferências conferidos. Evidências host,
JNI Linux, Canvas e físicas continuam classificadas separadamente.
Recuperação real de Wi-Fi/IP, soak e qualificação extensa ficaram como backlog,
sem alteração dos thresholds e sem transformar relatórios incompletos em PASS.

## Evidência anterior da v0.1

O protótipo v0.1 passou 16 verificações reais no tablet, não aceite v0.2/v0.3:
String com sequência avançando, grafo de nós real, Home/retorno com amostra nova,
perda de produtor/stale/remoção, substituição por Cyclone, rejeição/recuperação
reliable versus best-effort, domínio errado/correto, Float64, reinício de
processo, configurações preservadas e leitura retomada. Jazzy Fast DDS e Cyclone
foram exercitados. A fixture também cobriu `/rosout` Jazzy com processos ROS e
reader Cyclone separados.

Regressões nativas adicionais exercitam falhas reais de argumentos de criação
de tópico/reader, telemetria inativa, polls repetidos, receipts DDS vinculados a
identidades e recuperação com amostras novas. Veja [READER-FAILURE-TESTS](READER-FAILURE-TESTS.md).
Instrumentação empacotada e JNI Linux com extra-watch/rosout coexistindo não
comprovam execução dessa instrumentação no Android físico.

## Próximos trabalhos

- Compatibilidade Humble/Jazzy e matriz multidistribuição: [issue #18](https://github.com/lamggm/ros2_pocket/issues/18).
- A publicação v0.4.0 reutiliza o APK físico já testado, sem renomear seus bytes como build novo.
- CSV entrou na v0.5, perfis portáteis na v0.6 e lotes numéricos na v0.7;
  v0.8 foi publicada no escopo declarado; v0.9 passou soak local/lifecycle,
  com promoção/CI/release acompanhados na issue25 e no [fechamento](FINALIZATION-v0.9.md).
- Rede real RF/IP permanece não validada na issue31; dispositivos adicionais
  e novos cenários de endurance exigem tarefas próprias.

O fluxo é uma entrega pequena por vez, principal no chat e revisão/probe pontual
quando necessário. Features vão para a integração do ciclo. Não iniciar
campanhas de rede, CI extra, instalação ou promoção automaticamente ao terminar
uma tarefa. O filtro de push CI da integração v0.3 continua uma pendência
explícita; [Validação](VALIDATION.md#ci-público) explica o alcance dos checks de PR.

Chaves, preferências, endereços de dispositivo e logs crus ficam fora do Git.
Os documentos históricos são referências de contratos/resultados, não instruções
para repetir campanhas antigas ou iniciar operações físicas.
