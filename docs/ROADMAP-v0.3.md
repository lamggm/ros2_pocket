# ROS Pocket v0.3 — plano e sequência de integração

**Estado:** preparação autorizada de `integration/v0.3` e documentação; nenhuma implementação de produto autorizada por este documento. A sequência abaixo é a recomendação registrada para o próximo ciclo. Recursos opcionais continuam sujeitos a decisão explícita.

**Objetivo:** melhorar o uso cotidiano de bancada com navegação organizada e perfis completos, sem repetir entregas da v0.2.

**Arquitetura:** preservar Java Android → JNI/C++ → Cyclone DDS, operação somente leitura e o worker DDS serializado. Navegação e armazenamento de configuração não criam Session, executor ou readers paralelos.

## 1. Base e separação dos ciclos

- Base pública usada para preparar esta branch: `integration/v0.2` em `7b2dc76de3679935fac5adf69839818e8ca5a914`, consultada em 2026-10-25.
- `main` nessa consulta: `295753e8ee5de83da458a304fb139a5c35228557`.
- O PR [#1, integration/v0.2 → main](https://github.com/lamggm/ros2_pocket/pull/1) estava aberto e draft. Criar a v0.3 não conclui, substitui nem autoriza merge da v0.2.
- Esta base é **provisória para documentação**, não a declaração de uma v0.2 aceita. Trabalho local ainda não publicado não está incluído nem foi qualificado aqui.
- Antes de implementar a v0.3, concluir a v0.2 e incorporar seu estado final aceito nesta branch por merge normal. Não fazer rebase/force-push da integração publicada.
- Não levar mudanças v0.3 para `integration/v0.2`. Não transferir gates pendentes para a versão seguinte.

## 2. O que já pertence à v0.2

A conferência considerou o plano original, o plano de fechamento e sua divisão posterior nas frentes 09–15; a divisão em sete frentes substitui a anterior em quatro, não se soma a ela. O resumo abaixo torna essa fronteira consultável no próprio repositório.

- **01:** build reproduzível, wrappers, regressões, CI e proveniência de APK.
- **02:** diagnóstico de conexão, recuperação, erro persistente e stale; preservar configuração de conexão.
- **03:** Explorer, busca/filtros/favoritos, grafo real por GID e QoS anunciado.
- **04:** `/rosout` com filtros, pausa, clear e buffer limitado.
- **05:** mensagens estruturadas e caminhos numéricos dos tipos declarados.
- **06:** quatro watches independentes, budgets e isolamento sob falha.
- **07:** gráficos numéricos por latest snapshots, sobreposição, pausa, rings limitados, gaps e relógios distintos; refresh visual separado de intake.
- **08:** matriz de qualificação e campanha física do candidato.
- **09:** snapshot QA coerente/read-only, quatro contadores acessíveis sem rolagem e estatísticas dos gráficos, sem novo poll DDS.
- **10:** harness seguro de UI/IME/viewport/popups, seletores e captura fresca.
- **11:** coletor fail-closed e métricas reais de soak.
- **12:** falhas reais de criação de tópico e reader na Session de produção, com recuperação e isolamento.
- **13:** qualificação multiwatch: taxas/QoS mistos, quinta recusa, remoção e sobrevivência dos demais readers/logs.
- **14:** qualificação de gráficos: não finitos/gaps, relógios, pausa/intake, janela/eviction e desenho real.
- **15:** identidade/versão, reconciliação, revisão e candidato final.

Wi-Fi físico, mudança real de IP, soak e aceite do operador continuam gates da campanha v0.2. Os budgets já estabelecidos não são relaxados aqui: quatro watches a 2 Hz e gráfico visível por 30 min; PSS pico ≤256 MiB e crescimento ≤32 MiB; CPU média ≤25% e pico ≤80% de um core; intake por watch ≥90% por intervalo; bateria perde ≤10 pontos percentuais, fisicamente desplugada.

Fontes versionadas: [roadmap v0.2](ROADMAP-v0.2.md), [qualificação](DEVICE-QUALIFICATION-v0.2.md), [contrato QA](DEVICE-QA-CONTRACT.md), [harness](TABLET-HARNESS.md), [multiwatch](MULTIWATCH.md) e [gráficos](LIVE-PLOTS.md). A lista de compromissos não afirma que todas as frentes estejam implementadas ou aceitas na base provisória.

## 3. Sequência recomendada — não inverter por conveniência

```text
Preparação atual: integration/v0.3 + este plano (somente documentação)
  ↓
00 — v0.2 aceita + sincronização da base + confirmação do escopo
  ↓
01 — navegação de bancada
  ↓
02 — modelo e armazenamento de perfis completos
  ↓
03 — seleção e aplicação explícita dos perfis na UI
  ↓
04 — reconciliação, candidato e qualificação v0.3
  ↓
aceite do operador → autorização de merge → integration/v0.3 → main
```

Os nomes de subbranches abaixo são **propostos, não criados**. Cada uma deve nascer da `origin/integration/v0.3` atualizada, com PR para `integration/v0.3`, nunca para v0.2 ou diretamente para `main`.

### 00 — Fechar a base e confirmar escopo

**Pré-condição para implementação:** v0.2 com revisão, CI, qualificação física e aceite próprios. Conferir o SHA aceito e incorporar a versão final por merge normal; resolver contratos semanticamente e preservar a documentação v0.3.

1. Conferir `AGENTS.md`, status, branch, log, worktrees e remoto atualizado.
2. Verificar a conclusão real da v0.2; não inferir aceite por CI ou branch existente.
3. Incorporar seu estado final aceito e registrar o SHA efetivamente usado pela v0.3.
4. Confirmar navegação + perfis como escopo do ciclo antes de implementar. LaserScan pode substituir essa prioridade apenas por decisão explícita e revisão deste plano.
5. Publicar planos detalhados de testes/arquivos para os incrementos aprovados.
6. Preparar o CI para a nova integração: o workflow herdado filtra pushes em `main`, `integration/v0.2` e uma branch CI; **push em `integration/v0.3` não dispara automaticamente esse workflow**. No primeiro incremento de implementação, adicionar a integração ao filtro de push, preservando `pull_request`, runners hospedados, permissões mínimas e gates. Não alterar credenciais ou ampliar escopos silenciosamente para isso.

**Aceite:** base aceita identificada, escopo confirmado e caminho de validação do SHA integrado definido. A branch de planejamento, sozinha, não passa este gate.

### 01 — Navegação de bancada

**Branch proposta:** `feat/v0.3-01-bench-navigation`.

**Diferença nova:** separar conexão, Explorer, watches/gráficos e logs em seções/telas navegáveis. Reutilizar a observabilidade v0.2, não recriar contadores ou o contrato QA.

**Arquivos candidatos:** `app/src/main/java/com/lamggm/rospocket/{MainActivity,ExplorerView,PlotView,StructuredMessageView,RosoutConsoleView}.java`, recursos de UI/IDs e testes reais desses componentes. Extrair somente composição necessária; nenhuma migração geral de stack.

**Ordem interna:** registrar o fluxo atual → testes RED de navegação → composição mínima → GREEN → regressão de lifecycle/QA/harness → retrato e paisagem no tablet.

**Aceite:** trocar de seção não reinicia readers, não perde seleções nem altera identidades/gerações; pausa e quatro watches permanecem independentes. Diagnóstico continua acessível; QA distingue gráfico visível de seção oculta. Adaptar o harness aos seletores reais sem enfraquecer suas guardas.

### 02 — Modelo e armazenamento de perfis

**Branch proposta:** `feat/v0.3-02-profile-storage`. **Integra depois de 01.**

**Diferença nova:** conjuntos locais nomeados com domínio, peer opcional, até quatro topic/type/QoS e campos de gráfico. Isso vai além de preferências de conexão e favoritos já existentes.

**Arquivos candidatos:** novas classes de modelo/armazenamento e testes em `app/src/{main,test}/java/com/lamggm/rospocket/`; consultar `InputRules.java`, `ConnectionPolicy.java`, `WatchSlots.java` e `PlotController.java`. Nomes finais das classes e limites quantitativos serão definidos antes do RED, não tratados como APIs existentes.

**Ordem interna:** schema/versionamento/limites → RED de round-trip, corrupção, versão desconhecida e escrita interrompida → implementação mínima → GREEN → compatibilidade/migração das preferências anteriores.

**Aceite:** persistir somente configuração, nunca valores, verde de recepção, handles DDS ou gerações; corrupção não destrói configuração válida anterior. Leitura/gravação não cria subscription nem bloqueia o worker DDS. Sem conta, cloud ou sincronização externa.

### 03 — Seleção e aplicação de perfis

**Branch proposta:** `feat/v0.3-03-profile-workflow`. **Depende de 01 e 02 integradas.**

**Arquivos candidatos:** `MainActivity.java`, `WatchSlots.java`, `ConnectionPolicy.java`, `PlotController.java`, componentes de perfis e testes host/Android correspondentes.

**Ordem interna:** RED de criar/selecionar/restaurar/aplicar → UI mínima → GREEN → restart/Home, tipo divergente, tópico ausente e troca com conexão ativa.

**Aceite:** processo reiniciado restaura o perfil como configuração desconectada. Aplicação requer ação explícita, valida os dados e aguarda descoberta compatível; tópico ausente/type divergente fica pendente/erro explícito. Troca durante sessão ativa tem política deliberada e testada, sem misturar readers antigos e novos. Não criar quinto watch nem subscription oculta; configurações não viram amostras live.

### 04 — Reconciliação, candidato e qualificação

**Branch proposta:** `test/v0.3-04-device-qualification`. **Depende de 01–03 integradas e opcionais explicitamente aprovados, se houver.**

**Entregas:** revisão da integração, identidade de versão coerente (revalidar versionCode antes de escolher), documentação final, matriz v0.3 com budgets declarados e candidato fonte/APK/certificado rastreável. Sem feature de última hora.

**Arquivos candidatos:** `app/build.gradle`, README, documentação de perfis/navegação, novo plano de qualificação v0.3, ferramentas/testes existentes estritamente necessários. Não sobrescrever evidências nem critérios históricos da v0.2.

**Aceite:** CI do SHA integrado exato, revisão sem bloqueadores, APK instalado conferido e regressão física das novas jornadas + contratos herdados. Medir impactos de recursos com carga e budgets definidos antes da campanha. Host/Canvas/fixtures não substituem NDK e tablet físico. Mudança de código/artefato exige regressão afetada e nova vinculação de evidência. Aceite explícito do operador e autorização de merge são passos distintos.

## 4. Candidatos opcionais — sem implementação automaticamente autorizada

Não há dependência obrigatória destes itens para 01–03. Antes de inserir qualquer um, revisar a sequência e seus budgets; não empilhar tudo na frente 04.

- **LaserScan 2D:** spike de `sensor_msgs/msg/LaserScan` com arrays, tamanho CDR, carga e ambos os RMWs antes de decoder/Canvas. Visualização no frame da mensagem, sem TF/mapa/SLAM inventado. Manter teto 64 KiB e limite de readers; declarar uso de um dos quatro slots ou submeter nova arquitetura a revisão. Se virar prioridade central, substituir o ciclo proposto por decisão do operador, não criar uma segunda campanha implícita.
- **Exportação para o usuário:** arquivo sanitizado por seletor/compartilhamento Android, sem upload automático. Consumir contratos existentes; não duplicar dump QA, coletor ou analisador. CSV/JSON deve declarar snapshots/decimação, preservar inteiros e tratar não finitos/Unicode/injeção de fórmulas. Não chamar de rosbag.
- **Lotes numéricos:** somente após demonstrar necessidade de resolução temporal que latest-snapshot não atende. Definir bytes/amostras/overflow e medir contra baseline; não chamar separação intake/UI ou medição básica de desempenho de feature nova. Lotes não garantem recepção DDS sem perdas.

## 5. Regras de integração e validação

- Cada incremento aprovado: branch/worktree própria, RED → GREEN, revisão, incorporação da integração atualizada por merge e PR com base `integration/v0.3`.
- Integrar na ordem 01 → 02 → 03 → 04. Testes puros de armazenamento podem ser preparados em paralelo após 00 e com autorização, sem edições concorrentes na UI; isso não altera a ordem de merge.
- Squash por incremento e verificar novamente o SHA integrado; sem force-push compartilhado. Não abrir nem remover draft do PR final automaticamente nesta preparação.
- Um futuro PR `integration/v0.3` → `main` fica draft e sem auto-merge até qualificação e aceite. Nenhuma mudança no PR v0.2 é autorizada por este plano.
- Preservar fences de sessão/reader/applied epoch, erros persistentes, teardown idempotente, buffers e relógios. Nunca ressuscitar valor salvo como live.
- Usar os wrappers reais na base aceita, com pré-requisitos/toolchains presentes: `bash tools/test.sh --portable`, `bash tools/test.sh --fixture`, `bash tools/build.sh --offline`; bootstrap/build online quando necessário e autorizado. Conferir seleções não vazias, XMLs e exit codes. A preparação documental não executa nem declara esses gates verdes.
- Fixtures de ambos os RMWs isoladas, sem tópicos de comando de robô. Uma fila exclusiva para o tablet, sem automações concorrentes.
- Mesmo package/certificado autorizado e atualização sem uninstall; artefatos/evidências privadas fora do Git. Nenhuma instalação foi solicitada nesta preparação.
- Não alterar VPN, firewall, DNS ou acesso remoto. Testes físicos de rádio/IP só com recuperação independente e autorização específica.

## 6. Fora do ciclo

Publicação/teleop, escrita ou leitura de parâmetros sem spike RPC aprovado, serviços/actions, rosbag, imagens/vídeo, mapas/TF/RViz completos, tipos customizados dinâmicos, descoberta automática entre sub-redes, SROS2 e migração ornamental de stack. A v0.3 continua um inspetor somente leitura.
