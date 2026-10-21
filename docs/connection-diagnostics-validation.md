# Incremento 02 — registro de validação

Código exercitado: `28d7359aeaa730af3b6012cbdee9adc7a7b17710`.
Os commits posteriores deste registro não alteram o código do aplicativo.

## Build e regressões

- `bash tools/build.sh`: sucesso com JDK 17, Gradle 8.7, SDK 35 e NDK r28c.
- Java portável: domínio/input e política de retry, limite de 30 s, geração e idade no instante da pintura.
- CTest: **5/5** (`subscribe_failure_loopback`, `connection_state`, `session`, `core`, `connection_fixture`). Fixtures sintéticas próprias, em loopback/domínios 67 e 69.
- Android/Robolectric API 31: **19/19**, sendo 15 da Activity e 4 das regras de input. O JNI é um dublê explícito nestes testes; os testes DDS nativos e físicos são separados.
- `lintDebug`: zero erros; permanecem avisos, portanto não é uma alegação de lint sem warnings.
- `assembleDebug`: sucesso. As duas bibliotecas arm64 do APK possuem segmentos ELF LOAD alinhados a 16 KiB. Isso não comprova execução num dispositivo com páginas de 16 KiB.

## Corrida de seleção/QoS

A revisão independente encontrou uma possibilidade de pintar dados do reader anterior com uma geração da UI já atualizada, antes de executar a assinatura enfileirada. O código final registra `appliedReaderEpoch` no executor e captura `pendingReader` no snapshot: geração desejada ainda não aplicada nunca é apresentada como recepção atual.

Além da regressão de assinatura enfileirada, foi adicionado um teste determinístico que bloqueia o tick no monitor de identidade da política, solicita o reader substituto e drena a assinatura antes de pintar o snapshot antigo. Desativar deliberadamente a proteção faz esse teste falhar com AssertionError; restaurar a implementação faz passar. A mutação foi removida e o build completo foi repetido; a árvore está sem alterações de código pendentes.

## Tablet físico

Samsung SM-X400; atualização restrita a `com.lamggm.rospocket.dev`, com certificado compatível, sem uninstall nem limpeza de dados. APK anterior e preferências foram preservados privadamente. Nenhuma política de VPN, Wi-Fi, firewall ou outro aplicativo foi alterada.

**22/22 verificações da suíte `tests/tablet_regression.py` passaram em uma execução completa**, com publishers ROS Jazzy sintéticos no domínio 67:

- String real/contador de sequência avançando e grafo ROS;
- diagnóstico separado de peers DDS e nós ROS;
- Home/retorno com amostra nova;
- perda do publisher, leitura obsoleta, remoção de nó e estado reconectando;
- retorno do peer com Cyclone DDS após Fast DDS e recepção automática;
- incompatibilidade de QoS reliable/best-effort e recuperação;
- isolamento no domínio errado e redescoberta no correto;
- Float64 real e preservação do reader numérico após Home;
- reinício do processo, persistência de domínio e nova leitura após seleção.

A captura final foi inspecionada: painel de leitura legível, sem sobreposição/clipping bloqueante. Não foram encontrados novos registros de crash do pacote alvo. A fixture de teste foi encerrada/removida ao fim da execução.

## Identidade do artefato

- Pacote: `com.lamggm.rospocket.dev`, debug arm64-v8a.
- APK SHA-256: `9224938a0292ee279125cc8cf804b50ed02753d897c9049d3967bbde38d80151`.
- O APK instalado foi extraído de volta do dispositivo e seu SHA-256 é idêntico ao candidato.
- Logs, XMLs de UI, APKs, backup e preferências são evidências privadas fora do Git; não publicar endpoints nem dados do dispositivo.

## Limites e integração

As tentativas preliminares interrompidas por foco da cortina de notificações e preparo do campo desabilitado não foram contabilizadas como sucesso; o total acima vem apenas da execução final completa.

Não foram executados toggle físico de Wi-Fi, troca real de IP, endurance, execução em dispositivo 16 KiB nem teste em robô. Mudança de rede/IP possui regressões Android com shadows, não evidência de uma troca física. Home, reinício e troca de publisher não substituem esses gates de qualificação.

Não havia workflow GitHub Actions configurado no repositório no momento da validação. Os checks locais não são CI remota. A integração deve respeitar o gate comum da frente 01; este incremento não faz merge na integração nem libera o PR de integração para main.
