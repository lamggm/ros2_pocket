# ROS Pocket v1.1 — ROS Graph touch-first

[Uso do grafo](ROS-GRAPH.md) · [Última release](https://github.com/lamggm/ros2_pocket/releases/latest)

## Escopo e autorização

O operador antecipou a antiga proposta v2/issue33 para v1.1 e pediu um grafo
mais intuitivo que a apresentação densa do rqt: toque revela conexões, com
contexto esmaecido. Após implementação/teste DEV, autorizou PR, CI, merge e
release GitHub. Não inclui interrupção Wi-Fi, alteração VPN/firewall, novo
soak, instalação de produção no tablet ou atualização do índice F-Droid.

Nós/tópicos/arestas têm seleção, detalhes, busca/enquadramento e pan/zoom.
Associações são anunciadas via DDS: não comprovam tráfego nem compatibilidade
QoS. Nomes desconhecidos permanecem explícitos; posições e viewport são
estáveis na seleção. Projeção limitada antes de serializar, aviso de visão
parcial e arestas opostas selecionáveis. Veja o contrato de interação acima.

## Fonte e variantes congeladas

Fonte de ambos os APKs: `8ca67853680241404b78fa367022c53dca220a33`, limpa;
snapshot `6bc9883228ce508fc940e067ad79f80795957e620850558e03a88ad0bdf97a7c`.
Mudanças posteriores desta promoção são documentação; não reatribuem builds.

- **Produção:** `ros-pocket-v1.1.0-arm64.apk`, `com.lamggm.rospocket`,
  **1.1.0/code11**, não debuggable, ARM64/API28+, hooks OFF.
  4.908.392 bytes; SHA-256
  `b2ff731daa138ade947fa9e408923ceddf2fe809dc630a4862943289df69ea61`.
  Assinado pela mesma identidade upstream da v1.0, certificado SHA-256
  `c610ab18b97bd70184ffd7069a7205ab465b7aa8b55df98e89f1281fb24f48b4`.
- **DEV físico:** `ros-pocket-dev-v1.1.0-arm64.apk`, `com.lamggm.rospocket.dev`,
  **1.1.0-dev/code11**, debuggable, ARM64/API28+, hooks OFF.
  5.715.515 bytes; SHA-256
  `13239bb3c6d4e7540ff37b96a7553176692a182441e8f6d6ed2e1d2536eda08b`.
  Certificado debug SHA-256
  `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`.

Produção/DEV instalam lado a lado e não substituem um canal com certificado
diferente. F-Droid mantém canais/índice próprios; uma release GitHub não os
atualiza. Não desinstalar/limpar dados para contornar incompatibilidade.

## Verificações executadas

- Wrapper canônico offline na fonte congelada:538 casos Java, zero falhas/erros,
  um skip opt-in RealJniSessionTest;309 Python principais e21 CTests portáveis.
  Suites auxiliares22/18/19 preservadas separadamente, não somadas ao principal.
- Build Debug/Release e lint de ambas as variantes:zero erros/fatais,
  138 warnings por variante. Não anunciado como warning-free.
- Produção:manifesto, versão, assinatura upstream, ABI e capacidade OFF
  inspecionados nos bytes finais; payload ZIP preservado na assinatura e
  bibliotecas nativas iguais às do DEV físico. **Sem smoke físico de produção.**
- **DEV físico11/11:**instalação in-place/identidade, grafo DDS local,
  seleção de nó/tópico/linha, geometria estável, pan sem rolar o pai, zoom pelos
  botões, busca/enquadramento, troca de seção, remoção de nó e desconexão.
  Fixture DDS nativa sintética no próprio Android, com GIDs reais de endpoints
  e anúncios de grafo; não é um produtor ROS rclpy na LAN.
- Capturas reais inspecionadas:contexto esmaecido legível, foco evidente e sem
  rótulos sobrepostos. Pinça de dois ponteiros, Canvas e eventos acessíveis
  exercitados no host; pinça física e TalkBack físico não executados.
- Quatro tentativas incompletas permanecem separadas; uma rodada final completa
  passou11/11 no mesmo APK. Não houve costura de resultados parciais.
- Readback final DEV:APK instalado idêntico ao congelado, UID preservado,
  configurações originais restauradas byte a byte, settings e produção intactos,
  produtores/staging removidos e app parado. Buffer de crash do UID vazio.

## Limites que continuam abertos

O produtor ROS Jazzy/Cyclone no Ryzen estava ativo, mas o Android não confirmou
nós/tópicos pela LAN, mesmo com peer explícito. Não atribuímos causa de rede
sem prova e não alteramos VPN/firewall/Wi-Fi. **ROS real entre dispositivos,
LAN, RF/IP e robô não foram qualificados.** A issue31 permanece aberta.
O soak histórico v0.9 não se transfere a estes APKs.

A release distribui `production-artifact.json`, `dev-artifact.json`,
`acceptance.json` sanitizado e `SHA256SUMS` junto dos dois APKs. A promoção
exige CI do head da PR e pós-merge, igualdade de árvores e download público
com hashes conferidos; os links/resultados ficam nas notas da release.
Recompilações de CI não substituem os bytes congelados/testados.
