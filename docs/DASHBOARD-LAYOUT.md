# Painel editável nativo — DEV v1.5

A v1.5 acrescenta layout por perfil e migração do layout global, sem alterar os
sete cards ou criar leitores. Aplicar um perfil troca seu layout confirmado;
selecionar no spinner não aplica. Salvar o painel atualiza catálogo e snapshot
aplicado numa gravação de perfis confirmada. Exportação/importação schema6 leva
o layout; schemas anteriores continuam admitidos. [Contrato v1.5](ROADMAP-v1.5.md).

## Escopo

Personalização local de apresentação, não edição do robô. Preserva C++/Cyclone DDS/JNI, quatro watches, um leitor visual alternado LiDAR/câmera, descrição/TF passivos, perfis e temas. Não cria conexão, leitor extra, publisher, parâmetro, RPC ou comando.

## Uso

- O **lápis** no canto direito da linha de leituras configuradas, abaixo de Conexão, abre o rascunho. Área de toque48dp e descrição acessível **Editar painel**, sem faixa larga. Em edição, segure o corpo de qualquer card e arraste para qualquer célula livre; não é só reordenar uma lista. A grade aparece somente na edição. A alça diagonal continua abrindo tamanho/nome e redimensionando por gesto.
- **Salvar** valida e persiste; **Cancelar** ou Back descarta. **Padrão** muda apenas o rascunho até Salvar. Sair da seção ou do primeiro plano descarta alterações não salvas.
- Sete cards existentes: quatro watches, modelo 3D, LiDAR/câmera e gráfico. Não há cards extras, exclusão ou câmera e LiDAR simultâneos. Localização é uma página separada e alterna o leitor visual com esse card.
- Larguras/alturas lógicas de 1 a 4 unidades; visuais têm mínimo 2×2. Padrão: watches 1×1, modelo 4×3, sensor e gráfico 2×2. Célula quadrada de120dp ×120dp, escalada uniformemente pela fonte; espaçamento12dp. Assim1×1 é quadrado, e2×1 mede duas células mais um espaço na horizontal. Em edição, os controles sobrepõem o card intencionalmente sem alterar a célula nem diminuir seu viewport; o conteúdo não recebe gestos até sair da edição.
- Grade adaptativa com tantas colunas quadradas compactas quanto couberem na largura disponível. Não estica1×1 para preencher a linha; o espaço remanescente fica ao fim dela. Larguras limitadas somente na renderização em telas menores, sem alterar o tamanho lógico salvo. Coluna/linha e células vazias são preservadas. Há até32 colunas e64 linhas; a área de edição oferece linhas vazias e rolagem automática nas bordas durante o arraste. A posição encaixa na malha, sem sobrepor outro card; destino ocupado é rejeitado. Em telas menores, a projeção limita a largura e realoca colisões em memória sem regravar o layout. Entrar/Salvar no novo tamanho confirma essa projeção; Cancelar preserva o original. As antigas ações explícitas de mover antes/depois continuam reorganizando compactamente a grade, mas o arraste livre nunca as usa.
- Redimensionar muda os viewports reais. Controles técnicos do modelo ficam em **⋮**; indicação de pose permanece no card. Opções longas de sensores/gráfico rolam dentro dos respectivos cards. Tela cheia do modelo preservada.

## Visualizações das leituras

Em **Editar painel → tamanho/posição da leitura → Visualização da leitura…**, escolha:

- **Número / mensagem:** apresentação original, sem interpretar texto de mensagens como número.
- **Gauge semicircular:** arco e ponteiro do campo numérico selecionado.
- **Barra horizontal:** preenchimento linear da escala selecionada.

Gauge/barra usam o caminho exato (por exemplo `data`, `linear.x`, `pose.pose.position.x`), mínimo, máximo e unidade textual definidos pelo usuário. Unidade é rótulo, não conversão física nem inferência pelo nome do tópico. Use ponto decimal na escala. São campos tipados de `numericFields` do mesmo watch; não há parse do texto apresentado, novo leitor, série adicional nem mudança no seletor do gráfico.

