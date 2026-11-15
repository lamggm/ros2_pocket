# Painel editável nativo — DEV v1.4

## Escopo

Personalização local de apresentação, não edição do robô. Preserva C++/Cyclone DDS/JNI, quatro watches, um leitor visual alternado LiDAR/câmera, descrição/TF passivos, perfis e temas. Não cria conexão, leitor extra, publisher, parâmetro, RPC ou comando.

## Uso

- **Editar painel** cria um rascunho. Cada card ganha uma faixa própria de edição, sem cobrir nem diminuir seu conteúdo: alça pontilhada para arrastar e soltar antes de outro card, botão central com nome/tamanho, e alça diagonal para redimensionar por gesto. O botão central abre medidas e ações equivalentes de mover antes/depois, sem depender de gestos.
- **Salvar** valida e persiste; **Cancelar** ou Back descarta. **Padrão** muda apenas o rascunho até Salvar. Sair da seção ou do primeiro plano descarta alterações não salvas.
- Sete cards existentes: quatro watches, modelo 3D, LiDAR/câmera e gráfico. Não há cards extras, exclusão, dashboard por perfil ou câmera e LiDAR simultâneos neste incremento.
- Larguras/alturas lógicas de 1 a 4 unidades; visuais têm mínimo 2×2. Padrão: watches 1×1, modelo 4×3, sensor e gráfico 2×2. Unidade vertical 160dp, escalada para fontes grandes; espaçamento 12dp.
- Grade adaptativa de 4, 2 ou 1 colunas. Larguras limitadas somente na renderização em telas menores, sem alterar o tamanho lógico salvo. Ordem estável por linhas; não é posicionamento livre com células vazias.
- Redimensionar muda os viewports reais. Controles técnicos do modelo ficam em **⋮**; indicação de pose permanece no card. Opções longas de sensores/gráfico rolam dentro dos respectivos cards. Tela cheia do modelo preservada.

## Estado e limites

`dashboard-layout-v1.json` privado e independente dos perfis/conexões/tema/meshes. Schema1, até4096bytes, sete IDs conhecidos, únicos e tamanhos inteiros válidos. Leitura inválida usa padrão sem apagar o arquivo. Escrita AtomicFile, readback exato antes de confirmar. Falha anterior ao fechamento mantém edição e informa preservação do anterior. Falha durante/depois do fechamento é resultado incerto: o arquivo pode ter mudado; não promete rollback. O aviso permanece mesmo após Cancelar/Back, que descartam somente o rascunho em memória. Uma nova gravação confirmada ou reinício com releitura estabelece o layout salvo. Não é promessa contra falha física do armazenamento.

Reordenar/redimensionar não remove e reinsere as Views de conteúdo: modelo, buffers, câmera, filtros, histórico e seleção permanecem nos mesmos owners. Diálogos/gestos antigos são cercados pela identidade do rascunho. Fontes de dados continuam independentes da edição.

## Verificação

JUnit/Robolectric: modelo imutável/codec, falha real AtomicFile, rascunho/salvar/cancelar/padrão, menu nativo, gesto de resize, evento antigo, persistência, Back/seção/lifecycle, viewports reais, identidade do modelo/zoom/readers, layouts phone/tablet e temas. Ajuste de projeção preserva enquadramento no lado menor de cards altos, sem mudar geometria. Imagens host usam fixtures explicitamente sintéticas, somente testes; não entram no APK.

Aceite físico e build/review vinculados ao candidato constam dos recibos externos da entrega. Não equivalem a soak, FPS/bateria, reconexão RF/IP ou aceite do robô físico. Sem merge/release/produção neste incremento.
