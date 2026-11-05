# ROS Graph — v1.1 em desenvolvimento

A topologia antes reservada à v2/issue33 foi antecipada pelo operador para v1.1.
O objetivo é descobrir quem conversa com quem por toque, sem copiar a apresentação
densa do rqt_graph. Esta página não declara publicação ou aceite físico.

## Interação

- Abra **Grafo**. Nós são arredondados; tópicos retangulares.
- Toque em nó/tópico: destaca seus tópicos e os participantes das respectivas
  conversas; o contexto não relacionado fica esmaecido. Não muda posições/zoom.
- Toque na linha: mostra direção, endpoints e QoS anunciado. É uma associação,
  não uma garantia de compatibilidade nem medição de tráfego.
- Arraste dentro do desenho para mover; use pinça ou +/− para zoom.
- **Enquadrar** ajusta o foco selecionado; sem seleção ajusta toda a visão.
- Busca + Enter seleciona/enquadra o primeiro resultado determinístico,
  priorizando nome exato. Nomes repetidos são distinguidos pelo participante DDS
  nos detalhes, sem alegar identidade ROS única pelo nome.
- Toque no fundo ou **Limpar** remove o foco. O filtro de infraestrutura esconde
  `/rosout` e `/parameter_events`; pode ser desativado explicitamente.
- Detalhes completos aparecem abaixo do desenho; nomes longos no desenho são
  abreviados, não alterados na identidade ou detalhes.

## Fonte e limites

Usa o snapshot existente: `topics[].endpoints[]`, `writer`, `participant`, GID e
`nodes[]` confirmados pelo backend. Tópico e tipo formam a identidade; tipos
customizados podem aparecer sem decodificador de mensagens. Não há novos readers
nem mudanças de rede ao selecionar, filtrar ou navegar no grafo.

Formatação Humble/Jazzy permanece manual. Formato divergente do solicitado limpa
a visualização. Sem associação confirmada, apresenta **endpoint sem nó confirmado**,
sem adivinhar nome ROS pelo participante ou nome do tópico.

A visão tem limite de 160 elementos/400 conexões e mostra **VISÃO PARCIAL** quando
o snapshot excede esse limite; o orçamento é aplicado durante a projeção (até
8192 passos), preservando tópicos com suas extremidades antes de nós isolados.
Detalhes de foco limitados a 40 conexões e oito
endpoints da linha. Estes são limites de apresentação, não de descoberta DDS.
Posições sobreviventes e viewport são preservados no refresh; novos elementos
ocupam vagas livres. Mudança de orientação/enquadramento é explícita, não implica
qualificação de lifecycle ao vivo. Suspensão, desconexão e indisponibilidade
limpam associações anteriores. Não há persistência de topologia.

## Verificação

Testes de projeção, Views/Canvas e interação no host são distintos de fixtures
ROS reais e smoke físico. O smoke autorizado é curto e somente leitura; não
inclui interrupção RF/IP, soak, controle de robô, merge ou publicação.
A identidade e os resultados do candidato devem constar no relatório da entrega.
