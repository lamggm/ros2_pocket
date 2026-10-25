# Qualificação v0.2 — preparação, não aceite físico

**Estado: pendente.** Esta frente entrega um validador de relatórios e um plano
pré-declarado, não um candidato aprovado. Nenhum comando deste toolkit instala,
lança, modifica ou consulta automaticamente um dispositivo, publica DDS, altera
Wi-Fi/VPN/firewall ou faz merge. A integração continua draft até os gates reais.
Os 16 checks históricos do protótipo **não** qualificam a integração v0.2.
Existência de branch, código compilável ou CI verde não prova entrega de uma frente.

## Snapshot e limites de confiança

`tools/qualification.py` usa somente Python stdlib. Seu input é o JSON v1 real de
`tools/artifact_metadata.py`: HEAD, dirty, hash de conteúdo da fonte, identidade e
versões do APK, tamanho/hash, ABIs, certificado e classe de assinatura. Não edite
metadados para fazê-los coincidir. O APK deve ser o mesmo verificado por
`aapt2`/`apksigner`; a qualificação confere seus bytes, não reimplementa Android signing.

O contrato v1 exige `test_hooks` explicitamente **boolean false**, tanto no candidato
normal não instrumentado quanto na evidência `installed-apk`. True, ausente, null,
zero numérico e strings são inválidos. Metadados legados têm capacidade desconhecida;
a migração exige inspeção fresca dos binários exatos e novo relatório vinculado,
nunca inserir false em evidência antiga. Binário sem marcador reconhecido exige
rebuild, não edição do JSON. Veja o [contrato e migração](READER-FAILURE-TESTS.md).
Nenhum orçamento/plano de soak ou gate físico é dispensado por este campo.

- `init` e `evaluate` conferem fonte atual e tamanho/SHA-256 do APK local.
  **Ambos os esquemas** reinspecionam todos os ELF/ABIs: v1 exige exatamente OFF;
  apagar campos de diagnóstico não autoriza um binário ON/desconhecido. `analyze-soak`,
  `record` do soak e a API de avaliação também exigem os bytes do candidato.
  Use `--apk` explicitamente com analyze/record; por compatibilidade, se omitido,
  `metadata.apk` é resolvido ao lado do JSON (caminho absoluto também é inspecionado).
  Arquivo ausente, hash divergente ou política desconhecida bloqueiam; não há waiver JSON.
- Todo resultado registrado vincula-se ao snapshot completo e a arquivos locais
  não vazios com SHA-256. Ausência, alteração, escape do diretório e duplicação de
  evidência são recusados. JSON duplicado, não finito ou maior que 4 MiB é recusado.
  Todo JSON carregado ou gravado admite no máximo **64 níveis de contêineres** (objetos/listas,
  incluindo campos desconhecidos); o objeto/lista raiz conta como nível 1, escalares
  não acrescentam nível. A validação iterativa ocorre antes de cópia, comparação ou
  serialização posteriores. Aninhamento excessivo, inclusive quando excede a
  capacidade do decoder, é entrada inválida (`2` na CLI), nunca um traceback.
  A gravação valida o relatório completo (incluindo o wrapper) e seus bytes UTF-8
  serializados (indentação/escapes/newline incluídos) contra os mesmos 4 MiB antes
  da substituição atômica; rejeição preserva o relatório anterior ou não cria o arquivo.
- IDs obrigatórios são únicos e exatos: remover, duplicar ou inventar gates invalida
  o relatório. `pending`, `failed`, `blocked` e qualquer gate não executado impedem ready.
- `ready` exige fonte **limpa**, todos os gates explicitamente aprovados, evidências
  presentes e válidas, soak medido e aceite manual separado. Uma preparação dirty
  pode existir, mas jamais vira ready. Mudou HEAD, conteúdo, dirty ou APK? Gere um
  novo candidato/relatório e reexecute a integração e os gates afetados. O toolkit
  conservadoramente não importa passes entre snapshots.
- O validador não autentica uma pessoa, nem prova o conteúdo semântico de logs ou
  que uma captura veio fisicamente de um tablet. Operador e revisor devem conferir
  autenticidade, escopo, comandos, tempos, contagens e correspondência ao candidato.
  `ready` é validação de evidência declarada, **não autorização automática de release**.
