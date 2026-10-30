# Histórico numérico em lotes — contrato v0.7

[Roadmap](ROADMAP.md#v07) · [Issue #24](https://github.com/lamggm/ros2_pocket/issues/24)

## Resultado necessário

Levar ao histórico/CSV mais amostras realmente tomadas pelo reader entre polls,
sem transformar o aplicativo em gravador ou mudar o worker/renderização.
Conexão/watches/visual/rosout e perfis v0.6 permanecem separados e limitados.

## Medição antes da implementação

Baseline nativo inalterado da v0.6, processos separados de Session e produtor
Cyclone DDS, loopback em namespace privado; quatro segundos de envio por taxa,
poll com atraso herdado de 500 ms. Não é Android nem matriz de RMWs Jazzy.

- 20 Hz: 80 enviados/tomados; 8 snapshots distintos expostos.
- 50 Hz: 200 enviados/tomados; 8 snapshots distintos expostos.
- 100 Hz: 400 enviados/tomados; 9 snapshots distintos expostos.
- 500 Hz: 2000 enviados, 518 tomados; 9 snapshots distintos expostos. A diferença
  enviado/tomado não é atribuída automaticamente a uma perda DDS/rede específica.
- Maior duração observada de poll nessa medição: inferior a 2 ms no host; payload
  de um watch inferior a 4 KiB. Isso não promete CPU/latência Android ou quatro
  mensagens estruturadas simultâneas.

`Watch::values()` já toma até 64 amostras, mas sobrescreve o último valor/campos a
cada uma. A ponte publica somente o cache final e o histórico ingere uma vez por
contador distinto. Corrigir esse limite; não redesenhar a ingestão ou usar display
text como fonte dos números.

## Orçamento fixado antes da alteração produtiva

- Alvo de exercício: até **50 Hz por watch**, quatro watches e no máximo quatro
  séries/campos selecionados globalmente, no worker atual. É alvo de validação,
  não garantia universal contra atraso de CPU, DDS, lifecycle ou rede.
- Até **32 amostras por watch por lote**; registros fixos até 64 bytes cada:
  no máximo 8192 bytes de registros para os quatro watches. Até quatro paths de
  256 bytes globalmente; overhead do alocador/objetos é separado, não RSS total.
- Até **16 KiB adicionais de JSON de lotes por poll**. Esse orçamento não redefine
  os campos estruturados/latest/rosout já existentes no restante do snapshot.
- Saturação do buffer local descarta as amostras mais antigas, preserva as mais
  recentes e aumenta contador local por geração de reader. Não contar esse
  descarte como perda universal de DDS/rede. A fila DDS herdada continua em 64.
- Seleção de campos é explícita, limitada e generation-fenced. Não reter uma
  história de todos os campos de Odometry ou de todos os tipos por conveniência.
- Sem novo timer/worker DDS. Desenho continua desacoplado; teto herdado de quatro
  séries, 600 pontos por série e janela de 30 s continuam valendo. Limite de
  pontos pode encurtar a duração retida quando a taxa aumenta.

## Identidade, relógios e lifecycle

Cada amostra de lote preserva contador/ordem do intake nativo, receive-monotonic
capturado na tomada e source timestamp DDS original. Receive não é timestamp de
chegada ao rádio; não inventar tempo civil, interpolação ou nanos para desempatar.
NaN/Inf continuam gaps; conversão binary64 do gráfico mantém o limite de precisão
Int64 herdado, sem alterar a projeção estruturada exata.

Troca de sessão/reader, falha/remoção, novo pedido ou seleção descarta estado
retirado. Repetir um poll não duplica amostras. A ponte marca a revisão de seleção
aplicada; callbacks de uma seleção velha não preenchem uma recém-selecionada.
Perfis restauram intenção, não amostras, buffers ou contadores. Pausa conserva a
cópia de desenho enquanto a ingestão limitada prossegue; CSV exporta a cópia
escolhida com as origens/gaps e sinalização do descarte local conhecido.

## Aceite proporcional

TDD nativo, ponte e Java de lotes, limites, duplicação, identidade, clocks,
NaN/Inf, saturação/recuperação e lifecycle. Exercitar produtores separados reais
nos dois RMWs, quatro watches/visual/rosout e replay pelo histórico/CSV; revisão
independente pontual e smoke Android direcionado no APK exato. Declarar medições
reais separadas de controles sintéticos. CI da PR e pós-merge, release com os
bytes físicos qualificados, metadados e download público verificados.

Fora: filas infinitas, novos tipos, interpolação, rosbag, persistência de amostras,
cloud/sync, garantia sem perdas, Humble, rede/robô ou soak automático. A autorização
termina na entrega v0.7; não iniciar v0.8.

## Implementação e resultado

Contrato implementado e exercitado; [fechamento, APK e limites](FINALIZATION-v0.7.md).
Envelope adicional `numericBatch` schema1: revisão de seleção, quatro slots
com geração de reader, paths, sequência/clocks/valores ou null e `droppedLocal`.
O poll consome pendências. Troca de seleção esvazia as pendências dos watches
cujos paths mudaram; descarte conhecido não vira contagem de perdas anteriores à tomada.

O CSV de lotes usa formato2/18 colunas; a última é o contador local cumulativo
do reader no instante da cópia. [Formato e compatibilidade legada](CSV-EXPORT.md).
Pausa preserva também essa metadata. Sequências diferentes podem ter a mesma
recepção monotônica; gráfico e CSV conservam a ordem, inclusive o gap NaN/Inf.
Não inventar nanos para satisfazer validadores históricos de outro contrato.
