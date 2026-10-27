# ROS Pocket v0.3 — candidato de bancada e aceite físico pendente

## Estado e escopo

A preparação sem tablet produz um **candidato**, não uma versão fisicamente
aceita. O operador autorizou PR/CI, integração em `integration/v0.3`, documentação
e APK candidato. Não autorizou instalação nesta etapa, promoção para `main`,
tag, GitHub Release, loja ou assinatura de produção.

- Navegação: [PR #15](https://github.com/lamggm/ros2_pocket/pull/15), merge
  `de3c254`, CI 40537386529; smoke físico delimitado 11/11 na fonte `a86cf7d`.
- Perfis: [PR #16](https://github.com/lamggm/ros2_pocket/pull/16), merge
  `e655da3e8017416adb76e0aafb3771ed3057dbf0`, CI
  [40569878837](https://github.com/lamggm/ros2_pocket/actions/runs/40569878837)
  aprovado no head `d0b64c8`, com árvore integrada idêntica à revisada/testada.
  A validação local desse head teve 348 testes Java aprovados, dois skips opt-in,
  295 Python e 14 CTests aprovados, lint e APK ordinary aprovados.
- Este incremento só altera identidade de versão, sua regressão de BuildConfig
  e documentação. Não altera JNI/DDS, permissões, limites, políticas de sessão,
  workflows ou thresholds herdados.
- **Smoke físico dos perfis e do candidato integrado: PENDENTE.** O smoke da
  navegação conserva o seu SHA/APK; não é aprovação física do novo candidato.

## O que muda na v0.3

- Quatro seções persistentes: Conexão, Tópicos, Monitor e Logs, sem uma nova
  sessão DDS e sem recriar readers ao trocar de seção.
- Perfis locais nomeados: domínio, peer opcional, quatro slots tópico/tipo/QoS
  e até quatro campos numéricos de gráfico. Somente configuração é persistida.
- Selecionar, salvar e aplicar são ações diferentes. Aplicação somente
  desconectado; Conectar continua explícito. Reinício com catálogo existente
  restaura a última aplicação desconectada, nunca telemetria antiga como live.
- Escrita atômica e limitada; corrupção/schema desconhecido preserva o arquivo
  e preferências antigas. Não há reset destrutivo, cloud, sync ou import/export.

O uso e as exceções de exclusão/última aplicação estão no
[README](../README.md#local-profiles). LaserScan, exportação e lotes numéricos
continuam fora deste ciclo.

## Identidade e verificações do candidato

- DEV debug ordinary: `com.lamggm.rospocket.dev`, `0.3.0-rc1`, versionCode `3`,
  ARM64, hooks **OFF**. O cabeçalho mostra o BuildConfig efetivamente empacotado.
- O APK local de bancada usa o certificado debug do último APK físico conhecido.
  As chaves debug dos runners CI são diferentes; seus APKs não devem ser usados
  automaticamente como atualização in-place do tablet.
- Fonte exata e snapshot, hash/tamanho do APK, ABI e certificado vêm da inspeção
  do artefato, em `build/artifacts/app-debug.json`, e da cópia privada entregue.
  Uma versão nominal não identifica os bytes; conservar o APK e seu manifesto.
- Cada head precisa dos seus próprios testes/build/lint e CI aprovado. Conferir
  a árvore integrada contra a revisada e a executada pelo merge sintético do CI;
  o filtro de push da integração continua sem v0.3, conforme o roadmap.
- Build oficial: `bash tools/build.sh --offline` com toolchains já disponíveis.
  Para o CI, os wrappers existentes também exercitam fixtures DDS isoladas nos
  dois RMWs. Skips opt-in e limitações de ambiente devem continuar explícitos.
- Uma mudança posterior não herda aceite de um APK diferente. Não exigir
  igualdade binária entre builds independentes com certificados CI diferentes.

Nenhuma chave, preferência, endereço de dispositivo ou evidência privada pertence
ao Git. Antes da instalação futura, conferir novamente a identidade atualmente
instalada; a compatibilidade com o último APK conhecido não prova o estado atual.

## Smoke físico curto — todos os itens abaixo ainda PENDENTES

Este é um roteiro, **não evidência de execução nem autorização de ADB**. Usar
uma rodada privada nova, uma única automação, fixture própria somente de leitura
e domínio isolado. Nunca publicar comandos de robô para produzir estímulos.

- [ ] **Preflight e atualização:** com autorização física, identificar o tablet;
  preservar APK(s), UID, preferências sem imprimi-las, orientação e energia.
  Conferir package, code e certificado real. Instalar somente in-place; se houver
  incompatibilidade, parar, sem uninstall/clear data ou troca silenciosa de chave.
- [ ] **Salvar e selecionar:** criar perfil com quatro slots, QoS e campos
  numéricos suportados; salvar, selecionar outro e voltar. Selecionar não aplica
  nem conecta. Renomear conserva configuração; excluir exige confirmação.
- [ ] **Aplicar e reiniciar:** aplicar desconectado, fechar/reabrir e conferir
  domínio/peer/QoS/watches/campos da última aplicação, ainda desconectado e sem
  amostras antigas live. Edições de controles não aplicadas não prevalecem sobre
  esse snapshot numa recriação. Excluir o perfil aplicado limpa seu snapshot de
  startup conforme a confirmação, sem alterar uma sessão já em execução.
- [ ] **Conectar explicitamente:** receber dados novos reais da fixture nos slots
  configurados. Aplicar conectado deve ser bloqueado. Conferir contadores e
  timestamps progredindo, não apenas uma imagem ou texto estático.
- [ ] **Ausência/divergência:** usar um tópico ausente e um tipo divergente;
  manter seleção e estado pendente/erro explícito, sem substituir por outro
  tópico, criar quinto watch ou reutilizar pontos antigos.
- [ ] **Navegação e gráfico:** alternar Conexão/Tópicos/Monitor/Logs conectado,
  mantendo sessão/readers; inspecionar duas séries, pausa/retomada e logs com
  estímulos novos. Não confundir dados antigos desenhados com recepção nova.
- [ ] **Lifecycle e apresentação:** Home/retorno e retrato/paisagem preservam a
  configuração e respeitam teardown/reconexão da política existente. Conferir
  dados frescos e legibilidade real; host Views não comprovam renderização OEM.
- [ ] **Cleanup:** remover apenas a fixture da rodada, parar o app, restaurar e
  conferir preferências/configuração de tela/energia preservadas. Arquivar os
  resultados e hashes vinculados ao APK exato. Falha/timeout permanece falha ou
  incompleto, nunca PASS por juntar rodadas diferentes.

Ao executar futuramente, registrar resultados fora desta checklist histórica ou
num relatório com o SHA/APK exatos. Instalação e cada efeito relevante precisam de
readback; captura de tela isolada não comprova sessão DDS nem cleanup.

## Pendências e limites de entrega

- Aceite físico acima e autorização própria de promoção para `main`.
- Não há tag/release automática nem promessa de versão final `0.3.0` aceita.
- Wi-Fi/IP reais, soak, qualificação extensa, dispositivos adicionais e novos
  decoders continuam backlog não validado, sem relaxar seus validadores.
- A v0.3 continua um inspetor de bancada somente leitura: não há teleop,
  parâmetros/RPC, serviços/actions, rosbag, vídeo, mapas/TF ou tipos dinâmicos.
