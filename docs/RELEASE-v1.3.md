# ROS Pocket v1.3 — workspace nativo, temas e grafo circular

[Interface e contratos](UI-v1.3.md) · [Uso](USAGE.md) ·
[Última release](https://github.com/lamggm/ros2_pocket/releases/latest)

## Entrega e limites

A v1.3 reorganiza a interface Android nativa em **Painel, Explorar, Grafo e
Logs**, com aparência **Sistema/Claro/Escuro**. Conexão permanece no cabeçalho.
Painel mantém quatro watches e um leitor visual; Explorer dividido no tablet;
Logs compacto com inspetor do evento selecionado; grafo circular por namespace
com física visual local, drag/pan/zoom, pins e detalhes.

O operador autorizou fechar a PR e fazer o merge. Publicação GitHub acompanha
esse fechamento; instalação, parada da demo externa, rede/firewall, produção
no tablet e atualização de F-Droid não estão incluídos. CI da PR, igualdade da
árvore mergeada, CI pós-merge e download/hash dos assets são gates distintos;
seus IDs são registrados nas notas públicas da release.

**Os dois APKs finais desta entrega são qualificados no host; nenhum foi
instalado ou validado fisicamente nesta rodada.** Prévias Android usam fixtures
sintéticas somente nos testes. A demo externa, a webcam física e os APKs
intermediários não transferem aceite ao candidato final. Não se declara nova
medição ARM64 de FPS/consumo, soak, teste de VPN/RF ou matriz física de redes.

## Fonte e identidade dos bytes

Commit de aplicação: `a358dbc072a0e04f23e3dbd54203ead4f2e11965`.
Seus329 arquivos são byte a byte o snapshot revisado/testado:
`117269862835fafe080afe8b37bf382c39b2a6fd5d57382d3044953597d990f6`.

Os metadados preservam o HEAD original de build
`2f07f010d757fc844c4136a57f47672ec87d90c1` e `dirty=true`: consolidar o conteúdo
não significa reconstruir os APKs a partir do novo commit. O head posterior de
publicação altera somente documentação, com conteúdo/modos de aplicação e
testes preservados. O APK do CI não substitui os assets congelados.

- **Produção:** `ros-pocket-v1.3.0-arm64.apk`, package
  `com.lamggm.rospocket`, **1.3.0/code13**, não debuggable, ARM64/API28+,
  hooks OFF; **5.051.752 bytes**.
  SHA-256 `8f2c06d21ddcc08e8fbd49f746a884dd0582126a7cd6ac6b97f5e6cccd18c67a`.
  Certificado upstream SHA-256
  `c610ab18b97bd70184ffd7069a7205ab465b7aa8b55df98e89f1281fb24f48b4`.
- **DEV:** `ros-pocket-dev-v1.3.0-arm64.apk`, package
  `com.lamggm.rospocket.dev`, **1.3.0-dev/code13**, debuggable, ARM64,
  hooks OFF; **5.903.931 bytes**. Mesmos bytes entregues no chat.
  SHA-256 `8616209be24444ae0200a19514732d3fb864680410b8471abf39f4cd2b2bafad`.
  Certificado debug compatível SHA-256
  `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`.

Manifesto, assinatura, ABI, hooks OFF e conteúdo ZIP não relacionado à assinatura
foram verificados. Bibliotecas nativas de produção são byte a byte as do DEV;
C++/Cyclone/JNI e RosGraphModel permanecem inalterados pela UI. Isso comprova a
identidade desses componentes, não execução física dos APKs.

## Verificação proporcional

- Candidato final: **688 casos Java, zero falhas/erros e um skip opt-in**;
  **309 testes Python**, sem somar suites sobrepostas.
- Build Debug/Release e lintDebug/lintRelease aprovados: zero erros/fatais,
  **164 avisos em cada variante**. Prévias Canvas/Views Android em telefone e
  tablet, claro/escuro, inspecionadas; não são capturas de um aparelho.
- Revisão independente delimitada de integração UI/temas/lifecycle e ownership
  de readers sem bloqueador; revisão do grafo também aprovada após correção.
- Primeira revisão do grafo circular bloqueou dois casos com mais de24 namespaces:
  ID acessível ausente no grupo filtrado e pin perdido numa atualização.
  As regressões reproduziram NPE/perda de pin antes da correção e passaram
  depois. O candidato rejeitado e seu resultado não são reclassificados como PASS.
- A população de geometria permanece limitada a24 grupos; identidades e pins
  acompanham a associação real dos até160 vértices, independentemente do recorte.

## Contratos preservados

Aparência recolore Views existentes sem recriar Activity, reconectar DDS ou
substituir leitores. Inspecionar tópico não inicia leitura; Monitorar escolhe
slot e exige confirmação antes de substituir. Fences de configuração/sessão e
ownership do pedido impedem callbacks antigos de substituir escolhas novas.

Pausar física só pausa coordenadas; descoberta continua. Pausar Logs congela a
projeção, não o reader; o inspetor conserva a amostra selecionada e sua idade
naquela amostra. Distâncias/forças do desenho não são grandezas do robô, e setas
são associações anunciadas, não prova de tráfego ou compatibilidade QoS.

**Namespace não é pacote.** Apenas nomes absolutos válidos de nós/tópicos são
agrupados; o backend não fornece associação de pacote. A envoltória externa
“Topologia ROS” não é uma entidade descoberta. Pacotes fake do protótipo não
foram embutidos no APK. Em cenas densas, o texto sem espaço não é pintado nessa
escala; zoom, inspeção e identidade acessível permanecem disponíveis.

Canais produção/DEV/F-Droid têm assinaturas e processos distintos. As issues
[38](https://github.com/lamggm/ros2_pocket/issues/38) (VPN real) e
[31](https://github.com/lamggm/ros2_pocket/issues/31) continuam pendentes.
A release contém os dois APKs, `production-artifact.json`, `dev-artifact.json`,
`acceptance.json` sanitizado e `SHA256SUMS`; não inclui configurações privadas,
capturas físicas, chaves, credenciais ou logs da bancada.
