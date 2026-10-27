# ROS Pocket v0.2.0 — notas de versão (DRAFT, não lançado)

Escopo autorizado: **protótipo de bancada**, somente leitura, com DDS direto.
Host validado e teste curto de gráfico no tablet concluído. Qualificação física
extensa, Wi-Fi/IP e endurance ficam explicitamente não validados no backlog;
não bloqueiam esta entrega limitada. Promoção para `main`, tag, release e loja
continuam decisões separadas. Estado: [FINALIZATION-v0.2.md](FINALIZATION-v0.2.md).

## Identidade e arquitetura

- DEV: `com.lamggm.rospocket.dev`, versão `0.2.0`, code `2`.
- Android Java → JNI/C++ → Cyclone DDS 0.10.5; ARM64, minSdk 28/targetSdk 35.
- Sem bridge/servidor auxiliar nem publicação de comandos para robôs.
- Ordinary/default/nondebug com hooks OFF; diagnóstico ON somente por opt-in.
- APK/JSON do build identificam fonte, hash, ABI e certificado reais.
  A assinatura debug do CI pode diferir da local; nunca desinstalar para contornar isso.

## Funcionalidades na fonte reconciliada

- Wrapper e bootstrap/build verificáveis; CI hospedado e fixtures DDS isoladas.
- Diagnóstico de conexão, Explorer de tópicos/nós e QoS anunciado.
- Console `/rosout` independente, filtros, pausa, clear e buffer limitado.
- Primitivos e Vector3/Quaternion/Twist/Pose/Odometry estruturados.
- Quatro watches independentes, substituição/remoção e QoS por slot.
- Gráficos tipados de snapshots: até quatro séries, janela de 30 s/600 pontos,
  pausa visual e lacunas para NaN/Inf; sem reconstruir mensagens não amostradas.
- QA read-only e ferramentas de teste/qualificação separadas do uso normal.
- Correções de compatibilidade dos observadores Android, navegação local das
  séries e orçamento de execução do harness físico; freshness não foi relaxada.

## Verificação e limitações

O código `496b9bb9053f915f9e249de675dec3f44d25fc23` passou nove gates locais,
293 testes Python sem skips, testes Java/Canvas, JNI host separado, lint/build
e DDS isolado nos dois RMWs. O JNI opt-in é intencionalmente skipped na suíte Java
comum e foi exercitado em seu gate próprio. O CI do head vigente é consultável
na [PR #12](https://github.com/lamggm/ros2_pocket/pull/12); essas verificações
não são aceitação do app no Android físico.

O DEV ordinary do head documental `30008c9` passou build local final e o CI
Verify 40407163597. Nesse CI houve 287 Python e seis GraphProvenance skipped por
cache ausente; os seis passaram localmente. O APK ARM64 hooks OFF, SHA-256
`d1577e6a484bda95d33d6f9c49d254fd700b95760468ff39d1378ccbd2c339c8`,
foi atualizado in-place no tablet preservando UID e preferências. Passaram duas
séries finitas, pausa com pixels idênticos/intake avançando e retomada com pixels
novos; recortes inspecionados e cleanup/configuração restaurados por readback.
A fixture usou Fast DDS com peer explícito: não comprova descoberta multicast
automática. Evidência permanece vinculada a esse APK/SHA, não a commits futuros.

Qualificação física completa nos dois RMWs, Wi-Fi/IP, soak e aceite extenso
continuam pendentes, mas não são bloqueios do protótipo por decisão explícita.
Não há outra campanha automática nem promoção de resultado histórico para PASS.

IPv4 na mesma LAN Wi-Fi; AP/multicast/VPN/firewall podem bloquear descoberta.
SROS2, sub-redes roteadas, versões antigas do grafo, outros tablets e páginas
físicas de 16 KiB não estão qualificados. CDR acima de 64 KiB é rejeitado;
limites por amostra/ring não representam teto total de memória do processo.
Há avisos cosméticos/localização de lint conhecidos.

Sem parâmetros/RPC, serviços/actions, publicação/teleop, rosbag, imagens,
mapas/TF/RViz ou tipos dinâmicos customizados. Sem release/loja implícitos.
O plano estendido e seus critérios continuam inalterados para quem reivindicar
qualificação completa. A entrega limitada foi autorizada com as restrições acima;
relatórios extensos não ready continuam não ready, sem PASS inventado.