Somente valores numéricos finitos e atuais alimentam o indicador. Campo inexistente/incompatível/não finito aparece indisponível, nunca zero. Limites devem ser finitos e crescentes, com amplitude representável. Valor fora da escala satura apenas o desenho, preservando o número e o aviso **Fora da escala**. O histórico continua no card Gráfico já existente; gauges não armazenam amostras.

As opções seguem o mesmo rascunho, Salvar/Cancelar, owner do perfil, restart e transferência do layout. Layouts schema1 migram em memória para visualização numérica; schemas1/2 carregam nomes vazios, sem regravar no load. O catálogo continua schema6; o layout embutido tem versão própria. Versões antigas do app não devem receber layouts schema4. Leituras numéricas, gauges e barras usam a mesma célula quadrada, sem mínimo oculto de duas unidades. Gauge/barra mantêm desenho compacto no1×1; a unidade de resize por gesto coincide com a célula renderizada.

## Nome manual do card

Em **Editar painel → tamanho/posição do card → Nome do card…**, digite um nome opcional de até64 caracteres e confirme **Usar no rascunho → Salvar**. Disponível nos sete cards. Vazio restaura o nome padrão; Cancelar não altera o confirmado. O nome é salvo no layout do perfil, acompanha exportação/importação e restart, sem renomear tópico, robô, leitor ou perfil.

Nas leituras, o nome substitui o tópico no espaço limitado do card; textos longos são truncados com reticências. O tópico completo e o tipo continuam na descrição acessível e nos detalhes existentes. Nos demais cards, o nome opcional ganha uma faixa dentro do viewport, sem reparentar as Views nem alterar seus owners.

## Tela cheia do aplicativo

A Activity inicia em modo imersivo e o reaplica ao retornar ao primeiro plano ou recuperar foco após diálogo. Barras de status/navegação Android ficam ocultas; gestos de borda podem revelá-las transitoriamente. Não é kiosk, bloqueio de Home/Back nem alteração da política do tablet. Insets de recortes físicos são respeitados. Diálogos/teclado podem mostrar controles do sistema enquanto ativos.

## Estado e limites

Sem perfil aplicado, `dashboard-layout-v1.json` privado mantém o painel anônimo e serve de origem para migração em memória dos perfis antigos. Com perfil aplicado, o layout mora no catálogo `bench-profiles-v1.json`, inclusive no snapshot aplicado. O codec de cada layout escreve schema4 e lê schema1/2/3/4, até4096caracteres, sete IDs conhecidos, únicos, tamanhos inteiros válidos e posições inteiras limitadas sem sobreposição. Layouts1–3 recebem posições apenas em memória; nenhuma gravação implícita ao carregar. Arquivos antigos/corrompidos não são apagados ao carregar. Escritas usam AtomicFile/readback; resultado incerto mantém aviso mesmo após Cancelar/Back, sem promessa fictícia de rollback. Falha do catálogo exige reabertura para observar o estado real. Não é promessa contra falha física do armazenamento.

Reordenar/redimensionar não remove e reinsere as Views de conteúdo: modelo, buffers, câmera, filtros, histórico e seleção permanecem nos mesmos owners. Diálogos/gestos antigos são cercados pela identidade do rascunho. Fontes de dados continuam independentes da edição.

## Verificação histórica v1.4

JUnit/Robolectric: modelo imutável/codec, falha real AtomicFile, rascunho/salvar/cancelar/padrão, menu nativo, gesto de resize, evento antigo, persistência, Back/seção/lifecycle, viewports reais, identidade do modelo/zoom/readers, layouts phone/tablet e temas. Ajuste de projeção preserva enquadramento no lado menor de cards altos, sem mudar geometria. Imagens host usam fixtures explicitamente sintéticas, somente testes; não entram no APK.

Aceite físico e build/review vinculados ao candidato constam dos recibos externos da entrega. Não equivalem a soak, FPS/bateria, reconexão RF/IP ou aceite do robô físico. O incremento original era DEV sem promoção implícita; a autorização posterior
e a separação DEV físico/produção host constam de [RELEASE-v1.4](RELEASE-v1.4.md).
