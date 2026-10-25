# ROS Pocket v0.2 — preparação 15 e checklist de reconciliação

**Estado: needs-integration. Não é candidato final congelado, qualificação ou release.**
A decisão atual do operador permite implementar 11–15 em paralelo em worktrees
isoladas, reconciliar depois e testar fisicamente quando o tablet estiver disponível.
As dependências continuam obrigatórias para integração/aceite, não para iniciar
esta preparação. Nenhum dispositivo Android é operado nesta rodada.

## Identidade preparada, sem migração

- `versionName=0.2.0`, `versionCode=2`; cabeçalho `v` + `BuildConfig.VERSION_NAME`.
- O histórico Git disponível, consultado em todas as refs locais, contém somente
  code 1/name 0.1.0 antes desta mudança. Code 2 está disponível **nesse histórico**;
  isso não é uma consulta a lojas, instalações ou refs remotas não recuperadas.
- Package continua `com.lamggm.rospocket.dev`, namespace original, debug signer
  existente, minSdk 28/targetSdk 35 e build padrão ARM64. Nenhum uninstall,
  instalação, assinatura de produção, push, PR, merge ou publicação implícita.
- DDS direto, read-only, quatro watches e rosout independente permanecem iguais.
  A política ordinary/default/nondebug continua OFF. ON só por opt-in diagnóstico
  explícito/versionado e verificação dos ELF reais; versão nova não contorna guardas.
- Base `796b59aaf50a468344b825f693331e11bdabb06e`: recuperação local do conteúdo
  revisado da 12 sobre a história real de `7b2dc76de3679935fac5adf69839818e8ca5a914`.
  Não é o commit upstream original `7a5e841811f66bcb660da0f9b2b901f1aa272c68`.

## Implementado não significa aceito

| Frente | Fonte disponível nesta base | Pendente antes do candidato final |
|---|---|---|
| 01 build/CI | `94791b9` | Revisão e CI do SHA integrado final, scan e bootstrap fresco |
| 02 conexão | `31d5112` | Regressão integrada e Wi-Fi/IP físicos com recuperação autorizada |
| 03 Explorer/QoS | `e71bdf3` | Regressão integrada e aceitação física |
| 04 rosout | `bc72301` | Logs novos coexistindo durante todos os cenários finais |
| 05 estruturados | `cee351a` | Tipos/frames/limites no mesmo APK da campanha |
| 06 multiwatch | `d1f7a3d` | Frente 13, ambos os RMWs, aceite físico completo |
| 07 gráficos | `bddba51` | Frente 14, Canvas/capturas/gaps/eviction no tablet |
| 08 toolkit/matriz | `809306a` | Coletor 11, soak oficial e aceite separado |
| 09 QA | `0234bf6` | Reconciliação das projeções 11/12/14 e prova instalada |
| 10 harness | `7b2dc76` | Regressão física final com guardas preservadas |
| 12 reader failure | recuperação local `796b59a` | Revisão independente 12 e reconciliação do pai; não atribuir história ausente |
| 11/13/14 | Entregas independentes, não integradas nesta worktree | Contratos reais, testes não vazios e regressão combinada |
| 15 identidade/docs/CI | Esta preparação | Revisão independente, integração das anteriores e todos os gates abaixo |

A tabela registra presença de fonte, **não** aprova revisões ou testes atuais.
Os resultados desta frente ficam na evidência externa; resultados históricos de
outro APK nunca são transferidos para um novo build 0.2.0.

## Contrato executável do registro CI

`python3 tools/verify_finalization_ci.py` é um gate obrigatório de host no workflow
`verify.yml`, antes de upload. O registro `ci/finalization-gates.json` contém os
IDs exatos `11-soak`, `13-multiwatch`, `14-plot`, todos `pending_integration`.
O comando retorna 2 nesta árvore, enumerando as três pendências, sem fingir testes.
Os testes de regressão do próprio registro usam controles unittest sintéticos em
sandbox: não são implementações nem aceitação das frentes 11/13/14.

Depois de reconciliar cada frente, o **pai/revisor** deve:

