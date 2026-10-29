# ROS Pocket — orientação de trabalho

## Fonte de verdade

Trabalho atual autorizado: `docs/ROADMAP-v0.4.md`, integração `integration/v0.4`, feature `feat/v0.4-01-sensor-views`. As instruções específicas v0.3 abaixo são históricas, não destino das novas features. Leia o README e o roadmap do ciclo: [`docs/ROADMAP-v0.4.md`](docs/ROADMAP-v0.4.md) para o desenvolvimento atual; os roadmaps e finalizações anteriores preservam o histórico e os contratos herdados. Se uma instrução explícita do operador mudar o plano, atualize a documentação correspondente.

O projeto é um inspetor Android ROS 2/DDS **somente leitura**. Preserve DDS direto, o núcleo C++/Cyclone DDS e a ponte JNI; não faça migração de stack ou adicione bridge/servidor sem decisão explícita.

## Condução e escopo das tarefas

Orientações literais do operador, preservadas como referência de condução:

> vamos, um de cada vez. quero que faça o principal aqui do chat e delegue apenas tarefas menores

> Você está se perdendo muito nessas tarefas. Quero coisas mais simples e bem delimitadas, isso não é um app para nasa (ainda) ele tem tempo pra crescer e melhorar

> Deixa esse comportamento salvo no agents.md do projeto

- O ROS Pocket é um aplicativo em evolução, não um projeto de certificação aeroespacial. Prefira entregas simples, úteis e incrementais; não exija maturidade de produto final para encerrar cada melhoria.
- Trabalhe em **uma tarefa bem delimitada por vez**, com objetivo, limites e condição de término curtos. Conclua e relate esse resultado antes de abrir outra frente.
- O agente principal conduz a implementação e o diagnóstico na conversa. Delegue apenas tarefas menores e independentes, como uma revisão pontual; não crie uma cadeia de agentes para administrar trabalho simples.
- Use validação proporcional à mudança: reproduza o problema e teste diretamente o comportamento alterado e as regressões relevantes. Não transforme toda correção em reconstrução da infraestrutura de testes ou campanha completa de qualificação.
- Não encadeie automaticamente uma tarefa concluída em push, novas execuções de CI, campanhas físicas extensas, testes de rede, soak ou finalização de release. Essas ações pertencem a escopos separados e precisam estar incluídas no trabalho combinado com o operador.
- Separe bugs que impedem o uso e riscos reais de melhorias de robustez, casos extremos e funcionalidades futuras. Registre os itens adiáveis no backlog, em vez de ampliar silenciosamente a tarefa atual.
- Encerre com o que foi alterado, o resultado realmente verificado e as limitações restantes. Uma entrega limitada não significa aceite completo da versão: preserve resultados incompletos e não invente passes nem dispense silenciosamente requisitos de segurança ou release. Se o escopo de uma versão mudar, reconcilie o plano explicitamente.

## Ciclo v0.4 — implementação autorizada

- Integração `integration/v0.4`, feature `feat/v0.4-01-sensor-views`, sobre main c55fbb3.
- Escopo: [sensores visuais](docs/SENSOR-VIEWS.md), LaserScan 2D e Image/CompressedImage
  alternados. Um leitor visual adicional isolado dos quatro watches, mesmo domínio
  ROS de rede via ExternalDomainId; não elevar teto da telemetria.
- Principal implementa sequencialmente, revisão independente apenas de recortes.
  TDD, fixtures ROS isoladas, Views/Canvas/decode e build ARM64 hooks OFF.
- A implementação original não autorizava ADB/push/merge/release automaticamente.
  Depois, o operador autorizou webcam/teste físico no Ryzen e, após 6/6 PASS,
  merge na main e release v0.4.0. Reutilizar o APK físico sem recompilar; CI e
  documentação posteriores não mudam sua identidade interna 0.4.0-dev.
  Isso não autoriza rede/soak nem campanhas físicas adicionais.

## Ciclo v0.3 — histórico do fechamento autorizado

