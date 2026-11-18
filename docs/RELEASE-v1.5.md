# ROS Pocket 1.5.0

- **Camera e Lidar Scan simultâneos** no Painel, com tópicos e leitores separados. A página Localização usa o leitor de mapa sem manter as duas visualizações do Painel.
- Painel por perfil, grid editável, aliases, instrumentos, precisão/unidades e previews ampliáveis para gráfico, sensores e 3D.
- Gráfico com título editável, escalas e legenda. Localização passiva por OccupancyGrid e TF, sem estimar pose ausente.
- Pacotes de malhas locais vinculados ao URDF exato; nenhuma malha de robô é embutida no APK. A abertura do 3D não pede comandos no robô.

A aceitação DEV no tablet, com BART em simulação, confirmou câmera e LiDAR recebendo juntos antes e depois do popup da câmera, CAD completo restaurado e configuração preservada. Isso não qualifica robô físico, sincronização, carga prolongada ou a instalação da variante de produção no tablet.

Android 9+, ARM64. O app permanece somente leitura. [GitHub Releases](https://github.com/lamggm/ros2_pocket/releases/tag/v1.5.0) · [Repositório F-Droid independente](https://lamggm.github.io/ros2_pocket/fdroid/repo/). Não é o catálogo oficial f-droid.org.
