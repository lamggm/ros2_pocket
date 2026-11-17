# Readout precision, units and known-robot profiles (v1.5 DEV)

The application remains observation-only: no motion, service/action calls or operational publications are introduced.

## Presentation only

- Reading-card editor: `Casas decimais (auto ou 0–8)`. Automatic retains the former five-significant-digit format. Fixed precision rounds only the rendered number; a rounded negative zero is displayed unsigned. Typed values, scale checks, details and numeric history retain their original precision. Missing/stale values never become zero.
- Number mode uses the configured typed numeric field when supported; nonnumeric messages retain their message presentation.
- Plot toolbar pencil: `Editar unidades das séries` opens a bounded chooser outside the fixed-height card, so long typed-field lists cannot clip the editor. The inline `Unidade · <series>` action remains available in details. An empty declaration inherits the instrument's unit only for the same watch slot and field path, otherwise retains explicit source metadata or reports unknown. An explicit unit is a label, never a conversion. Save the profile configuration to retain that declaration.
- Unit edits do not change numeric intake demand. An ad hoc label is not a new persistent selection. Removing/retiring its series cannot leave a phantom native filter. Edit authorization is per series; loss of another watch does not cancel a healthy editor.
- Paused traces retain their immutable frozen points. Only a real label change replaces a presentation wrapper; an unchanged label preserves the original healthy paused series object. CSV carries the displayed declared unit and original Double values.

## Association and automatic loading

Saving a profile with a current, unambiguous, live `robot_description` observation records the description wire topic and exact URDF fingerprint. The association also requires the profile's exact domain, peer, ROS graph format and LAN/VPN mode. Profile names and observed robot names remain separate. This matching is an operator convenience, **not authentication**, proof of a unique physical serial number, or permission to control a robot.

Legacy/imported profiles without an association require one explicit save while their robot is identified. Opening a profile/catalog alone never learns an identity. Multiple matching profiles, multiple description writers, a missing/stale description or an unavailable catalog never cause a guessed selection.

On explicit connection, the current description is observed through the existing metadata reader. A unique known profile can be loaded through a controlled restart of the app's read-only DDS session, preserving the exact transport configuration. No Wi-Fi/VPN/firewall changes occur. An identical already-applied snapshot does not create a reconnect loop. A newer saved snapshot with the same profile ID is loaded on recognition when the current presentation matches either that saved snapshot or the previous applied snapshot; genuinely unsaved changes are preserved.

An open panel draft or presentation changed since the applied profile/initial connection snapshot suppresses automatic loading for that connection attempt. Save/cancel the changes and reconnect, or apply a profile explicitly while disconnected. Anonymous/ad hoc sessions receive the same protection. Failure/recovery of the metadata worker retires the old observation generation; associating another profile requires a new valid description.

## Persistence

- Profile/catalog writer schema 7; local readers accept 1–7 and portable readers 2–7. New fields are `robot` (nullable association) and `plots[].unit` (empty means automatic). No ROS samples, images, map, TF or ephemeral DDS participant IDs are persisted.
- Dashboard writer schema 5 adds `display.decimals` (`-1` automatic, otherwise integer 0–8); readers accept 1–5. Legacy read is in-memory only and does not rewrite bytes.
- Strict unknown/missing-field, numeric-type, bounds and UTF-8 validation remains in force. Legacy test fixtures remove only default-valued new fields before deliberately constructing an older schema.
- Rename, dashboard changes and portable transfer preserve an existing association. Saving offline does not invent one or erase an existing one. Selecting a catalog entry is not applying it.

Validation receipts, rejected candidates, independent reviews and physical acceptance belong to the external task evidence directory, not to production fixtures or APK assets. Host Android tests use an explicit metadata double; they do not prove physical DDS interoperability. The BART motion test, when executed, targets only its isolated private simulator domain, never the observation-egress domain or a physical robot.
