# ROS Pocket v0.2 — plano de implementação e integração

**Objetivo:** transformar o protótipo em um inspetor ROS 2 de bancada útil, mantendo DDS direto, operação somente leitura e conexão simples por domínio na LAN.

**Arquitetura:** preservar o núcleo Cyclone DDS/C++ e a ponte JNI; evoluir o modelo de sessão e a UI incrementalmente, sem migração ornamental de stack. Cada incremento nasce da integração atualizada, ganha testes e APK verificável e só então entra nela.

**Stack atual:** Java Android, JNI/C++, Cyclone DDS 0.10.5, Jazzy como alvo de interoperabilidade, JDK 17, Gradle 8.7/AGP 8.5.2, Android SDK 35 e NDK r28c.

## Estado atual e condução

Este roadmap preserva o plano e o baseline históricos. O estado reconciliado,
a validação host e as pendências estão em [FINALIZATION-v0.2.md](FINALIZATION-v0.2.md).
A orientação atual em `AGENTS.md` é uma tarefa delimitada por vez, com validação
proporcional. Autorizações históricas de paralelismo não são permanentes.
O operador autorizou o merge da PR #12 e o escopo de **protótipo de bancada**.
Os critérios atuais de entrega estão em [FINALIZATION-v0.2.md](FINALIZATION-v0.2.md):
validação host/CI, APK identificado e teste curto real de gráfico/pausa/retomada.
Wi-Fi/IP, qualificação física extensa e soak ficam no backlog, não bloqueiam o
protótipo e não recebem PASS. A promoção para `main` e release exigem autorização
separada. Os contratos de leitura, dados obsoletos, buffers e segurança não mudam.

## Baseline observado

- `main`: `295753e8ee5de83da458a304fb139a5c35228557`.
- Fonte pública sanitizada; sem workflows de CI ou wrapper Gradle versionado no baseline.
- Descoberta de endpoints/nós, seis tipos primitivos, uma assinatura e valores/Hz/idade.
- O protótipo passou 16 verificações físicas; falha real na criação de tópico tem regressão nativa separada.
- Não confundir isso com teste físico de perda de Wi-Fi, mudança de IP ou endurance: esses gates ainda faltam.

## Fluxo de branches

```text
main (baseline estável)
  └── integration/v0.2
        ├── ci/v0.2-01-reproducible-build
        ├── feat/v0.2-02-connection-diagnostics
        ├── feat/v0.2-03-topic-explorer-qos
        ├── feat/v0.2-04-rosout-console
        ├── feat/v0.2-05-structured-messages
        ├── feat/v0.2-06-multi-topic-watch
        ├── feat/v0.2-07-live-plots
        └── test/v0.2-08-device-qualification
```

- Oito branches foram usadas como frentes paralelas na implementação histórica. O padrão atual é sequencial, conforme `AGENTS.md`; novo paralelismo requer autorização explícita para a tarefa.
- Cada frente usa worktree própria. Ao avançar a integração, incorporar `origin/integration/v0.2` nas branches ativas por merge e revalidar; não reescrever história compartilhada com rebase/force-push.
- Criar cada branch de trabalho a partir da **integração já atualizada**; PR com base em `integration/v0.2`, nunca em `main`.
- TDD para comportamento novo: demonstrar RED, implementar mínimo, obter GREEN e fazer revisão antes do merge.
- Squash dos incrementos na integração. Depois de cada merge: testar o conjunto na integração e gerar candidato identificado pelo SHA.
- Evitar trabalho simultâneo no mesmo checkout/dispositivo; trabalho paralelo independente usa worktree própria.
- Manter PR de integração → main em draft e sem auto-merge até autorização própria do operador. O escopo atual é o protótipo definido em `FINALIZATION-v0.2.md`; qualificação extensa não é bloqueio automático dessa entrega limitada.
- Merge final por merge commit, preservando os incrementos auditáveis. Não reescrever a história pública.
- Este plano não modifica proteção de branch, acesso, VPN, firewall ou configurações do robô.

## Dependências para trabalho paralelo

