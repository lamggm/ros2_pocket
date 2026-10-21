# Contrato de conexão e snapshot — incremento 02

Consumidores 03/06 devem incorporar este contrato antes da integração. A sessão
continua C++/Cyclone DDS direto, serializada pelo JNI, somente leitura, com **um**
reader de valores. Descoberta DDS, grafo ROS e recepção são sinais independentes.
Este documento descreve o código desta branch; não declara aceite físico.

## Snapshot aditivo

`Session::poll()` / `NativeBridge.poll()` retornam JSON UTF-8 em `byte[]`.
Todos os campos anteriores mantêm seus nomes/tipos: `running`, `domain`, `topics`
(e seus campos), `nodes`, `ddsPeers`, `subscription`, `last`, `count`, `hz`, `age`,
`matched`, `qos`, `error`. O snapshot parado agora também contém os campos de
telemetria, vazios/zerados, e os diagnósticos abaixo. Não remover os campos antigos
quando o gerenciador de readers da 06 evoluir.

| Campo aditivo | Semântica |
| --- | --- |
| `connectionState` | `offline`, `discovering`, `connected`, `reconnecting`, `error` |
| `subscriptionState` | `inactive`, `waiting`, `receiving`, `stale`, `error` |
| `readerActive` | Existe um reader nativo de valores; não implica match/recepção |
| `fresh` | Somente `subscriptionState == receiving`; nunca inferir de `count` sozinho |
| `rosNodes` | Quantidade de nomes ROS distintos no array `nodes` |
| `sessionGeneration` | Inteiro de revisão da sessão dentro desta instância `Session` |
| `readerGeneration` | Inteiro de revisão do reader dentro desta instância `Session` |

`ddsPeers` é a quantidade de participantes vivos obtidos de
`DDS_BUILTIN_TOPIC_DCPSPARTICIPANT`, excluindo o GUID do próprio inspetor. Não é
calculado por tópicos, endpoints ou nós. Um participante pode anunciar zero,
um ou vários nós ROS; `rosNodes` não deve receber o rótulo de participantes.
Remoções seguem o disposal/lease DDS, não uma suposição sobre perda imediata da LAN.
O grafo usa o esquema Jazzy já existente; descoberta não prova decodificação do grafo.

## Estados, idade e erro

Sem erro: sessão parada é `offline`; participante local iniciado sem peer ainda
visto é `discovering`; pelo menos um peer é `connected`; após ver peers e perder
todos, a mesma sessão é `reconnecting`. Não recriar o participante para procurar
peers. `connected` **não** comprova QoS compatível, publisher do tópico selecionado
ou entrega de mensagens. Esses estados nunca dependem de `topics.length`.

Sem reader: `inactive`. Reader sem amostras: `waiting`, inclusive com zero matches.
Após amostras: `receiving` somente se `matched > 0` e `0 <= age <= 3` segundos;
caso contrário `stale`, mesmo com valor/count retidos. `age` usa
`std::chrono::steady_clock` desde a última amostra recebida, não timestamp ROS nem
relógio civil. A UI acrescenta o tempo monotônico na fila antes de pintar; um
snapshot atrasado não mantém a cor de recepção. Sem amostra é `-1`; Hz é a taxa de intake, não a taxa garantida da origem.
Uma amostra histórica não volta a ser recente só porque apareceu um participante.

Erro não vazio tem precedência nos dois classificadores e torna `fresh=false`.
`error` preserva falhas de start/subscribe em polls sucessivos. Uma operação de
assinatura bem-sucedida limpa a falha de assinatura, mas ainda fica `waiting` até
receber; Stop/reset limpa a sessão. Falhas de leitura de discovery só desaparecem
após um poll completo dessas leituras sem erro; falha de intake só desaparece após
uma leitura DDS de valores bem-sucedida. Isso não fabrica uma nova amostra.

A prevalidação de assinatura é deliberadamente **não destrutiva**: tipo sem
suporte/prefixo inválido mantém o reader, a seleção e a telemetria anteriores,
mas publica o erro persistente, `readerActive=true`, `subscriptionState=error` e
`fresh=false`. A UI diz que o leitor anterior foi preservado e não mostra seus
dados como atuais. Depois da prevalidação, a substituição é destrutiva: falha real
na criação de tópico/reader remove o anterior, limpa seleção/valor/count/Hz,
publica `age=-1`, `matched=0`, `readerActive=false` e erro persistente. Não dizer
que um leitor preservado foi apagado. Não fazer retry automático de assinatura:
seleção/QoS explícitos ou uma nova sessão podem tentar novamente.

