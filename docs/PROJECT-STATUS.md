# Versões, desenvolvimento e histórico

[Início](../README.md) · [Índice da documentação](README.md)

## O que está publicado

A [pré-release v0.3.0-rc1](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0-rc1)
contém APK DEV debug, checksum e manifesto. Fonte do APK:
`90191840826a30c3757f09fa8b33361def91d9f5`, package
`com.lamggm.rospocket.dev`, versionCode 3, ARM64, hooks OFF.
O certificado coincide com o último APK físico conhecido da bancada, não com
qualquer compilação independente ou runner CI. A release não é versão estável.

O **aceite físico curto passou 8/8** no SM-X400 / Android 16 em 28/10/2026.
Veja o [resultado completo e limites](SMOKE-v0.3.md). O operador autorizou a
promoção da integração para `main`; isso não muda a pré-release nem cria outra release.
Para instalar, use a release; para compilar a mesma fonte, use sua tag.

## Onde está o código

- `main`: destino da promoção autorizada do código v0.3, preservando o README
  organizado e registrando o aceite físico delimitado.
- `integration/v0.3`: integração de navegação/perfis/candidato, reconciliada com
  os guias da PR #19 para esta promoção; sem novas alterações de código do app.
- `v0.3.0-rc1`: tag da fonte exata do APK publicado; não deve ser movida para
  incorporar correções documentais ou builds novos.

Para implementar uma mudança, leia o `AGENTS.md` da branch de código escolhida.
As regras antigas de v0.2 e as regras de v0.3 correspondem a ciclos diferentes;
não trate uma atualização do README público como autorização de promoção.
O [roadmap v0.3](ROADMAP-v0.3.md) conserva sequência, limites e decisões do ciclo.

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
- Publicação de eventual versão final é uma etapa própria; o download continua
  sendo a pré-release rc1 já testada, sem renomear seus bytes como build novo.
- LaserScan, exportação e lotes numéricos ficam fora da v0.3.
- Rede real, endurance/soak e dispositivos adicionais exigem tarefas próprias.

O fluxo é uma entrega pequena por vez, principal no chat e revisão/probe pontual
quando necessário. Features vão para a integração do ciclo. Não iniciar
campanhas de rede, CI extra, instalação ou promoção automaticamente ao terminar
uma tarefa. O filtro de push CI da integração v0.3 continua uma pendência
explícita; [Validação](VALIDATION.md#ci-público) explica o alcance dos checks de PR.

Chaves, preferências, endereços de dispositivo e logs crus ficam fora do Git.
Os documentos históricos são referências de contratos/resultados, não instruções
para repetir campanhas antigas ou iniciar operações físicas.
