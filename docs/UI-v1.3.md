# ROS Pocket v1.3 — UI nativa e aparência

Escopo autorizado: implementar a direção Painel A com Explorer dividido B no tablet, com temas claro/escuro e opção Sistema. Entrega DEV com validação host; sem instalação, merge/release, produção ou atualização F-Droid implícitos.

## Organização

- Quatro destinos principais: **Painel**, **Explorar**, **Grafo**, **Logs**.
- Em largura de configuração de pelo menos600dp, trilho lateral; em telas menores, barra inferior. IDs de navegação e componentes existentes preservados.
- **Conexão** acessível no cabeçalho em qualquer seção, com os perfis e validações existentes. A sessão continua exigindo conexão explícita. Configuração não é um quinto item da navegação principal.
- **Aparência** no cabeçalho: Sistema (padrão), Claro e Escuro. Preferência separada dos perfis de conexão. Alterar tema recolore a árvore existente e invalida Canvas, sem Activity.recreate(), sem iniciar/parar sessão ou leitor. Mudança de uiMode no modo Sistema é tratada na mesma Activity. Mudanças de tamanho/orientação mantêm o lifecycle anterior; não há nova promessa de continuidade durante rotação.
- Painel é a tela inicial. Quatro cartões com hierarquia própria (tópico, valor dominante, tipo e estado), detalhes/QoS recolhíveis, um leitor visual e gráficos numéricos. Não cria leitores para preencher cartões vazios. Os cartões conservam a decisão de freshness e o dono da requisição; uma requisição substituída não pode reaproveitar o selo ou valor anterior. Cores não classificam os dados como atuais. Unidades não são deduzidas do nome do tópico.
- Fidelidade ao mockup A: cabeçalho compacto com contexto de conexão, trilho/barra com ícones nativos vetoriais, botões sem relevo padrão de plataforma, cartões com superfícies/bordas discretas e valores grandes. A partir de900dp e com fonte normal, quatro cartões por linha e visualizador/gráfico em colunas com viewports alinhados. Em larguras menores ou fonte ampliada, cartões em2×2 e visualizadores empilhados com rolagem. O trilho continua usando o limiar independente de600dp.
- Scan/câmera: abas destacam o modo selecionado; menu ⋯ abre metadados, parar e zoom. Ampliar muda apenas o tamanho da superfície existente. Gráfico: pausa no cabeçalho; menu ⋯ abre escolha de séries, instruções, proveniência e exportação. Os controles não foram removidos nem substituídos por dados de demonstração.
- A divisão larga preserva a proporção1,65:1 de visualizador/gráfico declarada no CSS do mockup escolhido, em vez de repartir a linha ao meio. O logo do cabeçalho também acompanha a paleta atual.
- Explorer: busca e favoritos locais; namespace/tipo em filtros expandíveis. Lista e detalhes lado a lado no tablet. No celular, seleção mostra detalhes, com retorno explícito à lista.
- Tocar um tópico inspeciona metadados, sem chamar o leitor. **Monitorar** oferece as quatro posições; substituir uma ocupada exige confirmação. Confirmações são vinculadas à configuração/geração e ao dono da requisição do slot, para não sobrescrever alterações posteriores.
- Tipos visuais usam o leitor visual existente; não acrescentam outra câmera/scan simultâneo. Decoder e QoS desconhecidos não são promovidos a compatíveis por causa da nova apresentação.

## Logs — console A com inspetor C

