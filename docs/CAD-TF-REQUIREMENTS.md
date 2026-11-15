# Requisito confirmado: malhas completas do CAD + TF ao vivo

Pedido do operador: “da a melhorada e testa, mas vou querer o cad com os tfs”.

## Sequência e critério de honestidade

A melhoria atual é distribuição do LOD, normais e iluminação, ainda em pose
URDF estática. Ela NÃO entrega nem dispensa o requisito abaixo. O próximo
incremento deve oferecer geometria visual original sem decimação e TF real;
não substituir esse requisito por giro local, mais triângulos ou pose inventada.
“CAD” aqui significa as malhas visuais exportadas do projeto, não um kernel CAD
paramétrico/editável dentro do Android.

## Contrato do próximo incremento

- Continuar somente leitura: descrição, transformações e telemetria existentes;
  sem comandos, RPC, parâmetros ou novo serviço no robô.
- Preservar fonte CAD, cores, unidades, associação literal URI e hash exato do
  URDF. Não ultrapassar limites antigos silenciosamente: definir orçamento e
  formato para geometria completa, medir RAM e tempo antes da aplicação.
- Usar buffers indexados/reutilizáveis por peça/link; compartilhar instâncias
  como as rodas. Não expandir/reconstruir toda a malha a cada amostra TF.
- Descobrir as fontes reais de TF e QoS. Ler `/tf_static` e `/tf` (ou nomes
  efetivamente oferecidos), com frame fixo explícito e transformações por link.
- Respeitar timestamps e relógio ROS/simulação; rejeitar ciclos, pais ambíguos,
  dados inválidos e transformações indisponíveis. Exibir ausência/atraso, sem
  apresentar pose URDF zero como TF atual. Joint states não são substituto
  silencioso de uma árvore TF ausente.
- Separar câmera/orbit local da pose recebida. Preservar quatro watches,
  sensores, perfis, seleção, lifecycle e fences de sessão.
- Provar transformação não nula e móvel por link, perda/recuperação de TF,
  pausa/reconexão e cold start no APK físico. Identificar fixture ROS isolada
  separadamente de robô físico/Gazebo completo. Comparar malhas completas com
  a referência, medir memória/fluidez; um decoder ou screenshot estático não
  estabelece esse aceite.

## Estado

Implementação em validação: pacote completo schema2, geometria indexada por
peça/instância, leitores TF passivos e tela cheia. Aceite físico deste candidato
ainda pendente; os passes do polish não se transferem para estes bytes.
O publisher manual de descrição permanece disponível para o operador e não
fornece TF por si só. Não alterá-lo ou removê-lo ao limpar testes do polish.
