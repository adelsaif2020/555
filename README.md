AzanBreakNative v2 - Pixel-perfect Compose UI + Breaks management + WorkManager ForegroundWorker scheduling

What's new in v2:
- Compose UI translated from the provided HTML/CSS (RTL aware, similar colors and layout).
- Full breaks management: add/edit/delete, per-break start/end URIs (persisted via takePersistableUriPermission).
- Notification channel created automatically on app start.
- Scheduling uses WorkManager (OneTimeWorkRequest with initialDelay) and CoroutineWorker runs as foreground to improve reliability under Doze.
- BootReceiver re-schedules stored breaks and today's prayers after device reboot.

Notes:
- You still need to implement runtime permission prompts and handle the case of exact timing if you require millisecond precision.
- Media playback uses MediaPlayer for content URIs chosen by user (requires persistable URI permission).
- Open the project in Android Studio, sync Gradle, and run on a device/emulator.
