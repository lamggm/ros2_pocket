# ROS Pocket

Inspecione tópicos ROS 2 pelo celular ou tablet Android, sem precisar abrir um
notebook. O ROS Pocket se conecta diretamente via DDS para mostrar mensagens,
gráficos e logs. Ele é **somente leitura**: não envia comandos para o robô.

[Baixar o app](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0-rc1) ·
[Guia de uso](docs/USAGE.md) · [Documentação](docs/README.md) ·
[Reportar um problema](https://github.com/lamggm/ros2_pocket/issues)

> A versão disponível é a pré-release **0.3.0-rc1**, um protótipo de bancada.
> O teste físico dos perfis e desse candidato integrado ainda está pendente.
> Não use o app como instrumento de segurança ou controle de uma operação.

## Baixar e instalar

1. Abra a [página da pré-release](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0-rc1).
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
tipos padrão. Tipos customizados, LaserScan, imagens, mapas, parâmetros,
serviços/actions e teleop não fazem parte desta versão.
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

Os guias de uso descrevem o **APK publicado**. O código da pré-release está na
tag [`v0.3.0-rc1`](https://github.com/lamggm/ros2_pocket/tree/v0.3.0-rc1);
o desenvolvimento segue em `integration/v0.3`. Atualizar esta documentação na
`main` não promove o código v0.3 nem altera o APK já publicado.
