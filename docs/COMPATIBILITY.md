# Compatibilidade e limites

[Início](../README.md) · [Índice da documentação](README.md)

Este documento descreve os limites atuais da v1.2 (produção1.2.0 e DEV1.2.0-dev,
code12); resultados históricos abaixo conservam sua versão. Descobrir um endpoint,
decodificar mensagens e reconstruir o grafo de nós são capacidades distintas.
Um tópico aparecer na lista não comprova as outras duas.

## Android e rede

- Android 9/API 28 ou superior; APK ARM64 (`arm64-v8a`).
- Interface em português. Não há aceite físico para todos os fabricantes,
  dispositivos antigos ou aparelhos Android com páginas de memória de 16 KiB.
- **LAN:** IPv4 na mesma Wi-Fi/LAN, Wi-Fi físico e `DontRoute=true` preservados.
- **VPN:** modo explícito com peer IPv4 obrigatório, VPN disponível ao UID,
  unicast e `DontRoute=false`, inclusive no leitor visual.
  [Operação e limites](VPN-DDS.md) · [Evidência por variante](RELEASE-v1.2.md).
- Interoperabilidade DDS com bridge ROS/Zenoh existente, sem backend Zenoh
  nativo nem instalação automática de servidor. A bancada usou bridge1.10.2/Jazzy.
- VPNs/sub-redes arbitrárias, IPv6-only e SROS2/DDS Security não herdam suporte.

A receita nativa usa alinhamento ELF compatível com 16 KiB, mas isso não é teste
em dispositivo com esse tamanho de página. A bancada física conhecida usa 4 KiB.

## Distribuições ROS 2

O projeto foi exercitado com **Jazzy**, em fixtures Fast DDS e Cyclone DDS.
A validação host/CI não equivale a execução Android física para cada combinação.
O [smoke físico curto](SMOKE-v0.3.md) dos perfis/candidato passou **8/8** no
SM-X400 / Android 16 com produtor Jazzy/Fast DDS e peer explícito. Não é uma
matriz física completa de distribuições, RMWs ou descoberta automática.

Na v0.8, a matriz **Humble/Jazzy × Fast DDS/Cyclone DDS** passou com produtores
ROS reais em contêineres privados: endpoints, String/Float64, `/rosout`, nomes de
nós e associações de endpoints. Humble também passou remoção/reinício do produtor
nos dois RMWs. [Versões e resultados por capacidade](HUMBLE-JAZZY.md).

O formato é **manual**: Jazzy/GID16 ou Humble/GID24, persistido no perfil;
somente o leitor escolhido é criado. Humble valida a cauda zero do GID do grafo,
sem converter handles RMW genéricos. Perfis antigos assumem Jazzy em memória,
sem regravar o catálogo no load. Formato desconhecido é rejeitado; uma escolha
incompatível não dispara fallback nem inventa nós. Sem grafo recebido, o estado
permanece não confirmado, enquanto a recepção tipada pode continuar independente.

O smoke físico v0.8 passou 4/4 com DDS sintético local Android, incluindo ambos
os grafos e recepção Float64. Não é matriz ROS real Humble/Jazzy pela LAN,
suporte a domínios mistos ou soak. Nomes vêm de `ros_discovery_info`, não de
nomes de participantes adivinhados.

A [issue #18](https://github.com/lamggm/ros2_pocket/issues/18) acompanha a matriz
multidistribuição. Outras versões não herdam suporte por analogia. Os campos
serializados de `rcl_interfaces/Log` coincidem nas branches Humble/Jazzy
consultadas; igualdade de schema não substitui teste de interoperabilidade.

## Tipos de mensagem

Os watches decodificam estes tipos padrão:

- `std_msgs/msg/String`, `Bool`, `Int32`, `Int64`, `Float32` e `Float64`.
- `geometry_msgs/msg/Vector3`, `Quaternion`, `Twist` e `Pose`.
- `nav_msgs/msg/Odometry`.

O reader dedicado de `/rosout` decodifica `rcl_interfaces/msg/Log` separadamente.
O leitor visual adicional suporta `sensor_msgs/msg/LaserScan`, `Image` e
`CompressedImage`, alternados; [encodings e orçamentos](SENSOR-VIEWS.md).
Isso não significa que o tipo esteja disponível no projetor genérico dos watches.
Tipos desconhecidos/customizados podem aparecer na descoberta, mas não recebem
um decoder presumido.

O inspetor estruturado expõe árvores limitadas por watch, caminhos numéricos,
timestamps de origem, frames UTF-8 e arrays fixos de covariância. NaN e Inf ficam
explícitos, nunca são inventados zeros. Contrato em [mensagens estruturadas](STRUCTURED-MESSAGES.md).

## Limites de recepção e visualização

- Até **quatro watches independentes**, cada um com valor/árvore, contagem, Hz
  recebido, matches, QoS, erro persistente e idade monotônica. Contagens de
  endpoints excluem os readers do próprio inspetor.
- Um reader dedicado e limitado de `/rosout`, independente dos quatro watches.
- Um leitor visual ativo por vez, com teto DDS separado de 4MiB; não altera os
  64KiB da telemetria. Smoke físico webcam JPEG + scan sintético 6/6 em
  Jazzy/Cyclone DDS com peer LAN explícito; não é uma matriz completa.
- Até **quatro séries numéricas**, com histórico de 30 segundos/600 pontos;
  Odometry oferece 87 folhas numéricas. Na v0.7, [lotes limitados](NUMERIC-BATCHES.md)
  transportam até 32 amostras/watch/poll dos quatro campos selecionados globais,
  com descarte local explícito. Não reconstrói cada mensagem da fonte.
  Pausa congela o desenho, não o intake; 600 pontos podem encurtar a janela de 30 s.
- Intake nativo aproximadamente duas vezes por segundo; atualização visível
  aproximadamente a cada 1,5 segundo. Hz recebido não é frequência garantida da fonte.
- Cache/intake limitado a 64 amostras por watch, 256 no total por poll.
  Texto mais recente limitado a 8192 bytes UTF-8.
- Teto DDS de **64 KiB CDR serializados por amostra**, antes da desserialização,
  também para grafo e logs. Amostras maiores são rejeitadas. Isso não é um teto
  global de memória, tráfego agregado ou quantidade de endpoints do processo.

O app opera em foreground. Stop libera DDS e recursos multicast. Conexão ativa,
reader ativo e dados recentes são estados diferentes; buffers antigos não
comprovam recepção. Veja também [lifecycle e perfis](USAGE.md#reinício-e-última-aplicação).

## O que não faz parte desta versão

O app não publica comandos nem oferece teleop, edição de parâmetros,
serviços/actions, gravação rosbag/vídeo, RTSP, mapas/TF ou um RViz completo.
CSV do histórico e lotes numéricos limitados já existem, conforme seus contratos.
Decodificação dinâmica de tipos customizados permanece fora deste ciclo.

Qualificação física gráfica completa, recuperação real de Wi-Fi/IP, high-rate,
endurance/soak e dispositivos adicionais permanecem não comprovados para este
candidato. Os testes existentes conservam seus thresholds; pendência não é PASS.
Os avisos conhecidos de lint sobre localização, ícone e regras de extração de
dados não foram convertidos em funcionalidades ou correções nesta documentação.
