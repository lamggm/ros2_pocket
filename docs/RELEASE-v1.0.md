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
Identidade e ícone passaram RED/GREEN com Views/BuildConfig reais; o helper
F-Droid passou controles focados e compilação nativa real sem rede, antes do build
final. Isso não antecipa o resultado do APK/CI/smoke ainda pendente.

## Gates desta entrega

1. Congelar fonte limpa; regressões afetadas, suite completa, lintDebug/lintRelease,
   hooks OFF nos APKs debug/release e assinatura/manifestos/ícones reais conferidos.
2. Build source-only F-Droid com Cyclone pinado e NDK fornecido, sem downloads no
   helper; metadados reais readmeta/lint/scanner, sem scanignore/skip-scan amplos.
3. Smoke proporcional no APK exato: DEV in-place e configuração preservada;
   produção separada não-debug, ícone/primeiro launch/recepção numérica real e
   Home/retorno/restart conforme contrato. Nenhuma prova da v0.9 vira PASS do APK1.
4. Revisão pequena, PR/CI do head exato, merge/árvore e CI pós-merge.
5. Release v1.0.0 com bytes realmente testados, manifesto/checksums e download
   público conferido. Chaves/identificadores/preferências/logs privados não publicados.

## F-Droid: etapas e bloqueios externos

Ver fdroid/README.md. Receita preparada/testada não é submissão; submissão não é
aprovação nem disponibilidade no catálogo. Aprovação, build e publicação dependem
dos mantenedores. A política prevê três meses de desenvolvimento, com exceções
como usuários comprovados; não inventar histórico ou usuários para obter inclusão.
O login GitLab observado ficou no desafio Cloudflare e não há sessão/credencial
GitLab disponível ao agente. A entrega deve relatar esse bloqueio se permanecer,
sem fingir MR enviado e sem substituir catálogo oficial por repositório próprio.
