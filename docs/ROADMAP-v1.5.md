# ROS Pocket v1.5 — dashboards por perfil e Localização

## Escopo inicial (histórico)

Implementar dashboards por perfil e uma página própria de mapa/pose. Android nativo Java/Canvas + C++/Cyclone/JNI preservados. Somente leitura: sem destinos, teleoperação, parâmetros, serviços/actions, GPS do telefone, tiles externos ou alteração do robô. Esta etapa não autoriza instalação, assinatura de produção, push/merge/release/F-Droid ou campanhas físicas/rede.

## Dashboard por perfil

- Tamanhos e ordem dos sete cards pertencem ao perfil aplicado, não ao perfil apenas selecionado no spinner. Editar/Salvar atualiza layout do catálogo e do snapshot aplicado em uma única gravação confirmada, preservando as demais configurações de cada um.
- Salvar configuração atual captura somente o layout confirmado, nunca um rascunho. Cancelar/Back/troca de seção/perfil descartam o rascunho. Aplicar perfil continua exigindo desconexão; não conecta nem cria leitores.
- Perfis antigos herdam em memória o layout global desta instalação. Abrir o app não regrava o catálogo nem apaga `dashboard-layout-v1.json`. O painel sem perfil continua usando esse arquivo independente.
- Schema6 inclui `dashboard` e `localization`; lê schemas1–5. Importar/exportar leva layout e escolhas de localização, não amostras/poses/assets/identidades DDS. Importação gera outro UUID. Schema5 é uma representação intermediária com dashboard, sem localização.
- Excluir o perfil aplicado não redireciona edições antigas para o painel global ou outro perfil. Falha/resultado incerto preserva o aviso e não anuncia rollback fictício; recarregar o app permite observar o estado real.

## Página Localização

- Quinto destino principal: Painel, Localização, Explorar, Grafo e Logs. Conexão/Aparência permanecem globais.
- Escolher OccupancyGrid em Explorar ou preencher tópico absoluto e tocar **Observar**. QoS reliable/transient-local do mapa é explícito. Escolhas só são retidas no perfil ao usar **Salvar configuração atual** em Conexão.
- Mapa `nav_msgs/OccupancyGrid`: dimensões/resolução/origem/frame respeitados, -1 desconhecido, 0 livre, 100 ocupado, intermediários em cinza. Limite1.048.576células/2048porlado e payload DDS limitado a4MiB; dados inválidos não são convertidos em espaço livre. Origem planar validada, com rotação em yaw. Sem costmap/path/scan sobreposto.
- O mapa é observado na página Localização; o Painel usa leitores independentes para câmera e LiDAR, sem ocupar os quatro watches. Decodificação/bitmap fora da UI e do worker DDS, com um trabalho pendente substituível e um resultado de UI pendente.
- Pose pelo TF observado no frame do mapa até o frame do robô escolhido. TF dinâmico best-effort/volatile; TF estático reliable/transient-local. Não depende de URDF nem cria leitor de descrição fictício. CAD e Localização alternam o mesmo owner nativo de metadados/TF.
- Não extrapola, estima localização ou inventa pose na origem. TF ausente, inválido, conflitante ou vencido remove o marcador. Timestamps repetidos não renovam a idade. Mapa retido pode continuar válido enquanto a pose expira; a UI distingue esses fatos.
- Pan/pinça, enquadrar, zoom e seguir robô são ações de viewport, nunca comandos. Troca de página, seleção, perfil, conexão e lifecycle invalidam dados/decodes/poses antigos. Uma mudança de publicador de mapa exige seleção explícita nova.

## Acabamento adicional autorizado

O incremento de operação está descrito em [OPERATOR-POLISH-v1.5.md](OPERATOR-POLISH-v1.5.md): cabeçalho/identidade, botão flutuante, estado TF legível, fontes recolhíveis, diagnóstico e rastro opcional. O aceite físico anterior não se transfere aos novos bytes.

## Validação e limites

TDD dos comportamentos centrais; testes de Views/persistência/codec/gerações; Canvas Android host em phone/tablet e claro/escuro com fixtures sintéticas restritas aos testes. Capturas reais de publicadores ROS em Fast DDS/Cyclone DDS passam pelo leitor nativo e decoder Android host, incluindo mapa retido anterior ao leitor e TF sem URDF. Fixtures isoladas, sem hardware do robô.

Resultados finais, build/assinatura/hash e limitações devem ser vinculados ao candidato exato na evidência externa da tarefa. Host/fixture/review/build não são aceite de APK no dispositivo, FPS/bateria/térmica, reconexão RF/IP ou robô físico. A v1.4 publicada permanece intacta.
