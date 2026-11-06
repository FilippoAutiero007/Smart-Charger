# Smart Charger

Controllo automatico di dispositivi Sonoff basato sulla percentuale della batteria del telefono, con telemetria completa stile AccuBattery.

## Installazione rapida

Scarica l'ultima release APK (attuale: **VoltGuard Pro v2.6**) dalla sezione [Releases](https://github.com/FilippoAutiero007/Smart-Charger/releases) e installala sul tuo dispositivo Android. Gli APK più recenti sono anche presenti nella root del repository (`VoltGuard-Pro-v2.6-release.apk`).

## Architettura

```
Smart-Charger/
├── auth-server/          # Backend per login automatico via email (Node.js + Express)
├── monitor-batteria/     # App Android VoltGuard Pro (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/example/
│       ├── battery/          # Service in foreground, monitor batteria, sticky intent
│       ├── battery/engine/   # BatteryTelemetryEngine + StandbyTracker (metriche/health)
│       ├── data/             # Repository + Room (sessioni carica, campioni scarica)
│       └── ui/               # Compose: schermate, gauge, grafici, navigation
├── index.js              # Server OAuth locale (Koa + eWeLink API)
├── config.js             # Configurazione eWeLink
├── sonoffControl.js      # CLI per controllo manuale Sonoff
└── README.md
```

## Come funziona

L'app Android **VoltGuard Pro** monitora la batteria del telefono e comanda un dispositivo Sonoff in base a soglie configurabili:

- **Batteria ≤ soglia ON** → Sonoff si **ACCENDE**
- **Batteria ≥ soglia OFF** → Sonoff si **SPEGNE**

Un servizio in foreground (`BatteryMonitorService`, `START_STICKY`) legge lo stato della batteria in tempo reale tramite sticky intent `ACTION_BATTERY_CHANGED`, così l'automazione resta attiva anche a schermo spento.

Utile per caricabatterie smart, power bank, o qualsiasi dispositivo vada attivato in base al livello della batteria.

## Funzionalità dell'app (v2.6)

Quattro tab con **Salute** come schermata predefinita:

| Tab | Contenuto |
|-----|-----------|
| **Salute** | Indicatore radiale stato batteria, metriche live (stato dispositivo, ciclo/vita, standby) |
| **Carica** | Tachimetro batteria, metriche (W, mA, temperatura, tempo), grafico tendenza orario, log evento |
| **Scarica** | Analisi della scarica: drain per app/schermo, campioni storici |
| **Automazione** | Accoppiamento eWeLink & Sonoff (cloud o LAN), selezione dispositivo, test comandi, soglie ON/OFF, allarmi e notifiche |

Inoltre:

- **Telemetria stile AccuBattery**: normalizzazione corrente (uA/mA e polarità OEM), potenza in Watt, temperatura, stime vita batteria (`BatteryTelemetryEngine`)
- **Persistenza Room**: sessioni di carica e campioni di scarica salvati localmente
- **Grafico tendenza**: storico batteria nelle ultime ore (orientamento landscape supportato)
- **Accoppiamento reale eWeLink/Sonoff**: login cloud o LAN, pairing dispositivo e test presa dall'app

## Configurazione rapida (via email)

1. Avvia il server di autenticazione (su Render o locale)
2. Nell'app: **Automazione → Accoppiamento eWeLink & Sonoff → Configurazione Automatica via Email**
3. Inserisci la tua email → ricevi un link di login
4. Clicca il link, autorizza l'app, ti verrà mostrato un codice a 5 cifre
5. Inserisci il codice nell'app → tutto configurato automaticamente

## Configurazione manuale

1. Esegui `npm install` poi `npm start` in questa cartella
2. Apri `http://localhost:8000/login` nel browser
3. Fai login con le credenziali eWeLink
4. I token vengono salvati in `token.json`
5. Copia `accessToken` e `refreshToken` nell'app (Automazione → Controllo Sonoff)
6. Inserisci l'ID del dispositivo Sonoff e le soglie