- Mantenha relatório/evidências apenas em `build/qualification/` (ignorado). A CLI
  restringe gravações e raiz de evidência a `build/` do checkout. Não publique dados
  de dispositivo, serial, endereços, dumps privados, keystore ou logs brutos.

## Matriz obrigatória

Todos os IDs começam `pending`; execute `python3 tools/qualification.py matrix`
para a matriz autoritativa e o plano. Os gates de bancada abaixo exigem amostras
novas do **APK integrado final**, não screenshots antigas ou labels em cache.

| ID | Evidência mínima / condição de aprovação |
|---|---|
| `host-tests` | Testes Python/Java/nativos e regressão integrada do snapshot final; seleções CTest não vazias |
| `host-lint` | Lint Android final; revisar avisos, não só exit code de assemble |
| `host-build` | Build/proveniência real, verificação do APK, caminho offline e identidade final |
| `secret-scan` | Histórico completo **e** fonte atual; saída redigida e versão/configuração do scanner |
| `increment-review` | Revisão explícita dos oito incrementos integrados, contratos e testes; registrar SHA/escopo/pendências por frente, não só branches |
| `fastdds` | Jazzy Fast DDS no tablet: descoberta, String/número novos e nós reais |
| `cyclonedds` | Jazzy Cyclone DDS: mesmos critérios e troca de produtor |
| `wrong-domain` | Domínio errado sem intake; correção restaura amostra nova |
| `lifecycle` | Stop/Home/recriação/restart: cleanup, configurações e amostra nova; sem callbacks antigos |
| `reader-failure` | Falha real de criação de tópico **e de reader**, erro persistente e telemetria inativa; recuperação sem dados verdes antigos |
| `qos-removal` | Incompatibilidade/recovery, remoção/substituição de endpoints/nós, writers de grafo independentes, associações GID e unknown honesto |
| `rosout` | Severidades, UTF-8, linhas longas/rajadas, limite de buffer, busca/pausa/clear sem parar outros readers |
| `structured` | Vector3, Quaternion, Twist, Pose, Odometry reais; headers/frames/covariâncias, Unicode, NaN/Inf e tipos desconhecidos |
| `multiwatch` | Quatro watches independentes, quinta recusada, rates/QoS mistos, falha de um não derruba outros; remoção/teardown idempotentes |
| `graphs` | Campos numéricos reais, buffers limitados, pause/resume/seleção; lacunas/NaN/Inf não viram zero; tempo de recebimento distinto da origem |
| `recovery-plan` | Operador confirma canal alternativo testado e plano de retorno antes de interrupção física |
| `wifi-loss-return` | Perda e retorno **físicos** do Wi-Fi, stale/diagnóstico, rebind e intake novo; executar/registrar recuperação pelo canal alternativo |
| `address-change` | Mudança real de endereço, nova interface/endereço mostrado, rebind/intake novo e recuperação de controle |
| `soak` | JSON bruto de medições completo aprovado pelo plano fixo abaixo |
| `installed-apk` | JSON v1 extraído do APK instalado/puxado, igual ao candidato (nome local pode diferir); leitura nova no tablet e atestado do operador |
| `operator-acceptance` | Aceite final separado: operador, nota de escopo/limites e evidência assinada/atestada para este candidato exato |

`increment-review` e todos os gates físicos exigem `--operator` e `--note` para
registrar `passed`. `operator-acceptance` não é inferido de qualquer outro gate.
Para `installed-apk`, a única evidência é um JSON v1 de `artifact_metadata.py`
produzido a partir do APK realmente instalado/puxado, com a fonte candidata
inalterada. Compare package/version/hash/certificado antes de atestar leitura nova.
Registre no atestado a referência local dos comandos e da leitura; não use somente
`pm list packages` ou copiar metadados do candidato. Builds split exigem procedimento
próprio: este fluxo espera o debug APK monolítico do projeto.

## Uso offline e explícito

Após revisão/build do candidato, **sem alterações de fonte posteriores**:

