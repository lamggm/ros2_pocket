# Sensores visuais — v0.4

Host e [smoke físico curto](FINALIZATION-v0.4.md) concluídos. A release v0.4.0
reutiliza o APK testado com identidade interna 0.4.0-dev; não é qualificação extensa.

A v0.4 acrescenta uma área visual no **Monitor**, alternando entre LaserScan e
câmera de tópico ROS/DDS. Não usa RTSP, rosbridge, servidor ou permissão CAMERA.

## Uso

1. Conectar ao Domain ID e à rede da fonte como na v0.3.
2. No Explorer, escolher **Visualizar** em um tópico suportado:
   `sensor_msgs/msg/LaserScan`, `sensor_msgs/msg/Image` ou
   `sensor_msgs/msg/CompressedImage`.
3. O Monitor abre a área visual. LaserScan/Câmera alternam o último tópico de cada
   modalidade escolhido nesta Activity. **Parar** remove a seleção da modalidade.
4. Zoom +/− e Enquadrar controlam apenas a escala do scan. +X aponta para cima,
   +Y para a esquerda, metros no frame original. Não há TF ou mapa.

Um leitor visual explícito é adicional aos quatro watches de telemetria; não
ocupa/reconfigura um watch. Só a modalidade atual tem leitor. Sair do Monitor
suspende esse leitor; voltar exige dados novos. Troca de tópico/modo, Stop,
desconexão, mudança de conexão e recriação invalidam frames pendentes/visíveis.
Seleções visuais não são persistidas nos perfis schema1.

## Tipos, limites e descarte

- LaserScan: até 8192 ranges e intensities, no máximo 4096 pontos desenhados por
  decimação uniforme dos índices; NaN/Inf e ranges fora dos limites não são pontos.
  Metadados angulares inválidos são erro, não coordenadas inventadas. Um scan
  inteiramente inválido pode renderizar a grade sem retornos.
- Image: **mono8, rgb8, bgr8, rgba8, bgra8**. Passo de linha/padding respeitado;
  até 1280 pixels por lado e 921600 pixels no total (inclui 1280×720).
  Depth, Bayer, YUV e outros encodings não estão implementados.
- CompressedImage: **JPEG e PNG** convencionais; até 1MiB de payload e os mesmos
  limites de pixels/dimensões, verificados antes do decode completo. compressedDepth
  não é suportado. Formato desconhecido, assinatura inválida e decode falho são erro.
- QoS visual inicial: **best effort / volatile, keep-last 1**. Sem seletor QoS
  próprio nesta entrega. Recebe também publicadores reliable compatíveis.
- Poll até 10Hz na fila DDS serial existente; a taxa efetiva depende de rede,
  publicador e processamento. Decode de imagem fica fora das filas UI/DDS.
  Há um decode ativo + último pacote pendente e uma única entrega UI pendente;
  frames intermediários são descartados. Não é gravador nem todos os frames.
- RECEBENDO exige idade menor que 3s; frames antigos ficam visualmente atenuados e
  **OBSOLETO**. Falhas limpam o desenho. Retry de aquisição/transporte visual é
  limitado a uma tentativa por segundo, sem reiniciar os watches de telemetria.

## Isolamento de orçamento

A telemetria preserva **64KiB CDR** por amostra. O sensor usa domínio Cyclone
interno `1000 + Domain ID`, com `ExternalDomainId` igual ao domínio ROS da conexão,
e teto separado **4MiB CDR**. Ambos trafegam DDS direto no mesmo domínio de rede.
IDL padrão permanece sem bounds artificiais. Sample excessivo é rejeitado por
Cyclone antes da deserialização; limites locais adicionais verificam conteúdo.

Esses limites não prometem teto de memória total, defesa contra taxa arbitrária,
segurança DDS/SROS2, baixa latência ou suporte universal de câmeras/distribuições.
Não são autorizadas publicações/comandos de robô.

## Validação reproduzível

`tools/test.sh --portable` registra os testes core/loopback sintéticos novos;
`tools/test.sh --fixture` inclui `sensor_jazzy_interop` nos containers isolados
Fast DDS e Cyclone DDS. `sensor_fixture_test.py` usa mensagens ROS reais em
processo separado do leitor nativo, incluindo RGB/PNG fragmentados >64KiB,
limites/erros e recuperação, enquanto telemetria continua avançando.

O probe grava opcionalmente os pacotes recebidos em `POCKET_SENSOR_RECORDS`.
Para o gate de replay Android host, apontar `POCKET_SENSOR_PACKETS` à raiz contendo
`rmw_fastrtps_cpp/{scan,image,compressed}.packet` e o diretório Cyclone equivalente.
`SensorCanvasTest` decodifica esses bytes e verifica pixels reais com Canvas nativo
host. Sem essa variável, somente esse replay opt-in é skip explícito; os testes
sintéticos permanecem obrigatórios.

Testes de concorrência preservam os REDs dos quatro bloqueadores da revisão:
ABA de visibilidade, falha de teardown, saída UI bounded e retry visual. Nenhum
resultado host/fixture/replay é apresentado como instalação/aceite Android físico.
