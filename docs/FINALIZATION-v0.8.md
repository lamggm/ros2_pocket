# v0.8 — fechamento delimitado Humble/Jazzy

[Projeto](../README.md) · [Uso, versões e matriz](HUMBLE-JAZZY.md) ·
[Issue #18: promoção, CI e publicação](https://github.com/lamggm/ros2_pocket/issues/18)

## Entrega e estratégia

Formato de grafo **manual**, persistido no perfil: `humble`/GID24 ou
`jazzy`/GID16. Não há detecção de distribuição, tentativa de dois parsers ou
fallback por dados plausíveis. Na auditoria, os produtores não forneceram
TypeInfo utilizável e o leitor errado podia produzir zero nós silenciosamente;
a escolha manual foi decidida explicitamente pelo operador.

- Perfis schema3; catálogos1/2 e envelopes portáteis2 continuam legíveis.
  Default Jazzy em memória, sem regravação no load; salvar/importar/exportar e
  renomear preservam o formato.
- Aplicar somente desconectado. Seletor bloqueado durante conexão desejada;
  configuração imutável capturada para o worker. JNI legado permanece Jazzy.
- Um leitor de grafo por sessão. Identificadores C/C++/sequences/descriptors dos
  layouts isolados para evitar colisão ODR, preservando tipos/metadados no fio.
- Humble valida GUID16 + cauda de oito zeros no GID do grafo auditado, sem
  generalizar a handles de APIs RMW. Preservados ownership do writer, participante,
  papel e limites de 128 nós/1024 referências.
- Formato desconhecido é rejeitado; grafo ausente/incompatível não inventa nós
  nem contamina String/Float64 ou o reader dedicado de rosout.

## Fonte e APK físico

- Fonte limpa do build: `096a7d575a3f53a466b18b6ed36e77d01e3073af`.
- `com.lamggm.rospocket.dev`, **0.8.0-dev**, versionCode **8**, ARM64/API28+.
- Assinatura **Android Debug v3**, compatível com a instalação anterior;
  capacidade de hooks diagnósticos **OFF**.
- SHA-256 do APK físico aprovado:
  `66d1f97ef0a6a7eb08fc367981e37aa1717b6fd09e1eb36d4eb17c6b79ab8958`.
- Build pelo wrapper offline isolado. A assinatura compatível foi aplicada no
  host autorizado, sem transferir material privado; todos os entries ZIP fora
  de `META-INF/` permaneceram idênticos ao build congelado.
- ELFs com LOAD alignment `0x4000`; isso não qualifica hardware com páginas16KiB.

Documentação pode avançar depois da fonte do APK. Todos os arquivos não
Markdown devem permanecer iguais à fonte física antes da promoção. O APK do
CI tem outra origem/assinatura e **não substitui esses bytes**. A issue #18 e o
manifesto público registram os SHAs, checks exatos e release efetivamente publicada;
este documento não antecipa aprovação de um CI ainda pendente.

## Host, interoperabilidade e revisão

- **497 testes Java**, zero falhas/erros, um skip opt-in legado.
- **21 CTests** portáveis; regressões Python e JNI Linux real aprovados.
- Lint: zero erros/fatais, **123 avisos**; Android build ARM64 offline aprovado.
- Revisão independente estática sem bloqueadores sobre snapshot
  `973803c29efa70ee764cc9f90d07b19187fda424d7d1510ff623d97e60579dac`.
  Duas sugestões não bloqueantes continuam adiadas: Bundle de restauração
  divergente e retenção de stdout/stderr parcial em timeout do probe.
- Quatro combinações reais, em processos separados dentro de contêineres
  privados `--network none`: **Humble/Fast DDS, Humble/Cyclone DDS,
  Jazzy/Fast DDS e Jazzy/Cyclone DDS**. Cada uma passou endpoints,
  String/Float64, rosout, nó e associações de endpoints.
- Humble: remoção por morte do produtor, expiração de lease e reinício com
  novos GUIDs passaram nos dois RMWs sem substituir Session/readers nativos.
- Controle deliberadamente incompatível Humble com seletor Jazzy manteve
  recepção tipada saudável e grafo `unconfirmed`, sem fallback.
- JNI Linux real exercitou ambos os formatos e quatro escolhas inválidas.
  É prova host, não execução NDK/Android.

Versões instaladas dos produtores, sem extrapolar para outros pacotes:

- Humble: rmw_dds_common1.6.0; rmw_fastrtps_cpp6.2.10/Fast DDS2.6.12;
  rmw_cyclonedds_cpp1.3.5/CycloneDDS0.10.5; rcl_interfaces1.2.3.
- Jazzy: rmw_dds_common3.1.1; rmw_fastrtps_cpp8.4.4/Fast DDS2.14.7;
  rmw_cyclonedds_cpp2.2.4/CycloneDDS0.10.5; rcl_interfaces2.0.4.
- Core usa CycloneDDS fixado em `2cdd114cbd18340c606573b4cc8dc20cc161ec5a`.

## Android físico — 4/4

Uma rodada completa em **2026-11-04**, SM-X400/Android16, no mesmo APK acima.
Produtores DDS nativos sintéticos separados no próprio Android, com os bytes
exatos de ddsc do candidato; **não é uma nova matriz ROS real pela LAN**.

1. Humble/GID24: nó/namespace visível, Float64 avançando no mesmo reader
   (68→78), perfil schema3 persistido com Humble.
2. Conexão viva: seletor desabilitado e Apply rejeitado, com recepção preservada.
3. Jazzy/GID16: novo nó/sessão, Float64 avançando no mesmo reader (67→77),
   perfil persistido com Jazzy.
4. Apply Humble desconectado e reinício do processo: perfil/formato restaurados,
   botão Conectar e nenhuma conexão automática.

Instalação somente in-place, sem uninstall/clear data; UID preservado.
Readback independente confirmou APK puxado novamente com hash idêntico,
restauração exata dos sete caminhos de configuração (incluindo ausências),
app parado e remoção do produtor/diretório de fixture. **267 arquivos privados**
coletados e conferidos por tamanho/hash; três screenshots inspecionadas.
Catálogo original ausente: migração legada não foi exercitada fisicamente,
embora esteja coberta no host.

A tentativa01 continua **incompleta**, bloqueada pelo keyguard antes de backup/
install, zero casos funcionais. Não foi somada à rodada4/4. A política persistente
de inatividade foi autorizada separadamente pelo operador e preservada no cleanup;
PIN/bloqueio manual continuam ativos. Não publicar preferências, endereços,
identificadores de transporte, dumps UI ou material de assinatura.

## Limites preservados

Sem matriz Android com ROS real Humble/Jazzy pela LAN, interrupção RF/IP/VPN,
soak, domínios mistos, tipos customizados ou suporte universal. Não há bridge,
comandos de robô, RPC ou teleop. As pendências históricas de qualificação não
foram transformadas em PASS nem os thresholds dos validadores relaxados.

O operador autorizou o fluxo de promoção/merge/release v0.8 e o início posterior
da v0.9. A release deve reutilizar exatamente o APK físico, após CI da PR e
pós-merge, com assets públicos baixados e comparados; preservar tags/releases
anteriores. A v0.9 começa em tarefa própria sobre a main publicada, sem grande
feature nova ou interrupção de rede sem recuperação independente.