```bash
# O build existente gera build/artifacts/app-debug.json com a identidade real.
python3 tools/qualification.py matrix
python3 tools/qualification.py init \
  --repo . --metadata build/artifacts/app-debug.json \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --output build/qualification/report.json

# Somente após executar/conferir o gate; host-tests.txt deve existir e ser não vazio.
python3 tools/qualification.py record \
  --repo . --metadata build/artifacts/app-debug.json \
  --report build/qualification/report.json --evidence-root build/qualification \
  --gate host-tests --status passed --evidence host-tests.txt

# Falta de feature/contrato/medição é blocker, não pass nem skip.
python3 tools/qualification.py record \
  --repo . --metadata build/artifacts/app-debug.json \
  --report build/qualification/report.json --evidence-root build/qualification \
  --gate graphs --status blocked --note 'Feature integrada ainda não disponível'

python3 tools/qualification.py evaluate \
  --repo . --metadata build/artifacts/app-debug.json \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --report build/qualification/report.json --evidence-root build/qualification
```

`record` atualiza um único gate por invocação e preserva o arquivo se rejeitado;
repita `--evidence` para múltiplos arquivos nos gates comuns. Soak e installed-apk
exigem exatamente um JSON de entrada, não o resumo da análise. Relatórios são
substituídos atomicamente, mas não há scheduler/lock multioperador: use um dono por
relatório/dispositivo e preserve tentativas anteriores em diretórios separados.
Saídas: `0` = comando válido / avaliação ready / soak passed; `1` = avaliação não
ready ou soak failed/incomplete; `2` = entrada/comando inválido. `init` bem-sucedido
**não** significa qualificação passada. Sem subcomando não há ação padrão.

## Soak pré-declarado (antes de executar)

Carga de bancada isolada, somente leitura: **30 minutos**, quatro watches ativos
com produtores sintéticos a **2 Hz cada**, ao menos um gráfico numérico visível;
sem pause, Home ou trocas de watch durante esta medição. Exercícios de lifecycle e
rede ficam em execuções separadas. Recursos ausentes ou contadores inacessíveis
são bloqueadores, não autorizações para reduzir a carga.

| Métrica | Orçamento fixo e justificativa |
|---|---|
| Cadência | 15 s, tolerância de ±2 s, 121–122 pontos e duração 1800–1815 s; limita custo da coleta sem permitir buracos |
| PSS | Pico ≤256 MiB e crescimento último−primeiro ≤32 MiB; teto conservador para inspetor leve, detecta retenção relevante em 30 min (não prova ausência de leak lento) |
| CPU | Média ponderada pelo intervalo ≤25% de **um core**, pico ≤80%; evita ocupação contínua de um core e identifica travamentos curtos observáveis |
| Ingestão | Cada watch em **cada intervalo** recebe ≥90% de 2 Hz × duração; contador cumulativo inteiro, sem reset ou intervalo parado; Hz apresentado não substitui contagem medida |
| Bateria | Perda início−fim ≤10 pontos percentuais, **desplugado em todas as amostras**; limite inicial de bancada, não benchmark comparável entre modelos |

O plano `SOAK_PLAN` vai no relatório e no JSON bruto; não é configurável para
relaxar um resultado. Mudanças de orçamento exigem revisão e uma nova campanha.
Sem CPU/PSS/intake/bateria numéricos finitos, identidade, carga ou duração/cadência,
`analyze-soak` retorna `incomplete`, métricas `null` (= desconhecido) e blockers.
Medição completa que excede orçamento retorna `failed`. Nenhuma média/contagem é
inventada ou extraída de labels sem observação real.

### Coletor oficial somente leitura — execução física ainda pendente

`tools/collect_soak.py` consome o contrato real de `Activity.dump` através de
`tools/device_snapshot.py`, sem UIAutomator, captura de acessibilidade, novo
reader ou publicação DDS. **Não executá-lo em dispositivo sem nova autorização.**
O desenvolvimento/teste host desta ferramenta não é um soak físico.

Antes da campanha, o operador prepara os quatro produtores **2 Hz**, deixa o
mesmo APK candidato em processo ordinário, todos os watches saudáveis e o gráfico
finito totalmente visível/despausado. O arquivo `load-plan.json` é o objeto exato
`soak_plan` da CLI `qualification.py matrix`, revisado antes da coleta. Ele não é
uma configuração de orçamento nem comprova sozinho a frequência do produtor;
retenha também a evidência da configuração da fixture. Não existe contrato de
introspecção da taxa configurada do publicador no QA e o coletor não o inventa.

