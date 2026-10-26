# ROS Pocket — orientação de trabalho

## Fonte de verdade

Antes de planejar ou implementar uma tarefa, leia [`docs/ROADMAP-v0.2.md`](docs/ROADMAP-v0.2.md) e o README. O roadmap contém a sequência recomendada, o escopo de cada incremento e seus gates de aceitação. Não substitua essa sequência por outra baseada em memória de conversa. Se uma instrução explícita do operador mudar o plano, atualize a documentação correspondente.

O projeto atual é um inspetor Android ROS 2/DDS **somente leitura**. Preserve DDS direto, o núcleo C++/Cyclone DDS e a ponte JNI; não faça migração de stack ou adicione bridge/servidor sem decisão explícita.

## Condução e escopo das tarefas

- O ROS Pocket é um aplicativo em evolução, não um projeto de certificação aeroespacial. Prefira entregas simples, úteis e incrementais; não exija maturidade de produto final para encerrar cada melhoria.
- Trabalhe em **uma tarefa bem delimitada por vez**, com objetivo, limites e condição de término curtos. Conclua e relate esse resultado antes de abrir outra frente.
- O agente principal conduz a implementação e o diagnóstico na conversa. Delegue apenas tarefas menores e independentes, como uma revisão pontual; não crie uma cadeia de agentes para administrar trabalho simples.
- Use validação proporcional à mudança: reproduza o problema e teste diretamente o comportamento alterado e as regressões relevantes. Não transforme toda correção em reconstrução da infraestrutura de testes ou campanha completa de qualificação.
- Não encadeie automaticamente uma tarefa concluída em push, novas execuções de CI, campanhas físicas extensas, testes de rede, soak ou finalização de release. Essas ações pertencem a escopos separados e precisam estar incluídas no trabalho combinado com o operador.
- Separe bugs que impedem o uso e riscos reais de melhorias de robustez, casos extremos e funcionalidades futuras. Registre os itens adiáveis no backlog, em vez de ampliar silenciosamente a tarefa atual.
- Encerre com o que foi alterado, o resultado realmente verificado e as limitações restantes. Uma entrega limitada não significa aceite completo da versão: preserve resultados incompletos e não invente passes nem dispense silenciosamente requisitos de segurança ou release. Se o escopo de uma versão mudar, reconcilie o plano explicitamente.

## Branches e integração

- A base de desenvolvimento deste ciclo é **`integration/v0.2`**, não `main`.
- Inspecione `git status`, branch atual e histórico antes de editar; preserve alterações existentes.
- Antes de abrir a próxima branch, busque o remoto e confira a integração atualizada. Crie a branch do incremento a partir de `origin/integration/v0.2` limpa e atualizada.
- O padrão de execução é sequencial, uma frente delimitada por vez. Paralelismo de implementação só com autorização explícita para a tarefa atual, em branches e worktrees isoladas; a possibilidade histórica de paralelizar incrementos não é autorização permanente.
- Antes de integrar uma frente, incorpore a integração atualizada na branch e valide os contratos/dependências do roadmap. Para branches públicas compartilhadas, prefira merge de `origin/integration/v0.2`; não faça rebase/force-push que reescreva trabalho de outro agente.
- Quando outra frente ainda não entregou um contrato necessário, implemente componentes isolados e testes unitários explicitamente identificados como tais. Não invente respostas ROS/Android para declarar a integração pronta.
- PRs de incrementos devem apontar para **`integration/v0.2`**. Teste e revise antes de mergear; use squash para cada incremento.
- Depois de integrar um incremento, valide novamente o conjunto e identifique o APK candidato pelo SHA exercitado.
- O PR de **`integration/v0.2` → `main`** permanece em draft e sem auto-merge até a qualificação final e aceite do operador no tablet. Não faça push/merge direto em `main` como atalho.
- O merge final deve preservar os incrementos auditáveis; não reescreva a história pública.
- Não faça duas implementações simultâneas no mesmo checkout ou duas automações concorrentes no tablet. Use worktrees isoladas para trabalho paralelo independente.

## Validação e operação

- Para mudanças de comportamento, use TDD: teste falhando, implementação mínima e teste passando. Exercite os modos de falha relevantes, não apenas compilação.
- Distinga teste portável, fixture DDS, CI e teste físico. Um resultado de Home/restart não prova perda real de Wi-Fi ou mudança de IP.
- Não marque dados antigos ou assinatura inativa como recebendo. Preserve erros até recuperação real; limite buffers, assinaturas e uso de recursos conforme o roadmap.
- Fixtures de bancada usam domínios isolados; nunca publique comandos, altere parâmetros, invoque serviços/actions ou controle movimento para provar conectividade.
- Não altere VPN, firewall, credenciais ou acesso remoto automaticamente. Um teste de Wi-Fi não pode derrubar o único canal de controle sem recuperação combinada.
- CI público usa runners padrão hospedados pelo GitHub, permissões mínimas e retenção controlada. Não habilite larger runners pagos nem exponha um host pessoal como runner irrestrito para PRs públicos.
- Não publique secrets, chaves de assinatura, APKs em commits, toolchains, endereços de dispositivos, logs privados ou dumps de interface. Consulte `.gitignore`; ele não remove segredos já presentes no histórico.
- Não declare uma função entregue porque há código ou CI verde: registre o SHA, as verificações realmente executadas e as limitações não exercitadas.

## Limites do ciclo

Parâmetros/RPC, serviços/actions, teleop/publicação, rosbag, RViz/mapas/TF e tipos customizados dinâmicos estão fora da v0.2. Mudanças nesse limite exigem decisão explícita e atualização do roadmap.