- A base é **`integration/v0.3`**. Sequência: **00 reconciliação → 01 navegação → 02 perfis locais → 03 fechamento proporcional**. Armazenamento e aplicação são passos internos da entrega de perfis.
- Navegação, perfis e candidato foram integrados. O [smoke físico curto](docs/SMOKE-v0.3.md) do APK público passou 8/8; o operador autorizou separadamente a promoção para `main`. A v0.3 fecha como protótipo de bancada, sem nova feature/campanha automática.
- Confira status, branch, histórico, worktrees, remoto e URL real de push antes de editar/publicar; preserve alterações existentes.
- Inicie cada incremento na `origin/integration/v0.3` atualizada, com PR para **`integration/v0.3`**, nunca para v0.2 ou diretamente para `main`.
- O padrão é sequencial; implementação paralela exige autorização específica e worktrees isoladas. Não faça implementações concorrentes no mesmo checkout.
- Incorpore a integração atualizada por merge normal, sem rebase/force-push compartilhado. A reconciliação da base preserva a ancestralidade de `main`: não usar squash para descartar essa relação. Incrementos comuns podem usar squash após revisão e CI; conferir novamente o SHA integrado.
- Navegação/perfis não criam sessão ou worker DDS paralelo, quinto watch ou dados salvos classificados como live. Aplicar perfil exige estar desconectado; conectar continua ação explícita.
- LaserScan, exportação e lotes numéricos são backlog fora desta versão, não opcionais a empilhar no último incremento.
- O CI atende PRs, mas o filtro de push de `integration/v0.3` permanece pendente: o operador escolheu publicar base/documentação **sem ampliar permissões** do login GitHub, que não possui `workflow`. Não alterar YAML/credenciais silenciosamente nem afirmar que todo push da integração dispara CI.
- A promoção `integration/v0.3` → `main` está autorizada após aceite físico e CI da árvore exata. Usar merge normal, preservar a documentação da main e conferir SHA/árvore. Essa autorização não inclui mover tag, trocar o APK testado, publicar nova release ou instalar de novo no tablet.

## Histórico da v0.2 — não reabrir a campanha

- A [PR #1](https://github.com/lamggm/ros2_pocket/pull/1) foi mergeada em `main` no commit `c659b8521c093aacc3ccecd0855c07727398db2b`; o [CI pós-merge](https://github.com/lamggm/ros2_pocket/actions/runs/40515533393) passou.
- A entrega autorizada é **protótipo de bancada**, conforme `docs/FINALIZATION-v0.2.md`. Qualificação extensa, Wi-Fi/IP e soak permanecem backlog não validado; não converter pendências em PASS nem alterar os validadores.
- Não levar features v0.3 para `integration/v0.2` ou tratar instruções antigas de PR draft como estado atual.
- Evidência física anterior conserva seu SHA/APK e alcance: merge/documentação nova não a transforma em teste do novo candidato.

## Validação e operação

- Para mudanças de comportamento, use TDD: teste falhando, implementação mínima e teste passando. Exercite falhas relevantes, não apenas compilação.
- Distinga teste portável, fixture DDS, CI e teste físico. Home/restart não prova perda real de Wi-Fi ou mudança de IP.
- Não marque dados antigos/assinatura inativa como recebendo. Preserve erros até recuperação real, buffers, limites de assinaturas e contratos herdados.
- Preserve fences de sessão/reader/applied epoch e teardown idempotente. Trocar seção não aciona `onStop`; sair do app obedece ao lifecycle vigente.
- Fixtures usam domínios isolados; nunca publique comandos, altere parâmetros, invoque serviços/actions ou controle movimento para provar conectividade.
- Não faça duas automações concorrentes no tablet. Atualização somente in-place, package/certificado compatíveis, sem uninstall/clear data e dentro de escopo físico autorizado.
- Não altere VPN, firewall, credenciais ou acesso remoto automaticamente. Testes de Wi-Fi exigem recuperação independente e autorização para interrupção.
- CI público usa runners hospedados padrão, permissões mínimas e retenção controlada. Não habilite larger runners pagos nem exponha host pessoal como runner irrestrito de PRs públicos.
- Não publique secrets, chaves de assinatura, APKs em commits, toolchains, endereços de dispositivos, logs privados ou dumps de interface. `.gitignore` não remove segredos do histórico.
- Registre SHA, verificações realmente executadas e limitações; código/CI verde não provam entrega física. Componentes isolados e fixtures sintéticas devem ser identificados como tais, sem inventar respostas ROS/Android.

## Limites do ciclo

Parâmetros/RPC, serviços/actions, teleop/publicação, rosbag, imagens/vídeo, LaserScan, RViz/mapas/TF, tipos customizados dinâmicos, exportação e lotes numéricos estão fora da v0.3 atual. Mudanças exigem decisão explícita e atualização do roadmap.
