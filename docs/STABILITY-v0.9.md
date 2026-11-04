# v0.9 — estabilidade de bancada, uma entrega por vez

[Roadmap](ROADMAP.md#v09) · [Issue #25](https://github.com/lamggm/ros2_pocket/issues/25) ·
[Base publicada v0.8.0](https://github.com/lamggm/ros2_pocket/releases/tag/v0.8.0)

## Início autorizado e base

O operador pediu o fluxo completo v0.8 e o início posterior da v0.9. A v0.8 foi
publicada/verificada antes da criação desta branch: PR#30/main
`d3096b82b0696d20245e291d6bcab5fa96d8a149`, CIs PR/pós-merge success, cinco assets
baixados anonimamente e comparados, preservando o APK físico
`66d1f97ef0a6a7eb08fc367981e37aa1717b6fd09e1eb36d4eb17c6b79ab8958`.

Branch de trabalho: `feat/v0.9-stability`. Principal implementa sequencialmente;
revisão independente pequena. Sem grande feature nova. Diagnóstico/reconexão,
QA, quatro watches, gráfico/CSV e lotes já existem; melhorar sua evidência e
corrigir bugs herdados não os transforma em novidades desta versão.

**Início não é fechamento da v0.9.** Não há APK0.9, nova instalação Android,
matriz LAN ou soak aprovados por este documento.

## Matriz curta e condições de campanha

1. Host: testes Python/Java/nativos e regressões afetadas no snapshot exato;
   distinguir controle sintético, JNI Linux, ROS real isolado e Android físico.
2. Lifecycle: restart/Home/retorno com dados novos e configurações preservadas;
   não aceitar label ou amostra antiga como recepção recuperada.
3. Endurance: somente após preflight oficial viável, quatro watches reais a
   2Hz cada e um gráfico finito totalmente visível, contínuo e despausado.
4. RF/IP: manter bloqueado até haver canal de recuperação independente testado
   e autorização explícita para interrupção. Tailscale sobre o mesmo Wi-Fi não
   é um canal independente. Não alterar VPN, firewall, DHCP ou rede para testar.
5. Visual/lotes: cenário próprio quando necessário, sem substituir o gate
   herdado ou atribuir passes entre APKs/snapshots.

A matriz extensa herdada continua em [qualificação v0.2](DEVICE-QUALIFICATION-v0.2.md)
e `python3 tools/qualification.py matrix`. Seus gates não são todos novas
entregas v0.9 nem passam automaticamente com esta matriz curta. Gate não
executado permanece pendente; redução de escopo exige decisão explícita.

## Budgets herdados, fixados antes da campanha

Valores conferidos pela CLI real `qualification.py matrix` no início do ciclo;
nenhum threshold foi modificado:

- Duração: **1800s**; intervalo15s ±2s;121–122pontos e span1800–1815s, conforme
  contrato do coletor, sem somar/resumir campanhas interrompidas.
- Quatro watches, **2Hz cada**, gráfico exigido e contadores reais distintos.
- PSS: pico≤262144KiB (256MiB), crescimento≤32768KiB (32MiB).
- CPU: média ponderada≤25% de um core; pico≤80%; primeira janela real medida,
  nunca preenchida com zero.
- Intake por watch em cada intervalo: pelo menos90% de2Hz×duração.
- Bateria: perda≤10pontos percentuais; alimentação fisicamente retirada e
  nenhuma fonte de carga em todas as amostras; nunca simular bateria.
- Fonte/APK/política/identidade, frescor, export fence, observabilidade do gráfico
  e instrumentos OS continuam sujeitos aos validadores existentes, sem relaxar
  falhas para admitir o novo candidato.

## Primeiro recorte: não perder o diagnóstico de timeout

Sugestão herdada da revisão v0.8: `tests/graph_format_interop.py` só gravava
`native.jsonl/native.err` depois de `subprocess.run` retornar. Em timeout, a
exceção pulava as gravações; o produtor era limpo, mas os bytes parciais do probe
se perdiam.

Reprodução TDD no entrypoint real com boundary de subprocessos sintético:

- RED: ausência dos arquivos em três variantes (bytes, textoUnicode, saídaNone),
  sem erro de setup; cleanup e exceção original já estavam preservados.
- Reparo mínimo: gravar stdout/stderr do `TimeoutExpired` antes de relançá-lo.
  Preservar bytes, inclusive nãoUTF8; texto vira UTF8 e ausência vira arquivo vazio.
- O limite de20s, exceção/falha, guard de namespace privado, ambiente separado do
  probe e finally do produtor permanecem intactos. Não fabricar result.json/PASS.
- Controle positivo adicional: captura completed ainda é gravada antes de uma
  falha posterior de validação de JSON.
- GREEN focado: **2 testes**, com três variantes no teste de timeout.
- Suite Python completa: **298 testes**, zero falhas/erros/skips, reutilizando
  somente leitura o JAR JSON já disponível. Controles sintéticos host, não uma
  campanha ROS real ou física. Revisão independente do snapshot aprovada sem
  bloqueadores; replay separado dos dois testes com zero falhas/erros/skips.

O código do app Android/nativo, versionCode e APK físico não foram alterados
neste primeiro recorte. Uma nova build/campanha terá identidade/evidência própria;
este reparo não transfere o aceite v0.8 para uma suposta release v0.9.

## Fora do primeiro recorte (histórico)

Naquele recorte não houve build/install novo, produtor real no tablet,
soak30min, RF/IP, sensor físico, serviços/actions, parâmetros, teleop, bridge
ou publicação de release v0.9. O preflight físico posterior está separado abaixo.

## Segundo recorte: preflight físico no baseline publicado

Após o operador pedir continuidade, a bancada reutilizou **exatamente o APK
v0.8.0 publicado**, sem instalar/recompilar ou editar seus metadados. Um checkout
isolado do commit de build `096a7d5` conferiu o snapshot original; o coletor e os
validadores permaneceram inalterados. Isso é baseline para preparar v0.9, **não
aceite de um APKv0.9 ainda inexistente**.

- Dispositivo real: API36; identidade de processo/UID/starttime/CLK_TCK e parser
  Android16 conferidos. Nenhuma instrumentação ativa observada no alvo naquele
  instante; não é um controle positivo/negativo de instrumentação nem prova de
  ausência contínua entre observações.
- Quatro escritores DDS Float64 reais no próprio Android, separados do app,
  com binário/IDL/fonte preservados e a biblioteca ddsc idêntica à do APK.
  Alimentação solicitada de2Hz por writer via stdin; os timestamps DDS foram
  fornecidos explicitamente pelo relógio civil do driver. Cadência do envio ao
  stdin não é frequência comprovada de recepção no rádio ou ausência de perdas.
- Perfil temporário de bancada schema3/applied, com namespace único, quatro
  watches best-effort/volatile e um campo numérico no gráfico. Preparação de
  carga por catálogo privado temporário, **não teste de importação/UI de perfis**.
- Os quatro contadores reais avançaram5→7; a observação admitida pelo coletor
  tinha **136 em cada watch**, gráfico inteiro/despausado e **61 pontos finitos**
  na série desenhada do slot0. Screenshot real inspecionada.
- A CLI oficial `collect_soak.py preflight` registrou `preflight-observation` e
  retornou **1/incomplete**, com `preflight only; no measured 30-minute soak`.
  Zero pontos de soak. Não foi convertido em PASS de endurance/CPU/PSS/bateria.
- Readback independente comprovou APK/UID, sete caminhos de configuração
  restaurados por bytes/existência, settings preservados, app parado e remoção
  dos quatro escritores/feeder/diretório de fixture. **78 arquivos privados**
  recolhidos e verificados por tamanho/hash.

A primeira tentativa de pré-requisitos ficou **incompleta** porque o observador
externo consultou `dumpsys window windows`, que não expõe `mCurrentFocus` nesse
firmware. O comando default `dumpsys window` expôs o campo; a segunda tentativa
passou os pré-requisitos. As duas foram preservadas, sem somar passes ou alterar
app/validador. Configuração/settings foram restaurados em ambas.

**Bloqueio físico atual para um soak:** o readback final encontrou AC conectado
(status2). A alimentação precisa ser fisicamente retirada antes de uma campanha
oficial; o preflight de observabilidade não prova bateria desplugada ao longo de
30 minutos. O stay-on persistente pedido pelo operador ficou efetivamente ativo
na alimentação, com PIN preservado. Nenhuma bateria foi simulada.

Continuam faltando congelar o candidato v0.9 e executar os gates relevantes nesse
mesmo artefato. Preservar falhas/rodadas incompletas, dados privados, PIN e a
configuração de energia explicitamente autorizada. RF/IP permanece condicionado
à recuperação independente e aprovação de interrupção; não houve alteração de
rede, VPN ou firewall.