Somente após autorização física explícita, em terminal privado:

```bash
python3 tools/collect_soak.py preflight \
  --repo . --metadata build/artifacts/app-debug.json \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --serial "$SERIAL" --authorize-read-only \
  --load-plan build/qualification/load-plan.json \
  --output-dir build/qualification/preflight-NOVO
# Para candidato ON, adicionar a ambos os comandos:
# --os-transport-validation build/qualification/os-transport-validation.json
python3 tools/collect_soak.py collect \
  --repo . --metadata build/artifacts/app-debug.json \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --serial "$SERIAL" --authorize-read-only \
  --load-plan build/qualification/load-plan.json \
  --output-dir build/qualification/soak-NOVO
```

- `preflight` consulta identidade, clocks, OS e QA, mas **sempre** sai `1`/incomplete:
  não mede CPU/intake por 30 minutos nem concede aceite. Revise seu evento
  `preflight-observation`; ausência deste evento indica preflight bloqueado.
- `collect` não aceita duração, cadência, número de watches ou budgets alternativos.
  Há uma janela prévia real de 15 s para o primeiro delta CPU, seguida de 121 pontos
  em deadlines absolutos; um 122º ponto fecha a duração mínima se o jitter das
  leituras deixar o 121º abaixo de 1800 s. Todo intervalo continua 13–17 s e o span
  aceito continua 1800–1815 s. Não há interpolação, catch-up ou resume.
- Cada comando tem timeout absoluto **≤5 s**, stdout/stderr limitados e cleanup do
  próprio subprocesso. Todas as consultas de uma amostra compartilham ainda um
  deadline de 5 s. Transporte lento/SELinux/getconf ausente bloqueiam, sem fallback.
- PID único, cmdline exato, UID e `/proc/PID/stat`/starttime são reconferidos e
  cercam OS/QA/métricas. `getconf CLK_TCK` é lido de novo em cada ponto. CPU usa
  delta exato `utime+stime / CLK_TCK / delta monotônico ×100`, um core, e verifica
  continuidade de uptime do dispositivo; não compara relógio Java com host.
- Os bytes/hash/ABI/política do APK local são reinspecionados; `pm path` monolítico
  e `sha256sum` do APK instalado devem coincidir em cada amostra. A CLI verifica
  também fonte/proveniência e, para ON, package/assinatura/metadados reais pelos
  verificadores existentes. Nenhuma assinatura ou instalação é realizada.
- OFF continua exigindo capability boolean false; ON continua true mesmo disarmed.
  Ambos recusam lease/runner/fault/ticket/operação ativos ou desconhecidos. O
  transporte OS conservador exige API36 e o parser AOSP já versionado; builds ou
  campos não suportados bloqueiam. ON exige previamente o atestado físico de
  controles OS positivo/negativo para o mesmo hash de build. Nada gera esse
  atestado automaticamente nem classifica controle sintético como físico.
- Cada ponto cerca a captura com **OS-before → export fence Java → captura worker
  estritamente posterior → OS-after**. Requests/readers/session/lifecycle/policy,
  UUID do processo, contadores, export clocks e sequência não podem regredir ou
  trocar. Uma captura ainda igual ao fence pode aguardar dentro do deadline;
  entrada malformada ou identidade divergente nunca é escondida por retry.
- PSS TOTAL (KiB), bateria `level/scale`, todas as fontes de alimentação conhecidas
  e status são lidos de verdade; power conectado, bateria com updates parados,
  campos ausentes/desconhecidos e ambiguidade bloqueiam. Não há zeros substitutos.
- `events.jsonl` é progressivo, privado (0600 em diretório 0700), fsync por evento,
  limitado a 20.000 registros/32 MiB. O orçamento de 32 MiB é o menor bound
  deliberadamente escolhido que suporta a campanha sintética pública com frame QA
  válido de 24.448 bytes de payload, 121–122 pontos completos e folga observada;
  ele ainda deixa o cap absoluto independente de 20.000 registros. Guarda leituras do alvo em base64 e hashes/
  intervalos; protobuf OS e fingerprint ficam **somente hash/tamanho**, para não
  reter argumentos ou processos alheios. A rejeição por esgotamento mantém esse
  teto; eventos terminais são best-effort e não podem impedir os snapshots
  `incomplete` de `soak-input.json` e `analysis.json`, que continuam atômicos.
  `soak-input.json` é substituído atomicamente a cada ponto e `analysis.json`
  contém o retorno oficial.
