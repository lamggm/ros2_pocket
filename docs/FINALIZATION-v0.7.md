# v0.7 — fechamento delimitado de lotes numéricos

[Projeto](../README.md) · [Contrato e budgets](NUMERIC-BATCHES.md) ·
[Issue #24: promoção, CI e publicação](https://github.com/lamggm/ros2_pocket/issues/24)

## Entrega

Lotes nativos/JNI com somente os campos numéricos selecionados, consumidos pelo
histórico/CSV no worker existente. Latest/inspeção, quatro watches, sensor visual,
rosout e perfis continuam separados. Não é rosbag nem garantia sem perdas.

- Até 32 amostras por watch, quatro campos globais e 16 KiB extras de JSON/poll.
- Registros nativos fixos de até 64 bytes: 8192 bytes de registros para quatro watches;
  paths/objetos/alocador são separados, não uma promessa de RSS total.
- Preservados sequência de intake, receive-monotonic e timestamp DDS. Relógios de
  recepção coincidentes não eliminam sequências diferentes nem gaps NaN/Inf.
- Saturação local retém o mais recente; contador cumulativo por reader aparece no
  gráfico e no CSV schema2. Não é contagem universal de perdas DDS/rede.
- Quatro séries, 600 pontos/30 s continuam limitados; a 50 Hz, o teto de pontos
  encurta a duração retida. Pausa e exportação usam cópias imutáveis.

## Fonte e APK físico

- Fonte limpa: `dc5e7725041524e923229fa2d66eed1e5c81fa75`.
- Árvore: `a51de63093fb5dc856f296791852f52b6e1608dc`.
- Snapshot de conteúdo também exercitado nas fixtures:
  `a691529bb8cc788f04291b36c2f36e4dc4957ad64109a45359645b3eb9c56cb2`.
- `com.lamggm.rospocket.dev`, **0.7.0-dev**, versionCode **7**, ARM64/API28+,
  debug v2 de bancada compatível com a instalação anterior, hooks **OFF**.
- SHA-256 do APK físico a distribuir:
  `2437dfbd53a55618a17680874e1e9cfa08ecc978e912808db9ac7ee57fba9b88`.
- O build host inicial usa outro certificado debug e não foi instalado.
  Reempacotar com o signer autorizado preservou todos os entries ZIP fora de
  `META-INF/`; não houve recompilação/substituição da aplicação para o tablet.
- Os dois ELFs têm LOAD alignment `0x4000`; o tablet não qualifica dispositivos
  com páginas de 16 KiB. Não publicar material privado de assinatura.

Documentação de promoção pode avançar depois desse commit. Sua igualdade com a
fonte física deve ser verificada em todos os arquivos não documentais. CI gera
outro APK/debug signer: **não substituir os bytes físicos pela recompilação CI**.
A issue #24 registra PR, SHA, CI pós-merge e release; esse documento não antecipa
aprovação de checks ainda pendentes.

## Host e revisão

- **490 testes Java, zero falhas/erros, um skip opt-in legado** de RealJniSession.
- Lint: **zero erros, 121 avisos**; build ARM64 ordinary/offline aprovado.
- Suite Python principal: **296 testes**, mais os controles de registro de gates;
  20 CTests portáveis, incluindo Linux JNI real e burst DDS com overflow.
- Cada RMW Jazzy executou 23 fixtures e o rosout first-burst separado: Fast DDS e
  Cyclone DDS em containers `--network none`, sem LAN/robô/tablet.
- Campanha integrada: quatro watches/rosout, três mutantes esperados mortos sem
  erros de setup, fases DDS com NaN/±Inf, gap/retorno, source reset/jump e janela;
  replay de lotes reais pelo controller, Canvas nativo host e CSV sem skip.
  Pedidos da referência da fixture são conferidos contra o controller real.
- Baseline antes do código, 50 Hz: 200 tomadas → 8 latest snapshots. Candidato:
  200 amostras transportadas, zero descarte local nessa rodada.
- Quatro watches nativos a 50 Hz, processos separados: **800 enviados/tomados/
  transportados, zero descarte local**; poll de 500 ms, maior duração observada
  2,673 ms e até 4110 bytes adicionais. Medição host, não latência/CPU Android.
- Sob saturação de um watch a 100 Hz: 400 tomadas, 257 transportadas e 143
  descartes locais. A 500 Hz: 2000 enviadas, 517 tomadas, 261 transportadas e 256
  descartes locais. A diferença enviado/tomado não localiza uma perda de rede.
- Revisão independente inicial encontrou perda de um gap quando dois tempos de
  recepção coincidiam. RED reproduziu 3 amostras → 2 pontos; o Canvas também
  desenhou o segmento proibido. Correção/replay/CSV/Canvas passaram e a
  re-revisão independente aprovou o recorte sem bloqueadores.
- Scanner: fonte revisada não vazia e histórico completo sem leaks encontrados.

A primeira rodada de fixtures foi interrompida por esse bloqueador e permanece
**INCOMPLETA**. Seu container foi removido com readback. A segunda rodada passou
completa, incluindo igualdade dos snapshots de fonte antes/depois; não houve
soma de passes entre rodadas.

## Smoke físico direcionado — 4/4

Uma rodada completa no **SM-X400 / Android 16**, no APK acima, com um produtor
DDS nativo separado no próprio Android, usando source/IDL inalterados e os bytes
ddsc exatos do candidato. Não é uma nova matriz Jazzy/LAN física.

1. Intake real e gráfico em lotes excedendo a projeção latest de 2 Hz; ring de
   600 pontos observado. O produtor foi solicitado a 50 Hz; taxa sobre o span de
   *tomada* não é medição da frequência da fonte ou chegada ao rádio.
2. CSV live salvo pelo seletor Android e lido por Python: **600 pontos, 150 gaps**,
   schema2/18 colunas, identidade, clocks e contador local preservados.
3. CSV pausado: **600 pontos, 150 gaps**, correspondentes à cópia congelada, com
   intake independentemente avançando antes de abrir o seletor.
4. Cancelamento do seletor e retomada com intake/gráfico novos.

Imagem real inspecionada: curva/gaps, contador local zero naquele instante e
controles legíveis, sem clipping impeditivo. Pausa/CSV foram verificados por
observações e arquivo; não inferir endurance a partir da imagem.

Instalação in-place sem uninstall/clear data; UID e preferências preservados.
Readback final confirmou APK exato, app parado, configurações por hash,
orientação/timeout originais, saída zero do produtor e remoção de diretório/
CSVs temporários. O stay-awake AC temporário foi restaurado ao **original 0**,
com readback efetivo e segurança do keyguard preservada. Os 112 arquivos da
rodada transferida foram conferidos contra hashes/tamanhos remotos privados.

## Limites que continuam abertos

Sem nova campanha RF/IP, Humble, dispositivos adicionais, soak/bateria ou
qualificação extensa. Os validadores históricos mais estritos de timestamps
não foram relaxados; qualificação versionada de séries com relógios coincidentes
exige trabalho próprio. Perfis não persistem amostras. A v0.8 não foi iniciada.
Merge deve ser seguido por release verificada, usando exatamente este APK;
releases e tags anteriores permanecem intactas.