- Apresentação nativa em linhas compactas, severidade com texto e cor, busca e chips de contagem sobre o ring retido (até 200 eventos). FATAL e níveis desconhecidos continuam preservados; os filtros são locais. Logger, horário e seletor de severidade completo ficam no controle Filtros. Ofertas QoS dos writers e solicitação do reader aparecem separadamente em Detalhes técnicos.
- Toque em uma linha abre o inspetor lateral quando a largura útil é de pelo menos900dp e a fonte está abaixo de1,3×. Em área menor, usa janela sobreposta ancorada à direita, com Fechar e rolagem própria. A lista não reconecta DDS nem cria leitores. O painel não é aberto por novas mensagens.
- O evento inspecionado é uma cópia fixada da amostra apresentada, não um índice do ring: entrada/evicção de mensagens não pode trocar silenciosamente o conteúdo. A seleção visual acompanha conteúdo inequívoco; eventos indistinguíveis não recebem um destaque arbitrário. Copiar evento usa apenas esse objeto selecionado.
- Horário de origem mostra UTC na lista e o valor ROS Time decimal exato (inclusive segundos negativos) no inspetor. A idade pertence à amostra selecionada, não é atualizada no painel fixado nem mede latência entre relógios. Não se afirma uma nova medição no instante do toque.
- Pausar congela a projeção apresentada, não o reader. A cópia mais recente continua sendo recebida e aparece ao retomar; os filtros podem ser aplicados à projeção congelada. Limpar e os fences de sessão existentes invalidam o inspetor e callbacks de linhas antigas, inclusive pausado/offline.
- Tema recolore controles, linhas, severidades e inspetor sem substituir a sessão. Rodapé e ação de cópia têm verificação de bounds no render Android host. A lista preserva Views quando só a idade muda e não invalida a projeção sem necessidade durante o primeiro layout.

As prévias `build/reports/logs-v13/*-host-synthetic.png` são Views/Canvas Android com fixtures somente em testes, não HTML nem evidência física. O APK continua sem dados ou modo de demonstração embutidos.

## Grafo — layout por forças e inspetor

- Canvas maior com busca e ferramentas compactas; física e filtros ficam recolhidos. Distinção nó/tópico, direções pub/sub, desconhecidos e aviso de visão parcial permanecem. Em zoom de visão geral, rótulos e traços têm tamanho mínimo em tela; nomes completos continuam no inspetor.
- Motor Java local, determinístico, sem Android/DDS: repulsão, molas, centralização, amortecimento e separação retangular. Até160 elementos e400 conexões; passos limitados, resfriamento e parada. Não há significado físico de força, distância ou velocidade do robô: coordenadas são apenas de layout.
- Física ligada por padrão. Parâmetros de centralização, repulsão, força e distância de conexão são locais ao painel. A pausa congela coordenadas, não descoberta/topologia. Não há nova chamada JNI, reader, RPC, parâmetro ou publicação.
- Um callback Choreographer somente com painel anexado/visível e janela visível, limitado a cerca de30 passos/s. Para ao esconder a seção/janela, remover a View ou acomodar o layout. A qualificação host não constitui medição de FPS/consumo em ARM64 físico.
- Novos elementos usam sementes determinísticas perto de vizinhos existentes; posições anteriores não são reinicializadas. Selecionar não reaquece a simulação. Arrastar um círculo move o elemento em coordenadas do mundo; arrastar o fundo move a câmera; pinça mantém zoom. Fixar/Soltar posição atua só no layout da sessão e é limpo nos fences existentes.
- Direção circular aprovada: nós preenchidos, tópicos vazados, nomes externos e bolhas coloridas de namespace. Os nós recebem a cor do grupo; tópico mantém a distinção por contorno/cor. As arestas encontram o perímetro circular real, inclusive quando o raio mínimo em tela amplia o glyph. Alvos de toque continuam com pelo menos48dp; a forma visual não é uma caixa.
- Namespace é derivado somente de nomes absolutos válidos dos nós/tópicos; nomes relativos, desconhecidos ou malformados não são reparados. Agrupamento direto em até24 namespaces, sem descartar vértices por esse limite. A envoltória externa é apenas “Topologia ROS”, não uma nova entidade ou árvore ROS descoberta. Não há descoberta de pacotes: o renderer aceita um pacote explicitamente identificado, mas não cria uma associação pacote↔namespace a partir de DDS. Os pacotes fake do protótipo não entraram no APK.
- Coesão de membros do mesmo namespace e repulsão dos envelopes são locais ao layout, além das molas das conexões. Arrastar a borda do namespace move sua família sem pan; cancelar libera holds. Fixar grupo conserva pins individuais ao soltar, e novos membros herdam o pin do grupo durante a sessão. Seleção, detalhe e ações virtuais de acessibilidade usam IDs de apresentação `ns:<caminho>`, nunca endpoints.
- O overview conserva raios mínimos em tela e escolhe posições externas de texto sem colisão com outros rótulos/glyphs. Nomes sem espaço são omitidos da pintura nessa escala, não da topologia nem da acessibilidade; zoom e inspetor continuam disponíveis. Em telefone denso, círculos/arestas ainda podem ficar próximos ou se cruzar. A revisão visual host não equivale a legibilidade integral de todo grafo em qualquer zoom.
- As novas prévias em `build/reports/graph-circular-v13/*-host-synthetic.png` usam15nós e16tópicos fake exclusivos da árvore de testes, agrupados em cinco namespaces. O teste de rótulos verifica as chamadas reais ao Canvas e a separação dos bounds; contratos herdados de faces retangulares/pixels fora do círculo foram atualizados para a geometria circular, preservando seleção, dimming e ausência de pilha de papéis Pub/Sub.
- Toque abre inspetor lateral quando há largura; tela estreita usa janela sobreposta. Detalhes rolam, ação de fixar permanece fora do corpo rolável, e esconder o grafo fecha o modal. Os filtros locais incluem infraestrutura, prefixo de namespace e foco; setas são associações anunciadas, nunca prova de tráfego ou compatibilidade QoS.
- Testes geométricos herdados declaram física pausada para manter sua fixture determinística; a física padrão, gestos e mudanças de topologia têm testes próprios. O scheduler host exercita callbacks reais com eventos de visibilidade de janela explicitamente sintéticos: Robolectric4.14 não fornece essa transição do gerenciador de janelas por padrão. Não confundir essa prova com um dispositivo.

