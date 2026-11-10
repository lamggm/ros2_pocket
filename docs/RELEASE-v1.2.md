# ROS Pocket v1.2 — VPN/DDS e interoperabilidade com bridge existente

[Configurar LAN/VPN](VPN-DDS.md) · [Grafo](ROS-GRAPH.md) ·
[Última release](https://github.com/lamggm/ros2_pocket/releases/latest)

## Escopo de fechamento

Após os testes, o operador autorizou merge e a publicação correspondente.
A v1.2 é uma entrega proporcional de protótipo de bancada: modo LAN/VPN,
configuração imutável até o sensor, migração de perfis schema4 e metadados dos
próprios leitores para interoperar com uma bridge ROS/Zenoh existente.
Preserva quatro watches, um leitor visual, formatos manuais Humble/Jazzy e
somente leitura. Não incorpora Zenoh nativo nem altera o runtime do robô.

Ausência/retorno físico da VPN continua pendente na issue38; não fechar essa
issue como se o requisito tivesse passado. RF/IP permanece na issue31.
Merge/publicação não autorizam instalação de produção, rede, novo soak ou F-Droid.

## Fonte e bytes congelados

Commit de aplicação: `da1281d2c88aff3b290d1731e70ff5d454fb4424`.
Seu snapshot de conteúdo `d365e6440a71067291569da58dfe7e256d3647089c9856efe160718f403d1f00`
é exatamente o usado no build DEV físico, originalmente sobre base
`5d1eaefd5962caa5f029ff10576ab343a4de0d42` com alterações locais.
O commit consolida esses bytes; não reescreve a proveniência histórica do APK.
Documentação/promoção posteriores não mudam a identidade dos builds.

- **Produção:** `ros-pocket-v1.2.0-arm64.apk`, package `com.lamggm.rospocket`,
  **1.2.0/code12**, não debuggable, ARM64/API28+, hooks OFF.
  4.973.928 bytes; SHA-256
  `5cafad3663b1fd6dc5a6d2bb527c2ee81a9d92f0331d5eb37a3cd5a930a2d300`.
  Certificado upstream SHA-256
  `c610ab18b97bd70184ffd7069a7205ab465b7aa8b55df98e89f1281fb24f48b4`.
- **DEV físico:** `ros-pocket-dev-v1.2.0-arm64.apk`, package
  `com.lamggm.rospocket.dev`, **1.2.0-dev/code12**, debuggable, ARM64/hooks OFF.
  8.111.675 bytes; SHA-256
  `6f97bc44920ef41aad303aed3d1a82093b12d8f1cf20a488f21a1c513e8f1759`.
  Certificado debug SHA-256
  `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`.

O DEV é reutilizado sem recompilar. Produção foi construída/lintada no host a
partir do commit de aplicação limpo e assinada com a identidade upstream existente.
Os conteúdos ZIP não relacionados à assinatura foram preservados; as bibliotecas
nativas são byte a byte iguais às do DEV físico. Isso **não transfere o smoke
físico ao APK de produção**, que não foi instalado nem exercitado no tablet.

## Evidência por janela, sem somar campanhas

- **Host do candidato:** 547 casos Java, zero falhas/erros e um skip opt-in;
  309 testes Python principais e26 CTests portáveis. Suites auxiliares não
  somadas ao total principal. Revisões estáticas delimitadas sem bloqueadores.
- **Produção host:** build Release, lintRelease com zero erros/fatais e137 avisos;
  manifesto/assinatura/ABI/hooks OFF e igualdade das bibliotecas nativas conferidos.
- **Runtime existente, APK DEV real:** descoberta de247 tópicos, grafo parcial
  (160 elementos/146 conexões), telemetria113→116 com frescor e LaserScan em
  `lidar_link`, 3226 ranges, 2356→2237 pontos em capturas separadas por5,44s.
  As rodadas gerais ficaram incompletas por erros do observador. Essa janela
  comprovou recepção, não um passe contínuo nem o desenho integral do scan.
- **Bancada Ryzen análoga:** ROS Jazzy sintético em domínio DDS loopback separado,
  duas bridges Zenoh1.10.2 e outro domínio DDS exposto somente pela Tailscale ao
  tablet. Não é um teste DDS direto que contorne o enlace Zenoh.
- **Leitores, subconjunto auditado de rodada incompleta:** identidades distintas
  após substituição; remoção com estado inativo/count0; recriação com amostras novas.
- **Rodada final direcionada10/10 PASS:** recepção, canvas completo480px e pontos
  mudando, parar/limpar o sensor enquanto a telemetria continua, restaurar scan,
  desconexão com canvas vazio e reconexão com sessão nativa3→5, telemetria6→16 e
  novo scan completo. A bridge registrou declarações/remoções dos interesses.
  Essa rodada não reclassifica as anteriores como uma campanha contínua13/13.
- **Cleanup independente:** bytes/UID do DEV, produção preexistente, configuração
  original e settings conferidos; DEV parado. Três containers temporários próprios
  removidos e sua ausência relida por IDs imutáveis. Logs/configurações privados
  não são anexados à release.

## Limites preservados e publicação

A bancada usa dados sintéticos, não substitui um novo teste integral de lifecycle
no robô. Não houve remoção real da VPN, interrupção Wi-Fi/IP, nova qualificação
LAN/multidistribuição física, soak deste APK ou teste físico de produção.
Os resultados históricos de versões anteriores não se transferem a estes bytes.

A release distribui os dois APKs, `production-artifact.json`, `dev-artifact.json`,
`acceptance.json` sanitizado e `SHA256SUMS`. CI da PR, igualdade da árvore mergeada,
CI pós-merge e download dos assets com comparação de hashes são gates separados
da evidência física acima. Seus identificadores são registrados nas notas públicas.
O APK recompilado pelo CI não substitui o DEV congelado.
F-Droid mantém índice/assinaturas independentes e não é atualizado por esta release.
