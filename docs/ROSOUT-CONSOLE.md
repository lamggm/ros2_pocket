# Console `/rosout` v0.2

O console recebe diretamente `rcl_interfaces/msg/Log` em `/rosout` usando
Cyclone DDS tipado e JNI. Não usa outro processo, bridge, parser de texto ROS,
persistência, exportação, publicação ou comandos.

## Schema e reader

O IDL implementa o schema de Jazzy: `builtin_interfaces/Time` (`int32 sec`,
`uint32 nanosec`), `uint8 level`, strings `name`, `msg`, `file`, `function` e
`uint32 line`. Os níveis padrão são DEBUG 10, INFO 20, WARN 30, ERROR 40 e
FATAL 50. A definição oficial consultada é
[`rcl_interfaces/msg/Log.msg` na branch jazzy](https://github.com/ros2/rcl_interfaces/blob/jazzy/rcl_interfaces/msg/Log.msg);
`Time.msg` confirma os componentes do timestamp
([fonte Jazzy](https://github.com/ros2/rcl_interfaces/blob/jazzy/builtin_interfaces/msg/Time.msg)).

`/rosout` tem topic e reader próprios na sessão. A assinatura da telemetria
selecionada pelo Explorer continua independente. O leitor solicita **best
effort / volatile**, compatível com writers reliable e best effort, sem exigir
durability transient-local. Assim ele recebe logs ao vivo sem pedir replay de
histórico transient-local. O snapshot apresenta separadamente reliability e
durability oferecidas pelos writers observados no discovery; essas ofertas não
são confundidas com a QoS solicitada pelo reader. Se `/rosout` for anunciado com
outro tipo DDS, o console informa incompatibilidade e não apresenta o reader como
recebendo. O decoder `Log` não aparece como suporte genérico no Explorer.

O reader DDS mantém `KEEP_LAST(128)` e a cada poll a ingestão toma no máximo
quatro lotes de 32 amostras (128 mensagens). A descoberta de endpoints/grafo e o
reader selecionado continuam sendo processados no mesmo ciclo, com seus limites
anteriores. O poll Android ocorre a cada 500 ms; taxa alta pode descartar dados
na fila DDS antes do buffer local. Não há promessa de captura sem perda em
rajadas acima dessa capacidade.

## Memória e apresentação

O ring buffer retém no máximo 200 entradas e 256 KiB contabilizados (tamanho do
objeto mais bytes dos quatro campos string). Cada entrada limita logger a 256
bytes, mensagem a 4096 bytes, arquivo e função a 1024 bytes cada. A cópia nativa
lê no máximo o limite do campo mais três bytes de lookahead UTF-8 antes de criar
a string intermediária; o texto retido é cortado em fronteira de code point e a
entrada sinaliza truncamento.

Esses limites definem a memória retida pela aplicação, não a memória total de
rede/deserialização. Strings DDS wire não têm limite de bytes no schema. Na
reconciliação com o incremento 06, a Session configura `Internal/MaxSampleSize`
em **64 KiB** para todo o domínio, incluindo `/rosout`: amostras serializadas
maiores são descartadas antes do cache, não truncadas em DDS. Isso não prova um
teto da memória total do processo/JNI/UI. `KEEP_LAST(128)` limita a quantidade de
amostras; o ring e as cópias de campos continuam com seus limites próprios. A cada
iteração são tomadas no máximo 4 × 32 = 128 amostras; o ring
local limita a retenção posterior a 200 entradas/256 KiB. A contagem `evicted`
é rotulada na UI como eviction do ring retido; não mede perda antes da leitura
DDS nem perda de rede.

O horário de origem é preservado em segundos e nanossegundos ROS. A UI soma os
componentes como decimal exato com nove casas, inclusive para segundos negativos
(relógio simulado), sem conversão com ponto flutuante. `name` é
exibido como logger, exatamente o campo transportado, sem inferir nome de nó. A idade de
ingestão é calculada à parte com relógio monotônico e exibida em milissegundos;
ela não depende do relógio do publisher. Filtros por severidade, logger, trecho
do timestamp de origem e texto (incluindo logger, mensagem e origem) são locais.

Pausar congela as entradas visíveis e informa que estado/idade exibidos estão
congelados; o poll, descoberta e reader continuam ativos e o último snapshot é
substituído continuamente dentro do limite do reader. Retomar renderiza imediatamente
esse snapshot mais recente. Limpar descarta
até os 128 samples ainda pendentes no reader e esvazia o buffer; amostras que
chegarem depois do clear podem aparecer normalmente. Stop, desconexão, falha de
conexão e parada da Activity limpam a apresentação e invalidam callbacks da sessão
anterior. Nenhum log é salvo automaticamente ou exportado.

## Validação

Correções da revisão desta branch: antes de `poll()`, cada snapshot recebe o
epoch de clear **já aplicado no worker**, não o epoch solicitado pela UI. A
pintura compara essa proveniência imutável com a geração atual; assim um clear
solicitado antes ou durante o poll não permite reapresentar logs anteriores,
mesmo que a limpeza nativa termine antes de a UI pintar. A UI descarta esses snapshots;
o clear enfileirado valida Activity, geração e identidade da configuração antes
de chamar JNI. Em pausa, o último snapshot continua sendo substituído e aparece
imediatamente ao retomar, enquanto as entradas e sua idade visível ficam
congeladas. O tipo wire `Log_` permanece exclusivo do reader dedicado; a
assinatura genérica segue rejeitada e não remove a telemetria anterior.

RED/GREEN local deste incremento fica em `build/evidence/` (ignorados pelo Git):

- `rosout-ring-red-behavior.log` e `rosout-ring-green.log`: count/bytes limitados.
- `rosout-byte-budget-red.log` e `rosout-byte-budget-green.log`: eviction pelo
  orçamento de bytes mesmo abaixo do limite de entradas.
- `rosout-unicode-red.log` e `rosout-unicode-green.log`: limite de mensagem e
  corte seguro de code point UTF-8.
- `rosout-lifecycle-red.log` e `rosout-lifecycle-green.log`: limpeza ao sair da
  Activity e fence da sessão antiga.
- `rosout-qos-ui-red.log` e `rosout-qos-ui-green.log`: oferta DDS visível separada
  da QoS solicitada.
- `rosout-json-utf8-red.log` e `rosout-json-utf8-green.log`: bytes UTF-8 inválidos
  são substituídos de modo que o snapshot continue JSON válido.
- `rosout-activity-green.log`: controles de pausa, clear e lifecycle integrados.
- `rosout-policy-race-red.log` / `rosout-policy-race-green.log`: worker bloqueado
  antes da captura da geração, clique em Limpar, clear nativo aplicado e só então
  drenagem da UI; nenhum snapshot anterior pode reaparecer.
- `rosout-negative-time-red.log` / `rosout-negative-time-green.log`: horário ROS
  negativo exibido corretamente e usado pelo filtro local.
- `rosout-clear-pause-red.xml` / `rosout-clear-pause-green.xml`: poll bloqueado
  através do clique em Limpar e snapshot mais novo durante a pausa.
- `rosout-clear-session-red.xml`: clear antigo reproduzido após troca de
  configuração antes de drenar a fila DDS; a versão verde está no relatório
  Android completo `app/build/test-results/testDebugUnitTest/`.

`bash tools/test.sh --portable` inclui o teste de limites do ring e incompatibilidade
de tipo em `/rosout`. `bash tools/test.sh --fixture` roda
`rosout_jazzy_interop` primeiro em container descartável `--network none`, uma
vez com `rmw_fastrtps_cpp` e outra com `rmw_cyclonedds_cpp`, para cada execução
usar produtor novo e receber o burst completo. A fixture aguarda um subscriber
DDS de `/rosout` antes de começar; não usa atraso fixo para supor descoberta.
Depois executa as demais fixtures preservadas no mesmo RMW. Relatórios separados
ficam como `<rmw>-rosout.xml` e `<rmw>.xml` em `build/native-host/`. A fixture ROS
publica todas as severidades, 800 logs e uma mensagem Unicode longa; o binário host Cyclone
valida limite/eviction, timestamps, QoS anunciado, independência da telemetria,
clear, stop e nova sessão. Os relatórios permanecem em `build/native-host/`.

Os testes Android Robolectric exercitam os controles e a integração da Activity
com JNI substituído; eles não simulam recepção DDS. Nenhum teste físico de tablet,
Wi-Fi, taxa/endurance ou consumo de recursos foi executado neste incremento. A
qualificação física permanece pendente para o gate 08.

## Reconciliação local com incremento 06

O reader dedicado não ocupa nenhum dos quatro slots de watch. A ingestão toma
até 128 logs e 64 amostras por watch (256 de telemetria) por poll. O mesmo worker
serializado aplica subscribe/remove/clear/stop; o clear mantém a captura do epoch
já aplicado **antes** do poll. Substituir/remover uma telemetria não limpa logs;
limpar logs não reinicia contadores nem gerações dos watches.

O gate real de domínio 71 publica Log via rclpy nos dois RMWs ao mesmo tempo que
quatro tipos primitivos. Verifica avanço de logs durante falha/QoS/remoção e
reassociação de watches, e avanço 6/12/24/48 dos quatro contadores durante clear
com gerações preservadas. O burst rosout de domínio 67 continua **primeiro** em
fixture fresca por RMW e com relatório separado. O conjunto reconciliado passou
11 CTests portáveis, 10 execuções de fixtures, 20 testes Python e 66 JVM; lint
0 erros / 73 warnings. Evidência/medições: [MULTIWATCH](MULTIWATCH.md).

Isto não integra os tipos estruturados da 05 nem conclui teste físico de
Wi-Fi/tablet/soak. A qualificação 08 e a publicação do PR continuam gates
separados; nenhum merge de 06 na integração está autorizado antes da 05.
