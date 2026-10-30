# Exportação CSV — candidato v0.5

[Projeto](../README.md) · [Roadmap](ROADMAP.md#v05)

Este recurso está em desenvolvimento na v0.5. Não pertence ao APK público v0.4.

## Usar

1. Em Monitor, selecione os campos do gráfico e espere pontos recebidos.
2. Se quiser um trecho congelado, pause o gráfico.
3. Toque **Exportar gráficos em CSV** e escolha o destino no seletor Android.
4. Confira o resultado abaixo do botão: sucesso, cancelamento ou falha.

O snapshot é capturado **antes** de abrir o seletor. O arquivo não muda para os
pontos de uma sessão posterior enquanto você escolhe o destino. Pausado exporta
a cópia visível congelada; em live, a cópia consistente do histórico no clique.
Uma seleção vazia/sem pontos não abre o seletor.

## Alcance e clocks

CSV UTF-8, linhas CRLF, cabeçalho fixo, uma linha por ponto/campo. Campos de texto
são citados e aspas duplicadas; texto começando por `=`, `+`, `-` ou `@` (após
espaços/controles de borda) recebe apóstrofo antes de ser citado, para não executar
fórmulas em planilhas. Valores numéricos usam representação binary64 independente
do locale; não são números formatados do texto de echo.

Colunas, em ordem:

- `format_version`: 1.
- `capture_mode`: `latest_snapshot`, não todas as mensagens DDS.
- `display_mode`: `paused` ou `live` no instante do clique.
- `series_id`: identificador interno da série, que pode conter quebra de linha.
- `slot`: índice zero-based do watch; -1 quando uma origem é desconhecida.
- `session_generation`, `reader_generation`: identidade da origem retida.
- `topic`, `type`: nomes originais da origem, quando disponíveis; não inferidos do rótulo visual.
- `field`, `unit`: caminho numérico e unidade declarada; unidade desconhecida fica vazia.
- `receive_offset_ns`: recepção monotônica relativa ao ponto mais antigo de todo o export.
- `receive_origin_monotonic_ns`: origem comum usada para esse offset, não horário civil.
- `dds_source_timestamp_ns`: timestamp DDS original daquele ponto, ou vazio se indisponível;
  não confundir com ROS header nem assumir relógio igual ao da recepção.
- `value`: valor retido pelo gráfico; vazio para lacuna.
- `state`: `finite` ou `gap`. O histórico já unifica NaN/Inf/campo inválido em gap;
  o export não reconstrói a causa nem preenche zero.
- `break_before`: mantém a quebra de linha do gráfico, incluindo início, intervalos
  longos ou regressão do timestamp DDS.

Os dados vêm do histórico de **snapshots**, não de um gravador de mensagens ou
rosbag. Saltos de contagem não viram amostras interpoladas. O histórico usa Double:
Int64 fora da precisão exata do binary64 pode já estar arredondado; o CSV não
recupera precisão perdida. Nunca inferir unidades de um campo chamado x.

## Recursos e lifecycle

- Mantém limites do gráfico: até quatro séries; orçamento padrão por série já
  existente, até o teto configurável de 2048 pontos por série.
- Arquivo limitado a **2 MiB UTF-8**, serializado antes de abrir o stream do provedor.
- Uma escrita de provedor por processo, sem fila de exports ilimitada; I/O não usa
  UI nem worker DDS. Tentativa concorrente pode pedir para tentar novamente.
- Usa somente URI `content:` devolvida pelo seletor; sem permissão ampla de storage,
  URI persistente ou alteração de permissões do manifesto.
- O seletor é outra Activity: se colocar o app fora do primeiro plano, o lifecycle
  herdado suspende DDS, como Home. Isso não foi modificado para manter captura em
  background. A escrita em si não bloqueia recepção quando o app está foreground.
- Recriação/morte da Activity durante a escolha perde o snapshot pendente e pede
  nova exportação; não persistir telemetria em saved state para esconder isso.
- Uma escrita já aceita pelo usuário pode terminar após destruir a tela; seu
  snapshot é imutável e callbacks não atualizam uma Activity descartada. Provedor
  que bloquear mantém somente esse worker ocupado, não o worker DDS.
- Erro de escrita/fechamento não é sucesso; o destino pode ficar vazio ou parcial.
  Não há promessa de commit atômico em provedor externo nem exclusão automática
  de arquivo do usuário. Mensagem de erro não imprime URI ou conteúdo privado.

## Validação

TDD do timestamp por ponto, CSV, budgets/segurança de células, identidade, botão,
seletor e escrita fora da UI. Controles de cancelamento, falha de provedor, URI
inválida, recriação e snapshot pausado invalidado antes do resultado.

O teste de replay de gráficos também exporta dados de capturas reais de DDS e o
CSV é lido por Python. Replay de registros reais retidos é evidência host, não uma
nova execução de DDS nem aceite físico do candidato. Build/lint e testes completos
continuam separados do smoke no tablet, que precisa de transporte ADB disponível.
