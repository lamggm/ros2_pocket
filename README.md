# ROS Pocket

> **Branch de desenvolvimento v0.5:** [exportação CSV dos gráficos](docs/CSV-EXPORT.md).
> O download público abaixo continua na v0.4; não representa aceite físico da v0.5.

> **v0.4 — sensores visuais:** LaserScan 2D e câmera por tópico ROS/DDS,
> alternados na mesma área, sem bridge/RTSP. [Uso e limites](docs/SENSOR-VIEWS.md) ·
> [aceite físico curto: 6/6](docs/FINALIZATION-v0.4.md).

Inspecione tópicos ROS 2 pelo celular ou tablet Android, sem precisar abrir um
notebook. O ROS Pocket se conecta diretamente via DDS para mostrar mensagens,
gráficos e logs. Ele é **somente leitura**: não envia comandos para o robô.

[Baixar o app](https://github.com/lamggm/ros2_pocket/releases/tag/v0.4.0) ·
[Guia de uso](docs/USAGE.md) · [Documentação](docs/README.md) ·
[Roadmap v0.5–v0.9](docs/ROADMAP.md) ·
[Reportar um problema](https://github.com/lamggm/ros2_pocket/issues)

> A release **v0.4.0** é um protótipo de bancada. Reutiliza o APK físico testado,
> identificado internamente como **0.4.0-dev**, versionCode **4**, assinatura debug.
> O [smoke físico de sensores](docs/FINALIZATION-v0.4.md) passou **6/6** no SM-X400 / Android 16.
> Não use o app como instrumento de segurança ou controle de uma operação.

## Baixar e instalar

1. Abra a [página da release](https://github.com/lamggm/ros2_pocket/releases/tag/v0.4.0).
2. Em **Assets**, baixe o arquivo `.apk`. Os arquivos “Source code” não são o app.
3. Instale em um Android **9 ou superior**, com processador **ARM64**. O Android
   pode pedir permissão para instalar aplicativos da origem escolhida.

O aplicativo aparece como **ROS Pocket DEV**. Para atualizar uma instalação
existente, o certificado precisa ser compatível. Se o Android rejeitar a
assinatura, **não desinstale nem limpe os dados como atalho**: veja as
[instruções de instalação e atualização](docs/USAGE.md#instalar-e-atualizar).

<a id="features-and-limits"></a>
<a id="local-profiles"></a>

## O que dá para fazer

- Encontrar tópicos, filtrar a lista e consultar tipos, endpoints e QoS.
- Acompanhar até **quatro tópicos** com contadores, idade dos dados e mensagens estruturadas.
- Plotar até **quatro séries numéricas**, com pausa da visualização.
- Ler e filtrar logs de `/rosout`.
- Salvar configurações em **perfis locais** e aplicá-las antes de conectar.
- Visualizar **LaserScan 2D ou câmera ROS**, alternados no Monitor, sem ocupar os watches.

A interface tem quatro seções: **Conexão, Tópicos, Monitor e Logs**.
O app usa DDS direto; não exige rosbridge ou um servidor auxiliar.

<a id="connect"></a>

## Primeira conexão

1. Coloque o Android e os participantes ROS 2 na mesma rede Wi-Fi/LAN.
2. Em **Conexão**, informe o mesmo **Domain ID** usado no ROS 2.
3. Deixe o peer IPv4 vazio para tentar descoberta automática e toque em **Conectar**.
4. Em **Monitor**, escolha um dos watches. Abra **Tópicos** e toque em **Ler** no tópico desejado.
5. Volte ao **Monitor** para acompanhar os dados ou abra **Logs** para ver `/rosout`.

Se nada aparecer, confira domínio, multicast e QoS no
[guia de conexão](docs/USAGE.md#problemas-de-conexão).

## Compatibilidade

O projeto foi exercitado com **ROS 2 Jazzy**, usando Fast DDS e Cyclone DDS.
**Humble ainda não tem suporte validado**; há uma diferença conhecida no formato
do grafo de nós. O trabalho de compatibilidade multidistribuição está na
[issue #18](https://github.com/lamggm/ros2_pocket/issues/18).

O APK atual é ARM64, usa IPv4 na mesma LAN e decodifica uma lista definida de
tipos padrão, incluindo [sensores visuais com limites explícitos](docs/SENSOR-VIEWS.md).
Tipos customizados, mapas/TF, parâmetros, serviços/actions e teleop não fazem parte desta versão.
Consulte os [tipos suportados e limites](docs/COMPATIBILITY.md).

<a id="development-sequence"></a>
<a id="build-on-linux-x86_64"></a>
<a id="verification"></a>
<a id="dependencies"></a>

## Para continuar

- [Usar o app](docs/USAGE.md): conexão, watches, gráficos, logs e perfis.
- [Consultar a documentação](docs/README.md): índice de todos os guias e referências.
- [Compilar o APK](docs/BUILDING.md): requisitos, build online/offline e artefatos.
- [Entender os testes](docs/VALIDATION.md): o que cada validação comprova e o que está pendente.
- [Acompanhar versões e desenvolvimento](docs/PROJECT-STATUS.md): release, branches, histórico e backlog.
- [Planejamento v0.5–v0.9](docs/ROADMAP.md): objetivos, limites, milestones e issues; funções futuras, não presentes no APK atual.

A v0.4 preserva watches, gráficos, logs e perfis da v0.3. A release distribui os
mesmos bytes usados no smoke físico, não o APK recompilado pelo CI. O manifesto
explica a fonte local testada e a reconciliação documental/CI posterior.
As releases anteriores permanecem intactas.