- **01 build/CI:** pode começar imediatamente. Deve fornecer os gates comuns antes de liberar merges de funcionalidades como verificados no CI.
- **02 conexão:** pode começar imediatamente sobre os estados reais atuais; publicar alterações de contrato de sessão/snapshot para as demais frentes.
- **03 explorer/QoS:** pode preparar modelo, filtros e testes em paralelo; incorporar os estados/contratos da 02 antes do merge quando forem alterados.
- **04 rosout:** pode preparar IDL, decoder, buffer e UI em paralelo. Coordenar edições de `PocketTypes.idl`/registro de tipos com a 05; não duplicar o gerenciador de readers da 06.
- **05 tipos estruturados:** pode começar imediatamente em IDL, estrutura de campos e testes. Publicar o contrato dos caminhos numéricos para a 07.
- **06 multiwatch:** pode desenvolver usando os primitivos atuais, preservando os contratos da sessão 02 e incorporando os decoders/formatos da 04/05. Reader de logs e watches devem coexistir; nada de duas implementações concorrentes do mesmo gerenciador.
- **07 gráficos:** pode desenvolver buffers, componente visual e testes isolados agora. A aceitação ponta a ponta depende dos caminhos da 05 e das assinaturas da 06; mocks de teste não contam como recepção real.
- **08 qualificação:** pode preparar matriz e automação em paralelo. Só executa o aceite final sobre o SHA integrado com as funcionalidades concluídas.

A ordem preferencial de integração continua 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08. Uma alteração de ordem só é aceitável com independência real demonstrada e gates preservados; alterações em `inspector.cpp`, JNI e IDL precisam reconciliação explícita, não resolução cega de conflitos.

## Sequência proposta

### 01 — Build reproduzível e regressões automatizadas

**Branch:** `ci/v0.2-01-reproducible-build`.

**Entregas:** wrapper Gradle 8.7 com checksum oficial, bootstrap documentado e build que não dependa de cache particular; testes portáveis e fixtures reproduzíveis; CI com lint, testes, build e varredura de segredos. APK/metadados identificados por versão, SHA, ABI e assinatura.

**Arquivos:** criar `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.github/workflows/verify.yml`, `tools/test.sh` e receita de imagem de fixture em `tests/`; ajustar `tools/build.sh`, `tools/bootstrap.sh` e README.

**Gates:** checkout limpo consegue resolver dependências e construir; caminhos offline continuam documentados; testes existentes passam; artefato aponta para o commit exercitado. CI público roda em runner hospedado/efêmero, nunca como executor irrestrito no Ryzen pessoal. Sem secrets/release keys em PRs. Chave debug local permanece fora do Git; assinatura de CI pode diferir da do tablet e não autoriza desinstalar o app para atualizar.

### 02 — Diagnóstico de conexão e recuperação

**Branch:** `feat/v0.2-02-connection-diagnostics`.

**Entregas:** painel de rede/interface/domínio, participantes DDS versus nós ROS, estado da assinatura e erro persistente; estados claros de descoberta, conexão, reconexão e dados obsoletos. Preservar configuração e controlar retentativas sem recriar sessões em loop.

**Arquivos:** `MainActivity.java`, `NetworkRules.java`, `NativeBridge.java`, `inspector.{hpp,cpp}`, `jni.cpp`; novos testes de transições em Java/nativo e ampliação de `tests/tablet_regression.py`.

**Gates:** retorno do Home/processo, perda/retorno do peer, erro de criação e recuperação continuam corretos; nenhum valor antigo é exibido como ativo. Testes físicos de Wi-Fi/IP só com canal de controle alternativo ou ação manual combinada: não derrubar a própria sessão ADB Wi-Fi. Nenhuma alteração automática de VPN/firewall.

### 03 — Explorador de tópicos/nós com QoS real

**Branch:** `feat/v0.2-03-topic-explorer-qos`.

**Entregas:** busca, filtro por namespace/tipo, favoritos locais, detalhes de publicadores/assinantes e QoS anunciado por endpoint. Associar nós aos endpoints usando GIDs reais do grafo; manter associação desconhecida explícita. Mostrar incompatibilidade oferecido/solicitado sem fingir que confiabilidade resolve tudo.

**Arquivos:** `model.hpp`, `inspector.cpp`, `MainActivity.java`, `tests/core_test.cpp` e fixtures/testes de grafo/QoS.

