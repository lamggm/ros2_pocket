# Testes, CI e evidências

[Início](../README.md) · [Índice da documentação](README.md)

Um build, um teste no host e uma execução no Android comprovam coisas diferentes.
Este guia reúne as receitas antes misturadas no README. Os relatórios continuam
vinculados à fonte e ao APK realmente exercitados; reorganizar texto não gera
novos resultados de teste.

## Escolher a validação

- **Portável/host:** regras, parsers, modelos, Views host, contratos de ferramentas
  e testes C++/sessão loopback. Não comprova carregamento NDK em dispositivo.
- **Fixture ROS isolada:** processos ROS/DDS reais nos dois RMWs. Não é teste de
  rede Wi-Fi, bateria ou interface física do Android.
- **Tablet:** instalação, lifecycle Android, tela e tráfego real no dispositivo.
  Exige autorização e preparação próprias.
- **Qualificação extensa:** campanhas adicionais, incluindo soak e rede, com os
  gates/limiares do plano correspondente. Um smoke curto não substitui esse plano.

Para os resultados já existentes, veja [versões e histórico](PROJECT-STATUS.md).
Na v0.4, o [fechamento de sensores](FINALIZATION-v0.4.md) registra host/revisões e
smoke físico **6/6** com webcam real e LaserScan sintético. Esse smoke não é soak
nem nova qualificação física de todos os quatro watches.
Na v0.3, os oito critérios do [roteiro físico](FINALIZATION-v0.3.md) passaram
numa rodada completa; consulte o [resultado e limites](SMOKE-v0.3.md).

## Testes portáveis

Na raiz de um checkout com as ferramentas/dependências preparadas:

```bash
bash tools/test.sh --portable
```

O wrapper executa os CTests `core` e `session` com label `portable`, sessão real
em loopback `127.0.0.1`/domínio 67, regras Java e contratos Python de scripts e
metadados. Não precisa de Docker ou publicador ROS externo. Build ordinary também
executa o conjunto portável, testes Android unitários host e lint.

Os gates 11/13/14 têm verificação de registro em
[`tools/verify_finalization_ci.py`](../tools/verify_finalization_ci.py), chamada
pelo wrapper. O caminho padrão do registro é `ci/finalization-gates.json`; o
verificador rejeita estado pendente, seleção ausente/vazia, skips, falhas e
contagem abaixo do mínimo. Existência do verificador ou registro não prova que
um workflow remoto executou nem permite omitir um gate que falhou.

## Fixtures DDS reais sem tablet

```bash
bash tools/test.sh --fixture
```

O [Dockerfile da fixture](../tests/fixture.Dockerfile) usa a base pública Jazzy
por digest e fixa versões APT adicionais, inclusive transitivas. Para cada RMW
Fast DDS/Cyclone DDS, executa `rosout_jazzy_interop` primeiro com produtor novo,
depois as demais fixtures. O produtor aguarda subscriber DDS, não um sleep fixo.

Publicador ROS e reader nativo host, com o prefixo ddsc fixado, executam no mesmo
container descartável **`--network none`**, loopback privado/domínio 67. Binários
e bibliotecas são montados somente leitura. São exercitados tipos estruturados
e primitivos, Unicode/truncamento, NaN/Inf, caminhos numéricos e recuperação de reader.

O nome da imagem deriva do Dockerfile, publicador e configuração loopback;
não apenas do Dockerfile. Isso evita que worktrees paralelas reutilizem uma
imagem de fixture diferente sob o mesmo nome. Containers têm nomes únicos,
limites de recursos, lifetime limitado e cleanup; não reutilizam o nome da
fixture física. Não têm acesso à rede do host, LAN, robô ou tablet. Os XMLs ficam
em `build/native-host/`; logs sintéticos não são publicados como assets do CI.

A receita pressupõe ABI host compatível com Ubuntu 24.04. Pacotes são validados
por metadados APT assinados e versões fixadas. Se upstream remover uma versão,
o provisionamento deve falhar, não atualizar silenciosamente. Rever pins exige
reexecutar os dois RMWs. Guarde a imagem construída para bancada offline; a
receita não promete retenção eterna dos pacotes nem camadas byte-idênticas.

O mesmo entrypoint chama `tools/host_campaigns.py`: multiwatch real nos dois
RMWs, controles de mutação, fases DDS reais e replay pelo modelo/Canvas host.
Os adapters com hooks usam build host separado, sem habilitá-los no APK ordinary.
O replay força a suíte Java completa, guarda XML próprio e rejeita o opt-in vazio
ou skipped. `POCKET_TOOLCHAINS` é explícito e não precisa de caminhos pessoais do CI.

## CI público

