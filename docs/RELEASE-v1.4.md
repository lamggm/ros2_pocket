# ROS Pocket v1.4 — CAD offline, TF passivo e painel editável

## Entrega autorizada

O operador autorizou promoção, merge, release GitHub e atualização do repositório
F-Droid independente existente. Isso não autoriza instalar produção no tablet,
interromper rede, modificar robôs ou publicar CADs privados. A inclusão no catálogo
oficial f-droid.org é externa e não é alegada por esta atualização.

## Funcionalidade

- Descrição URDF observada por DDS, XML limitado e sem resolução de rede/entidades.
- Assets offline schema1/LOD ou schema2/full vinculados ao nome, SHA256 XML exato
  e URI literal. Cores, unidades, buffers indexados e limites preservados.
- CAD full em GLES2, TF observado opt-in com frescor/autoridade e frame fixo,
  gestos e tela cheia. Giro cosmético não representa movimento do robô.
- Painel com sete cards redimensionáveis/reordenáveis, rascunho, Salvar/Cancelar,
  persistência própria e resultado incerto explícito em falha pós-fechamento.
- C++/Cyclone DDS/JNI, quatro watches, um leitor visual alternado, perfis e temas
  preservados. Somente leitura; sem comandos, RPC ou parâmetros no robô.

[Uso](UI-v1.4.md) · [CAD/TF](CAD-TF-VIEWER.md) ·
[Assets](ROBOT-ASSETS.md) · [Painel](DASHBOARD-LAYOUT.md).

## Proveniência

Commit de aplicação: `7be5839cc4ca9a0b44fef88f4009d4c4914d029e`. Seus386arquivos são idênticos à candidata
revisada e testada, snapshot
`ba2eb344f5724fdb523ca1af14cf05e58a95d23771acd1286c9369f0479dba4d`.
A promoção inicial alterou apenas documentação e descrições da loja. Uma correção
posterior ficou restrita ao produtor da fixture nativa TF: ele agora usa loopback
explícito como o receptor, sem alterar o app, assertions ou prazos. O CI inicial
[43747008570](https://github.com/lamggm/ros2_pocket/actions/runs/43747008570) falhou
nesse teste; o reproducer rejeitou o original com uma segunda interface e aceitou
a correção com o mesmo oracle. O APK do CI não substitui os artefatos congelados;
merge/tag e IDs do head final constam nas notas da release.

- **DEV físico:** `ros-pocket-dev-v1.4.0-arm64.apk`,
  `com.lamggm.rospocket.dev`,1.4.0-dev/code14,debuggable,ARM64,hooks OFF.
  8435259bytes; SHA256 `7275299218aad34e8cad5fba4784c3847a14a0d115d0a89e18315895591d8247`.
  Certificado debug compatível `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`.
  Preserva o HEAD original de build `e87ff0eb3a7331b7ab0a9955d3f076bc8e84eb80`,
  dirty=true e o snapshot acima. Consolidação em commit não é um rebuild.
- **Produção host:** `ros-pocket-v1.4.0-arm64.apk`,
  `com.lamggm.rospocket`,1.4.0/code14,não debuggable,ARM64/API28+,hooks OFF.
  5240168bytes; SHA256 `4d8f3549c883092c7565f20abd80946d6319856ed8f4c91c9347d52c8e732e81`.
  Certificado upstream `c610ab18b97bd70184ffd7069a7205ab465b7aa8b55df98e89f1281fb24f48b4`.
  Build de release e lintRelease em worktree limpa do commit de aplicação,
  com as bibliotecas nativas exatas da candidata DEV. Assinatura/payload conferidos.
  **Produção não foi instalada/testada fisicamente nesta entrega.**

## Verificação e alcance

- Build canônico da candidata:777casos Java,zero falhas/erros,um skip opt-in
  `RealJniSessionTest.realJniLeaseFailureReceiptPollRecoveryAndSurvivors`.
  Não é contabilizado como executado.316testes Python e28CTests portáveis;
  validadores auxiliares não são somados novamente à população Python.
- Revisões independentes delimitadas dos incrementos e correções aprovadas;
  falhas históricas/replays anteriores preservados, não reclassificados.
- DEV físico: importação SAF/cache, malhas completas, gestos, fullscreen, TF de
  bancada, resize/reorder/Salvar/Cancelar, retorno e restart/perfis/layout.
- Rodada adicional observou uma simulação software-only ROS Jazzy real pela LAN:
  quatro leituras frescas, scan360pontos, CAD1904642faces/seis malhas, TF observado,
  gráfico600pontos e restauração fria do mesmo APK. Não qualifica hardware físico.
- Não se transfere soak ou FPS/bateria de APKs antigos. Calibração, fabricação,
  câmera nessa simulação, navegação física, ausência/retorno real de VPN(issue38)
  e interrupção RF/IP(issue31) permanecem fora do aceite desta entrega.
- Em validação final, o tablet entrou posteriormente em suspensão/bloqueio;
  nenhuma captura/toque foi feito através do bloqueio e a política de energia
  não foi alterada. Isso não apaga o aceite em primeiro plano nem prova recepção
  enquanto suspenso.

## Distribuição

[GitHub Latest](https://github.com/lamggm/ros2_pocket/releases/latest).
A release inclui somente dois APKs, metadados sanitizados, acceptance.json e
SHA256SUMS. Sem logs/endereços/seriais privados, chaves ou modelos CAD.

O repositório F-Droid **independente** usa os mesmos bytes de produção e preserva
sua chave de índice; não é inclusão no catálogo oficial. URL de adição:

`https://lamggm.github.io/ros2_pocket/fdroid/repo?fingerprint=2F2F3B87A332250B8F05CBB3072383F7A2067DEFF90CB828543CCCF961F75F86`

Atualizar GitHub não atualiza esse índice automaticamente. Publicação só é
concluída após readback do merge/CI/release e verificação de downloads/indexes
v1/v2 com fingerprint fixo. IDs/resultados de publicação constam nas notas da release.
