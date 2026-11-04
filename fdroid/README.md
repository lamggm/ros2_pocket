# F-Droid packaging

This directory is a submission overlay for fdroiddata, not a hosted repository or
an assertion that ROS Pocket is available in the official catalogue.

- metadata/com.lamggm.rospocket.yml describes production1.0.0/code10.
- srclibs/CycloneDDS.yml pins the source via the build's exact srclib commit.
- Copy both files into a current fdroiddata checkout. Its official config/category
  assets are required for lint; do not invent a local category whitelist.
- tools/fdroid-native.sh runs in the build phase, after scanning. It accepts the
  reviewed Cyclone source and provisioned NDK28.2.13676358, checks their identities,
  compiles host IDLC, Android DDS and JNI from source, and forces hooks OFF.
- The helper performs no network download, Gradle build, signing, tests or install.
  F-Droid supplies Gradle/SDK/NDK and builds the release variant; production has no
  embedded signing configuration. Do not ship prebuilt native libraries in Git,
  call bootstrap.sh online inside the recipe, or bypass the source/binary scanner.
- English/Portuguese descriptions, icon and changelog are in fastlane/metadata/android.

Validation commands in a disposable fdroiddata checkout with real prerequisites:

```sh
fdroid readmeta
fdroid lint com.lamggm.rospocket
fdroid scanner --exit-code com.lamggm.rospocket:10
fdroid build --verbose --latest --test --scan-binary com.lamggm.rospocket
```

The final v1.0.0 tag must exist before upstream can fetch it. Until then, local
validation can target a frozen candidate commit in a disposable recipe, recording
that exact commit/tree separately. Do not describe a host native build as an
execution on the official buildserver or a successful store publication.

Submission: an authenticated GitLab merge request to fdroid/fdroiddata (or RFP to
fdroid/rfp), followed by maintainer review/build/publication. GitHub production and
F-Droid use independent signing identities; DEV is a third, separate application ID.
Neither changing repos nor matching version names bypasses Android's signer checks.

Official sources:
- https://f-droid.org/docs/Inclusion_Policy/
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/

Current inclusion policy asks for three months of active development unless an
exception applies (for example proven users). Do not fabricate eligibility. A login
wall/anti-bot challenge is an external submission blocker, not a packaging failure.
