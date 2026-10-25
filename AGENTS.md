# ROS Pocket — orientação de trabalho

## Fonte de verdade

Antes de planejar ou implementar uma tarefa, leia o README e o roadmap do ciclo: [`docs/ROADMAP-v0.2.md`](docs/ROADMAP-v0.2.md) para v0.2; [`docs/ROADMAP-v0.3.md`](docs/ROADMAP-v0.3.md) para v0.3, mantendo os contratos herdados da v0.2. O roadmap contém a sequência recomendada, o escopo de cada incremento e seus gates de aceitação. Não substitua essa sequência por outra baseada em memória de conversa. Se uma instrução explícita do operador mudar o plano, atualize a documentação correspondente.

O projeto atual é um inspetor Android ROS 2/DDS **somente leitura**. Preserve DDS direto, o núcleo C++/Cyclone DDS e a ponte JNI; não faça migração de stack ou adicione bridge/servidor sem decisão explícita.

## Preparação da v0.3

- `integration/v0.3` é uma branch de planejamento criada sobre a integração pública v0.2; não demonstra conclusão ou aceite da v0.2.
- Nesta preparação estão autorizados somente branch e documentação. Não implementar funcionalidades, instalar APKs ou operar o tablet sem tarefa posterior.
- Seguir a sequência do roadmap v0.3: **00 base v0.2 aceita/sincronizada e escopo confirmado → 01 navegação → 02 armazenamento de perfis → 03 aplicação na UI → 04 qualificação**. LaserScan, exportação e lotes numéricos continuam opcionais e exigem decisão explícita.
- Antes da implementação, incorporar por merge normal a v0.2 final aceita. Não migrar pendências de qualificação nem levar mudanças v0.3 para a integração v0.2.
- Futuras branches v0.3 partem da `origin/integration/v0.3` atualizada; seus PRs apontam para `integration/v0.3`, não `main` ou v0.2. Validar cada incremento e também o SHA integrado, sem rebase/force-push compartilhado.
- As regras de branches abaixo continuam específicas da campanha v0.2; não sobrescrevem o fluxo v0.3. Preservar seu PR draft e seus gates próprios.

## Branches e integração da v0.2

- A base de desenvolvimento do ciclo v0.2 é **`integration/v0.2`**, não `main`.
- Inspecione `git status`, branch atual e histórico antes de editar; preserve alterações existentes.
- Antes de abrir a próxima branch, busque o remoto e confira a integração atualizada. Crie a branch do incremento a partir de `origin/integration/v0.2` limpa e atualizada.
- As oito frentes da v0.2 podem trabalhar em paralelo, cada uma em branch e worktree próprias. A numeração do roadmap indica prioridade de integração e dependências, não um bloqueio para iniciar trabalho independente.
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
