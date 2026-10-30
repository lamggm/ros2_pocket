# v0.6 — fechamento delimitado de perfis portáteis

[Projeto](../README.md) · [Uso e contrato](PORTABLE-PROFILES.md) ·
[Roadmap](ROADMAP.md#v06) · [Issue #23](https://github.com/lamggm/ros2_pocket/issues/23)

## Entrega e identidade

Perfis existentes agora guardam a intenção scan/câmera e podem ser exportados ou
importados individualmente em JSON pelo seletor Android. Não há banco, cloud,
worker DDS novo ou conexão automática. Conectar continua uma ação explícita.

- Fonte do build/teste: `153e3ee36d18f38953a66e8d69b0840d4f328f3b`, árvore
  `2cc39c8c847fdb266524215f23cd0b0d311e2f39`, checkout limpo.
- `com.lamggm.rospocket.dev`, **0.6.0-dev**, versionCode **6**, ARM64, API 28+,
  assinatura debug compatível com a bancada e hooks OFF.
- APK físico instalado e relido, SHA-256:
  `024c7514158049544ec5103eff704199dafdcd771c5b60263f0415eb9d5a6e35`.
- A embalagem host usa outro certificado/SHA. Todos os ZIP entries fora de
  `META-INF/` são byte-idênticos ao candidato físico; a assinatura de bancada
  não mudou o payload. A promoção acrescenta documentação, não substitui o APK.
- O merge solicitado inclui a release correspondente por preferência do operador.
  CI da PR, merge, CI pós-merge e publicação são verificações separadas,
  acompanhadas na issue #23 e no manifesto da release. Não usar APK do CI como
  substituto dos bytes físicos acima nem reconstruir só para remover `-dev`.

## Host e revisão

- TDD de admissão JSON/UTF-8 estrita e limitada, schema1/2, migração sem save
  implícito, visual, export/import, UUID novo, duplicados, cancelamento e lifecycle.
- **457 casos Java, zero falhas/erros e 1 skip opt-in legado**. Replays de capturas
  DDS reais retidas foram executados em cópias; não são campanha DDS/LAN nova.
- Wrapper oficial offline completo aprovado, com testes portáveis, build ARM64 e
  hooks OFF; lint **zero erros, 121 warnings**. Scanner de segredos percorreu
  fonte não vazia e histórico, sem achados.
- Revisão independente estática do snapshot aprovada sem bloqueadores. A fonte
  de aplicativo/testes foi comparada ao snapshot revisado. Melhorias de robustez
  não foram convertidas em requisitos de uma campanha extensa.

## Smoke físico Android — 7/7, mesma rodada

SM-X400 / Android 16, APK acima, com configuração sintética em namespace próprio.
Não foi usado publicador DDS, câmera, LiDAR, rede de robô ou amostras artificiais
apresentadas como telemetria real.

1. Catálogo representativo schema1 carregado com visual vazio, bytes inalterados
   e app desconectado. O catálogo original da bancada era ausente e foi preservado.
2. Arquivo válido importado pelo seletor, catálogo inalterado antes de confirmar,
   UUID renovado e nenhuma aplicação/conexão implícita. Save explícito em schema2
   preservou a configuração legada.
3. Exportação com aviso de privacidade, seletor real e releitura do arquivo:
   envelope corresponde exatamente ao perfil salvo, não à edição não salva.
4. Cancelar o seletor de exportação preserva catálogo e desconexão.
5. Importar JSON inválido mostra erro sem modificar o catálogo.
6. Aplicação explícita desconectada restaura as duas seleções visuais; alternar
   LaserScan/câmera não cria sessão DDS. Tópicos ausentes continuam só intenção.
7. Salvar configuração atual preserva visual, quatro watches/QoS e gráfico;
   reinício do processo restaura câmera/tipo/modo e permanece desconectado.

A rodada completa é `attempt-02`, independente da `attempt-01` incompleta:
na primeira, o guard de foco interrompeu antes dos casos e a leitura posterior
mostrou keyguard ativo/Dozing. Não houve correção no app para obter o PASS.
Após novo desbloqueio manual, Awake/keyguard foram conferidos antes da rodada.

Atualização in-place, UID preservado, APK instalado relido e conferido. Ao final,
preferências/configurações e energia/orientação originais foram restauradas e
verificadas, arquivos de teste removidos e app parado. A captura final foi
inspecionada: tópico/tipo legíveis, desconectado e sem imagem anterior apresentada
como live. Evidências privadas não integram os assets públicos.

## Limites e backlog

- Um perfil por arquivo, até 128 KiB, até 16 perfis locais. Sem credenciais,
  amostras, imagens, handles ou gerações DDS. JSON pode revelar peer/IP e tópicos.
- I/O de provedor é separado da UI/DDS e sem fila crescente, mas ainda não tem
  timeout/cancelamento: provedor travado pode ocupar o worker até encerrar o
  processo. Não prometer tempo máximo nem recuperação automática desse caso.
- Cancelar/erro preservam catálogo; uma falha de export pode deixar destino
  vazio/parcial, conforme aviso. Recriação invalida callbacks, não retoma imports.
- Smoke de persistência/documentos, não nova qualificação de DDS, QoS runtime,
  imagens, RF/IP, multidistro, LiDAR ou soak de 30 minutos. Testes host de falhas
  não são falhas físicas injetadas no provedor.
- v0.7–v0.9 permanecem propostas. Nenhum deploy, campanha extra ou próximo ciclo
  é iniciado por este fechamento.
