# ROS Pocket v0.2 — fechamento e pendências

## Escopo atual

Fechar o software disponível **sem usar o tablet**: documentação coerente,
testes/build locais, publicação na PR draft, CI e APK debug identificado.
Isso não é aceite físico nem autorização de merge, release ou loja.

O projeto é um protótipo útil em evolução. Uma tarefa delimitada por vez,
validação proporcional e melhorias adiáveis no backlog, conforme `AGENTS.md`.
O plano de implementação histórico está em [ROADMAP-v0.2.md](ROADMAP-v0.2.md);
este documento registra o fechamento atual, não uma nova campanha de certificação.

## Implementado e verificado no host

A reconciliação 11–15 e suas correções estão na branch de qualificação da
[PR #12](https://github.com/lamggm/ros2_pocket/pull/12), com base em `integration/v0.2`.
O registro `ci/finalization-gates.json` tem os três gates host **registered**;
`registered` significa testes host reais selecionados, não aceite físico.

A rodada local do código `496b9bb9053f915f9e249de675dec3f44d25fc23` concluiu:

- Matriz completa: nove gates aprovados, incluindo build offline, lint,
  empacotamento ordinary/diagnóstico/nondebug e inspeção dos dois runners.
- Python: 293 testes, sem skips.
- Java/Robolectric: 277 casos, com um skip intencional do JNI opt-in;
  o gate Linux JNI real foi executado e aprovado separadamente.
- DDS real em fixtures isoladas Fast DDS e Cyclone DDS, com replay Canvas host.
- Revisão independente aprovada da correção de orçamento funcional;
  launcher externo também revisado, inclusive falhas de cleanup.
- Fonte e histórico examinados pelo scanner de segredos, sem findings.

Alterações posteriores somente de documentação não alteram o código exercitado;
seu CI e os metadados do APK devem identificar o próprio commit, sem reatribuir
os logs anteriores a outro SHA. Consulte os checks do head vigente da PR para o
resultado remoto; CI anterior não vale como CI do novo head.

## Entrega sem tablet

- Versionamento: `0.2.0`, code `2`, package DEV `com.lamggm.rospocket.dev`.
- APK ordinary debug com hooks OFF para uso normal; variante diagnóstica é
  separada e opt-in. Não confundir capacidade compilada ON com lease inativo.
- Identidade do artefato: APK e JSON extraídos/verificados pelo build,
  fonte limpa, hash, ABI e certificado. O signer local pode diferir do CI.
- PR permanece draft, sem auto-merge. Artefatos e evidências brutas ficam fora
  do Git; não publicar endereços, dumps, credenciais nem material de assinatura.

## Próxima tarefa com tablet — pequena e separada

Verificar seleção de séries, gráfico visível, pausa e retomada. Registrar o que
funcionou e qualquer bug encontrado; terminar esse recorte sem abrir outras
campanhas automaticamente. Preservar dados e comparar assinatura antes de
qualquer atualização; certificado divergente não autoriza uninstall/clear data.

## Pendências físicas e aceite

Continuam **não concluídos para o candidato final**:

- Regressão física integrada e gráficos completos nos dois RMWs; resultados
  históricos e prefixos parciais mantêm seu próprio SHA e escopo.
- Wi-Fi/IP físicos: dependem de recuperação alternativa testada e autorização
  de interrupção; não mexer em rede/VPN/firewall para tentar antecipá-los.
- Soak oficial: depende de tablet real, sem alimentação, quatro watches e gráfico.
- Readback do APK instalado, consolidação de evidências e aceite separado.

O [plano oficial](DEVICE-QUALIFICATION-v0.2.md) e seus critérios permanecem
inalterados. O pedido de simplificação não aprova testes não executados nem
remove gates silenciosamente. Se o operador optar por entregar um protótipo com
escopo menor, registrar explicitamente os critérios e limitações antes do aceite.
Até lá, `ready`, `full12` e `physicalAccepted` permanecem falsos.

## Backlog, não trabalho automático desta entrega

- Otimização adicional do harness somente se bloquear um teste concreto.
- Robustez/endurance e compatibilidade com outros dispositivos em tarefas próprias.
- Cosmética/localização e avisos de lint conhecidos, sem impacto funcional demonstrado.
- Novas funções (parâmetros, serviços/actions, teleop, rosbag, RViz/TF ou tipos
  customizados dinâmicos) continuam fora da v0.2.
