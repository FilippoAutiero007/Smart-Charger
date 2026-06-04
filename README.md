# Smart Charger

Controllo automatico di dispositivi Sonoff basato sulla percentuale della batteria del telefono.

## Installazione rapida

Scarica l'ultima release APK dalla sezione [Releases](https://github.com/FilippoAutiero007/Smart-Charger/releases) e installala sul tuo dispositivo Android.

## Architettura

```
Smart-Charger/
├── auth-server/          # Backend per login automatico via email (Node.js + Express)
├── monitor-batteria/     # App Android VoltGuard Pro (Kotlin + Jetpack Compose)
├── index.js              # Server OAuth locale (Koa + eWeLink API)
├── config.js             # Configurazione eWeLink
├── sonoffControl.js      # CLI per controllo manuale Sonoff
└── README.md
```

## Come funziona

L'app Android **VoltGuard Pro** monitora la batteria del telefono e comanda un dispositivo Sonoff in base a soglie configurabili:

- **Batteria ≤ soglia ON** → Sonoff si **ACCENDE**
- **Batteria ≥ soglia OFF** → Sonoff si **SPEGNE**

Utile per caricabatterie smart, power bank, o qualsiasi dispositivo vada attivato in base al livello della batteria.

## Configurazione rapida (via email)

1. Avvia il server di autenticazione (su Render o locale)
2. Nell'app: **Opzioni → Controllo Sonoff → Configurazione Automatica via Email**
3. Inserisci la tua email → ricevi un link di login
4. Clicca il link, autorizza l'app, ti verrà mostrato un codice a 5 cifre
5. Inserisci il codice nell'app → tutto configurato automaticamente

## Configurazione manuale

1. Esegui `npm install` poi `npm start` in questa cartella
2. Apri `http://localhost:8000/login` nel browser
3. Fai login con le credenziali eWeLink
4. I token vengono salvati in `token.json`
5. Copia `accessToken` e `refreshToken` nell'app (Opzioni → Controllo Sonoff)
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
| `RESEND_API_KEY` | API key di Resend per l'invio email |
| `BASE_URL` | URL pubblico del server (es. https://tuo-app.onrender.com) |
| `FROM_EMAIL` | Mittente email (es. onboarding@resend.dev) |

## Build APK (sviluppatori)

```bash
cd monitor-batteria
./gradlew assembleDebug
```

L'APK si trova in `app/build/outputs/apk/debug/app-debug.apk`.

## Requisiti

- **Android**: minSdk 24, targetSdk 36
- **Node.js**: versione 20.x per il server locale
- **eWeLink**: account developer su https://dev.ewelink.cc con un'applicazione registrata

## Credenziali

Per ottenere `APP_ID` e `APP_SECRET`:
1. Vai su https://dev.ewelink.cc e fai login
2. Crea un'applicazione (tipo: Android)
3. Ottieni le credenziali nella pagina dell'app
