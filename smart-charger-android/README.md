# 🔋 Smart Charger - App Android

## 📖 Cos'è Smart Charger?

Smart Charger è un'applicazione Android che ottimizza la ricarica della batteria del tuo dispositivo gestendo automaticamente le prese intelligenti Sonoff/eWeLink. L'app monitora costantemente il livello della batteria e può accendere/spegnere automaticamente il caricatore per mantenere la batteria in un range ottimale (es. 20%-80%).

## ✨ Funzionalità Principali

- 🔋 **Monitoraggio Batteria in Tempo Reale**: Visualizza livello e stato di carica
- 🔌 **Controllo Dispositivi Sonoff**: Gestisci prese intelligenti eWeLink
- 📊 **Dashboard Intuitiva**: Interfaccia moderna e facile da usare
- 🔔 **Notifiche Push**: Avvisi quando batteria è carica/scarica
- 🌐 **Lavora in Background**: Continua a funzionare anche quando l'app è chiusa
- 📧 **Notifiche Email**: Avvisi via email per eventi importanti
- 🔒 **Autenticazione Sicura**: Login con Clerk (email/password)
- ☁️ **Backup Cloud**: Dati salvati su Convex backend

## 🏗️ Architettura del Progetto

```
smart-charger-android/
├── src/
│   ├── components/          # Componenti UI riutilizzabili
│   │   ├── BaseButton.tsx   # Pulsante personalizzato
│   │   └── BaseInput.tsx    # Input testo personalizzato
│   │
│   ├── constants/           # Configurazioni e costanti
│   │   └── Config.ts        # Tutte le configurazioni dell'app
│   │
│   ├── hooks/               # Custom React Hooks
│   │   └── useAuthManager.ts # Hook per gestione autenticazione
│   │
│   ├── navigation/          # Sistema di navigazione
│   │   └── index.tsx        # Navigator principale
│   │
│   ├── screens/             # Schermate dell'app
│   │   ├── SignInScreen.tsx    # Schermata login
│   │   ├── SignUpScreen.tsx    # Schermata registrazione
│   │   ├── DashboardScreen.tsx # Dashboard principale
│   │   └── ProfileScreen.tsx   # Profilo utente
│   │
│   ├── services/            # Servizi e logica business
│   │   ├── battery.ts          # Monitoraggio batteria
│   │   ├── sonoff.ts           # API eWeLink/Sonoff
│   │   ├── convex.ts           # Backend Convex
│   │   ├── notifications.ts    # Notifiche push
│   │   ├── backgroundTask.ts   # Task in background
│   │   └── tokenCache.ts       # Cache token Clerk
│   │
│   ├── theme/               # Stili e tema
│   │   └── theme.ts         # Colori, spaziature, ecc.
│   │
│   └── types/               # TypeScript types
│       └── index.ts         # Definizioni tipi
│
├── assets/                  # Risorse (immagini, icone)
├── .env                     # Variabili d'ambiente (SECRET!)
├── .env.example             # Esempio variabili d'ambiente
├── app.json                 # Configurazione Expo
├── app.config.ts            # Configurazione dinamica Expo
├── package.json             # Dipendenze npm
└── tsconfig.json            # Configurazione TypeScript
```

## 📦 Tecnologie Utilizzate

### Frontend (App Mobile)
- **React Native**: Framework per app native con JavaScript/TypeScript
- **Expo**: Piattaforma che semplifica lo sviluppo React Native
- **TypeScript**: JavaScript tipizzato per meno errori
- **React Navigation**: Navigazione tra schermate

### Autenticazione
- **Clerk**: Sistema di autenticazione completo (email, OAuth, ecc.)
- **Expo Secure Store**: Storage sicuro per token e credenziali

### Backend & Database
- **Convex**: Backend serverless con database in tempo reale
- **Resend**: Servizio per invio email transazionali

### Dispositivi IoT
- **eWeLink API**: Controllo dispositivi Sonoff (prese intelligenti)

### Notifiche & Background
- **Expo Notifications**: Sistema notifiche push
- **Expo Background Fetch**: Task periodici in background
- **Expo Task Manager**: Gestione task schedulati

## 🚀 Setup e Installazione

