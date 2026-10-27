# ROS Pocket v0.3 — navegação e perfis de bancada

**Estado:** base reconciliada pela PR #14, merge `a5fc28d056e4bde233b242131ae3d9d5094e7911`, com a mesma árvore do head `8b65985` aprovado no CI 40523750784. Navegação implementada na branch `feat/v0.3-01-bench-navigation`, ainda sem integração nem aceite físico. Perfis não implementados. Trabalho principal no chat, uma entrega delimitada por vez.

**Objetivo:** navegar pelo inspetor sem uma página gigante e recuperar configurações completas de bancada. Não repetir como novidade o Explorer, logs, quatro watches ou gráficos da v0.2.

**Arquitetura:** Java Android → JNI/C++ → Cyclone DDS, somente leitura, com worker DDS serializado. Preservar limites, relógios, identidades de sessão/readers, applied epochs, erros persistentes e teardown idempotente. Sem outro executor, sessão paralela, bridge ou migração de stack.

## 1. Base reconciliada e fronteira com a v0.2

- A preparação original foi publicada em `a9002fc1f9f42ecd9b3c864587419c1a0ccb12ab`, sobre uma integração v0.2 provisória. É histórico, não a base atual de implementação.
- A base final a incorporar nesta reconciliação é `main` em `c659b8521c093aacc3ccecd0855c07727398db2b`, merge da [PR #1](https://github.com/lamggm/ros2_pocket/pull/1).
- O [CI pós-merge 40515533393](https://github.com/lamggm/ros2_pocket/actions/runs/40515533393) terminou com sucesso nesse SHA. A reconciliação preserva sua ancestralidade por merge normal; não fazer rebase/force-push da integração publicada.
- A v0.2 foi entregue como **protótipo de bancada**, não como qualificação física extensa. O alcance real e as identidades dos artefatos estão em [FINALIZATION-v0.2](FINALIZATION-v0.2.md).
- Wi-Fi/IP, soak e qualificação extensa permanecem **não validados**, em backlog explícito. Não bloqueiam automaticamente este ciclo, não viram PASS e não autorizam alteração de thresholds/validadores históricos.
- O código da v0.2 inclui diagnóstico/recuperação/stale, Explorer/favoritos/QoS/grafo, rosout, mensagens estruturadas, quatro watches, gráficos por latest snapshots e ferramentas de QA. Reutilizar, não reimplementar nem rebatizar como novidade v0.3.
- Evidência host, Canvas, JNI Linux, Android e física conserva sua classificação e seu SHA/APK. Estar no histórico do novo commit não transfere aceitação física ao novo candidato.

## 2. Sequência curta

```text
00 — base e documentos reconciliados
  ↓
01 — navegação de bancada
  ↓
02 — perfis locais: modelo/armazenamento → aplicação explícita na UI
  ↓
03 — fechamento proporcional, candidato e smoke das novas jornadas
  ↓
autorização própria de merge → integration/v0.3 → main
```

Os incrementos têm branches/worktrees próprias e PRs para `integration/v0.3`, nunca para v0.2 ou diretamente para `main`. Concluir e relatar uma tarefa antes da seguinte. Os nomes abaixo são propostos, exceto a branch da tarefa 00 já iniciada.

### 00 — Reconciliar a base

**Branch:** `chore/v0.3-00-base-reconciliation`.

**Arquivos:** `AGENTS.md`, README e este roadmap. Trazer por merge os arquivos da `main` final, sem alterar novamente o código herdado.

- Preservar a documentação v0.3 e as correções finais da v0.2, inclusive as regras de condução simples em AGENTS; resolver conflitos semanticamente.
- Registrar PR #1 encerrada, base real e redução explícita de escopo; não ressuscitar o bloqueio de qualificação extensa.
- Verificar que o delta em relação à `main` final contém somente os documentos desta tarefa. Nenhuma versão do app, feature, gate ou workflow é alterado.
- Revisar diff, links e sequência; publicar PR para `integration/v0.3` e verificar seu CI. Esta tarefa não autoriza merge final da v0.3 em `main`.

**Limitação de CI decidida pelo operador:** o login GitHub atual não possui `workflow`; a verificação de SSH também não autenticou. O operador escolheu publicar base/documentação **sem ampliar permissões**. Portanto o filtro de push herdado continua sem `integration/v0.3`; PRs já disparam o workflow existente. Nenhuma alteração de YAML ou credencial faz parte desta tarefa.

**Follow-up explícito:** habilitar push de `integration/v0.3` somente após disponibilizar uma credencial já autorizada ou autorização específica para o escopo necessário. Preservar `pull_request`, runners hospedados, permissões mínimas, actions pinadas e todos os gates. Até lá, não alegar CI automático de push: conferir SHA/árvore efetivamente testados antes de cada integração; se a árvore integrada diferir, exigir uma execução autorizada no SHA exato antes de iniciar o incremento seguinte. CI de PR não é automaticamente CI do commit integrado.

**Pronto quando:** base/ancestralidade preservadas, docs coerentes, PR publicada e resultado real de validação identificado. Publicação não significa merge da PR nem feature pronta.

### 01 — Navegação de bancada

**Branch proposta:** `feat/v0.3-01-bench-navigation`.

Quatro seções, com os componentes já existentes:

- **Conexão:** domínio, peer, conectar/desconectar, diagnóstico e acesso aos perfis quando implementados.
- **Tópicos:** Explorer, busca, filtros, favoritos e QoS anunciado.
- **Monitor:** quatro watches, mensagens estruturadas e gráficos.
- **Logs:** console rosout.

**Arquivos candidatos:** `app/src/main/java/com/lamggm/rospocket/{MainActivity,ExplorerView,PlotView,StructuredMessageView,RosoutConsoleView}.java`, recursos/IDs e testes correspondentes. Extrair só a composição necessária; a opção inicial é uma Activity com painéis, não um novo framework de navegação.

**Passos:** registrar fluxo real → RED de troca de seção → composição mínima → GREEN de preservação de identidade/seleção/pausa → regressão de lifecycle e observabilidade → revisão pontual e smoke autorizado de retrato/paisagem.

**Pronto quando:** trocar de seção não reconecta, recria readers, perde seleções ou faz dados parecerem stale por causa da navegação. Diagnóstico continua acessível. Seção oculta não finge desenho de gráfico; intake segue sua política vigente, e o retorno respeita pausa/janelas. Home/Stop continua liberando recursos conforme o lifecycle: navegação interna não é saída do app.

Adaptar `tests/tablet_ui.py`/`tests/tablet_plot_acceptance.py` apenas se os seletores realmente mudarem; preservar ownership, foco, IME, freshness e oráculos de desenho. Não reconstruir o harness inteiro por conveniência.

### 02 — Perfis locais completos

**Branch proposta:** `feat/v0.3-02-bench-profiles`, depois de 01 integrada. Modelo/armazenamento e fluxo de UI são passos internos sequenciais, não duas campanhas.

**Dados:** nome, Domain ID, peer opcional, até quatro pares tópico/tipo com QoS e campos de gráfico já suportados. Referência inicial: até 16 perfis; fixar limites de nome/bytes e schema v1 nos testes do modelo antes da implementação. Não salvar amostras, contadores live, handles DDS ou gerações.

**Operações:** criar a partir da configuração, salvar alterações explicitamente, selecionar, renomear e excluir.

**Política de aplicação:** selecionar não conecta nem modifica sessão ativa. Aplicar é permitido somente desconectado; conectado, instruir desconexão explícita. Aplicar carrega e valida a configuração, e Conectar permanece ação deliberada. Sem hot-swap transacional de readers, autosave de cada toque ou conexão automática por perfil.

Após reinício, restaurar a configuração escolhida desconectada, sem apresentar amostras antigas como live. Tópico ausente/type divergente fica pendente/erro explícito, sem substituir a seleção por outro tópico.

**Arquivos candidatos:** novas classes de modelo/armazenamento e testes sob `app/src/{main,test}/java/com/lamggm/rospocket/`; consultar `MainActivity.java`, `InputRules.java`, `ConnectionPolicy.java`, `WatchSlots.java` e `PlotController.java`. Nomes finais de classes não são APIs existentes.

**Passos:** schema/limites → RED de round-trip, corrupção, versão desconhecida e escrita interrompida → persistência local atômica → GREEN de preservação da configuração válida/preferências antigas → RED do fluxo UI → implementação mínima → GREEN de restart, aplicação conectada bloqueada, tópico ausente e tipo divergente → revisão e smoke autorizado.

**Pronto quando:** salvar, reiniciar, selecionar, aplicar e conectar funcionam sem perda silenciosa do perfil válido, quinto watch, assinatura oculta ou mistura de estado antigo/novo. I/O não bloqueia o worker DDS. Sem conta/cloud, sincronização, importação ou exportação.

### 03 — Fechamento proporcional

**Branch proposta:** `test/v0.3-03-bench-closeout`, após as duas features integradas.

**Arquivos candidatos:** `app/build.gradle`, README, docs de navegação/perfis e plano curto de aceite v0.3. Revalidar versionCode/package/certificado antes de escolher identidade; a reconciliação 00 não muda a versão 0.2.0 herdada.

- Revisão sem bloqueadores e testes/build/lint/CI aplicáveis no SHA/árvore exatos.
- APK ordinary, hooks OFF, fonte/hash/ABI/certificado rastreáveis. Evidências privadas fora do Git.
- Instalação física apenas quando autorizada, in-place e sem uninstall/clear data.
- Smoke final delimitado: navegar conectado com watches/gráfico/log; salvar perfil; reiniciar desconectado; aplicar e conectar; tópico ausente; Home/retorno; orientação; cleanup/restauração conferidos.
- Correção exige repetir o recorte afetado e vincular o artefato novo, não reutilizar aprovação do anterior.
- Registrar limitações. Não iniciar soak, Wi-Fi/IP ou campanha gráfica extensa automaticamente. Seus validadores e thresholds permanecem intactos.
- Autorização de merge final é separada; sem tag/release automática.

## 3. Validação e integração

- Mudança comportamental: RED → GREEN → revisão independente pontual. Principal implementa; delegação só de revisão/probe pequeno.
- Usar wrappers reais e toolchains disponíveis: `bash tools/test.sh --portable`, `bash tools/test.sh --fixture` quando aplicável e `bash tools/build.sh --offline`. Conferir testes não vazios, XMLs e exit codes; não presumir resultados por serem comandos listados.
- Na tarefa documental 00, conferir links, diff/ancestralidade, arquivos preservados e CI de PR. Não iniciar validação física de features ainda inexistentes.
- Fixtures DDS isoladas, sem comandos de robô. Uma automação por vez no tablet; nunca alterar VPN/firewall/DNS/acesso remoto como atalho.
- Conferir URL real de push, SHA remoto, head/base da PR e árvore testada. Nenhum force-push ou bypass de gate.
- O futuro PR para `main` fica draft/sem auto-merge até aceite do escopo acordado e autorização própria.

## 4. Backlog, não opcionais implícitos

LaserScan 2D, exportação CSV/JSON, lotes numéricos, robustez extensa de rede/endurance e compatibilidade adicional ficam fora desta versão. Sua ausência não é feature implementada nem teste aprovado. Antes de promover qualquer item, definir outra tarefa com hipótese, limites e aceite próprios.

## 5. Fora do produto atual

Publicação/teleop, parâmetros/RPC, serviços/actions, rosbag, imagens/vídeo, mapas/TF/RViz completos, tipos customizados dinâmicos, descoberta entre sub-redes, SROS2 e migração ornamental de stack. A v0.3 continua um inspetor somente leitura.
