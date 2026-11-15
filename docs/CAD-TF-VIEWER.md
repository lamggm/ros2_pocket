# CAD completo, TF observado e tela cheia (DEV em validação)

## Operação

Importe o pacote local vinculado ao hash exato do robot_description. `--full`
no conversor produz schema2/geometry=full, sem redução de faces, incluindo
triângulos duplicados que o LOD removia. São malhas visuais originais exportadas,
não um kernel CAD paramétrico. Cores diffuse/normais; sem texturas ou PBR.

O card oferece **Tela cheia**, com a mesma instância, câmera e zoom, Recentrar,
Giro e Voltar ao painel/Back. Abrir/fechar não cria conexão DDS ou reinicia leitores.

**Usar TF observado** é explícito e exige o pacote indexado. O frame fixo é a raiz
URDF indicada no status (neste incremento não há seletor map/odom). Cada link usa
TF observado × origem visual × escala. Giro/arraste/pinça mudam só a câmera.
Se TF estiver ausente, inválido ou atrasado, o modo TF não mostra uma pose URDF
zero como se fosse observação. O operador pode voltar à pose URDF de referência.
Tópicos TF efetivamente usados aparecem no status; não são deduzidos do nome do
robô. Auto-admissão exige um tópico tf e/ou tf_static não ambíguo, do tipo
TFMessage. Vários tópicos desse papel bloqueiam seleção automática neste MVP.

## Limites e tempo

- Schema1 LOD: limites anteriores de GLB8MiB,120k faces, ZIP16MiB/inflado32MiB.
- Schema2 completo: ZIP64MiB/inflado128MiB, GLB64MiB, JSON GLB128KiB,
 2milhões de faces únicas/4milhões instanciadas, payload indexado96MiB.
- O manifest vem primeiro. Índices GLES2 ushort em chunks <=65535vértices;
 buffers compartilhados entre instâncias como rodas, matrizes por link.
- Leitores TF adicionais pertencem ao participante interno de descrição; não
 ocupam os quatro watches nem o leitor de câmera/scan. Seu teto wire520KiB
 não aumenta o teto64KiB da telemetria. Anunciam só leitores reais.
- TF dinâmico best-effort/volatile; tf_static segue reliability/durability
 oferecidas.128links/256frames, histórico32por aresta e <=256transforms/amostra.
- Escolhe o último tempo ROS comum e amostras anteriores/iguais a ele, sem
 interpolar ou comparar relógio ROS com wall-clock do Android. Expira após2s
 sem timestamp dinâmico avançar: repetição do mesmo stamp não renova freshness.
- Regressão de relógio, substituição de autoridade e pais conflitantes não são
 resolvidos por adivinhação. Reconectar limpa o histórico; recuperação de uma
 pausa com a mesma autoridade e timestamps avançando é automática. Não há
 seguimento de /clock ou recuperação automática de um novo epoch neste MVP.
- Matrizes mudam sem reconstruir/uploadar a malha; atualização10Hz no executor
 DDS existente, suspensa pelo lifecycle/epoch. Renderer só solicita quadros
 relevantes quando o modelo está visível; giro local mantém seu gate separado.

## Evidência

Host: conversão real BART1377506faces,5malhas únicas/8instâncias,
51654596bytes de payload GPU único; replay pelo decoder real, sem decimação.
Duas faces duplicadas antes filtradas pelo LOD agora são preservadas. A conversão
remove metadados internos de adjacency do JSON, nunca os dados de geometria.
Primeira conversão com esses extras e seus replays falhos foram preservados.

Testes host/native e revisão não equivalem a aceite no Mali/tablet. Fonte TF de
bancada é explicitamente sintética, embora use ROS/CDR reais; não é movimento
observado no robô físico. Essa fotografia antecede o aceite DEV posterior de CAD/TF/tela cheia e
observação de simulação ROS no tablet, registrados em [release v1.4](RELEASE-v1.4.md).
Não é aceite físico do robô ou da variante produção. Promoção/merge/release e
índice F-Droid foram autorizados separadamente; rede/hardware continuam preservados.