**Gates:** endpoints duplicados/removidos, reinício de nós, múltiplos writers e QoS incompatível; não confundir participante DDS com nó ROS. Busca/favoritos não reiniciam a sessão.

### 04 — Console `/rosout`

**Branch:** `feat/v0.2-04-rosout-console`.

**Entregas:** decodificar `rcl_interfaces/msg/Log`; console com severidade, nó, horário da origem, busca, pausa e limpar. Buffer limitado; persistência/exportação não é automática.

**Arquivos:** `PocketTypes.idl`, `inspector.cpp`, UI de console e fixtures. Extrair tela/modelo pequeno se necessário, sem reescrita completa da UI.

**Gates:** severidades, UTF-8, mensagens longas, rajadas e limite do buffer; pausar a tela não vaza memória nem paralisa descoberta. Não publicar logs privados em artefatos de CI.

### 05 — Inspeção estruturada de mensagens úteis

**Branch:** `feat/v0.2-05-structured-messages`.

**Escopo fechado:** `geometry_msgs/msg/Vector3`, `Quaternion`, `Twist`, `Pose` e `nav_msgs/msg/Odometry`, além dos primitivos existentes.

**Entregas:** árvore de campos/valores, caminhos numéricos selecionáveis, header/timestamps quando existentes e limites explícitos de tamanho. Decodificação por IDL tipado e formato estruturado; não usar parsing de texto formatado como API. `Twist` será somente recebido, nunca publicado.

**Arquivos:** `PocketTypes.idl`, `inspector.{hpp,cpp}`, JNI/modelo de apresentação e fixtures tipadas.

**Gates:** valores reais e frames/header, arrays fixos de covariância, Unicode em frame IDs, NaN/Inf e tipos desconhecidos. Mensagens customizadas arbitrárias continuam fora do escopo; não prometer decodificação universal por DDS discovery.

### 06 — Watch de múltiplos tópicos

**Branch:** `feat/v0.2-06-multi-topic-watch`.

**Entregas:** até quatro assinaturas simultâneas, estado/QoS/idade independente, ativar/remover sem desmontar as outras e buffers limitados. Preservar a serialização do núcleo nativo e impedir callbacks de gerações antigas.

**Arquivos:** `inspector.{hpp,cpp}`, `jni.cpp`, `NativeBridge.java`, UI/modelos e testes de concorrência/lifecycle.

**Gates:** mistura de publishers/QoS/rates, falha de um leitor não derruba os demais, limite de quatro, teardown idempotente e memória limitada. Definir e medir orçamento de CPU/memória/Hz recebido; não adivinhar desempenho a partir do desenho.

### 07 — Gráficos numéricos ao vivo

**Branch:** `feat/v0.2-07-live-plots`.

**Entregas:** selecionar campos numéricos da inspeção estruturada, sobrepor séries e pausar/retomar. Ring buffers com janela/quantidade limitadas e taxa visual independente da ingestão.

**Arquivos:** modelo de histórico e componente de gráfico novos; integração com watch/UI e testes de série temporal.

**Gates:** memória não cresce sem limite; NaN/Inf e lacunas não viram zeros inventados; separar tempo de recebimento monotônico de timestamp de origem e resets de relógio. Unidades só quando conhecidas, sem inferi-las de qualquer campo chamado x.

**Estado da frente 07 reconciliada com 04/05/06 (`d1f7a3d`):** históricos/Canvas
ligados aos snapshots tipados de cada um dos quatro readers, incluindo todos os
87 campos numéricos de Odometry, com identidade por slot/reader/sessão.
Limites, contratos e regressões reais isoladas estão em [LIVE-PLOTS.md](LIVE-PLOTS.md).
Fixtures DDS e Robolectric não substituem revisão independente, CI do SHA final
nem teste físico. O smoke curto no tablet passou no escopo documentado em
`FINALIZATION-v0.2.md`; qualificação física extensa/endurance seguem pendentes,
sem bloquear o protótipo autorizado.

### 08 — Qualificação física e candidato v0.2

**Branch:** `test/v0.2-08-device-qualification`.

**Entregas:** matriz de aceitação da integração, correções pequenas encontradas, notas de versão e APK candidato rastreável. Nenhuma função extra de última hora.

