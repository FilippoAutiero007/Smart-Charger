# VoltGuard Pro

App Android per il monitoraggio della batteria con controllo automatico di dispositivi Sonoff.

## Funzionalità

- **4 tab con Salute predefinita**: Salute, Carica, Scarica, Automazione
- **Monitoraggio batteria live**: percentuale, temperatura, voltaggio, corrente (mA), potenza (W), stato di carica
- **Telemetria stile AccuBattery**: normalizzazione corrente OEM (uA/mA, polarità), stime vita batteria e standby (`BatteryTelemetryEngine`, `StandbyTracker`)
- **Grafico tendenza**: storico della batteria nelle ultime ore, con supporto landscape
- **Persistenza Room**: sessioni di carica e campioni di scarica salvati localmente
- **Servizio in foreground**: `BatteryMonitorService` con `START_STICKY` e sticky intent `ACTION_BATTERY_CHANGED`
- **Controllo Sonoff reale**: accoppiamento eWeLink cloud o LAN, selezione dispositivo e test comandi
- **Soglie personalizzabili**: soglia ON (accensione) e OFF (spegnimento) con notifiche automatiche
- **Login automatico**: configurazione via email tramite server esterno (auth-server)

## Build

```bash
./gradlew assembleDebug     # debug
./gradlew assembleRelease   # release
```

Gli APK si trovano in `app/build/outputs/apk/{debug,release}/`.

## Tecnologie

- Kotlin + Jetpack Compose
- Material 3 (dark theme, palette ElegantPurple)
- Room (persistenza locale)
- WorkManager (controllo periodico batteria)
- OkHttp (API eWeLink)
- Android SDK 36, minSdk 24
