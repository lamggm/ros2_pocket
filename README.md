# ROS Pocket — independent F-Droid repository

GitHub Pages deployment branch. Application source and releases stay on `main`:
https://github.com/lamggm/ros2_pocket

This is an independent upstream repository, **not** inclusion in the official
f-droid.org catalogue. No GitLab account or running self-hosted server is required
for users of this repository.

## Add to F-Droid

Open the published landing page for the QR code:
https://lamggm.github.io/ros2_pocket/fdroid/repo/

Or add this exact URL in F-Droid → Settings → Repositories → Add:

```
https://lamggm.github.io/ros2_pocket/fdroid/repo?fingerprint=2F2F3B87A332250B8F05CBB3072383F7A2067DEFF90CB828543CCCF961F75F86
```

Repository signing certificate SHA-256:
`2F2F3B87A332250B8F05CBB3072383F7A2067DEFF90CB828543CCCF961F75F86`

The APK keeps the **upstream production** certificate used by GitHub releases:
`c610ab18b97bd70184ffd7069a7205ab465b7aa8b55df98e89f1281fb24f48b4`.
It can coexist with DEV. This does not bypass Android's certificate rules for a
separately signed official F-Droid build.

## Maintenance

Only publish qualified production APKs from verified GitHub releases. Retain the
repository-index signing identity; never generate a replacement during an update.
The private keystore, passwords, working config, backup and build/status logs stay
outside this branch and outside the application's source checkout.

For each update, use the private publisher workspace with fdroidserver 2.4.5 and
its existing signing key, place the verified APK under `repo/`, update app metadata,
and run `fdroid update --pretty`. This indexes the already signed APK; do not
rebuild or re-sign it. Validate the strict v2 entry signature and index hash chain,
the legacy v1 signature using fdroidserver's v1 compatibility verifier, the pinned
repository fingerprint and the indexed APK hash/certificate.

Copy only public `repo/` content to `fdroid/repo/`, excluding generated `status/`
logs; retain this README, root landing page and `.nojekyll`. Commit and normally
push `gh-pages` without force. Never copy the private publisher workspace wholesale.
After GitHub Pages deploys, fetch the public signed index and every referenced
asset/APK, verify signatures and digests again, and test a wrong fingerprint is
rejected. Updating the GitHub release alone does **not** automatically regenerate
this repository. Index signing is local; no signing secret is stored in Actions.
