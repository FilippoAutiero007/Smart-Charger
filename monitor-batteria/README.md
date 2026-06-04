# VoltGuard Pro

App Android per il monitoraggio della batteria con controllo automatico di dispositivi Sonoff.

## Funzionalità

- **Monitoraggio batteria**: percentuale, temperatura, voltaggio, stato di carica
- **Grafico tendenza**: storico della batteria nelle ultime ore
- **Notifiche**: avvisi quando la batteria raggiunge soglie critiche
- **Controllo Sonoff**: accende/spegne automaticamente un dispositivo Sonoff in base alla percentuale batteria
- **Login automatico**: configurazione via email tramite server esterno (auth-server)
- **Soglie personalizzabili**: soglia ON (accensione) e OFF (spegnimento) configurabili

## Build

```bash
./gradlew assembleDebug
```

L'APK si trova in `app/build/outputs/apk/debug/app-debug.apk`.

## Tecnologie

- Kotlin + Jetpack Compose
- Material 3 (dark theme)
- WorkManager (controllo periodico batteria)
- OkHttp (API eWeLink)
- Android SDK 36, minSdk 24
