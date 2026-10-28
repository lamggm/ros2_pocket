# Compatibilidade e limites

[Início](../README.md) · [Índice da documentação](README.md)

Este documento descreve o APK publicado **0.3.0-rc1**. Descobrir um endpoint,
decodificar mensagens e reconstruir o grafo de nós são capacidades distintas.
Um tópico aparecer na lista não comprova as outras duas.

## Android e rede

- Android 9/API 28 ou superior; APK ARM64 (`arm64-v8a`).
- Interface em português. Não há aceite físico para todos os fabricantes,
  dispositivos antigos ou aparelhos Android com páginas de memória de 16 KiB.
- IPv4 na mesma Wi-Fi/LAN. O app seleciona Wi-Fi físico e usa `DontRoute=true`
  para filtrar locators distantes de VPNs/bridges Docker.
- Sub-redes roteadas, IPv6-only e SROS2/DDS Security não são suportados ou
  validados por este protótipo. Não há um servidor/bridge intermediário.

A receita nativa usa alinhamento ELF compatível com 16 KiB, mas isso não é teste
em dispositivo com esse tamanho de página. A bancada física conhecida usa 4 KiB.

## Distribuições ROS 2

O projeto foi exercitado com **Jazzy**, em fixtures Fast DDS e Cyclone DDS.
A validação host/CI não equivale a execução Android física para cada combinação.
O [smoke físico curto](SMOKE-v0.3.md) dos perfis/candidato passou **8/8** no
SM-X400 / Android 16 com produtor Jazzy/Fast DDS e peer explícito. Não é uma
matriz física completa de distribuições, RMWs ou descoberta automática.

**Humble ainda não tem suporte validado.** A descoberta DDS e tipos padrão podem
interoperar, mas isso precisa ser demonstrado por capacidade. O grafo atual usa
`rmw_dds_common/Gid` de 16 bytes, do Jazzy; a branch upstream Humble usa 24 bytes.
O decoder do grafo Humble não está implementado. Nomes de nós vêm de
`ros_discovery_info`, não de nomes de participantes adivinhados.

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
- Até **quatro séries numéricas**, com histórico de 30 segundos/600 pontos;
  Odometry oferece 87 folhas numéricas. O gráfico usa snapshots recentes,
  não reconstrói cada mensagem da fonte. Pausa congela o desenho, não o intake.
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
serviços/actions, rosbag, imagens/vídeo, LaserScan, mapas/TF ou um RViz completo.
Exportação para o usuário, lotes numéricos e decodificação dinâmica de tipos
customizados ficam fora deste ciclo.

Qualificação física gráfica completa, recuperação real de Wi-Fi/IP, high-rate,
endurance/soak e dispositivos adicionais permanecem não comprovados para este
candidato. Os testes existentes conservam seus thresholds; pendência não é PASS.
Os avisos conhecidos de lint sobre localização, ícone e regras de extração de
dados não foram convertidos em funcionalidades ou correções nesta documentação.
