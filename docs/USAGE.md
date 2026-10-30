# Usar o ROS Pocket

[Início](../README.md) · [Índice da documentação](README.md)

Este guia corresponde à v0.6; confira a versão na [última release](https://github.com/lamggm/ros2_pocket/releases/latest).
O candidato físico mantém a versão interna **0.6.0-dev**, code **6**.
O [smoke físico de perfis](FINALIZATION-v0.6.md) passou **7/7** no SM-X400 / Android 16;
isso não é qualificação extensa ou suporte a todos os dispositivos.

## Instalar e atualizar

O APK precisa de Android 9/API 28 ou superior e processador ARM64 (`arm64-v8a`).
Na página da release, baixe o `.apk` em **Assets**, não os arquivos de código-fonte.
A release também fornece `SHA256SUMS` e `app-debug.json` para conferir a integridade.

A instalação usa o nome **ROS Pocket DEV**, package `com.lamggm.rospocket.dev`.
O candidato é debug, versionCode 6, e tem hooks de teste desativados.
A assinatura debug não é uma assinatura de produção.

Para atualizar sem perder configurações, o Android exige o mesmo package e um
certificado compatível. O APK publicado reutiliza o certificado do último APK
físico conhecido da bancada; um APK compilado em outra máquina ou no CI pode
usar outra chave. Antes de atualizar, preserve o APK anterior e as configurações.
Se houver incompatibilidade, pare e confira a identidade: não use desinstalação
ou limpeza de dados como solução automática. Nenhuma instalação é feita por
simplesmente seguir os guias de build ou baixar o arquivo.

## Conhecer a interface

- **Conexão:** domínio, peer, conectar/desconectar, diagnóstico, nós e perfis locais.
- **Tópicos:** Explorer, busca, filtros, favoritos, endpoints e QoS anunciado.
- **Monitor:** seleção de Watch 1–4, mensagens, campos numéricos, gráficos e sensores visuais.
- **Logs:** console de `/rosout`, filtros, pausa e limpeza da visualização.

Trocar de seção não desconecta a telemetria. O leitor visual só opera no Monitor;
sair dele suspende o sensor e invalida a imagem anterior.
O app não publica comandos, altera parâmetros ou movimenta o robô.

## Conectar e ler um tópico

1. Coloque o Android na mesma rede Wi-Fi/LAN dos participantes ROS 2.
2. Em **Conexão**, informe o mesmo Domain ID. O intervalo aceito é **0–101**.
3. Deixe o peer IPv4 vazio para descoberta automática. Use um peer explícito
   somente quando souber o endereço correto; isso não comprova que multicast funciona.
4. Toque em **Conectar**.
5. Em **Monitor**, escolha **Watch 1–4**. Em **Tópicos**, toque em **Ler** em um
   tópico de tipo suportado. Volte ao Monitor para ver os valores e a árvore de campos.

Cada watch tem seleção e QoS próprios. Alterar QoS ou usar **Remover watch
selecionado** afeta apenas o slot selecionado, não os outros leitores.
A descoberta pode mostrar um tipo que o app ainda não sabe decodificar.
Veja a [lista de tipos](COMPATIBILITY.md#tipos-de-mensagem).

Os contadores e Hz descrevem o que o app recebeu, não uma garantia da frequência
original do publicador. Confira também a idade dos dados: um valor na tela pode
ser antigo mesmo com a conexão ativa.

## Gráficos e logs

Para LaserScan 2D ou câmera, selecione **Visualizar** no tópico suportado no
Explorer. O Monitor alterna um sensor visual por vez, sem ocupar os watches.
Veja [formatos, limites, zoom e comportamento](SENSOR-VIEWS.md).

Selecione campos numéricos de qualquer um dos quatro watches para criar até
quatro séries. A escolha de campos no gráfico é separada da seleção no inspetor
estruturado. **Pausar** congela o desenho; a recepção continua. Substituir ou
remover um reader invalida as séries daquele reader, sem reaproveitar seus pontos
como se viessem do novo tópico.

O gráfico guarda snapshots recentes, não cada mensagem publicada. Para limites,
lacunas, NaN/Inf e comportamento de pausa, consulte [gráficos](LIVE-PLOTS.md).

Em **Logs**, o reader dedicado de `/rosout` continua independente dos quatro
watches. Os filtros e a pausa são locais à apresentação. O campo `name` da
mensagem identifica um logger; não é prova de identidade de um nó.
Detalhes em [console rosout](ROSOUT-CONSOLE.md).

## Perfis locais

Em **Conexão**, abaixo dos diagnósticos, os perfis guardam domínio, peer opcional,
quatro slots tópico/tipo/QoS, até quatro campos de gráfico e as seleções scan/câmera
com seu modo visual. Eles não guardam
amostras, contadores live, handles DDS ou identidades de sessão/readers.

1. Configure conexão, watches, campos do gráfico e sensores visuais desejados.
2. Escolha **Novo perfil**, dê um nome e toque em **Salvar configuração atual**.
3. Para reutilizar, desconecte, selecione o perfil e toque em **Aplicar**.
4. Confira a configuração e toque em **Conectar** separadamente.

Selecionar no dropdown não aplica nem conecta. Salvar substitui explicitamente
a configuração daquele perfil; **Renomear** muda apenas o nome. Excluir exige
confirmação e não altera uma sessão em execução. Aplicar enquanto conectado ou
reconectando é bloqueado. Um tópico ausente ou tipo divergente fica pendente ou
com erro explícito, sem troca silenciosa por outro tópico. Campos restaurados
aguardam dados novos de uma fonte compatível e podem ser desmarcados normalmente.

### Levar um perfil para outra instalação

Selecione um perfil **salvo** e toque em **Exportar perfil selecionado (JSON)**.
Leia o aviso de peer/IP e nomes de tópicos, confirme e escolha o destino no seletor
Android. Edições ainda não salvas não entram no arquivo. Para CSV de amostras do
gráfico, use o recurso separado [Exportar gráficos](CSV-EXPORT.md).

Em **Importar perfil (JSON)**, escolha o arquivo e confirme **Adicionar** com um
nome único. O app valida formato/schema/campos e até 128 KiB, gera outra identidade
e adiciona sem substituir, aplicar ou conectar. Se o nome já existe, escolha outro;
cancelamento ou arquivo inválido preservam o catálogo. Aplicar e Conectar seguem
separados. Perfis schema1 antigos continuam legíveis, com visual vazio, e só são
reescritos em schema2 por uma operação explícita. [Contrato e limites](PORTABLE-PROFILES.md).

### Reinício e última aplicação

Com um catálogo de perfis existente, o app inicia desconectado, mesmo se as
preferências antigas pedissem reconexão. Ele restaura o **snapshot da última
aplicação explícita**, não a última seleção nem uma edição salva depois.
Esse snapshot prevalece sobre rascunhos de Views restaurados pelo Android.
Excluir o perfil da última aplicação também limpa seu snapshot de startup,
como informa a confirmação; isso não modifica a configuração/sessão atual.

Ao sair para Home, o app libera recursos DDS conforme o lifecycle de foreground.
O retorno à mesma Activity segue a política de reconexão existente. Retorno,
recriação de Activity e reinício de processo são casos diferentes; nenhum deve
transformar amostras antigas em dados novos.

### Limites e falhas de armazenamento

O schema v1 aceita até 16 perfis. Nomes são únicos sem distinção de caixa,
têm 1–48 caracteres Unicode, no máximo 128 bytes UTF-8, e não aceitam controles
ou caracteres de formatação. O arquivo tem teto de 128 KiB. Cada perfil tem
quatro slots, inclusive vazios, e até quatro campos numéricos únicos suportados.

O arquivo privado `bench-profiles-v1.json` usa `AtomicFile`, leitura limitada,
salvamento explícito e guarda contra escrita baseada em catálogo desatualizado.
Corrupção ou schema desconhecido preserva arquivo e preferências antigas,
bloqueia edição de perfis e conexão automática; a conexão manual continua possível.
Não há botão de reset destrutivo. Uma falha de gravação deve aparecer como erro,
não como um catálogo vazio salvo com sucesso. Resolvido o arquivo, reabra o app.

O I/O é limitado e ocorre no startup ou em operações manuais da UI, nunca no
worker DDS. Isso não é uma garantia de durabilidade sob queda de energia nem
um banco multiprocesso. Não há conta, cloud, sync, importação ou exportação.

## Problemas de conexão

- Confira o Domain ID nos dois lados. Domínio igual é necessário, não suficiente.
- Confira se a rede permite multicast e tráfego entre clientes. Isolamento de AP,
  filtros e firewall podem impedir descoberta.
- Confira o tipo e o QoS. O padrão do app é **best effort / volatile**. Um reader
  reliable não casa com writer best-effort; transient-local não casa com volatile.
  Ausência de incompatibilidade conhecida não garante compatibilidade completa.
- Confira se há dados novos, não só um tópico descoberto ou valor antigo.
- Consulte o diagnóstico em Conexão e os [estados de conexão](connection-diagnostics.md).

Algumas VPNs impedem o bind na rede Wi-Fi física. O app não altera a VPN,
firewall ou configurações de rede. Se necessário, o dono do dispositivo pode
excluir explicitamente o app da VPN; um app excluído deixa de usar a VPN/tailnet.
Não interrompa seu único canal remoto de acesso para tentar descobrir o problema.

## Referências de cada recurso

[Explorer e QoS](EXPLORER-QOS.md) · [Quatro watches](MULTIWATCH.md) ·
[Mensagens estruturadas](STRUCTURED-MESSAGES.md) · [Gráficos](LIVE-PLOTS.md) ·
[Logs](ROSOUT-CONSOLE.md) · [Compatibilidade e limites](COMPATIBILITY.md)
