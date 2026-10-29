# Fechamento v0.4 — sensores visuais

## Resultado e alcance

Escopo funcional concluído: **LaserScan 2D e câmera ROS/DDS alternados** na mesma
área do Monitor. DDS direto, somente leitura, sem RTSP/bridge/TF/mapa. Watches,
gráficos, rosout e perfis preservados; o leitor visual tem orçamento próprio.
[Uso, tipos e limites](SENSOR-VIEWS.md) · [escopo original](ROADMAP-v0.4.md).

O operador autorizou merge na main e release após o smoke físico. A release
v0.4.0 é um **protótipo de bancada**, não certificação de estabilidade/produção.
Os critérios extensos herdados não foram dispensados nem convertidos em PASS.

## APK distribuído: mesmos bytes do tablet

- Package: `com.lamggm.rospocket.dev`.
- Identidade interna: **0.4.0-dev**, versionCode **4**.
- ARM64 / Android API28+, target35, assinatura **debug v2**, hooks OFF.
- SHA256 físico/instalado/pull-back:
  `598fe35b3cfc91aeeea2991c702012f55e25a2ff407a29045d12b8023dbcac6f`.
- Certificado SHA256:
  `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`.
- A release não recompila o APK para alterar a etiqueta interna. Quem já recebeu
  este candidato físico tem os mesmos bytes. O APK do CI não substitui este asset.

A fonte original foi construída numa worktree local baseada em
`c55fbb325130c090b3c6df0ebab85d56c22949ee`, ainda com alterações não commitadas.
O snapshot host testado foi
`b68fd7dde60aa8792e4a70ca4dc147d535c8cb7bbeed8f4dcea8569680efcc0f`.
Para o update físico, a assinatura default do Xeon era incompatível; foi
reutilizado o signer existente do Ryzen. **Todos os ZIP entries não-META-INF**
foram comparados e ficaram byte-idênticos ao candidato host. Somente assinatura
mudou; o hash host antigo não é usado como identidade do APK físico.

A árvore publicada incorpora, após o smoke, apenas documentação e ligação do
replay real de sensores aos entrypoints CI, sem alterar a implementação produtiva
nem o APK congelado. O manifesto da release distingue essa reconciliação do
snapshot original; não apresenta o binário como rebuild de um commit inexistente.

## Host e revisão

- Wrapper integrado offline, portáveis/sessão, lint e build ARM64 aprovados.
- Java host: **408 testes**, **406 passaram**, zero falhas/erros, dois skips
  opt-in legados (PlotDdsReplayTest e RealJniSessionTest) naquele build.
- **58 testes de sensores**, sem skips, incluindo DDS real → decoder → Canvas
  nativo host, com pacotes recebidos nos dois RMWs.
- Lint: zero erros, **108 warnings**, sem alegar relatório sem avisos.
- CTests portáveis/sessão: **18/18**. Regressões DDS afetadas aprovadas em
  Fast DDS e Cyclone DDS, incluindo scan, RGB/PNG fragmentados >64KiB,
  rejeição/recuperação e coexistência com telemetria/rosout.
- Revisão nativa aprovada; implementação nativa produtiva inalterada desde ela.
- Revisão Java bloqueou ABA, teardown, fila UI e retry; correções RED/GREEN.
  Uma segunda revisão detectou decode antigo substituindo limpeza pendente;
  regressão determinística e correção aprovadas na revisão final, sem bloqueadores.

Para o CI de integração, `tools/test.sh --fixture` agora extrai os pacotes reais
scan/image/compressed de cada container/RMW e `host_campaigns.py` passa a raiz ao
replay SensorCanvasTest. Isso evita um novo skip no gate integrado; não afrouxa
seu allowlist. Workflows/credenciais/permissões não são alterados. O resultado
remoto do CI pertence ao SHA efetivamente publicado, não é deduzido deste resumo.

## Smoke físico curto: 6/6 na mesma rodada

SM-X400 / Android16, webcam USB real w300 no host autorizado, JPEG **640×480 a
5FPS**, publicador ROS Jazzy/Cyclone DDS e peer LAN explícito. Scan é sintético.

1. Update in-place com mesmo signer/UID; configuração privada preservada.
2. Telemetria real do publicador owned chegando ao Watch1.
3. Webcam renderizada e frames novos, enquanto a telemetria avança.
4. Pausa só da publicação da câmera → OBSOLETO/atenuação → retomada com dados novos.
5. Scan com 360 pontos/360 ranges, grade/origem/escala, sem câmera sobreposta;
   retorno pelo botão Câmera com frame novo.
6. Parar limpa o desenho; Home invalida foreground; retorno recebe frames novos.

Capturas físicas foram inspecionadas; seis exports QA válidos comprovaram
avanço do Watch1 de 7 para 466 com identidade de sessão/leitor preservada antes
de Home. Isto **não é nova qualificação física dos quatro watches**. O frame_id
sequenciado da fixture foi diagnóstico, sem semântica TF.

A rodada final passou independentemente. Tentativas anteriores incompletas por
observador UIAutomator sem idle/setup OCR/TSV ficaram preservadas como falhas,
com cleanup, não somadas para fabricar uma rodada aprovada. No preview ativo,
capturas com foco/package e OCR substituíram apenas a observação live; não houve
patch produtivo para satisfazer o observador.

## Cleanup e limites

Readback posterior confirmou: publicador owned removido, FFmpeg encerrado e
webcam liberada; app parado; APK/UID conferidos, preferências/settings restaurados,
sem novas entradas de crash do UID observadas. A v0.4 permanece instalada.
Logs, imagens da webcam, configurações privadas, endereços e chaves não são assets
públicos; este resumo é sanitizado.

Não validados por este fechamento: soak/endurance, RF/Wi-Fi/IP-change, Humble,
multicast automático desta rodada, LiDAR físico, Image RGB em câmera física,
matriz completa de encodings/câmeras/dispositivos. CSV/gravação/áudio/teleop,
serviços/actions/parâmetros e TF/mapa permanecem fora do escopo.
