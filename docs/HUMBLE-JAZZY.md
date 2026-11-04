# Compatibilidade Humble/Jazzy — ciclo v0.8

[Roadmap](ROADMAP.md) · [Issue #18](https://github.com/lamggm/ros2_pocket/issues/18)

## Estado e primeira entrega

Início autorizado pelo operador após a v0.7 publicada. Base:
`c836ee4872c95ec58353f4728aa94eba356a9dee`. Principal implementa sequencialmente;
delegação limitada a auditoria de fontes/revisão pontual.

Primeiro incremento: **spike Humble real, isolado e sem mudança produtiva**.
Responder, antes de implementar:

1. O núcleo v0.7 descobre endpoints e recebe String/Float64 e `/rosout` em Humble
   com Fast DDS e Cyclone DDS? Registrar versão/RMW/source e resultados por capacidade.
2. Os layouts de grafo podem ser selecionados de forma demonstrável e limitada,
   sem presumir distribuição pelo nome `ros_discovery_info`, misturar GIDs ou
   interpretar um formato desconhecido? Comparar schemas e metadados reais.

Não marcar uma combinação PASS por analogia. Descoberta, amostra tipada, rosout,
nomes/namespaces e associação de endpoints são provas separadas. Jazzy permanece
baseline a regredir, não suporte Humble automaticamente herdado.

## Matriz host verificada no spike

O núcleo v0.7 foi reconstruído sem mudança produtiva em Ubuntu22.04, com
CycloneDDS0.10.5 fixado no commit original. Produtor rclpy e leitor nativo reais
rodaram em processos separados, em contêineres privados `--network none`:

- Humble / Fast DDS: endpoints, String, Float64 e rosout PASS; grafo/associações
  ausentes no núcleo atual. RMW6.2.10; Fast DDS2.6.12; rmw_dds_common1.6.0.
- Humble / Cyclone DDS: mesmas capacidades PASS e mesma ausência de grafo.
  RMW1.3.5; Cyclone0.10.5; rmw_dds_common1.6.0.
- Jazzy / Fast DDS: endpoints, String, Float64, rosout, nó e associações de
  endpoints PASS. RMW8.4.4; Fast DDS2.14.7; rmw_dds_common3.1.1.
- Jazzy / Cyclone DDS: mesmas capacidades PASS, incluindo nó e associações.
  RMW2.2.4; Cyclone0.10.5; rmw_dds_common3.1.1.

São leituras representativas, não todos os tipos padrão nem qualificação Android,
LAN, restart de grafo ou soak. Versões completas dos pacotes, imagens por ID,
fontes/hashes e 60 snapshots nativos por combinação estão nas evidências locais.

## Resultado da seleção de formato: replanejamento necessário

O protótipo GID24 leu o nó Humble em ambos os RMWs e conferiu cauda zero e GUID do
participante. Não está integrado no app. Os produtores testados não forneceram
informação DDS de tipo utilizável: a API retornou sucesso com ponteiro nulo.
Validação forçada não casou leitores; fallback nominal casou os dois layouts e o
GID16 leu zero nós silenciosamente. Isso **não demonstra seleção automática segura**.

Recomendação para um incremento separado: seletor explícito de formato de grafo
Humble24/Jazzy16, configuração persistida e limitações claras. Preservar o default
Jazzy e compatibilidade dos perfis existentes; não escolher parser por resultados
plausíveis. O roadmap manda replanejar diante dessa ambiguidade, antes de estender
código produtivo. **Decisão do operador: implementar seletor manual Humble/Jazzy,
persistido no perfil**, confirmada pelo formulário de decisão após o spike.
O escopo está replanejado: tokens estritos `jazzy`/`humble`, default Jazzy em
perfis antigos, schema3 para escritas novas e leitura/migração em memória de
schemas1/2. Aplicação continua desconectada; nenhum seletor muda uma sessão viva.
Somente o formato escolhido cria o leitor de grafo. O app não promete detectar a
distribuição de cada produtor nem suporte a domínios mistos. Escolha desconhecida
é rejeitada e incompatibilidade permanece explícita; preservar validação de
participante/papel, GID24 com cauda zero e limites do grafo. Na decisão inicial,
app e tablet ainda estavam na v0.7; o avanço da implementação está registrado abaixo.

## Implementação candidata e evidência host

O candidato **0.8.0-dev/code8** implementa o seletor em Conexão, bloqueado durante
uma conexão desejada, com formato capturado na Config imutável do worker. Salvar,
aplicar, renomear e exportar/importar preservam o formato. Escritas são schema3;
catálogos1/2 e envelopes portáteis2 continuam legíveis com defaultJazzy.
O carregamento não regrava o arquivo. O JNI antigo continua no defaultJazzy.

Um único leitor de grafo é criado para o formato selecionado. Os identificadores
C/C++ de GID24 são isolados dos de GID16, sem renomear tipos/metadados DDS no fio.
Humble exige cauda zero nos GIDs de participante e endpoints; preserva os limites
de128nós/1024referências, validação de participante/papel e ownership do writer.
Isso não converte handles de APIs RMW em GUIDs.

Evidência executada sobre a fonte candidata, separada do baseline anterior:

- Matriz real isolada das quatro combinações: String, Float64, rosout, nó e
  associações de endpoints PASS. A fixture está em `tests/graph_format_interop.py`;
  ela exige namespace privado com somente loopback e registra probe/hash/RMW.
- Humble com seleção manual Jazzy: recepção tipada segue saudável; grafo sem nós
  inventados e estado **unconfirmed**. Não há fallback para outro parser.
- Humble/FastDDS e Humble/Cyclone: SIGKILL do produtor, expiração de lease,
  remoção do nó/GUIDs antigos e reinício com novos GUIDs associados PASS,
  mantendo as mesmas identidades de Session/readers nativos.
- 497testesJava, zero falhas/erros, um skip legado opt-in; 21CTests portáteis e
  testesPython executados. Lint zero erros/fatais,123avisos.
- Novo entrypoint CTest de grafo Jazzy passou em cada RMW da fixture existente.
- Ponte JNI Linux real inicia ambos os formatos e rejeita quatro escolhas
  inválidas; não é prova de carregamento NDK ou execução Android.
- Revisão independente estática aprovada sem bloqueadores; scan de fonte sem leaks.

## Android: build e smoke físico direcionado

O mesmo candidato **0.8.0-dev/code8**, ARM64, assinatura debug compatível e
capacidade diagnóstica OFF foi buildado pelo wrapper offline. O APK instalado e
puxado novamente do tablet tem SHA-256:

```text
66d1f97ef0a6a7eb08fc367981e37aa1717b6fd09e1eb36d4eb17c6b79ab8958
```

Fonte do build: `096a7d575a3f53a466b18b6ed36e77d01e3073af`. Mudanças documentais
posteriores não recompilam nem substituem esses bytes.

Em **2026-11-04**, a rodada física completa `attempt-02` passou **4/4**, com
produtores DDS nativos sintéticos locais no próprio Android, no domínio da
bancada:

1. Humble/GID24: nó do produtor observado, recepção Float64 avançando no mesmo
   reader (68→78) e perfil schema3 salvo com o formato Humble.
2. Durante conexão: seletor de formato desabilitado e Apply rejeitado, sem
   interromper a recepção.
3. Jazzy/GID16: novo nó/sessão, recepção Float64 avançando no mesmo reader
   (67→77) e perfil salvo com o formato Jazzy.
4. Apply do perfil Humble desconectado e reinício real do processo: formato
   Humble restaurado, botão Conectar e nenhuma conexão automática.

Instalação somente in-place, sem uninstall/clear data e com UID preservado.
Readback independente confirmou o APK instalado, restauração exata dos sete
caminhos de configuração (incluindo ausências), app parado e remoção do produtor
e dos arquivos de fixture. Foram recolhidos e verificados por tamanho/hash
267 arquivos privados de evidência; três screenshots foram inspecionadas.
O catálogo original estava ausente: migração de catálogo legado continua
coberta pelos testes host, **não exercitada fisicamente** nesta rodada.

A tentativa01 permanece incompleta: keyguard bloqueou o preflight antes de
backup/install, com zero casos funcionais. Ela não foi somada ao resultado 4/4.
O operador autorizou separadamente a política persistente de inatividade:
timeout máximo de 2147483647 ms na bateria (aproximadamente 24,9 dias), stay-on
configurado para AC/USB/wireless e PIN/bloqueio manual preservados. O timeout
efetivo foi conferido; o stay-on alimentado não foi exercitado porque o tablet
estava na bateria. Essa política permanece aplicada por pedido explícito, sem
ser desfeita pelo cleanup do smoke.

**Alcance:** Android físico/JNI/Views e DDS sintético local PASS; matriz ROS real
Humble/Jazzy×FastDDS/Cyclone e lifecycle Humble PASS em contêineres host privados,
conforme seção anterior. São provas distintas. Não houve qualificação Android
com ROS real pela LAN, interrupção RF/VPN, soak ou suporte a domínios mistos.
Nenhum push/PR/CI remoto/merge/release v0.8 integra este checkpoint; promoção é
uma etapa separada.

## Limites

- DDS direto e app somente leitura; manter buffers, lifecycle, clocks e watches.
- Formato desconhecido deve ficar explicitamente não suportado. Sem nomes de nós
  inventados, bridge, RPC, tipos customizados ou heurística permissiva de CDR.
- Se seleção segura exigir alteração grande ou continuar ambígua, registrar o
  bloqueio e replanejar antes de estender a implementação.
- Fixtures de host sem rede externa/LAN; provisionamento baixa dependências
  públicas separadamente. Não tocar robô, VPN, firewall, RF, soak ou v0.9.
- Tablet disponível não é PASS. Descobrir transporte/modelo/user/power/keyguard
  novamente antes de ação física. Sem uninstall/clear data, alteração de PIN ou
  uso de baseline antigo como backup do novo ciclo.
- APK v0.7 instalado e histórico de evidências permanecem preservados. Versão8,
  build/revisão, smoke e promoção só depois da estratégia validada.