- [ ] Conferir contratos reais e arquivos entregues; não inferir APIs/nome de testes.
- [ ] Em cada gate, registrar `test_files` explícitos de host (`tests/test_*.py`,
  arquivos diretos não-symlink), `minimum_tests` inteiro positivo revisado e
  `state: registered`. Nenhum mesmo arquivo pode satisfazer dois registros.
- [ ] Usar somente suites unittest seguras no host/CI sem tablet/ADB/credenciais.
  Instrumentation, DDS container específico ou Canvas Java não viram unittest:
  conservar/adicionar seus gates reais em separado, com contagens/XML e
  `--no-tests=error` nas seleções CTest. Se o contrato requer outro runner,
  reconciliar o runner com testes negativos antes de registrar, não criar adapter falso.
- [ ] Executar o registro real: cada arquivo precisa selecionar testes; cada gate
  precisa atingir o mínimo; falha, erro, skip ou expected-failure bloqueiam.
- [ ] Reexecutar Python completo, Java/Robolectric (incluindo Canvas), native
  core/session, fixtures Fast DDS/Cyclone, lint e builds OFF/diagnóstico explícito.
- [ ] Preservar hosted `ubuntu-24.04`, actions SHA-pinned, `contents: read`,
  `pull_request`, checkout sem credenciais persistentes, sem secrets/keys,
  timeouts/isolamento e allowlist APK+JSON/retenção de 7 dias. Não usar bypass,
  `continue-on-error`, seleção vazia ou condição que dispense o novo gate.

O registro cobre somente **testes host adicionais**. Não substitui a suite inteira,
CI remoto observado do SHA exato, revisão independente ou qualificação física.
Não remover `pending_integration` apenas para deixar o workflow verde.

## Antes de congelar qualquer candidato final

- [ ] Integrar/revisar 11/12/13/14/15 uma por vez na árvore do pai; resolver os
  pedidos de contrato, inclusive mudanças compartilhadas não pertencentes à 15.
- [ ] Conferir todos os incrementos 01–15 por SHA/escopo/revisor/findings, sem
  autoaprovação do implementador. Regressão combinada após a última edição.
- [ ] Finalizar fontes/documentos **antes** do build final; registrar commit/tree
  limpos, source snapshot antes/depois e build sem mutação de fonte.
- [ ] Gerar/inspecionar o próprio APK: package/code/name/ABIs, assinatura debug,
  SHA-256/tamanho, ELF e metadados correspondentes. Preservar certificado e dados.
- [ ] Observar CI remoto verde no SHA exato, não no head anterior nem só local.
- [ ] Só então congelar bytes para **nova** campanha. APKs de preparação não são
  o candidato final; os testes físicos anteriores não os qualificam.

## Depois, somente com bancada e autorização

- [ ] Vincular APK instalado puxado de volta e metadados ao candidato exato;
  nenhuma desinstalação para contornar divergência de certificado.
- [ ] Regressões físicas integradas com ambos os RMWs e casos completos 12/13/14.
- [ ] Perda/retorno Wi-Fi e IP real somente com canal alternativo, recuperação
  verificada e autorização de interrupção. Home/restart não substitui isso.
- [ ] Soak oficial: 30 min, 15±2 s, 121–122 pontos, 1800–1815 s, quatro watches
  2 Hz e gráfico visível; PSS pico≤256 MiB/crescimento≤32 MiB, CPU média≤25%/
  pico≤80% de um core, intake≥90% por watch/intervalo, desplugado/perda≤10 pp.
  Sem UIAutomator; diagnóstico ON exige fences e OS-before/after reais.
  Métrica ausente/unknown/gap/troca de processo/geração bloqueia, nunca vira zero.
- [ ] Todos os 21 gates oficiais mais aceite explícito e separado do operador.
  Só o processo oficial pode avaliar ready; esta preparação não o marca.
- [ ] Autorização separada antes de liberar draft/merge main/publicar/assinar release.

Referências: [roadmap](ROADMAP-v0.2.md), [notas draft](RELEASE-NOTES-v0.2.md),
[matriz e coleta, propriedade da 11](DEVICE-QUALIFICATION-v0.2.md),
[política diagnóstica](ANDROID-DIAGNOSTIC-POLICY.md),
[contrato QA](DEVICE-QA-CONTRACT.md). Nenhuma seção do coletor foi alterada pela 15.
