# v0.5 — fechamento delimitado da exportação CSV

[Projeto](../README.md) · [Uso e contrato CSV](CSV-EXPORT.md) ·
[Roadmap](ROADMAP.md#v05) · [Issue #22](https://github.com/lamggm/ros2_pocket/issues/22)

## Entrega

A v0.5 acrescenta **Exportar gráficos em CSV**: uma cópia consistente do histórico
numérico retido, live ou pausado, salva pelo seletor Android. Preserva origem,
clocks separados, gaps e limites; não é rosbag nem captura de todas as mensagens.
O operador autorizou o fechamento e merge após os testes abaixo. A promoção
preserva a implementação já testada; as alterações de fechamento são documentais.

**Não é publicação de release.** O download público permanece na v0.4.0 até uma
autorização própria. Não reconstruir ou substituir o APK físico por um artefato
de CI como se fossem os mesmos bytes.

## Identidade do candidato físico

- Fonte de build/teste: `3d7232aa8fd020f73108fecd968f2942e002cb01`, árvore
  `236051783af64aa32024b4f8453f2c5c77a0dc68`, checkout limpo.
- `com.lamggm.rospocket.dev`, **0.5.0-dev**, versionCode **5**, ARM64, API 28+,
  assinatura debug compatível com o tablet; hooks OFF.
- SHA-256 do APK físico instalado e relido:
  `c4a99bad142ed2b9c5cf19e05fb5db91813b945c181c12dbec3b03ed8b37c8ef`.
- APK host tem assinatura e SHA-256 diferentes. Todos os ZIP entries fora de
  `META-INF/` foram comparados e são byte-idênticos; a assinatura compatível foi
  aplicada sem reconstruir o payload. A identidade do artefato físico prevalece.

## Evidências verificadas

### Host e revisão

- TDD do timestamp por ponto, snapshot, serializador CSV, limites/escaping/proteção
  de fórmulas e integração Activity/seletor/I/O.
- Suíte Java: **418 casos, zero falhas/erros e 1 skip opt-in legado**; lint sem
  erros, com 115 warnings. Build ARM64 aprovado.
- Replay de capturas reais retidas de Fast DDS e Cyclone DDS pelo histórico/view
  produtivos, export CSV e releitura em Python. Replay não é campanha LAN nova.
- Revisão independente estática do snapshot: aprovada sem bloqueadores. O recorte
  de aplicativo foi comparado byte a byte à fonte promovida. CI da PR e da main
  permanece um resultado separado, vinculado à promoção na issue #22.

### Smoke físico CSV — 4/4, mesma rodada

No SM-X400 / Android 16, com produtor DDS nativo separado executado no Android:

1. Amostras numéricas DDS reais recebidas e gráfico físico exibido.
2. CSV live salvo pelo seletor, relido em Python: **58 pontos, 13 gaps**.
3. CSV pausado corresponde à cópia congelada enquanto a recepção avança:
   **36 pontos, 10 gaps**.
4. Cancelar o seletor e retomar com dados novos.

A rodada completa aprovada é independente das tentativas anteriores incompletas,
que foram preservadas. Falhas de provedor/URI, recriação e invalidação do snapshot
foram exercitadas no host, não simuladas como falhas físicas do provedor.

### Recuperação Jazzy/LAN — teste curto separado

A comunicação LAN falhou antes do DDS, com resolução ARP incompleta entre Ryzen e
tablet, inclusive sem ADB local. Após o operador reconectar ambos ao Wi-Fi:

- Ping bidirecional sem perdas e vizinho ARP resolvido.
- ADB pela LAN voltou a conectar.
- Publicador Jazzy/Cyclone DDS no Ryzen, domínio isolado e peers LAN explícitos:
  Pocket conectado, **1 peer e 1 nó ROS**; contagem avançou de **13 para 28** na
  mesma sessão/leitor, com amostras novas e frescas.
- Mesmo APK físico, sem correção no app ou alteração de VPN/firewall pelo agente.

Esse teste comprova recuperação curta da LAN/Jazzy. Não foi repetição do fluxo
CSV inteiro usando Jazzy, nem qualificação de descoberta multicast automática.
A reconexão eliminou o sintoma, mas não identifica definitivamente o componente
Wi-Fi responsável pelo estado anterior.

## Limites e preservação

- O seletor externo pode tirar o app do primeiro plano e suspender DDS pelo
  lifecycle herdado. I/O CSV não bloqueia o worker DDS; não há captura background
  nem promessa de recepção contínua enquanto o seletor está em primeiro plano.
- Até quatro séries, teto configurável de 2048 pontos por série e CSV de 2 MiB.
  Valores são os binary64 já retidos; precisão Int64 perdida não é recuperada.
- Sem novos C++/JNI, permissões, serviços, bridge, RPC ou comandos para robôs.
- Atualização física in-place, UID/configurações preservados. Produtores owned e
  temporários removidos; preferências/settings restaurados e conferidos; app
  parado após os testes. Nenhum novo crash fatal do UID observado no smoke CSV.
- Sem soak de 30 minutos, matriz Humble, LiDAR físico, qualificação extensa de
  RF/IP/reconexão ou estabilidade prolongada. Não iniciar v0.6 automaticamente.
