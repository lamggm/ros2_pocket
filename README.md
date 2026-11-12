# ROS Pocket

> **v1.3 — workspace nativo e grafo circular:** Painel, Explorar, Grafo e Logs;
> aparência Sistema/Claro/Escuro, quatro watches, console com inspetor e física
> visual por namespace. [Interface](docs/UI-v1.3.md) ·
> [Artefatos, testes host e limites](docs/RELEASE-v1.3.md).
>
> **Histórico v1.2 — LAN/VPN e bridge ROS/Zenoh existente:** modo explícito, peer IPv4
> obrigatório em VPN, perfis schema4 e anúncios dos próprios leitores.
> DDS/Cyclone/JNI preservados, sem comandos ou backend Zenoh nativo.
> [Configurar](docs/VPN-DDS.md) · [Artefatos e limites](docs/RELEASE-v1.2.md).
>
> **Histórico v1.1 — ROS Graph touch-first:** toque em nó ou tópico destaca suas conexões
> e esmaece o contexto, sem embaralhar o layout. Busca, pan/zoom, enquadramento
> e detalhes de endpoints/QoS. [Uso](docs/ROS-GRAPH.md) ·
> [Artefatos, testes e limites](docs/RELEASE-v1.1.md).
>
> **Histórico v0.9:** soak físico local de30min e lifecycle aprovados naquele
> APK, sem transferência desse aceite para novos bytes. RF/IP não validado.
> [Resultados anteriores](docs/FINALIZATION-v0.9.md).

> **v0.8 — grafo Humble/Jazzy com seleção manual:** formato persistido no perfil,
> matriz ROS real no host e smoke Android direcionado 4/4.
> [Uso, matriz e limites](docs/HUMBLE-JAZZY.md) · [fechamento](docs/FINALIZATION-v0.8.md).
>
> **v0.7 — histórico numérico em lotes:** mais amostras reais entre polls,
> campos selecionados e descarte local explícito no gráfico/CSV.
> [Uso e budgets](docs/NUMERIC-BATCHES.md) · [fechamento e smoke físico 4/4](docs/FINALIZATION-v0.7.md).
> Preserva [perfis portáteis v0.6](docs/PORTABLE-PROFILES.md) e [exportação CSV](docs/CSV-EXPORT.md).

> **v0.4 — sensores visuais:** LaserScan 2D e câmera por tópico ROS/DDS,
> alternados na mesma área, sem bridge/RTSP. [Uso e limites](docs/SENSOR-VIEWS.md) ·
> [aceite físico curto: 6/6](docs/FINALIZATION-v0.4.md).

Inspecione tópicos ROS 2 pelo celular ou tablet Android, sem precisar abrir um
notebook. O ROS Pocket se conecta diretamente via DDS para mostrar mensagens,
gráficos e logs. Ele é **somente leitura**: não envia comandos para o robô.

