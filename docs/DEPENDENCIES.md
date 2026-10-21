# Pinned native dependency provenance

- Cyclone DDS **0.10.5**, commit
  `2cdd114cbd18340c606573b4cc8dc20cc161ec5a`:
  https://github.com/eclipse-cyclonedds/cyclonedds/tree/0.10.5
- Built from source for Linux host tools and Android arm64-v8a, API 28, with
  security, SSL, shared-memory, examples, DDSPerf and tests disabled; shared ddsc,
  IDLC built on host only. Android JNI uses static libc++.
- Cyclone is available under EPL-2.0 or BSD-3-Clause (the Eclipse Distribution
  License); its upstream LICENSE/NOTICE are retained in `third_party/`.
- Android NDK **r28c / 28.2.13676358**, official Linux archive:
  https://dl.google.com/android/repository/android-ndk-r28c-linux.zip
  Official metadata: https://dl.google.com/android/repository/repository2-3.xml
  Archive bytes: `722261334`; metadata SHA-1:
  `a7b54a5de87fecd125a17d54f73c446199e72a64`.
- NDK stays in the external per-user cache; it is not redistributed in the APK.
- JDK: existing user-local Temurin 17. Android API/build tools 35 / 35.0.0;
  Gradle 8.7, Android Gradle Plugin 8.5.2, JUnit 4.13.2.
- Native link options use 16 KiB max-page alignment for JNI; NDK r28 also builds
  libddsc with 16 KiB-compatible ELF segments. Physical tablet uses 4 KiB pages;
  an actual 16 KiB-page Android device has not been tested.
- Message interface names and Jazzy graph layout are based on ROS upstream:
  https://github.com/ros2/rmw_dds_common/tree/jazzy/rmw_dds_common/msg
  and installed Jazzy IDL. Generated C descriptors are rebuilt with pinned IDLC.

## Gradle wrapper and CI supply-chain references

Official Gradle 8.7 wrapper scripts/JAR are copied unchanged from tag `v8.7.0`:
https://github.com/gradle/gradle/tree/v8.7.0/gradle/wrapper

- Distribution: https://services.gradle.org/distributions/gradle-8.7-bin.zip
- Official distribution SHA-256 reference:
  https://services.gradle.org/distributions/gradle-8.7-bin.zip.sha256
  `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`
- Official wrapper JAR SHA-256 reference:
  https://services.gradle.org/distributions/gradle-8.7-wrapper.jar.sha256
  `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`
- The wrapper enforces `distributionSha256Sum`; CI separately verifies the JAR
  before executing it. Gradle wrappers are Apache-2.0 licensed upstream.

CI action SHAs were resolved from their upstream tag refs (not guessed):

| Repository/tag | Commit |
| --- | --- |
| `actions/checkout@v4.2.2` | `11bd71901bbe5b1630ceea73d27597364c9af683` |
| `actions/setup-java@v4.7.1` | `c5195efecf7bdfc987ee8bae7a71cb8b11521c00` |
| `android-actions/setup-android@v3.2.2` | `9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407` |
| `actions/upload-artifact@v4.6.2` | `ea165f8d65b6e75b540449e92b4886f43607fa02` |

Android command-line tools revision `12266719` is pinned in CI. Public JDK 17,
SDK platform 35 and build-tools 35.0.0 are prerequisites, not vendored binaries.

OCI index digests were resolved with `docker buildx imagetools inspect`:

- Public fixture base `docker.io/library/ros:jazzy-ros-base-noble`:
  `sha256:103b6c417973394659af6804d4917cd489b91e5ed22eb7bef263025a3bed2410`.
- Secret scanner `docker.io/zricethezav/gitleaks:v8.24.2`:
  `sha256:b5918eb91b8d2473cec722f066abb4352e4ffdc4ec9f4283ec143aba9ec9ebc4`.

The fixture Dockerfile pins every additional APT package version; its signed
repositories/base provide rclpy/std_msgs and Fast DDS. The Cyclone RMW is Jazzy's
2.2.4 with Cyclone DDS 0.10.5. See the Dockerfile for the complete dependency
addition lock. Package removal upstream requires an explicit reviewed pin refresh.
