# ROS Pocket v0.2 — notas de versão (DRAFT, não lançado)

**Sem candidato qualificado, aceite físico ou autorização de release.** Este
arquivo é preparação da frente 08, não anúncio de entrega. A integração → main
permanece draft e sem auto-merge até a campanha final e o aceite do operador.

## Escopo pretendido — confirmar no SHA final

A v0.2 pretende manter Android Java → JNI/C++ → Cyclone DDS e leitura direta DDS,
sem bridge/servidor auxiliar ou comandos para o robô:

1. Build reproduzível, wrapper fixado, CI e fixtures DDS isoladas.
2. Diagnóstico de conexão/rede e recuperação limitada com estados verdadeiros.
3. Explorer de tópicos/nós e QoS anunciado, distinguindo desconhecido de garantido.
4. Console `/rosout` com filtros, pausa e buffer limitado.
5. Vector3/Quaternion/Twist/Pose/Odometry estruturados, campos numéricos e limites.
6. Até quatro watches independentes, sem falha de um contaminar os demais.
7. Gráficos de campos numéricos, histórico limitado e lacunas honestas.
8. Matriz explícita de qualificação do candidato e plano de soak mensurável.

Esta lista é **escopo do roadmap**, não checklist concluído. Uma branch existente,
merge parcial ou build verde não comprova funcionalidades integradas e aceitas.
A preparação começou sobre os incrementos 01–03 e incorporou a integração com
01–04 antes da abertura da PR; 05–07 precisam ser conferidos na integração final.
Por decisão do operador, a PR da frente 08 só pode ser mergeada **depois** da PR
[07 — gráficos](https://github.com/lamggm/ros2_pocket/pull/5), com a base atualizada
e os gates novamente validados; não habilitar auto-merge antes dessa condição.
Não há evidência nesta frente de que todas as funcionalidades tenham sido
entregues ou exercitadas juntas.

## Entrega desta preparação

- `tools/qualification.py`: matriz de gates pendentes vinculada ao snapshot real
  de fonte/APK, registro explícito com hashes de evidências, avaliação fail-closed
  e análise pura de soak. Nenhuma ação padrão de rede/dispositivo.
- [Plano de qualificação](DEVICE-QUALIFICATION-v0.2.md): procedimentos futuros,
  identidade instalada, canal alternativo antes de interrupções físicas e
  orçamentos pré-declarados de 30 minutos/quatro watches/gráfico.
- Testes unitários do toolkit, incluindo rejeição de falsos positivos e controle
  sintético positivo de **validação do relatório**, não de aceitação do tablet.

## Candidato e aceite — preencher só com evidência real

| Campo | Estado |
|---|---|
| SHA final integrado / fonte limpa / hash de conteúdo | Pendente |
| APK real: package, versionCode/versionName, tamanho/SHA-256 | Pendente |
| Certificado SHA-256 / ABIs / identidade instalada | Pendente |
| Testes finais, lint, build, secret scan, revisão de todos os incrementos | Pendente |
| Fast DDS/Cyclone físicos e domínio errado | Pendente para o candidato v0.2 |
| Lifecycle, reader failure, QoS/remoção, rosout, tipos, watches e gráficos | Pendente para o candidato v0.2 |
| Perda/retorno físicos de Wi-Fi e mudança real de endereço | Pendente; exige recuperação alternativa aprovada |
| Soak medido com todas as métricas e carga integrada disponíveis | Pendente; indisponibilidade bloqueia, não dispensa |
| Leitura real com APK instalado idêntico e aceite separado do operador | Pendente |

As 16 verificações antigas do protótipo são histórico, não prova do candidato
v0.2. Não houve qualificação física, instalação, coleta ADB, medição de CPU/PSS/
intake/bateria, mudança de rede ou aceite nesta preparação. A automação não
fabrica métricas e não converte ausência de falha em sucesso.

## Limites preservados

IPv4 na mesma LAN Wi-Fi; multicast, isolamento de AP e políticas de VPN/firewall
podem impedir descoberta. SROS2, roteamento entre sub-redes, compatibilidade de
esquemas de grafo antigos e aceitação física em outros tablets/16 KiB continuam
sem comprovação aqui. Buffers limitados e soak curto não provam endurance ilimitado.

Fora do ciclo: editar parâmetros, serviços/actions, publicação/teleop, rosbag,
imagens/mapas/TF/RViz e decodificação dinâmica de tipos customizados. Não há
assinatura de produção, upload, distribuição, migração de dados ou instalação
implícita. APK debug com certificado diferente não autoriza desinstalar o existente.

Antes de retirar DRAFT, substituir pendências por referências sanitizadas ao
relatório final revisado e ao candidato exato. Evidência bruta permanece local em
`build/qualification/`; não publicar serial, endereço, logs privados ou keystore.
