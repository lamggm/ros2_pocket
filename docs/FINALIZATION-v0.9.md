# v0.9 — estabilidade de bancada

[Projeto](../README.md) · [Escopo e histórico](STABILITY-v0.9.md) ·
[Promoção, CI e release: issue #25](https://github.com/lamggm/ros2_pocket/issues/25)

## Alcance de fechamento

O operador autorizou concluir os testes restantes e fechar a versão até merge e
release. O aceite proporcional desta entrega é **protótipo de bancada**:
correções reproduzidas do harness, budgets fixados antes da medição, soak físico
local de 30 minutos, Home/retorno e reinício real do processo no candidato exato,
regressões e revisão, CI da PR/pós-merge e publicação dos mesmos bytes físicos.
Não há grande feature nova nem alteração do caminho de recepção do app.

**RF/IP não foi executado nem aprovado.** O requisito condicionado a recuperação
independente e autorização de interrupção não foi satisfeito; fica explicitamente
fora do aceite desta release e aberto na [issue #31](https://github.com/lamggm/ros2_pocket/issues/31).
Isso reconcilia o roadmap sem transformar Home em perda de Wi-Fi ou declarar
`ready=true` da qualificação histórica extensa. Não houve mudança de VPN,
firewall, DHCP ou acesso remoto. Nenhum novo ciclo começa por este fechamento.

## Identidade imutável

- Fonte limpa do app/build: `073821c4d69273863e9c510bbb177a9b6141c9e8`.
- Snapshot do build: `9b26e2347f7a39ed45ab4076e7282762fabac6556edc0d0af437e5f532049297`.
- Harness físico/host revisado: `ca69285ed69deec95abd2ce6cd741aae0aea4834`.
- APK: **0.9.0-dev/code9**, `com.lamggm.rospocket.dev`, ARM64/API28+,
  assinatura Android Debug compatível, hooks diagnósticos **OFF**.
- SHA-256: `fce2b53d6842e5ba13ac51de47a7a2d346c49b150d8ba3e51bb6d05f7c4cbed0`.
- Certificado SHA-256: `beb0d55eb6b42476aef6f769f20c0c887eb7b58562df83c4e32fc0396fe3aa5d`.

Os ajustes posteriores a073821c são de harness/testes/documentação; o payload do
app permanece igual. Build offline, ZIP entries fora de `META-INF/`, certificado
e APK instalado foram conferidos. Metadados do descendente foram emitidos pelo
produtor oficial após verificar build/fonte, sem editar proveniência manualmente.
O asset público `app-debug.json` identifica a fonte original do APK; o manifesto
separa fonte do app, harness e commit futuro de publicação. **O APK do CI não
substitui o candidato físico**, e documentação nova não é rebuild cosmético.

## Reparos e host

1. Probe de grafo: conservar stdout/stderr parcial no `TimeoutExpired`, sem
   modificar timeout20s, falha original ou cleanup. RED/GREEN com bytes,
   Unicode e saída ausente; revisão independente aprovada.
2. Primeira janelaCPU: baseline após uma observação completa de priming,
   separada/não medida, na mesma fase dos pontos posteriores. Não preencher
   CPU com zero nem somar priming à duração; limites de13..17s preservados.
3. Desenho pendente: modo observacional explícito para frames coerentes,
   mantendo validação estrita default. Validar cada estrutura, identidade,
   frescor e continuidade intermediária; somente aceitar captura posterior ao
   export fence com desenho concluído, dentro do prazo original. O raw real
   rejeitado da tentativa02 continua rejeitado na aceitação estrita.

Suite Python fresca **303 testes**, zero falhas/erros/skips; coletor19 testes;
controles independentes em relatório próprio, sem somá-los artificialmente.
Wrapper offline aprovado; **21 CTests portáteis**. XML Java retido: **497 testes,
zero falhas/erros, um skip opt-in legado**; no rebuild do harness a tarefa estava
UP-TO-DATE, não foi alegada nova execução Java. Lint do candidato: zero erros/
fatais e123avisos. O CI público executa seus próprios gates no head publicado.
Revisões dos reparos sem bloqueadores e auditoria de alcance independente.

## Soak físico oficial — PASS

SM-X400/Android16, mesmo APK congelado, quatro escritores Float64 DDS nativos
separados no próprio Android, solicitados2Hz cada, um gráfico finito inteiro,
visível e despausado. **Não é matriz ROS real pela LAN nem medição de rádio.**

- **121 amostras /1800,694606s contínuos**, intervalo original15s±2s.
- PSS pico **98283KiB**, crescimento **−6493KiB**; budgets262144/32768KiB.
- CPU média ponderada **10,1869%**, pico **11,2902%** de um core; budgets25/80%.
- Menor razão intake por watch/intervalo **0,97066188**, mínimo exigido0,90.
- Bateria: perda **3pp**, limite10pp; nenhuma alimentação física nos pontos.
- Os quatro contadores avançaram **165→3767** no intervalo medido.
- **487 leiturasQA**, cinco observações com desenho pendente; nenhuma delas
  virou amostra aceita. Todos os121 pontos retidos têm desenho concluído.

CLI oficial e replay independente da cópia transferida: **passed**, nenhum
blocker. Cleanup/readback independente confirmaram APK/UID, sete caminhos de
configuração por bytes/existência, settings preservados, app parado e remoção de
writers/feeder/fixture. **70 arquivos privados** recolhidos e verificados por
hash/tamanho/contenção/unicidade; não publicados.

Tentativa01 permanece incomplete/zero pontos pela fase inicialCPU; tentativa02
permanece incomplete/28pontos/405,255764s por captura pendente. Elas não foram
costuradas nem reclassificadas pela rodada03. Nenhum threshold foi afrouxado.

## Lifecycle físico — 2/2 PASS

Rodada completa separada, no mesmo APK e quatro escritores locais Android:

1. **Home/retorno:** mesmo PID, foreground=false enquanto fora do app, novas
   gerações de lifecycle/sessão, readers associados à nova sessão e recepção
   retomada. Payload Float64 visível **65→115**, não apenas label retido;
   domínio, peer e catálogo/perfil aplicados preservados.
2. **Reinício real do processo:** processo anterior parado e novo PID confirmado.
   Configuração restaurada, botão Conectar e nenhuma conexão automática; após
   ação explícita, quatro watches frescos/sem erro e payload novo **115→175**.

Readback independente da restauração repetiu hash/existência dos sete caminhos,
settings/UID/APK e ausência de produtores/fixture, com app parado. **175 arquivos
privados**, incluindo as três tentativas, transferidos e conferidos.

As duas primeiras invocações do harness continuam incomplete: uma exigia
indevidamente nome do perfil no status já conectado; a outra exigia geração de
reader globalmente crescente, embora ela seja local à sessão. Diagnósticos reais,
três controles do oráculo e cleanup separados foram preservados. O app e os
validadores oficiais não foram alterados para contornar essas falhas. A terceira
rodada completa é a fonte do2/2, sem somar passos das anteriores.

## Promoção e publicação

A [issue #25](https://github.com/lamggm/ros2_pocket/issues/25) e o manifesto público
registram a PR, SHA publicado, CI do head exato e CI pós-merge efetivos; este texto
não antecipa sucesso de um CI pendente. Antes de promover, comparar blobs/modos
dos arquivos protegidos com a fonte revisada, permitindo apenas documentação.
Após merge, conferir árvore e ancestralidade, publicar `v0.9.0` e baixar os cinco
assets públicos novamente para comparar hashes:

- `ros-pocket-v0.9.0-arm64-dev.apk`
- `app-debug.json`
- `source-provenance.json`
- `VALIDATION-v0.9.md`
- `SHA256SUMS`

A release mantém a identidade interna dev/code9 e signer debug. Sem uninstall,
clear data, troca de chave, rebuild para mudar rótulo, alteração de tags antigas
ou distribuição de logs/UI/preferências/endereços privados. Fechamento operacional
só após promoção, checks e downloads efetivamente verificados.

## Limitações que não viraram PASS

Interrupção RF/IP e recuperação de rede física continuam abertas na issue31.
Não há nova matriz Android/ROS real Humble/Jazzy pela LAN, certificação de16KiB,
qualificação universal de sensores, dispositivos/RMWs ou segurança de produção.
Visual/lotes não receberam feature nova: regressões host/CI e cenário numérico
finito do soak não substituem todas as campanhas históricas. Os gates antigos
não executados permanecem não validados em seus documentos, sem reabrir toda a
qualificação extensa para esta entrega proporcional de bancada.
