# Versões, desenvolvimento e histórico

[Início](../README.md) · [Índice da documentação](README.md)

## v0.5 — fechamento delimitado de CSV

A v0.5 implementa [exportação CSV dos gráficos](CSV-EXPORT.md). O
[fechamento](FINALIZATION-v0.5.md) registra fonte/APK, revisão independente,
418 casos Java (zero falhas/erros, 1 skip), build/lint e smoke físico CSV **4/4**
com produtor DDS local Android. O reteste curto Jazzy/LAN passou separadamente
após o operador reconectar ambos os dispositivos ao Wi-Fi.

Fechamento e merge foram autorizados; **não há nova release neste passo**. O
download público continua na v0.4.0. O candidato físico 0.5.0-dev/code5 é
preservado sem rebuild e não se confunde com APK host/CI. Issue de entrega: #22.
V0.6–v0.9 permanecem propostas; nenhuma campanha extra é iniciada automaticamente.

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
- CSV entrou na v0.5; perfis portáteis e lotes numéricos permanecem propostos no [roadmap](ROADMAP.md).
- Rede real, endurance/soak e dispositivos adicionais exigem tarefas próprias.

O fluxo é uma entrega pequena por vez, principal no chat e revisão/probe pontual
quando necessário. Features vão para a integração do ciclo. Não iniciar
campanhas de rede, CI extra, instalação ou promoção automaticamente ao terminar
uma tarefa. O filtro de push CI da integração v0.3 continua uma pendência
explícita; [Validação](VALIDATION.md#ci-público) explica o alcance dos checks de PR.

Chaves, preferências, endereços de dispositivo e logs crus ficam fora do Git.
Os documentos históricos são referências de contratos/resultados, não instruções
para repetir campanhas antigas ou iniciar operações físicas.
