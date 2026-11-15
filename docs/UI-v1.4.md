# ROS Pocket v1.4 — descrição e card 3D (DEV)

## Incrementos posteriores

O painel agora tem [grade editável e persistência local](DASHBOARD-LAYOUT.md).
Ela substitui as proporções fixas anteriores; apresentação não altera fontes DDS.
Para o perfil de geometria completa, TF observado opt-in e tela cheia, prevalece
[CAD e TF](CAD-TF-VIEWER.md); as limitações geométricas do primeiro card abaixo
são históricas, não os limites do perfil full.

## Contrato

Esta implementação observa tópicos já existentes; não instala serviços, não
publica comandos/dados no robô e não usa HTTP para buscar assets.

- O nome de `<robot name="...">` do URDF substitui **Seu painel**, no Painel.
- Um leitor de metadata independente procura `robot_description` ou
  `<namespace>/robot_description`, em `std_msgs/msg/String`.
- Uma única descrição/publicador é escolhida automaticamente. Havendo vários
  tópicos, **Escolher descrição** exige escolha explícita; múltiplos publicadores
  no mesmo tópico são indicados como ambíguos, sem misturar modelos.
- QoS vem da oferta descoberta. Usa reliable/transient-local quando oferecidos,
  permitindo receber a descrição retida publicada antes da conexão. Uma oferta
  volatile não promete histórico: o app informa que aguarda nova publicação.
- O reader não ocupa os quatro watches nem o leitor câmera/LaserScan. Mantém
  C++/Cyclone DDS/JNI e o anúncio existente apenas das suas identidades reais de
  leitor em `ros_discovery_info`, sem leitores fictícios ou writers de comandos.

## Geometria e interação

- Box, cylinder e sphere; cores RGBA; composição parent→joint origin→visual origin.
- Pose de referência estática (juntas em deslocamento zero). Não acompanha
  `joint_states`/TF, limites de juntas, mimic ou movimento físico.
- Card nativo Android: projeção 3D local com faces sombreadas, giro lento padrão,
  arraste de um dedo, pinça para zoom, Recentrar e desligamento do giro automático.
- Giro suspenso durante toque, fora da seção/janela/viewport, em pause/stop e detach.
  Trocar tema recolore as Views existentes, sem recriar a sessão ou o modelo.
- Referências de malhas são inventariadas, nunca baixadas nem substituídas por
  geometria inventada. `package://`/STL/DAE/URIs não transportam bytes no URDF.
  **Importar malhas (ZIP)** carrega GLBs locais vinculados às URIs literais e ao
  SHA-256 da descrição exata. Nome igual sozinho não autoriza a associação.
  Texturas, skins, animação e DAE/STL diretos no Android continuam fora do escopo.
- **Modelo completo · primitivas URDF** ou **malhas locais otimizadas** significa
  todos os visuais disponíveis, não TF, texturas, CAD exato ou equivalência ao RViz.
  Malhas simplificadas são apenas contexto visual; não usar para medições/colisão.
  Assets ausentes ou incompatíveis mantêm o modelo parcial e a lista correspondente.
- Sem descrição válida, desconectado ou depois de Stop, não aparece um robô fake;
  o título volta a Seu painel. Falhas de parsing/render permanecem explícitas.

## Limites

- XML UTF-8 até 512 KiB; 128 links, 256 visuais, 4096 elementos, profundidade 48.
- DTD/entidades externas e xacro não expandido recusados; sem resolução de rede.
- Árvore única, links/juntas coerentes, números finitos e dimensões positivas.
- Render OpenGL ES até 120000 faces montadas, em resolução nativa e com MSAA
  quando disponível; fallback Canvas até 8192 faces. Materiais embutidos e cores
  por peça são preservados; excedentes não são silenciosamente truncados.
- Domínio DDS interno separado (`2000 + ROS domain`, ExternalDomainId original),
  MaxSampleSize 520 KiB antes da remontagem/deserialização, histórico/provisão de
  uma amostra. O teto 64 KiB da telemetria e o domínio visual não são elevados.
  Esses limites por amostra não são garantia de memória total ou defesa completa
  contra peers hostis. DDS continua no escopo de rede confiável sem SROS2.

## Pacote local de malhas

Veja [formato, importação e conversor desktop](ROBOT-ASSETS.md). O pacote é
independente dos perfis, persiste no armazenamento privado e só é associado à
descrição UTF-8 exata. Não habilita busca de URIs ou comunicação nova no robô.

## Verificação e entrega

Testes focados RED/GREEN e regressões exercitam o parser Android real, Canvas
nativo host, gestos/callbacks, Activity/título/QoS/temas, ambiguidade e cancelamento.
Teste C++ com publicador DDS sintético em processo separado e namespace de rede
privado exercita uma String retida >64 KiB anterior ao reader, rejeição >512 KiB,
recuperação e coexistência com a Session de telemetria. Isso não é aceite físico
Android nem teste do runtime de um robô.

Fixtures e imagens demonstrativas existem somente nos testes. APK DEV 1.4.0,
code14, ARM64, hooks OFF. A implementação inicial não autorizava promoção
implícita. O aceite DEV e a autorização posterior de merge/release/índice F-Droid
estão consolidados em [RELEASE-v1.4](RELEASE-v1.4.md), com hashes e limites;
produção não herda a validação física do DEV.