[Baixar a última release](https://github.com/lamggm/ros2_pocket/releases/latest) ·
[Guia de uso](docs/USAGE.md) · [Documentação](docs/README.md) ·
[Roadmap e histórico](docs/ROADMAP.md) ·
[Reportar um problema](https://github.com/lamggm/ros2_pocket/issues)

> A v1.3 mantém o alcance de protótipo de bancada. Produção é
> **1.3.0/code13**, não debuggable, com assinatura upstream; DEV é
> **1.3.0-dev/code13** e instala ao lado. Ambos ARM64/hooks OFF.
> O candidato final passou688 casos Java (zero falhas, um skip),309 testes
> Python e revisão independente. Produção passou build/lint/assinatura no host.
> **Nenhum dos APKs finais v1.3 foi instalado ou validado fisicamente nesta rodada.**
> As prévias usam dados sintéticos exclusivos dos testes. Aceites anteriores
> não se transferem a estes bytes; VPN real e RF/IP continuam pendentes.
> [Identidades, evidências e limites](docs/RELEASE-v1.3.md).
> Não use o app como instrumento de segurança ou controle de uma operação.

## Baixar e instalar

1. Abra a [página da última release](https://github.com/lamggm/ros2_pocket/releases/latest) e confira a versão.
2. Em **Assets**, baixe o arquivo `.apk`. Os arquivos “Source code” não são o app.
3. Instale em um Android **9 ou superior**, com processador **ARM64**. O Android
   pode pedir permissão para instalar aplicativos da origem escolhida.

O APK principal aparece como **ROS Pocket**; o APK de bancada como
**ROS Pocket DEV**, com package e assinatura distintos. Para atualizar uma instalação
existente, o certificado precisa ser compatível. Se o Android rejeitar a
assinatura, **não desinstale nem limpe os dados como atalho**: veja as
[instruções de instalação e atualização](docs/USAGE.md#instalar-e-atualizar).

<a id="features-and-limits"></a>
<a id="local-profiles"></a>

## O que dá para fazer

- Encontrar tópicos, filtrar a lista e consultar tipos, endpoints e QoS.
- Explorar o [grafo nó → tópico → nó](docs/ROS-GRAPH.md), destacando conexões por toque.
- Acompanhar até **quatro tópicos** com contadores, idade dos dados e mensagens estruturadas.
- Plotar até **quatro séries numéricas**, com pausa da visualização.
- Reter até **32 amostras por watch/poll** dos campos selecionados; ver descarte local conhecido, sem promessa de captura sem perdas.
- Exportar o histórico retido do gráfico em **CSV**, live ou pausado.
- Ler e filtrar logs de `/rosout`.
- Salvar configuração de conexão, watches, gráficos e sensores em **perfis locais**, transportar um perfil em JSON e aplicar antes de conectar.
- Escolher manualmente o grafo **Humble (GID24)** ou **Jazzy (GID16)**, persistido no perfil.
- Escolher **LAN / Wi-Fi** ou **VPN / Tailscale** com peer explícito, inclusive para o leitor visual.
- Visualizar **LaserScan 2D ou câmera ROS**, alternados no Painel, sem ocupar os watches.

A interface tem quatro destinos: **Painel, Explorar, Grafo e Logs**.
**Conexão** e **Aparência** ficam no cabeçalho. Toque no Explorer inspeciona;
**Monitorar** escolhe explicitamente um dos quatro slots e confirma substituição.
O app usa DDS direto; não exige rosbridge ou um servidor auxiliar.

<a id="connect"></a>

## Primeira conexão

1. Em **Conexão**, escolha **LAN / Wi-Fi** ou **VPN / Tailscale**.
   Em LAN, use a mesma rede; em VPN, mantenha o app incluído na VPN e informe o peer IPv4 acessível.
2. Informe o **Domain ID** do lado DDS ao qual o Android deve se conectar.
3. Escolha o formato do grafo: **Humble (GID24)** ou **Jazzy (GID16)**. O app não detecta a distribuição.
   Em LAN, peer vazio tenta descoberta automática; em VPN ele é obrigatório.
   Toque em **Conectar**. [Detalhes e limites](docs/VPN-DDS.md).
4. Abra **Explorar**, selecione um tópico e toque em **Monitorar** para escolher
   um dos quatro watches. Substituir uma posição ocupada exige confirmação.
5. Volte ao **Painel** para acompanhar os dados ou abra **Logs** para ver `/rosout`.

Se nada aparecer, confira domínio, multicast e QoS no
[guia de conexão](docs/USAGE.md#problemas-de-conexão).

## Compatibilidade

**Humble/Jazzy × Fast DDS/Cyclone DDS** foram exercitados em contêineres host
privados: endpoints, String/Float64, rosout, nós e associações de endpoints.
O formato de grafo é manual, sem fallback: Jazzy usa GID16 e Humble usa GID24
com validação da cauda. Perfis antigos assumem Jazzy sem regravação no load.
As janelas físicas v1.2 incluem bridge existente e ROS Jazzy sintético no Ryzen
via Zenoh/Tailscale; não demonstram as quatro combinações ROS reais pela LAN.
[Matriz histórica](docs/HUMBLE-JAZZY.md) · [Alcance v1.2](docs/RELEASE-v1.2.md).
Domínios mistos, outras distribuições e tipos não exercitados não herdam suporte.

O APK atual é ARM64, usa IPv4 em LAN ou no modo VPN explícito e decodifica uma lista definida de
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
- [Roadmap v0.5–v0.9](docs/ROADMAP.md): entregas e propostas futuras, limites, milestones e issues.

A v1.3 preserva watches, sensores, logs e perfis. A release distribui o DEV
congelado validado no host e a variante produção separada, não substituídos
pelo APK recompilado no CI. O manifesto conserva o HEAD/snapshot original do
build e distingue o commit de consolidação, a promoção documental e o merge.
As releases anteriores permanecem intactas.