**Plano original de qualificação estendida (preservado):**

A lista abaixo continua obrigatória para alegar qualificação completa, não para
o escopo reduzido autorizado. Os critérios vigentes do protótipo e a evidência
curta já executada estão em `FINALIZATION-v0.2.md`. Nenhum resultado antigo foi
convertido em PASS; os validadores extensos permanecem inalterados.

- Testes/lint/build/secret scan do SHA final; todos os incrementos revisados.
- Interoperabilidade Jazzy Fast DDS e Cyclone DDS e domínio incorreto isolado.
- Regressões de lifecycle, reader failure, QoS, remoção de nós, watchers e gráficos.
- Perda/retorno de Wi-Fi e mudança de endereço exercitados com recuperação de acesso prevista; resultado não substituído por teste de Home.
- Soak em bancada com quatro watches e gráfico, duração/orçamento definidos antes da execução; crescimento de memória, CPU, ingestão e bateria medidos e resultados registrados sem identificadores privados.
- APK instalado deve coincidir com SHA/hash/assinatura do candidato, com leitura real no tablet e aceite do operador.
- Mudou o código do candidato? Reexecutar gates afetados e a regressão de integração; resultado de APK anterior não libera SHA novo.

## Fora da v0.2

Parâmetros, chamadas de serviços/actions, publicação/teleop, gravação rosbag, imagens/mapas/TF/RViz e tipos customizados dinâmicos. Parâmetros usam RPC ROS 2: antes de prometer leitura/edição, fazer spike explícito sobre cliente rcl/RMW versus implementação de serviços em DDS, interoperabilidade e modelo de timeout. Operações de escrita exigem outra decisão de produto e testes de segurança; não entram por acidente porque a UI ganhou um botão.

## Histórico — preparação paralela 11–15

Esta seção registra o plano daquela rodada, não trabalho atual nem autorização
permanente. A reconciliação e revisão já concluídas estão em `FINALIZATION-v0.2.md`.

O operador autorizou implementar **11/12/13/14/15 em paralelo**, em worktrees
isoladas, com reconciliação posterior pelo pai e testes físicos quando o tablet
estiver disponível. Isso substitui a espera para iniciar implementação; não
remove dependências de integração, revisão, CI do SHA exato ou gates físicos.
Nenhum Android (tablet ou emulador) é operado nesta rodada.

Os incrementos 01–10 já têm fonte na história disponível. A base local da rodada
é `796b59aaf50a468344b825f693331e11bdabb06e`, recuperação do conteúdo revisado da
12 sobre `7b2dc76de3679935fac5adf69839818e8ca5a914`, **não** o commit upstream
original atribuído `7a5e841811f66bcb660da0f9b2b901f1aa272c68`. Presença de código,
recuperação ou teste isolado não significa aceite integrado/físico.

- **11:** coletor de soak e consumidores QA/OS, mantendo plano e orçamentos oficiais.
- **12:** revisão independente da recuperação e regressões reais, sem inventar história.
- **13:** fixture/harness multiwatch próprios, limite/quinto pedido/QoS/falha/logs novos.
- **14:** fixture/harness gráficos próprios, gaps/clock/pausa/eviction/Canvas real.
- **15:** identidade `0.2.0`/code `2`, UI derivada do BuildConfig, regressões e
  documentos/checklist; CI com registro obrigatório **pendente** de 11/13/14.

A 15 pode preparar versão/CI antes de as outras integrarem, mas não pode congelar
candidato final nem declarar ready. O [checklist de reconciliação](FINALIZATION-v0.2.md)
distingue fonte implementada de aceite pendente por incremento e descreve o
registro fail-closed. A revisão independente e reconciliação combinada continuam
pendentes. Fonte/documentos devem terminar antes do build do futuro candidato;
um APK novo recebe hash/metadados próprios e não herda testes físicos antigos.

Estado vigente: integração → main draft e sem auto-merge até autorização própria.
A PR #12 tem merge autorizado. Wi-Fi/IP com recuperação autorizada, soak oficial
de 30 minutos e qualificação extensa continuam pendentes, fora dos bloqueios do
protótipo. Sem assinatura release, tag, loja ou publicação implícitas. Não repetir
o smoke físico nem abrir campanhas automaticamente.
