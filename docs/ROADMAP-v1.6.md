# ROS Pocket v1.6 — grafo hierárquico

Primeiro incremento, a partir da v1.5.0 publicada: a bolha de um namespace presente no grafo inclui seus subnamespaces visíveis. Por exemplo, `/gb002/mission/coverage` fica dentro de `/gb002/mission`, junto dos membros diretos. `/gb002/mission_extra` continua separado. Não são criados nós ou namespaces-pai fictícios quando o caminho existe apenas como prefixo; `/` continua sendo o grupo dos membros diretos da raiz.

As conexões Pub/Sub e os nomes ROS completos são os mesmos. Seleção, destaque, arraste e fixação do grupo-pai incluem seus descendentes; a sub-bolha pode ser selecionada pela própria borda ou pelo título, inclusive com zoom reduzido. Descendentes fixados pelo pai indicam a origem da fixação e não são deslocados individualmente. A separação por forças mantém bolhas irmãs apartadas e evita que membros diretos pareçam pertencer à sub-bolha. O limite de bolhas não remove vértices nem conexões.

A mudança é só na apresentação local. O núcleo DDS/JNI, o GB001-v2 e a distribuição v1.5.0 não são alterados. Esta etapa prepara um APK DEV para teste; publicação e atualização em tablet são etapas separadas.