`start` sempre encerra o estado anterior antes de validar/criar a nova sessão;
se falhar, `poll` fica parado com erro persistente, sem telemetria anterior ativa.
`sessionGeneration` e `readerGeneration` avançam em Stop/reset/start; a revisão
do reader também avança na substituição destrutiva, inclusive se ela falhar.
Prevalidação rejeitada e perda/retorno de peers não mudam as revisões. São opacos,
monotônicos durante a vida da instância e não são IDs globais ou persistentes.

## JNI, fila Android e recuperação

As assinaturas JNI anteriores permanecem: `start(int,String,String)`, `poll()`,
`subscribe(String,String,boolean,boolean)` retornam JSON em `byte[]`; `stop()` é
idempotente. Start/subscribe mantêm `{ok,error}` (error em falha); start inclui
`domain` em sucesso. Não adicionar callbacks concorrentes ao núcleo. O mutex JNI
e a fila Android única serializam operações, inclusive teardown entre Activities.
`NativeBridge.Api` permite um dublê explícito apenas nos testes Android; produção
usa `JniApi`, que inicializa a classe JNI no primeiro uso e chama os mesmos símbolos.
Chamadores diretos antigos de `NativeBridge.start/poll/subscribe/stop` continuam
carregando a biblioteca na inicialização da classe, com as mesmas assinaturas.

`ConnectionPolicy` é Java portável. Recebe milissegundos monotônicos de
`System.nanoTime()`. Falhas de aquisição/start/poll tentam novamente depois de
1, 2, 4, 8, 16 e no máximo 30 segundos, continuando limitadas a 30 s indefinidamente;
o ticker em primeiro plano continua verificando rede a cada 500 ms. Só um poll de
sessão iniciada confirma recuperação e zera o backoff. Mudança de identidade de
rede/interface/IPv4 zera o prazo; disconnect/connect explícito também zera erro e
prazo. Ausência de peers e falha de assinatura não agendam reconstrução da sessão.
Não usar relógio civil nem encerrar o ticker ao atingir o limite de espera.

Toda UI enfileirada carrega geração de lifecycle e revisão da política, além da
identidade de configuração. Rede/endereço, nova seleção/QoS, Stop e desconexão
invalidam callbacks anteriores. A revisão aplicada ao reader é registrada na fila;
enquanto uma assinatura está apenas enfileirada, polls do leitor anterior também
ficam pendentes na UI, mesmo que já tenham capturado a nova revisão da política. A Activity em primeiro plano e o dono nativo são
verificados separadamente: um Stop tardio da Activity anterior não pode liberar
a sessão/rede da nova. Falhas de rede/start/assinatura retiram imediatamente o
rótulo e a cor de recepção; falha/pendência não conserva mensagem antiga como atual.
Stop libera participante, vínculo de processo e multicast na mesma fila. A UI
não altera VPN, firewall, Wi-Fi ou política de energia; uma recusa do vínculo de
rede é mostrada como uso da rota padrão, preservando o fallback LAN existente.

Persistência local: domínio, peer opcional, intenção de conexão e QoS do reader.
Home/Stop/Start da mesma Activity preserva seleção de tópico (inclusive numérico),
mas cria um reader novo sem carregar valor antigo. Um processo/Activity novo exige
seleção de tópico, conforme o comportamento anterior; não persiste telemetria.

## Verificação e limites

`bash tools/build.sh` executa regras Java portáveis, CTest core/session/classificador,
fixture DDS própria de transições no domínio 69 e regressão legada de erro de criação
com publisher próprio em loopback no domínio 67, antes do build Android e
`testDebugUnitTest lintDebug assembleDebug`. A variante legada `subscribe_failure`
continua disponível separadamente para a fixture ROS externa de domínio 67.
As fixtures internas publicam apenas amostras sintéticas locais, nunca comandos.

Robolectric 4.14.1 exercita Android API 31 com JNI explicitamente substituído;
não simula sucesso de DDS real. Para usar jars de runtime já disponíveis sem
resolver downloads, definir `POCKET_ROBOLECTRIC_JARS` para uma pasta com os jars
`android-all-instrumented` de API 31 e API 35 (a versão compile/target também é
resolvida pelo Robolectric). Dependências Gradle seguem o cache/offline existente.
Em máquina nova, preparar essas dependências antes do build offline.

`tests/tablet_regression.py` amplia asserts de diagnóstico junto aos gates físicos
anteriores; sua edição/compilação não significa execução. Perda física de Wi-Fi,
troca real de IP, interoperabilidade externa, instalação e aceite do APK continuam
sob coordenação do operador/qualificação. Home e loopback não substituem esses gates.
