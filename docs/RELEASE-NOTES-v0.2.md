# ROS Pocket v0.2.0 — notas de versão (DRAFT, não lançado)

**Preparação paralela da frente 15, não candidato final congelado.** Sem aceite
físico, soak final, ready ou autorização de release. A integração → main permanece
draft e sem auto-merge. O operador autorizou desenvolver 11–15 em worktrees
isoladas agora, reconciliar depois e testar no tablet quando estiver disponível.

## Identidade preparada

- `versionName=0.2.0`, `versionCode=2`; cabeçalho usa a versão gerada pelo build,
  não um rótulo v0.1 independente. Code 2 não aparece no histórico local disponível
  antes desta preparação; não foi consultada uma loja ou dispositivo.
- Package DEV `com.lamggm.rospocket.dev`, signer debug existente, minSdk 28,
  targetSdk 35 e padrão ARM64 preservados. Não há migração/uninstall/instalação.
- Ordinary/default/nondebug continuam com hooks OFF. Diagnóstico ON exige opt-in
  explícito/versionado e inspeção dos ELF reais; nome de versão não muda política.
- Cada APK novo tem identidade/hash próprios; versão não substitui SHA de fonte,
  snapshot, certificado e hash de bytes. Resultados de APK antigo não se transferem.

## Funções implementadas na fonte disponível — aceite final pendente

Android Java → JNI/C++ → Cyclone DDS 0.10.5, DDS direto, **somente leitura**,
sem bridge/servidor auxiliar nem comandos de aplicação para o robô:

1. Wrapper fixado, bootstrap/build verificáveis, CI e fixtures DDS isoladas.
2. Diagnóstico de conexão/rede e recuperação limitada com estados explícitos.
3. Explorer de tópicos/nós e QoS anunciado; desconhecido não vira compatível.
4. Console `/rosout` independente, com filtros, pausa, clear e buffer limitado.
5. Tipos primitivos e Vector3/Quaternion/Twist/Pose/Odometry estruturados;
   campos numéricos, frames/stamps/covariâncias e limites explícitos.
6. Até quatro watches independentes, substituição/remoção/QoS por slot.
7. Gráficos de snapshots numéricos tipados: quatro séries, 30 s/600 pontos,
   pausa só visual, gaps honestos; não reconstrói todas as mensagens da origem.
8. Toolkit/matriz oficial de qualificação e orçamentos mensuráveis de soak.
9. QA debug read-only com contadores/proveniência e estatísticas de desenho.
10. Harness físico guardado; falhas de criação têm regressões e suporte
    diagnóstico recuperados na base local da 12, sujeitos à revisão/reconciliação.

Fonte dessas funções existe, mas esta lista **não** afirma revisão final, CI
integrado, funcionamento físico completo ou endurance. A base desta rodada é a
recuperação local `796b59aaf50a468344b825f693331e11bdabb06e` sobre a história real
`7b2dc76de3679935fac5adf69839818e8ca5a914`; não é o commit upstream original
`7a5e841811f66bcb660da0f9b2b901f1aa272c68`. Frentes 11/13/14 ainda não estão
integradas nesta preparação. [Checklist por incremento](FINALIZATION-v0.2.md).

## CI preparado, deliberadamente não liberado

O workflow mantém runner GitHub hospedado efêmero, permissões mínimas, actions
fixadas por SHA, sem secrets/release keys e upload somente APK debug+metadados.
O novo registro obrigatório de testes host 11/13/14 está `pending_integration` e
bloqueia o CI antes do upload. O pai deve registrar suites reais, não vazias e
revisadas após reconciliar as frentes. Testes sintéticos do mecanismo de registro
não são gates funcionais das três frentes. Nenhum CI remoto é alegado nesta rodada.

## Candidato e aceite — preencher só com evidência real

| Campo | Estado |
|---|---|
| Reconciliação 11/12/13/14/15 e revisão independente de todos os incrementos | Pendente |
| SHA final integrado / fonte limpa / snapshot antes e depois do build | Pendente; não congelado |
| APK final: package, code/name, tamanho/SHA-256, ABIs/certificado | Pendente; build de preparação não é final |
| CI remoto do SHA exato, testes/lint/build/secret scan finais | Pendente; registro 11/13/14 bloqueia |
| Identidade instalada puxada de volta, mesmo APK da campanha | Pendente; nenhuma instalação nesta rodada |
| Ambos os RMWs físicos, domínio incorreto e regressão integrada | Pendente para o novo candidato |
| Falhas reais/lifecycle/QoS/remoção/rosout/tipos/multiwatch/gráficos completos | Pendente para o novo candidato |
| Wi-Fi/IP físicos com recuperação alternativa autorizada | Pendente |
| Soak oficial com CPU/PSS/intake/bateria, quatro watches e gráfico | Pendente; curto/sintético não substitui |
| Todos os 21 gates e aceite separado do operador | Pendente |

As 16 verificações antigas do protótipo são histórico, não prova do novo APK.
Não houve nesta preparação ADB, operação de tablet/emulador, coleta física,
medição de bateria, alteração de rede, publicação ou assinatura de produção.
Evidências host/APK de preparação ficam fora do Git, ligadas ao próprio SHA/hash;
a revisão do pai não deve aceitar alegações de um binário anterior como novas.

## Limites preservados

IPv4 na mesma LAN Wi-Fi; políticas de AP/multicast/VPN/firewall podem bloquear
descoberta. SROS2, sub-redes roteadas, esquemas antigos de grafo, outros tablets
e páginas físicas 16 KiB não têm aceite aqui. DDS rejeita CDR acima de 64 KiB;
limites de amostra/ring não são um limite total de memória do processo. Há dívida
cosmética de lint conhecida, não reformada nesta frente.

Fora do ciclo: parâmetros/RPC, serviços/actions, publicação/teleop, rosbag,
imagens/mapas/TF/RViz e tipos dinâmicos customizados. Sem release/loja implícitos.
Certificado diferente nunca autoriza apagar dados ou desinstalar para atualizar.

Antes de retirar DRAFT: cumprir [FINALIZATION-v0.2.md](FINALIZATION-v0.2.md) e o
[plano oficial](DEVICE-QUALIFICATION-v0.2.md), referenciar o relatório revisado e
os bytes finais. Não publicar logs brutos, serial, endereço, keystore ou dumps.
