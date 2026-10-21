# Topic Explorer / QoS — incremento 03

Base de integração: `integration/v0.2`. Este incremento não entrega as frentes 01/02 nem substitui a qualificação física 08.

## Uso

- Busca sem distinção de caixa em tópico, tipo e associação de nó anunciada.
- Namespace respeita fronteira de caminho: `/robot` não inclui `/robotics`. `/` não restringe; uma barra final é ignorada.
- Filtro de tipo por trecho de `package/msg/Type`, combinável com busca e namespace.
- Favoritos exclusivamente locais, por par **wireName + wireType**; não guardam GIDs, amostras ou dados do grafo. A preferência persiste após recriar a tela/processo; um tópico ausente não é inventado na lista. A identidade é independente do domínio, deliberadamente.
- `Detalhes / QoS` funciona mesmo sem decodificador. Não cria reader nem muda a assinatura. `Ler`/tocar no nome de um tipo suportado continua sendo a ação explícita de seleção de leitura.
- Filtros e favoritos não chamam JNI. A lista é uma projeção do último snapshot; atualizações idênticas preservam seus controles.
- A lista mostra até **200** tópicos correspondentes; o total e o limite são explícitos. Refine a busca para acessar os demais. Até **256** favoritos; detalhes mostram até **128** endpoints do tópico, com aviso de truncamento. Esses são limites de apresentação, não alegação de throughput.

## Identidade e grafo

`topics[].endpoints[]` acrescenta GID real, participante DDS, papel writer/reader, nomes de nós associados e QoS anunciado. Os campos anteriores do snapshot e a API JNI permanecem disponíveis.

A associação de nó usa os GIDs das sequências de leitores/publicadores de `ros_discovery_info`, com papel e participante correspondentes. O participante DDS **não** é tratado como um nó. Sem associação confirmada, a UI mostra **desconhecido**. Atualizações de grafo, endpoints removidos e reinício de participantes não devem conservar uma associação antiga como se fosse atual.

O esquema do grafo continua sendo o Jazzy com GIDs de 16 bytes. Descoberta de endpoint não prova decodificação de tipos desconhecidos, nem suporte a esquemas de grafo de outras distribuições.

## QoS: não vender certeza falsa

- Publicador anuncia a oferta; assinante anuncia o pedido. Os detalhes apresentam ambos, sem comparar um assinante remoto como se fosse uma oferta para o app.
- QoS inclui confiabilidade, durabilidade, histórico/profundidade, deadline, liveliness/lease, ownership e ordem de destino. Durações são nanossegundos; `-1` representa infinito e `-2`, política não anunciada/desconhecida. Profundidade `-1` significa desconhecida.
- `compatibility` do publicador compara oferta com o perfil do leitor do app. `incompatible` identifica incompatibilidade comprovada e razões; `unknown` **não significa compatível** nem garante matching/entrega.
- Alterar Reliable não corrige durabilidade, deadline, liveliness, ownership, partições, representação de tipo, rede ou um decodificador ausente.
- Contagem `matched` e recepção de mensagens continuam vindo do middleware. A análise estática não substitui esses sinais.
- Histórico/profundidade são apresentados, não tratados indiscriminadamente como requisitos de matching.

## Verificação

Testes Android usam Robolectric (framework JSON e Views reais no host), não stubs de `android.jar`. Exercitam filtros combinados, fronteira de namespace, favoritos/persistência/identidade, detalhes de tipos não suportados, associações desconhecidas/atualizadas, remoção, leitura explícita, incorporação na Activity, desconexão e limites.

As fixtures nativas exercitam descoberta DDS, payload do grafo e QoS em domínio isolado, sem nenhum publicador de aplicação no produto. Scripts de teste e resultados de bancada não são runtime dependencies.

`bash tools/build.sh --online` (ou `--offline` com dependências aquecidas) usa o wrapper e os gates comuns da frente 01: testes Python/Java, CTests `portable` incluindo o explorer nos domínios 69/70, testes Android, lint, APK e proveniência. Execução nativa sem build Android: `bash tools/test.sh --portable`. As três regressões `fixture` de conexão/assinatura da frente 02 ficam em `bash tools/test.sh --fixture`, dentro de containers sem rede externa, para cada middleware Fast DDS/Cyclone DDS.

Os comandos, resultados do SHA final e limites de aceitação ficam no PR. A integração preserva o [contrato de conexão da frente 02](connection-diagnostics.md): rede/start indisponíveis e desconexão limpam somente o snapshot do explorer, sem destruir controles, filtros ou favoritos; a leitura usa a mesma fila e as mesmas cercas de geração. Um teste de host não prova renderização, JNI carregado, Wi-Fi/IP, lifecycle ou recepção no tablet. Não houve autorização implícita para instalar/substituir o pacote no dispositivo. A qualificação física da frente 08 continua separada.
