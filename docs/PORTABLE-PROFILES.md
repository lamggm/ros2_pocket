# Perfis portáteis — v0.6

[Roadmap](ROADMAP.md#v06) · [Issue #23](https://github.com/lamggm/ros2_pocket/issues/23)

## Escopo

Extensão dos perfis existentes, sem novo worker DDS, banco ou conexão automática.
A versão adiciona configuração visual e transporte de **um perfil por arquivo**.
O arquivo contém peer/IP e nomes de tópicos; a interface avisa antes de exportar.
Nunca inclui mensagens, imagens, amostras, gerações ou handles DDS.

## Configuração local

- Schema 2 mantém id/nome, domínio/peer, quatro watches e até quatro séries.
- `visual`: `mode` (`scan` ou `camera`), `scan` e `camera`. Cada seleção é nula ou
  `{wire, type}`; scan aceita LaserScan, câmera aceita Image/CompressedImage.
- A seleção do modo ativo e as duas escolhas são intenção, não leitores/dados.
  Aplicar limpa a imagem anterior e não cria leitor até Conectar e abrir Monitor.
- Schema 1 é lido com visual vazio. O arquivo local existente
  `bench-profiles-v1.json` não muda de nome e não é reescrito ao carregar;
  somente uma operação explícita grava schema 2 via AtomicFile existente.
- Selecionar/salvar/importar não aplica. Aplicar exige desconexão. Reinício com
  catálogo restaura somente o último snapshot aplicado, sempre desconectado.

## Transporte JSON

Envelope: `{format: "ros-pocket-profile", schema: 2, profile: {...}}`.
O perfil usa os campos locais, mas a identidade é substituída por UUID novo ao
importar. Um arquivo não escolhe qual perfil local substituir.

- UTF-8 estrito, no máximo 128 KiB; profundidade máxima 12. Campos/tipos/schema,
  nomes, combinações sensor/tipo e limites são validados antes de qualquer save.
- Importar usa ACTION_OPEN_DOCUMENT; exportar selecionado usa
  ACTION_CREATE_DOCUMENT, MIME application/json. Só URIs content:, sem permissão
  ampla ou persistente de storage. Leitura/escrita do provedor fora da UI/DDS;
  commit local usa o AtomicFile limitado já existente nas operações de perfis.
- Export congela o perfil salvo antes do seletor; não recaptura configuração live.
- Após leitura válida, a importação mostra resumo e nome editável. **Adicionar**
  exige confirmação explícita; nome duplicado exige outro nome. Cancelar não salva.
  Perfis existentes e o snapshot aplicado permanecem iguais.
- Arquivo inválido, provedor com erro, schema futuro, excesso de tamanho, catálogo
  lotado ou conflito de escrita mantêm o catálogo anterior intacto.
- Recriação descarta a operação pendente; nenhum callback antigo altera a nova tela
  ou importa silenciosamente. Se o seletor tirar o app do foreground, DDS obedece
  ao lifecycle existente. Não há captura background.

## Validação executada e limites

TDD de schema/migração, storage e Views reais no host; 457 casos Java, zero falhas
ou erros e um skip opt-in legado. Revisão independente sem bloqueadores; build
ARM64/hooks OFF e lint sem erros. [Fechamento](FINALIZATION-v0.6.md).

Smoke físico direcionado **7/7**: schema1 sem regravação no load; importar com
confirmação/UUID novo; exportar o perfil salvo apesar de edição não salva; cancelar;
rejeitar JSON inválido; aplicar e alternar as intenções scan/câmera; salvar e
reiniciar desconectado. Usou configuração sintética, sem produtor DDS ou imagens.

O limite de bytes não é timeout de provedor: um provedor que não retorna pode
ocupar o único worker de transferência até encerrar o processo. Não há fila
crescente nem bloqueio do worker DDS; timeout/cancelamento de I/O fica como
robustez futura. Não é cloud/sync, catálogo completo, editor de dashboards ou
gravação de amostras. Os testes não qualificam soak, rede ou sensores físicos.
