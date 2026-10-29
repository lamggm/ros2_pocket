# ROS Pocket v0.4 — sensores visuais

## Escopo autorizado
O operador pediu limpeza dos resíduos do Pocket no Xeon, branches e implementação.
Base: main c55fbb3. Integração local `integration/v0.4`; implementação sequencial
em `feat/v0.4-01-sensor-views`. Sem merge main, release ou ADB implícitos.

- Visualização alternada, uma área para LaserScan 2D ou câmera de tópico ROS/DDS.
- Scan no frame do sensor: pontos, escala métrica, zoom/enquadrar, metadados e idade.
- Câmera: Image rgb8/bgr8/rgba8/bgra8/mono8, CompressedImage JPEG/PNG.
- Um único leitor visual explícito, separado dos quatro watches de telemetria.
  O domínio DDS interno separado usa ExternalDomainId para o domínio ROS escolhido;
  não cria servidor/bridge, não publica e não altera o teto 64KiB da telemetria.
- Orçamento visual: até 4MiB CDR, um sample no histórico, imagem até 1280x720
  (921600 pixels), payload comprimido até 1MiB, scan até 8192 ranges/4096 pontos.
  Limites são de admissão/payload; não promessa de memória total ou defesa de taxa arbitrária.
- Poll visual até 10Hz na fila serial existente, decode fora dela, no máximo um
  frame em decode e o último pendente, uma entrega UI pendente; sem promessa de todos os frames ou baixa latência.
- Desconexão, troca de modo/tópico, Stop e recriação removem imagem anterior;
  frames tardios não podem reaparecer. Scan/câmera não entram nos perfis schema1.

## Fora
CSV, gravação, áudio, RTSP, TF/mapa/SLAM, profundidade/encodings arbitrários,
Humble, teleop, rede/soak e certificação física extensa. Preservar passes e limites v0.3.

## Aceite proporcional
TDD, contratos nativos, publisher ROS real isolado, Views/Canvas/decode/lifecycle,
regressões portáveis e Android, lint, build ARM64 hooks OFF e revisão independente.
Resultados host não são aceite físico; teste no tablet exige autorização própria.