- Na regressão pública sintética follow-up, o frame QA foi aceito pelos parsers
  reais e a campanha reteve 12.718.837 bytes em 4.253 registros para 121 pontos;
  restaram 20.835.595 bytes e 15.747 registros antes dos caps. O `soak-input.json`
  resultante teve 3.971.216 bytes, abaixo do limite compartilhado de serialização
  de 4 MiB. Isso é classificação host-only sintética, não soak físico, Android,
  ADB ou aceite de bateria/CPU/PSS.
- Diretório de saída deve ser novo e estar sob `build/` desta fonte; arquivo,
  symlink, tentativa anterior ou execução interrompida não são retomados. SIGINT/
  SIGTERM preservam pontos completos e deixam incomplete; SIGKILL/falha de disco
  pode deixar apenas o último snapshot atômico/journal parcial, jamais PASS. O
  analisador recusa envelope oficial incomplete, curto ou inconsistente e revalida
  clocks/CPU/identidades/QA/OS dos coletores OFF e ON. O input manual legado mantém
  seu contrato separado; não se transforma em saída autenticada do coletor.

Saídas do coletor: `0` = coleta completa e análise de métricas passed (não aceite
físico/ready); `1` = preflight, incomplete ou failed; `2` = entrada/saída inválida.
Não commitar nem publicar arquivos da campanha. Fixture, controles OS físicos,
revisão independente, screenshot e aceite final continuam pendentes.

As leituras pontuais abaixo documentam as entradas do coletor; não compõem uma
campanha e continuam exigindo autorização. Defina SERIAL explicitamente e recuse
serial vazio, múltiplos PIDs ou cmdline divergente:

```bash
: "${SERIAL:?defina explicitamente o dispositivo autorizado}"
timeout 5s adb -s "$SERIAL" shell pidof com.lamggm.rospocket.dev
# PID deve ser um único inteiro positivo validado, nunca escolhido de outro app.
: "${PID:?valide o PID e o cmdline exatos antes de continuar}"
timeout 5s adb -s "$SERIAL" exec-out cat "/proc/$PID/cmdline"
timeout 5s adb -s "$SERIAL" shell dumpsys meminfo --local "$PID"
timeout 5s adb -s "$SERIAL" exec-out cat "/proc/$PID/stat"
timeout 5s adb -s "$SERIAL" exec-out cat /proc/uptime
# CLK_TCK do dispositivo deve ser conhecido/verificado, não presumido como 100.
timeout 5s adb -s "$SERIAL" shell getconf CLK_TCK
timeout 5s adb -s "$SERIAL" shell dumpsys battery
```

Não use `top` global, dumps de apps alheios, logcat irrestrito ou conteúdo pessoal.
Registre PSS TOTAL do processo (KiB), bateria level/scale ×100 e estado de carga.
CPU deve ser delta de utime+stime / CLK_TCK / delta monotônico ×100, um core;
verifique a identidade/starttime do processo entre leituras. A primeira amostra
pode usar uma janela prévia medida de 15 s; não preencha zero por falta de delta.
Se SELinux/proc/getconf impedir leitura, marque `null` e bloqueie o gate. Dados
brutos e a transformação devem ser revisáveis. Os quatro contadores **cumulativos
reais por watch** precisam ser observados/exportados pela app/fixture; DDS
publicado, frequência configurada e um contador global não provam intake de quatro
readers. Se a integração não os disponibilizar, o soak permanece bloqueado.

Formato do JSON bruto (valores abaixo descrevem tipos, não medições de exemplo):

- `snapshot`: objeto completo de `artifact_metadata.py` do candidato.
- `plan`: cópia exata de `soak_plan` pré-declarado no relatório.
- `package`: identidade exata DEV; `pid`: inteiro positivo observado;
  `watches`: 4; `graph`: true, atestados durante toda a execução.