Prévia e animação em `build/reports/graph-v13` são Canvas/Views Android com topologia sintética exclusiva de testes. Não há fixture de grafo ou dados de demonstração embutidos no APK.

## Preservado

C++/Cyclone/JNI, anúncios de leitores para bridge existente, quatro watches, um leitor visual, seleção manual Humble/Jazzy, LAN/VPN e parâmetros capturados por sessão. Nenhuma atuação, parâmetro/RPC ou alteração de runtime do robô. As bibliotecas nativas ARM64 são reutilizadas byte a byte do DEV congelado v1.2, com fonte nativa inalterada; isso não transfere o aceite físico da versão anterior para o APK v1.3.

Pausa do gráfico, parada do leitor visual e desconexão da sessão mantêm contratos diferentes. Temas não invertem pixels das imagens de câmera. Cores de texto, superfícies, grafo, curvas e pontos do scan são resolvidas por paleta.

## Verificação e limites

Testes de Views Android/Robolectric e Canvas nativo no host; doubles JNI e dados sintéticos são explicitamente separados de DDS real. Prévias geradas em tablet/celular e nos dois temas são renderizações host, não capturas do tablet. Testes de regressão existentes mantêm as asserções operacionais; testes de pixels canônicos declaram tema escuro e o fluxo herdado de leitura agora exerce a escolha/confirmacão real do slot.

As prévias `tablet-reference-*-host-synthetic.png` usam três leituras, uma série e um pacote de scan explicitamente sintéticos, somente na árvore de testes. O APK não contém um modo de demonstração nem inventa leituras para reproduzir os valores do mockup. Testes de alinhamento impedem que o baseline interno de um Button multilinha desloque os cartões desenhados em Canvas. Testes de gráfico localizam a superfície por ID, não pela posição variável do filho no layout.

O observador de layout só altera textos do cabeçalho quando o conteúdo muda. Um teste Android reproduz o pedido de novo layout causado por reatribuir textos idênticos e verifica a ausência desse ciclo após a correção; isso não constitui um benchmark de CPU no tablet.

O build DEV e os relatórios finais pertencem à evidência externa da tarefa. Não declarar nova validação física, teste de VPN/RF, instalação, publicação ou aceite de produção a partir desses testes.
