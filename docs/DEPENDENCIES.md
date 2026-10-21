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