- `samples`: 121–122 objetos com `monotonic_s` em segundos, `pss_kib`,
  `cpu_percent`, `received_counts` (lista de quatro inteiros, ordem estável),
  `battery_percent`, `charging` (false), `package` e `pid` observados no ponto.
  Ausente = `null`, nunca zero. Não há preenchimento automático de campos.

```bash
python3 tools/qualification.py analyze-soak \
  --metadata build/artifacts/app-debug.json --input build/qualification/soak-input.json \
  --apk app/build/outputs/apk/debug/app-debug.apk
# Se passed, registrar o INPUT bruto como evidência, não o resumo acima.
python3 tools/qualification.py record \
  --repo . --metadata build/artifacts/app-debug.json \
  --report build/qualification/report.json --evidence-root build/qualification \
  --gate soak --status passed --evidence soak-input.json \
  --apk app/build/outputs/apk/debug/app-debug.apk
```

## Segurança e execução futura

Antes de perda de Wi-Fi/mudança de endereço, o operador deve testar controle local
ou USB independente, definir retorno/timeout e obter aprovação explícita. Não
interromper o único caminho remoto. Este toolkit **não** faz toggles, DHCP, ADB
install, pareamento, exceções de VPN ou regras de firewall. Use apenas fixtures
sintéticas em domínios isolados, jamais tópicos de comando do robô. Sem canal
alternativo ou feature integrada, registre `blocked`. Home/restart/peer morto
não substituem interrupção física de RF ou troca real de endereço.

Os testes `tests/test_qualification.py` são descobertos pelo runner Python atual.
Incluem controle positivo inteiramente **sintético de validação do relatório**;
seu ready comprova apenas o algoritmo, nunca aceite físico. Build Android,
interoperabilidade, campanhas de rede, soak e aceite permanecem execuções futuras
separadas para o SHA/APK final integrado.

## Fail-closed diagnostic consumer details

The pinned Android16 parser validates every accepted field recursively, including
foreign process/instrumentation records, wire types, duplicate scalars, booleans,
PID conflicts and duplicate active records. Unknown **or unsupported** root/nested
sections reject the observation; no opaque protobuf payload proves absence.
The supported schema is an explicit subset of `android-16.0.0_r1` AOSP
`activitymanagerservice.proto`, `package_item_info.proto`, `component_name.proto`,
`bundle.proto` and `util/common.proto`. Complex root sections not yet supported
remain blocked, even if legitimate. A real vendor positive/negative transport
control is still mandatory and may require extending the strict typed subset;
synthetic parser tests do not establish device support.

Every sample requires a positive Java export fence strictly newer than the prior
same-process fence, capture and export. Its worker capture must be strictly newer
than this fence, with advancing sequence/time and exact PID/starttime/instance.
Host ordering/deadlines remain separate; host clocks are never compared with Java.

Graph acceptance consumes actual completed `drawnSeries` statistics, not prepared
list length: ordered series indices, current live slot/reader association, bounded
exact integer counts with finite points, coherent receive-time range and recent
newest data. It checks `observedMonotonicNs == exportedMonotonicNs` (the actual
Activity passes the same timestamp), positive draw time and exact observed−draw
age. The exported series identifies a slot, not a separate reader-generation
field; association uses that sample's coherent watch plus stable campaign reader
identity, without inventing a stronger producer field.

Metadata outputs use exclusive unpredictable staging files and atomic per-file
replacement without following destination symlinks. Both files are staged first;
the sidecar is published before the candidate commit point. This is not a
multi-file filesystem transaction: after an interrupted partial publish, require
its `target_sha256` and `target_source_snapshot` to equal the candidate before
using a sidecar. Never infer coherence from filenames or successful parsing.

## Explicit diagnostic DEV migration

See [ANDROID-DIAGNOSTIC-POLICY.md](ANDROID-DIAGNOSTIC-POLICY.md) for metadata/report/soak schema 2, binary-derived capability, coherent cache-only runtime evidence and mandatory independent per-sample OS instrumentation proofs. Ordinary v1 remains explicit boolean false. Diagnostic compiled capability remains true while disarmed. Same candidate APK throughout; no transferred passes. Android/vendor OS positive control and physical/soak acceptance remain **UNVERIFIED**.
