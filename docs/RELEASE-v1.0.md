# v1.0 — empacotamento e distribuição

Base: v0.9.0/main b39db937b422d930d2d0f5106244a9c5c824926f.
O operador autorizou fechar a v1, criar um ícone e disponibilizar no F-Droid.
Não há nova feature de recepção: diagrama nó→tópico→nó fica na issue33 para a v2.
RF/IP continua não validado na issue31; preservar o alcance de bancada da v0.9.

## Identidade definida antes do build

- Produção: com.lamggm.rospocket, 1.0.0/code10, não debuggable, label ROS Pocket.
- Bancada: com.lamggm.rospocket.dev, 1.0.0-dev/code10, label ROS Pocket DEV.
- Ambos ARM64/API28+, DDS direto, hooks nativos OFF; sem novas permissões.
- Produção instala ao lado do DEV: não copiar dados privados, limpar dados ou
  substituir uma instalação de outro certificado. Perfis podem ser transportados
  pelo fluxo JSON existente, por ação explícita do usuário.
- GitHub produção usa chave upstream própria, mantida fora de Git/evidências.
  F-Droid assina seu próprio build: os canais não oferecem atualização cruzada.
  Não trocar chaves/retagar versões históricas para ocultar incompatibilidade.

## Ícone e licença

Código original e ícone Apache-2.0, escolha delegada pelo operador. Dependências
mantêm suas próprias licenças/avisos. SVG-fonte em artwork; vetores adaptativos e
monocromáticos Android13 e PNGs de launcher/store derivados. Sem usar marca ROS.
Identidade e ícone passaram RED/GREEN com Views/BuildConfig reais. O build
canônico offline terminou com 498 testes Java, 0 falhas/erros e 1 skip; lint
debug/release sem erros/fatais (129 warnings cada). O helper compilou Cyclone DDS
pinado + JNI ARM64 offline, hooks OFF. `fdroidserver build --verbose --latest
--test --scan-binary` terminou exit 0 localmente, incluindo `assembleRelease` e
scanner binário; não era um buildserver dedicado/oficial.

APK upstream de produção testado: arm64-v8a, 4.879.521 bytes, SHA-256
`8fa81ab6b128eba46a69fd97c8fd4797d519152cd1a3b2a81961b5eeb35fa8d1`, source
`c7c7bfff30306c96ebb0b88bd51ae7d99e1d51c3`, hooks OFF. `apksigner` e `aapt2`
confirmaram certificado upstream, package/version code e `debuggable=false`.
O APK físico passou smoke 7/7: identidade somente leitura, instalação lado a
lado, telemetria numérica DDS fresca, retorno Home, restart de processo,
desconexão explícita e persistência do estado desconectado. 322 publicações
numéricas foram enviadas pelo fixture DDS nativo no próprio Android, que foi
removido ao final. DEV, configurações e settings pré-existentes foram
comparados e preservados; a instalação de produção ficou parada no tablet.
Este fixture não valida descoberta entre dispositivos, LAN, RF ou issue31.

## Gates desta entrega

1. **PASS:** fonte limpa `c7c7bfff30306c96ebb0b88bd51ae7d99e1d51c3`; suite completa,
   lintDebug/lintRelease, APKs hooks OFF, assinatura/manifesto e metadados reais.
2. **PASS local:** helper e build fdroidserver completos em fonte pinada, lint e
   scanner. Não confundir execução local com confirmação do buildserver oficial.
3. **PASS 7/7 no APK exato:** smoke físico descrito acima; dados privados DEV
   preservados. Não transfere evidência da v0.9 nem qualifica LAN/RF.
4. **Em andamento:** revisão independente passou sem bloqueador; PR #36 draft,
   checks ainda pendentes. Exigir check verde do head final, merge-tree e CI
   pós-merge antes da release.
5. **Pendente:** release v1.0.0 com esses bytes, manifesto/checksums e download
   público conferido. Chaves/identificadores/preferências/logs privados não publicados.

## F-Droid: etapas e bloqueios externos

Ver fdroid/README.md. Receita preparada/testada não é submissão; submissão não é
aprovação nem disponibilidade no catálogo. Aprovação, build e publicação dependem
dos mantenedores. A política prevê três meses de desenvolvimento, com exceções
como usuários comprovados; não inventar histórico ou usuários para obter inclusão.
O login GitLab observado ficou no desafio Cloudflare e não há sessão/credencial
GitLab disponível ao agente. A entrega deve relatar esse bloqueio se permanecer,
sem fingir MR enviado e sem substituir catálogo oficial por repositório próprio.