## Deploy del server di autenticazione

Il server `auth-server/` può essere deployato su [Render.com](https://render.com) (piano free):

1. Connetti il repository a Render
2. Crea un **Web Service**
3. Imposta:
   - **Root Directory**: `auth-server`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
4. Aggiungi le variabili d'ambiente (vedi `auth-server/.env.example`)
5. Deploy

### Variabili d'ambiente richieste

| Variabile | Descrizione |
|-----------|-------------|
| `APP_ID` | App ID da https://dev.ewelink.cc |
| `APP_SECRET` | App Secret da https://dev.ewelink.cc |
| `BASE_URL` | URL pubblico del server (es. https://tuo-app.onrender.com) |
| `PORT` | Porta del server (default 3000) |

Email — due opzioni (Gmail SMTP ha priorità se configurato, Brevo resta come fallback):

**Opzione A — Gmail SMTP (mittente reale Gmail, consigliato per consegna verso Gmail):**

| Variabile | Descrizione |
|-----------|-------------|
| `GMAIL_USER` | Account Gmail mittente (deve avere la verifica in due passaggi attiva) |
| `GMAIL_APP_PASSWORD` | App Password Google a 16 caratteri (spazi rimossi automaticamente) |
| `MAIL_FROM_NAME` | Nome visualizzato nell'email (es. VoltGuard Pro) |

Setup Gmail: attiva la 2FA → https://myaccount.google.com/apppasswords → crea una App Password → copia le variabili sopra.

**Opzione B — provider cloud SMTP (Brevo, free 300 mail/giorno, nessun dominio richiesto):**

| Variabile | Descrizione |
|-----------|-------------|
| `SMTP_HOST` | Host SMTP (default `smtp-relay.brevo.com`) |
| `SMTP_PORT` | Porta SMTP (default `587`; fallback automatico `2525`, oppure `465` con SSL) |
| `SMTP_USER` | Login SMTP (Brevo: Settings → SMTP & API → SMTP key) |
| `SMTP_PASS` | Password SMTP (la SMTP key generata, visibile una sola volta) |
| `FROM_EMAIL` | Mittente: deve essere l'indirizzo verificato come sender su Brevo |
| `MAIL_FROM_NAME` | Nome visualizzato nell'email (es. VoltGuard Pro) |
| `MAIL_FROM` | Opzionale: envelope From completo con dominio verificato |
| `RESEND_API_KEY` | Opzionale: fallback automatico a Resend se SMTP fallisce |

Setup Brevo (5 minuti, no carta):
1. Registrati su https://www.brevo.com
2. **Settings → Senders → Add sender**: aggiungi il tuo indirizzo e verifica col codice a 6 cifre ricevuto via email (niente DNS)
3. **Settings → SMTP & API → tab SMTP → Generate SMTP key** → copia username + key nelle variabili sopra

Opzionali:

| Variabile | Descrizione |
|-----------|-------------|
| `CRON_SECRET` | Secret per il cron manuale `POST /_cron/renewals` |

Nota: il rinnovo automatico dei token (ogni ~25 giorni, 5 giorni prima della scadenza) è gestito via API `/subscribe-renewal` senza variabili aggiuntive.

## Build APK (sviluppatori)

```bash
cd monitor-batteria
./gradlew assembleDebug     # debug
./gradlew assembleRelease   # release (usa debug.keystore di fallback se non configurato)
```

Gli APK si trovano in `monitor-batteria/app/build/outputs/apk/{debug,release}/`.

## Requisiti

- **Android**: minSdk 24, targetSdk 36
- **Node.js**: versione 20.x per il server locale
- **eWeLink**: account developer su https://dev.ewelink.cc con un'applicazione registrata

## Credenziali

Per ottenere `APP_ID` e `APP_SECRET`:
1. Vai su https://dev.ewelink.cc e fai login
2. Crea un'applicazione (tipo: Android)
3. Ottieni le credenziali nella pagina dell'app
