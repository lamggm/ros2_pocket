# Compilar o APK

[Início](../README.md) · [Índice da documentação](README.md)

Para instalar o app, use a [pré-release pronta](https://github.com/lamggm/ros2_pocket/releases/tag/v0.3.0-rc1).
Este guia é para quem quer compilar. A receita é Linux x86_64; para fixtures,
o binário host precisa ter ABI compatível com a base Ubuntu 24.04 usada no container.

## Escolher o código

O APK publicado vem da tag `v0.3.0-rc1`, fonte `9019184`. Para compilar essa versão:

```bash
git clone --branch v0.3.0-rc1 --single-branch https://github.com/lamggm/ros2_pocket.git
cd ros2_pocket
```

O desenvolvimento segue em `integration/v0.3`. A documentação da `main` pode
mostrar o APK publicado sem promover o código dessa versão para a branch.
Confira `git status`, branch e SHA antes de compilar ou alterar arquivos.

## Preparar o ambiente

Você precisa de JDK 17, Android SDK platform 35/build-tools 35.0.0, compilador
C/C++, CMake 3.16 ou superior, Ninja, Python 3.11 ou superior, Git, curl e unzip.
Docker é necessário somente para as fixtures ROS isoladas, não para o build
ordinary. Instale o SDK e aceite suas licenças separadamente. Exemplo, ajustando
os caminhos para suas instalações:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
sdkmanager --licenses
```

O projeto fornece o wrapper oficial Gradle 8.7; não precisa de Gradle global,
cache privado de dependências, conta ou chave de assinatura de produção.
O wrapper confere a distribuição por SHA-256. Pins e referências upstream,
incluindo NDK r28c, Cyclone DDS 0.10.5 e licenças, estão em [DEPENDENCIES](DEPENDENCIES.md).

## Primeiro build com rede

Na raiz do checkout, com acesso aos repositórios Android/Maven do Google,
Maven Central, Gradle e GitHub:

```bash
# Opcionais: diretórios novos deixam explícito quais caches serão usados.
export POCKET_TOOLCHAINS="$PWD/build/toolchains"
export GRADLE_USER_HOME="$PWD/build/gradle-user-home"
bash tools/bootstrap.sh --online
bash tools/build.sh --online
```

Os wrappers bootstrap/build usam `--online` por padrão. Bootstrap verifica o
arquivo oficial do NDK por tamanho/SHA-1 upstream, o commit fixado do Cyclone e
compila IDLC/ddsc host e ddsc Android. O build gera descritores de tipos, executa
os testes portáveis e sessão DDS loopback, compila bibliotecas Android e chama:

```text
:app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Use os wrappers do projeto, não um Gradle arbitrário substituído por variável.
Ao trocar toolchains, separe builds/caches: CMake guarda caminhos absolutos de
compilador e bibliotecas. O download/provisionamento reproduz dependências,
não promete APKs assinados byte a byte idênticos em máquinas diferentes.

## Build offline depois de preparar os caches

Use os mesmos `POCKET_TOOLCHAINS` e `GRADLE_USER_HOME` de um build online bem-sucedido:

```bash
bash tools/bootstrap.sh --offline
bash tools/build.sh --offline
bash tools/test.sh --portable
```

Offline não faz o primeiro provisionamento. Ele precisa do NDK verificado,
checkout Cyclone fixado, ferramentas nativas, JDK/SDK/licenças, distribuição do
wrapper já extraída e todas as dependências Gradle/Maven, inclusive lint e JUnit.
A ausência de downloads nativos/distribuição falha cedo; a ausência de artefatos
Maven falha no Gradle, sem fallback silencioso para online.

O wrapper Gradle sozinho não respeita `--offline` para baixar sua distribuição;
o script do projeto confere a localização exata desse cache antes de executá-lo.
`POCKET_TOOLCHAINS`, `GRADLE_USER_HOME`, `TMPDIR`, `ANDROID_HOME` e
`ANDROID_SDK_ROOT` são configuráveis. Os caches padrão ficam fora do Git.
O modo fixture é separado, pode baixar imagem/pacotes e não faz parte da promessa offline.

## Onde ficam os resultados

- `app/build/outputs/apk/debug/app-debug.apk`: APK ARM64 com assinatura debug.
- `build/artifacts/app-debug.json`: package, versão/code, ABI, tamanho, SHA-256,
  certificado debug e origem Git, incluindo `dirty` e hash do snapshot de conteúdo.

Os metadados vêm de `aapt2`, `apksigner` e inspeção ZIP, não de valores presumidos
do Gradle. O build compara snapshots antes/depois para rejeitar edição durante
a execução. O snapshot cobre nomes/conteúdos de arquivos tracked e untracked não
ignorados, inclusive deleções; não cobre caches do usuário. Um build sujo registra
`dirty: true`, não se apresenta como artefato do commit limpo.

Certificados debug podem variar entre máquinas, CI e tablet. Um APK recompilado
não autoriza desinstalar uma versão com outro certificado. Confira também as
[instruções de atualização](USAGE.md#instalar-e-atualizar). LICENSE/NOTICE das
dependências nativas acompanham os assets. Binários, chaves, ambiente local e
evidências ficam ignorados e não devem ser publicados em commits.

Os wrappers não fazem assinatura de produção, upload de release ou instalação.
A pré-release pública foi uma operação separada; compilar não repete essa publicação.

## Testes adicionais

Com Docker disponível, a fixture real Jazzy é uma operação separada:

```bash
bash tools/test.sh --fixture
```

Ela exercita Fast DDS e Cyclone DDS isolados; não precisa de tablet. Consulte
[Validação](VALIDATION.md) para isolamento, relatórios, gates de CI e os limites
do que cada execução comprova. Não execute campanhas de rede/dispositivo por
analogia com um build bem-sucedido.
