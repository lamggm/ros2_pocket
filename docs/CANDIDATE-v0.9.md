# Candidato v0.9 — congelamento antes da campanha

[Estabilidade e budgets](STABILITY-v0.9.md) · [Issue #25](https://github.com/lamggm/ros2_pocket/issues/25)

## Autorização e escopo

Depois do preflight físico no baseline v0.8, o operador autorizou congelar um
candidato v0.9, validar/buildar, atualizar in-place preservando dados e executar
a campanha oficial nos mesmos bytes, somente se fisicamente desplugado.
Não inclui mudança de RF/IP, VPN, firewall, PIN, uninstall/clear data ou comandos
de robô. A v0.8 publicada e seus artefatos permanecem preservados.

## Identidade escolhida antes do build

- Package `com.lamggm.rospocket.dev`, assinatura debug compatível com a instalação.
- Identidade interna **0.9.0-dev**, versionCode **9**; ARM64/API28+.
- Capacidade de hooks diagnósticos **OFF**, sem instrumentação/faults ativos.
- Além do reparo de retenção de diagnóstico do harness já revisado, a mudança
  produtiva para preparar este candidato é apenas a identidade Gradle. Não há
  mudança de stack, DDS, JNI, recepção, gráfico ou preferência para obter PASS.
- TDD de identidade: o teste real de BuildConfig/Views falhou na versão0.8 antes
  da mudança e passou com0.9/code9, mantendo package, debug e SOMENTE LEITURA.

O SHA de fonte congelado, árvore, snapshot completo, SHA-256 do APK, certificado,
ABIs e política são extraídos dos arquivos reais e registrados nas evidências da
campanha/issue25 após build. Este documento não inventa hash nem antecipa o
resultado de testes/revisão/build ainda em execução.

## Sequência de gates

1. Revisão independente pequena do delta de identidade; fonte congelada/limpa.
2. Wrapper oficial offline: regressões Python/nativas/Java, lint, build ARM64 e
   inspeção real de metadata/ELFs/política. Reter falhas/skips e comparar fonte.
3. Assinatura compatível no host autorizado, sem copiar material privado;
   verificar certificado e igualdade do payload fora de META-INF. Não publicar
   APK do CI/rebuild diferente como se fosse o físico.
4. Backup fresco de APK/configurações/settings/UID; update **in-place** e pull-back
   dos bytes instalados, com hashes iguais. Sem uninstall/clear data.
5. Preflight com quatro watches a2Hz e gráfico inteiramente visível, no APK
   congelado. Configuração de fixture pode ser temporária e restaurada; isso não
   é um novo aceite de importação/UI de perfis.
6. Coleta oficial de30min, se preflight admitir e alimentação estiver fisicamente
   retirada. Mesmo APK/snapshot durante toda a campanha, sem pause/Home/restart,
   simulação de bateria, interpolação, junção de tentativas ou budgets reduzidos.
7. Análise oficial, leitura independente do resultado bruto e cleanup/restauração
   conferidos. Soak passed não satisfaz sozinho os demais gates/aceite da versão.

Os budgets normativos permanecem os da CLI `qualification.py matrix`, registrados
no início da v0.9. AC conectado, captura não observável, mudança de identidade,
source/APK divergente ou transporte desconhecido bloqueiam; não relaxar validação
para admitir o candidato. Uma tentativa incompleta fica incompleta.

## Estado desta preparação

Identidade definida e regressão focada RED/GREEN executada. **Build, assinatura,
instalação e soak desse novo candidato ainda não são declarados aprovados.**
O preflight anterior foi feito no APKv0.8 e não foi transferido como aceitev0.9.
No último readback anterior à preparação, o tablet estava conectado à alimentação;
a retirada física é um pré-requisito futuro, não um comando que o agente possa
simular. Fonte/artefato/resultados posteriores ficam identificados na issue25.
