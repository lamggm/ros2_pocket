# ROS Pocket v0.3 — aceite físico de bancada

[Estado do projeto](PROJECT-STATUS.md) · [Fechamento](FINALIZATION-v0.3.md) ·
[Índice](README.md)

## Resultado e identidade

**PASS: 8/8 critérios do smoke físico curto**, executados numa única rodada
completa em 28/10/2026. É aceite técnico de **protótipo de bancada**, não
qualificação extensa nem liberação para aplicações críticas.

- Dispositivo: Samsung **SM-X400**, Android **16**, usuário Android 0.
- Fonte exata do APK: `90191840826a30c3757f09fa8b33361def91d9f5`.
- APK público: [v0.3.0-rc1](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0-rc1),
  DEV debug ordinary, `com.lamggm.rospocket.dev`, versionCode **3**, ARM64,
  hooks **OFF**, **5.109.846 bytes**.
- SHA-256: `ef51c365acb3d90c27686e18241c5e1b8092f09ed89545d0b7f9c4d59c4460c9`.
- Rodada completa: `smoke-02`, **12:53:20–13:17:44 UTC**, 24 min 24 s decorridos.
  Esse intervalo inclui instalação/navegação; **não é soak**.
- Estímulos: publicador rclpy ROS Jazzy/Fast DDS real, separado do Android,
  com tópicos e domínio de teste próprios. Não houve comandos ou controle de robô.
- Rede: peer explícito existente; não qualifica descoberta multicast automática.

O código do aplicativo não mudou durante os testes. A promoção preserva os bytes
fontes do app/testes/wrappers dessa fonte; mudanças documentais não significam
rebuild ou novo aceite de outro APK. Os APKs independentes do CI podem ter outro
certificado debug e não substituem automaticamente a instalação física.

## Oito critérios aprovados

1. **Preflight/atualização:** artefato anterior preservado; instalação in-place,
   sem uninstall/clear data; UID e configurações conferidos após instalar.
2. **Salvar/selecionar:** criar dois perfis pelas Views; selecionar não aplica
   nem conecta; renomear preserva a configuração; excluir respeita cancelar
   e exige confirmação.
3. **Aplicar/reiniciar:** última aplicação restaura domínio, peer, quatro slots,
   QoS e campos do gráfico, ainda desconectada. Process restart e recriação real
   por rotação descartam edições de Views não aplicadas; amostras não são restauradas.
4. **Conectar explicitamente:** quatro readers recebem dados novos; os quatro
   pares de reliability/durability solicitados foram conferidos após conectar e
   reconectar. Aplicação de perfil conectado foi bloqueada.
5. **Ausência/divergência:** remover tópico do Watch 1 e trocar tipo do Watch 2,
   ambos selecionados para plot, gera estados explícitos, zero intake fresco e
   gráfico sem pontos antigos. Salvamento real confere que as seleções não foram
   substituídas; os outros readers avançam. Recuperação recebe novas amostras.
6. **Navegação/gráfico/logs:** trocar as quatro seções preserva sessão/readers
   com contadores avançando. Duas séries; pausa preserva pixels com intake ativo,
   inclusive após sair/voltar da seção; retomada muda pixels. Logs próprios avançam.
7. **Lifecycle/apresentação:** Home invalida cache e retorno tem sessão nova;
   retrato/paisagem conferidos por dimensões PNG, reconexão e inspeção visual.
   Excluir o perfil aplicado limpa o próximo startup sem interromper a sessão ativa.
8. **Cleanup:** fixture própria removida; app parado; preferências byte-idênticas
   ao backup; perfis de teste ausentes como antes; tela/energia restauradas;
   identidade do APK/UID conferida novamente.

## Evidência e revisão

- **995 arquivos** da rodada completa recolhidos e conferidos por conjunto,
  tamanho e SHA-256; **71 exports QA**, dos quais **59 válidos**, com os quatro
  pares de QoS solicitados esperados. Estados desconectados/transitórios permanecem
  no conjunto como inválidos, sem serem promovidos a prova de recepção.
- Comparação de pixels: pausa e retorno pausado idênticos; retomada diferente.
- Correções dos oráculos passaram por revisão independente pontual e quatro
  controles QoS explicitamente sintéticos. Esses controles não são evidência física.
- Capturas reais de conexão/gráfico em retrato/paisagem foram inspecionadas:
  legibilidade favorável, duas séries distintas e sem teclado/obstrução funcional.
  Chave de cores ausente na legenda e destaque sutil da seção são observações
  cosméticas, não bloqueadores do escopo.
- Readback posterior confirmou app parado, fixture ausente, APK/UID e hashes dos
  arquivos de configuração corretos. Sem entradas recentes no buffer de crash
  filtrado pelo UID do app.
- Valores originais de tela/energia foram preservados em memória e comparados
  após restauração pelo runner. Não se arquivou um arquivo separado de baseline
  desses settings; o readback posterior registra os valores atuais.

A primeira tentativa (`smoke-01`) permanece **incompleta**, com seu erro de
observação do segundo salvamento e cleanup preservados. Não há evidência pós-ação
suficiente para atribuir sua causa. Nenhum PASS dessa tentativa foi somado ao
8/8 da nova rodada completa.

Relatórios, manifestos, backups, dumps e logs detalhados ficam em evidência
privada fora do Git. Este documento é o resumo sanitizado, não uma publicação
ou promessa de reexecução independente desses dados privados.

## Limites mantidos

Não foram validados por este smoke: perda real de Wi-Fi/IP, descoberta automática,
soak/endurance, outros dispositivos/versões Android, Humble ou outras distribuições.
Fast DDS é o produtor desta rodada; ela não é uma matriz física completa dos RMWs.
Sem alterações de rede/VPN/firewall, código do app, thresholds ou validadores.
LaserScan, exportação e lotes numéricos continuam backlog.

O operador autorizou separadamente a promoção da integração para `main` após esse
aceite. A tag e o APK `v0.3.0-rc1` permanecem imutáveis; não há nova release estável
ou versão nominal `0.3.0` criada por este merge.
