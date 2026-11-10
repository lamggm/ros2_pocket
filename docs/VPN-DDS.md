# VPN/DDS e bridge ROS/Zenoh — v1.2

[Início](../README.md) · [Uso](USAGE.md) · [Evidências e variantes](RELEASE-v1.2.md)

O backend continua **C++/Cyclone DDS/JNI**. A v1.2 acrescenta um modo de rede
explícito; não substitui DDS por Zenoh nativo, `rmw_zenoh` ou rosbridge.

## Escolher a rede

- **LAN / Wi-Fi:** mantém a seleção de Wi-Fi físico, multicast e `DontRoute=true`.
  Peer vazio tenta descoberta automática; um peer explícito não prova multicast.
- **VPN / Tailscale:** exige a VPN disponível para o UID do app e um **peer IPv4
  explícito**, sem porta. Usa unicast, multicast desabilitado e `DontRoute=false`.
  Um app excluído da VPN não tem acesso à tailnet por esse modo. Não há fallback
  silencioso para LAN quando a VPN necessária está ausente.

1. Desconecte o app antes de editar configuração ou aplicar um perfil.
2. Escolha o modo, o Domain ID do lado DDS acessível e o peer correspondente.
3. Selecione **Jazzy (GID16)** ou **Humble (GID24)**; a escolha continua manual.
4. Conecte e confira interface/IP, modo e descoberta no diagnóstico.
5. Selecione um tópico no Explorer. Confirme contador avançando e idade recente,
   não apenas um peer ou tópico descoberto. LaserScan usa o leitor visual separado.

Domínio, endereço, peer, transporte e formato são capturados na configuração
imutável da sessão, inclusive no domínio visual. Mudar um seletor não retargeta
um leitor já ativo. Perfis schema4 persistem o modo; schemas1–3 assumem LAN em
memória. Leitura/migração não transforma um perfil legado em VPN silenciosamente.

## Bridge existente

`zenoh-bridge-ros2dds` pode parear um leitor DDS sem abrir uma rota para seus dados:
a bridge também precisa associar seu GID a uma entidade ROS. O Pocket anuncia
apenas as identidades reais de seus próprios leitores em `ros_discovery_info`.
Esses anúncios acompanham criação, substituição, remoção e encerramento.

É a exceção de publicação de **metadados de descoberta**, não publicação de
telemetria ou comandos: `writer_gid_seq` permanece vazio. Não há teleop,
serviços/actions, RPC ou escrita de parâmetros. O app não instala nem reconfigura
a bridge do robô, e não exige instalar um novo servidor para o uso DDS direto.

A bancada análoga exercitou duas bridges **1.10.2**, ROS Jazzy e Android físico
pela Tailscale. Isso não qualifica todas as versões de bridge, VPNs arbitrárias,
Humble físico através da bridge, IPv6-only, SROS2 ou redes roteadas em geral.

## Limites operacionais

VPN ativa e conexão desejada não garantem dados. Tópicos/tipos/QoS incompatíveis,
locators inacessíveis e filtros de rede ainda podem impedir recepção.
O app não altera VPN, exclusões por app, firewall ou Wi-Fi automaticamente.
Não interrompa seu único canal remoto para testar recuperação.

A ausência/retorno real da VPN continua **não qualificada fisicamente** e aberta
na [issue38](https://github.com/lamggm/ros2_pocket/issues/38). RF/IP permanece na
[issue31](https://github.com/lamggm/ros2_pocket/issues/31). O aceite proporcional
da v1.2 não transforma essas pendências em PASS; veja as janelas de evidência
separadas no documento de release.
