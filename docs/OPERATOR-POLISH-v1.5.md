# v1.5 — acabamento de operação

Incremento autorizado pela mensagem1571932976709767410, sobre99fea432df159062503e00a97da14ece1271ab4d. Não altera backend, tetos de leitores nem capacidade somente leitura. Sem publicação, instalação ou transferência de aceite físico implícitas.

## Cinco alterações

1. **Localização compacta:** viewport adaptado à altura, zoom agrupado, Fontes e ajustes recolhível; navegação com rótulos em uma linha e largura maior no tablet.
2. **Estado útil:** pose com idade de recepção monotônica derivada do deadline TF original. Sem comparar relógio ROS com relógio do Android. TF estático é identificado como estático. Timestamp ROS exato fica no diagnóstico, não no resumo.
3. **Cabeçalho global:** título centralizado na largura da tela e alinhado à marca ROS Pocket. O nome vem do `<robot name>` da descrição URDF já observada/selecionada nesta conexão, não do nome do perfil, de nó, mesh ou IP. Sem presença DDS atual: `desconectado`; com presença mas sem nome disponível: `desconhecido`. Perfil aplicado e avisos de edição são informação separada. Adicionar vira botão flutuante `+` no canto inferior direito do Painel, acima das áreas seguras e da navegação; continua abrindo Explorar, sem criar leitor.
4. **Diagnóstico:** seleção/mapa recebido, cadeia/frame TF, erros reais, timestamp de origem e QoS solicitado/ofertas observadas. As ofertas do mapa são comparadas com o pedido do mapa, nunca com o perfil do Watch1. Somente best_effort versus reliable solicitado ou volatile versus transient_local solicitado geram incompatibilidade comprovada dessas políticas; desconhecido continua desconhecido e outras políticas/rede não são garantidas. Nova seleção/sessão/frame invalida diagnóstico antigo.
5. **Rastro opcional:** até256posições observadas em memória, azul e distinto do marcador verde. Interrupções de TF quebram segmentos; não há estimativa, extrapolação ou ligação através de ausência. Troca de contexto/frame/sessão limpa o histórico. Desligar Exibir rastro limpa e para a coleta; só a preferência global local ON/OFF persiste em `location-display`, não posições. Nenhum leitor DDS adicional. A preferência não altera o schema6 do perfil.

## Verificação

Regressões TDD de estados/Views, geometria do cabeçalho, save/applied/edição, QoS conhecido/desconhecido, invalidação de frame e limite/gaps/OFF do rastro. Pixels Android Canvas host verificam contraste do `+`, segmentos sem ponte e remoção do marcador. Previews phone/tablet claro/escuro permanecem explicitamente sintéticos, só em testes. Revisão independente e build/lint/artifact metadata devem ser vinculados aos bytes finais. Teste físico do candidato anterior permanece histórico, não prova este novo APK.
