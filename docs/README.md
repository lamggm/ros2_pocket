# Documentação do ROS Pocket

[Voltar ao projeto](../README.md) ·
[Baixar a v0.4](https://github.com/lamggm/ros2_pocket/releases/tag/v0.4.0)

Para começar a usar o APK, leia os guias abaixo. As referências técnicas e os
registros históricos ficam separados para não interromper o passo a passo.
Os guias descrevem a v0.4; código de outras branches pode diferir. O APK físico
mantém o nome interno 0.4.0-dev, sem recompilação para alterar a etiqueta.

## Sensores visuais v0.4

- [Sensores visuais](SENSOR-VIEWS.md): LaserScan 2D, Image/CompressedImage, uso, limites e validação host.
- [Escopo v0.4](ROADMAP-v0.4.md): implementação autorizada e fronteiras do ciclo.
- [Fechamento v0.4](FINALIZATION-v0.4.md): host/revisões e smoke físico 6/6, identidade do APK e limites.

## Para quem usa o app

- [Exportação CSV — candidato v0.5](CSV-EXPORT.md): histórico de snapshots, clocks, limites e comportamento do seletor Android; não faz parte do APK público v0.4.

- [Guia de uso](USAGE.md): instalar/atualizar, conectar, ler tópicos, usar gráficos,
  logs e perfis; problemas de descoberta, QoS e VPN.
- [Compatibilidade e limites](COMPATIBILITY.md): Android, ROS 2, mensagens
  suportadas, rede, buffers e recursos que não estão implementados.
- [Versões e estado do projeto](PROJECT-STATUS.md): APK disponível, branches,
  evidência por versão e trabalhos pendentes.

## Para quem compila ou testa

- [Compilar o APK](BUILDING.md): ambiente Linux, dependências, wrappers,
  build online/offline, assinatura debug e metadados de saída.
- [Testes, CI e evidências](VALIDATION.md): testes host, fixtures DDS, CI,
  reprodução autorizada no tablet e separação de aceite físico.
- [Dependências e licenças](DEPENDENCIES.md): versões, commits, checksums,
  imagens e referências upstream. LICENSE/NOTICE nativos acompanham o APK.
- [Orientação para agentes](../AGENTS.md): regras de trabalho da branch em que
  o arquivo é consultado; não substitui o guia de uso.

## Referências técnicas por recurso

Estes documentos mantêm contratos e detalhes da implementação, em português
ou inglês. Não precisam ser lidos em sequência para instalar ou usar o app.

- [Diagnóstico de conexão](connection-diagnostics.md).
- [Explorer, endpoints e QoS](EXPLORER-QOS.md).
- [Quatro watches](MULTIWATCH.md).
- [Inspeção de mensagens estruturadas](STRUCTURED-MESSAGES.md).
- [Gráficos numéricos](LIVE-PLOTS.md).
- [Console rosout](ROSOUT-CONSOLE.md).
- Perfis locais: [uso, persistência e exceções](USAGE.md#perfis-locais).

## Contratos de testes e instrumentação

- [Harness de tablet](TABLET-HARNESS.md): transport, isolamento, foco e navegação.
- [Falhas de criação de reader](READER-FAILURE-TESTS.md): testes nativos e limitações de execução Android.
- [Política diagnóstica Android](ANDROID-DIAGNOSTIC-POLICY.md): hooks e autorização de instrumentação.
- [Contrato do dump QA](DEVICE-QA-CONTRACT.md): metadados debug de worker/gráfico.
- [Proveniência dos gráficos](GRAPH-QA-PROVENANCE.md): vínculo entre dados, séries e desenho.
- [Publicação local de metadados](ARTIFACT-PUBLICATION.md): protocolo de arquivos e recuperação;
  não é a publicação de GitHub Releases.
- [Qualificação multiwatch](MULTIWATCH-QUALIFICATION.md): plano/runner específico, não um aceite automático.
- [Qualificação extensa v0.2](DEVICE-QUALIFICATION-v0.2.md): plano histórico com gates e soak próprios.

## Planejamento e histórico

- [Roadmap v0.5–v0.9](ROADMAP.md): direção futura, estados, aceite e limites por versão.
- [Milestones](https://github.com/lamggm/ros2_pocket/milestones): acompanhamento sem datas prometidas; v0.5 planejada e v0.6–v0.9 propostas.

Documentos de uma fase registram o que estava autorizado e validado naquele
momento. Consulte o [estado atual](PROJECT-STATUS.md) antes de aplicar decisões
antigas. Não reutilize aprovação de outro SHA/APK nem trate pendência como PASS.

- [Roadmap v0.3](ROADMAP-v0.3.md).
- [Fechamento do protótipo de bancada v0.3](FINALIZATION-v0.3.md).
- [Aceite físico curto v0.3: 8/8](SMOKE-v0.3.md).
- [Roadmap v0.2](ROADMAP-v0.2.md).
- [Entrega limitada de bancada v0.2](FINALIZATION-v0.2.md).
- [Notas de fechamento v0.2](RELEASE-NOTES-v0.2.md).
- [Validação histórica dos diagnósticos](connection-diagnostics-validation.md).
- [Reconciliação dos incrementos 04/06](MERGE-04-06.md).
- [Reconciliação dos incrementos 05/06](MERGE-05-06.md).

Novas propostas ficam nas [issues](https://github.com/lamggm/ros2_pocket/issues),
como a [compatibilidade com várias distribuições ROS 2](https://github.com/lamggm/ros2_pocket/issues/18).