O [workflow Verify](../.github/workflows/verify.yml) usa runners efêmeros
`ubuntu-24.04`, permissões `contents: read`, checkout sem credenciais persistidas,
sem chaves de release, secrets ou runners pessoais. PRs usam `pull_request`,
não `pull_request_target`; actions são fixadas por commit upstream e o scanner
por digest de imagem.

O job limitado faz scan redigido do histórico completo, confere wrapper,
provisiona caches novos, executa portáveis/unitários/lint/build, fixtures Jazzy
e repete o caminho offline. Só APK debug e JSON de proveniência são enviados,
com retenção de **7 dias**. Não são enviados dumps de dispositivo, snapshots DDS,
logs privados, chaves ou toolchains. A release pública é separada desses assets temporários.

O filtro de push herdado ainda não inclui `integration/v0.3`. PRs disparam
verificação; não alegue que todo push na integração tem CI próprio. Conferir
head e árvore executados antes de integrar. Não ampliar credenciais ou alterar
YAML para contornar isso sem escopo autorizado. Detalhes no [roadmap v0.3](ROADMAP-v0.3.md).

## Reproduzir numa bancada Android autorizada

Nunca use um domínio de robô para testes sintéticos de lifecycle. O
[harness de tablet](TABLET-HARNESS.md) descreve transport/image explícitos,
guardas de usuário/package/foco, dumps privados, navegação limitada e freshness.
Instalação é somente in-place, após conferir package/certificado, sem
uninstall/clear data. Uma automação por vez; preservar preferências e configurações.

`tests/tablet_regression.py` cria sua própria fixture única e rotulada nos
domínios reservados 67/68, com remaps de tópicos/nós String/Float64 por rodada.
Não precrie um container fixo nem pré-selecione um tópico para esse comando.
Ele usa imagem local explícita sem pull, verifica ownership, para pelo ID
imutável e tenta desconectar o app de forma independente. Não para containers
alheios por semelhança de nome/domínio nem altera VPN/firewall/Wi-Fi.

`tests/tablet_accept.py` é outro teste: somente endereço LAN/descoberta, com
`POCKET_TABLET`, `POCKET_EXPECT_WIFI_IPV4`, app já conectado e publicador genérico
`/pocket_test/chatter` controlado à parte. Não cria esse publicador nem estabelece
aceite v0.2. A regressão também exige `POCKET_ROS_IMAGE`;
`POCKET_FIXTURE_INTERFACE` opcional fixa apenas o publicador Cyclone próprio à
interface LAN verificada. `POCKET_BENCH_PEER` opcional configura o peer IPv4;
vazio significa automático. O relatório registra o modo: sucesso com peer
explícito não qualifica descoberta automática multicast.

A navegação da v0.3 mudou onde os controles aparecem. Não presuma que um harness
histórico de página única valida todas as seções sem adaptação/revisão.
O teste curto acordado é o roteiro em [FINALIZATION-v0.3](FINALIZATION-v0.3.md),
não uma ordem para disparar toda a qualificação histórica.

Para a regressão nativa separada de falha de assinatura, com publicador genérico
controlado e CMake host configurado com `-DPOCKET_TEST_ADDRESS=<host-LAN-IPv4>`:

```bash
ctest --test-dir build/native-host -R '^subscribe_failure$' --output-on-failure
```

Esse teste depende da fixture; não é parte do build offline nem autorização de
LAN. Detalhes em [falhas de reader](READER-FAILURE-TESTS.md).

## O que continua separado

O [plano extenso v0.2](DEVICE-QUALIFICATION-v0.2.md) mantém seus 21 gates e soak
medido para alegar qualificação completa. Seus thresholds não foram reduzidos;
esse aceite amplo não era exigido pelo escopo limitado de protótipo de bancada.
Falha, timeout ou teste pendente não vira PASS por combinar rodadas.

O [dump QA debug](DEVICE-QA-CONTRACT.md) expõe metadados limitados dos quatro
contadores reais do worker e do gráfico desenhado sem novo poll DDS; é somente
leitura e fica desabilitado em builds release. Dump válido não é aprovação;
estado de hooks indisponível continua bloqueando o soak quando exigido.

Testes Linux JNI/Session, replay Canvas host, instrumentação empacotada e execução
real no Android são resultados diferentes. A [política diagnóstica](ANDROID-DIAGNOSTIC-POLICY.md)
e a [proveniência gráfica](GRAPH-QA-PROVENANCE.md) detalham seus contratos.
Registros locais de evidência e publicação recuperável de metadados estão em
[ARTIFACT-PUBLICATION](ARTIFACT-PUBLICATION.md); não são publicação de GitHub Release.
Logs crus, identidades de endpoints e evidência privada não pertencem ao Git.
