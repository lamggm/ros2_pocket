# Compact dashboard previews

Small visual cards devote their viewport to the plot, **Lidar Scan**, **Camera** or **Modelo 3d**. Each opens a centered dialog on tap. Roomy cards retain inline controls and an expand action. The layout editor owns drag/resize gestures.

The plot title is saved through the dashboard pencil → plot options → **Nome do card**. The chart draws the title inside the viewport, with raw Y ticks, X in seconds relative to the latest plotted reception, and a bottom-right swatch legend. Units are declared per series, with no conversion; a constant series only labels its actual Y value. Empty history does not invent an axis range or a zero.

The dialog reparents the existing View, not its buffers or readers. Navigation, rebind and background close the dialog and its unit editor. LiDAR and camera have independent topic settings and read simultaneously on the Dashboard when both topics are configured. Opening a preview does not restart either reader. Stopping or changing one topic does not stop the other. Leaving the Dashboard suspends both (Localization uses the map reader instead); returning requests fresh data. The saved `visual.mode` remains readable for older builds but does not limit concurrent playback. Layout schema 6 migrates schemas 1–5 in memory, retaining the old LiDAR slot and saved positions and adding Camera in a free slot. The profile envelope is unchanged.

Offline robot assets remain bound to the exact UTF-8 URDF fingerprint. The original package is preserved; subsequent imports use up to four fingerprint-keyed slots, with the imported slot taking precedence for that description. Imports need explicit local ZIP selection; neither DDS nor the Android app fetches mesh URIs.