### Prerequisiti
- Node.js 18+ installato
- npm o yarn
- Account Clerk (https://clerk.com)
- Account Convex (https://convex.dev)
- (Opzionale) Account Resend per email
- (Opzionale) Dispositivi Sonoff/eWeLink

### 1. Clona il Repository
```bash
git clone <repository-url>
cd smart-charger-android
```

### 2. Installa Dipendenze
```bash
npm install
```

### 3. Configura Variabili d'Ambiente
Copia `.env.example` in `.env` e compila i valori:

```bash
cp .env.example .env
```

Modifica `.env` con i tuoi valori:
```env
# Clerk - Ottieni da https://dashboard.clerk.com/
EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_your_key_here

# Convex - Ottieni da https://dashboard.convex.dev/
EXPO_PUBLIC_CONVEX_URL=https://your-deployment.convex.site

# eWeLink (opzionale se non hai dispositivi Sonoff)
APP_ID=your_ewelink_app_id
APP_SECRET=your_ewelink_app_secret
EWELINK_REGION=eu

# Resend (opzionale per email)
RESEND_API_KEY=re_your_key_here
```

### 4. Avvia l'App in Sviluppo
```bash
npm start
```

Questo aprirà Expo Dev Tools. Puoi:
- Premere `a` per aprire su emulatore Android
- Scansionare QR code con app Expo Go su telefono fisico

## 📱 Build APK per Android

### Opzione 1: Build Locale (richiede Android Studio)
```bash
# Build development
npx expo run:android

# Build release
npx expo build:android -t apk
```

### Opzione 2: Build con EAS (Consigliato)
```bash
# Installa EAS CLI
npm install -g eas-cli

# Login
eas login

# Configura progetto
eas build:configure

# Build APK
eas build --platform android --profile production

# Build preview APK (più veloce)
eas build --platform android --profile preview
```

L'APK verrà generato e potrai scaricarlo da Expo.

## 📚 Guida per Principianti

### Come Funziona l'App?

1. **Login**: Ti registri con email e password tramite Clerk
2. **Connessione eWeLink**: (Opzionale) Colleghi il tuo account eWeLink per controllare dispositivi Sonoff
3. **Monitoraggio**: L'app inizia a monitorare la batteria del tuo telefono
4. **Automazione**: Quando la batteria raggiunge soglie configurate (es. 20% o 80%), l'app:
   - Ti invia una notifica
   - (Se hai dispositivi Sonoff) Può accendere/spegnere il caricatore automaticamente
5. **Background**: L'app continua a funzionare anche quando è chiusa

### File Importanti da Conoscere

#### `App.tsx` - Entry Point
Il file principale che avvia l'app. Configura Clerk e il sistema di navigazione.

#### `src/services/battery.ts` - Servizio Batteria
Gestisce tutto il monitoraggio della batteria:
- Legge livello batteria
- Controlla se è in carica
- Ascolta cambiamenti in tempo reale

#### `src/services/notifications.ts` - Notifiche
Gestisce le notifiche push:
- Richiede permessi
- Invia notifiche locali
- Configura canali notifiche Android

#### `src/services/backgroundTask.ts` - Background Task
Esegue controlli periodici anche quando app è chiusa:
- Controlla batteria ogni 30 minuti
- Invia notifiche se necessario
- Può controllare dispositivi Sonoff

#### `src/services/convex.ts` - Backend
Comunica con il backend Convex:
- Salva/recupera dati utente
- Gestisce credenziali eWeLink
- Invia email tramite Resend

#### `src/services/sonoff.ts` - Dispositivi Sonoff
Controlla dispositivi Sonoff/eWeLink:
- Login con credenziali eWeLink
- Lista dispositivi
- Accende/spegne dispositivi

### Flusso Autenticazione

1. Utente inserisce email e password su `SignUpScreen`
2. Clerk crea account e invia email di verifica
3. Utente inserisce codice di verifica
4. Clerk autentica utente
5. App salva token in `SecureStore`
6. Utente viene reindirizzato alla `DashboardScreen`

### Come Aggiungere una Nuova Funzionalità

Esempio: Aggiungere notifica quando batteria raggiunge 50%

1. **Aggiorna Background Task** (`src/services/backgroundTask.ts`):
```typescript
// Aggiungi dentro TaskManager.defineTask():
if (batteryLevel === 50) {
  await NotificationService.sendLocalNotification({
    title: '🔋 Batteria al 50%',
    body: 'La batteria è esattamente a metà!',
  });
}
```

2. **Testa**: Riavvia app e attendi che background task giri

## 🐛 Troubleshooting

### "Configurazione Mancante" all'avvio
- Controlla che `.env` esista e contenga `EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY`
- Riavvia Metro bundler con `npm start --reset-cache`

### Notifiche non funzionano
- Verifica di aver concesso permessi notifiche nelle impostazioni Android
- Controlla che `expo-notifications` sia installato
- Le notifiche NON funzionano su emulatore, serve dispositivo fisico

### Background task non gira
- Android ha limitazioni severe sui background task
- Disabilita "Ottimizzazione batteria" per l'app nelle impostazioni
- Verifica che l'app abbia permesso "WAKE_LOCK"

### Build fallisce
- Pulisci cache: `expo start -c`
- Reinstalla dipendenze: `rm -rf node_modules && npm install`
- Verifica che tutte le dipendenze siano compatibili

## 📧 Supporto

Per problemi o domande:
1. Controlla questa documentazione
2. Leggi i commenti nel codice (sono dettagliati!)
3. Controlla console per errori con `npx expo start`

## 📄 Licenza

MIT License - Vedi file LICENSE
