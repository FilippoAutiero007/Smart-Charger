# 📦 Guida Build APK - Smart Charger Android

## 🎯 Obiettivo

Questa guida ti mostra come creare un file APK installabile per Android che gli utenti possono scaricare e installare direttamente sul loro telefono senza passare dal Play Store.

## 📋 Prerequisiti

Prima di iniziare, assicurati di avere:

1. ✅ Node.js 18+ installato
2. ✅ npm o yarn funzionante
3. ✅ Account Expo (gratuito) - Registrati su https://expo.dev
4. ✅ File `.env` configurato con tutte le chiavi necessarie
5. ✅ Connessione internet stabile

## 🚀 Metodo 1: Build con EAS (Consigliato - Cloud Build)

EAS Build è il metodo più semplice perché la build avviene su server di Expo, non serve installare Android Studio.

### Step 1: Installa EAS CLI

```bash
npm install -g eas-cli
```

### Step 2: Login su Expo

```bash
eas login
```

Inserisci email e password del tuo account Expo.

### Step 3: Configura il Progetto

```bash
cd smart-charger-android
eas build:configure
```

Questo comando:
- Crea/aggiorna `eas.json` con configurazioni build
- Collega il progetto al tuo account Expo

### Step 4: Build APK Production

```bash
eas build --platform android --profile production
```

Opzioni:
- `--profile production`: Build ottimizzata per distribuzione finale
- `--profile preview`: Build più veloce per test
- `--profile development`: Build con debugging tools

### Step 5: Attendi Completamento

Il processo richiede **10-20 minuti**. Puoi:
- Aspettare nel terminale
- Chiudere terminale e controllare su https://expo.dev/accounts/[your-account]/projects/smart-charger-android/builds

### Step 6: Scarica APK

Una volta completata la build:

1. Riceverai un link nel terminale
2. Oppure vai su https://expo.dev
3. Naviga in Projects → smart-charger-android → Builds
4. Clicca su "Download" per scaricare l'APK

### Step 7: Installa su Android

**Metodo A - Via USB:**
```bash
adb install app-release.apk
```

**Metodo B - Via Download Diretto:**
1. Carica l'APK su Google Drive / Dropbox / Server web
2. Apri link sul telefono Android
3. Abilita "Installa app da fonti sconosciute" se richiesto
4. Tocca il file APK per installare

## 🏠 Metodo 2: Build Locale (Richiede Android Studio)

Se vuoi buildare localmente senza usare i server Expo.

### Step 1: Installa Android Studio

1. Scarica da https://developer.android.com/studio
2. Installa Android SDK
3. Configura variabili d'ambiente:
   - `ANDROID_HOME=/path/to/Android/sdk`
   - Aggiungi `$ANDROID_HOME/platform-tools` al PATH

### Step 2: Build Locale

```bash
cd smart-charger-android
npx expo run:android --variant release
```

Questo:
- Compila l'app localmente
- Genera APK in `android/app/build/outputs/apk/release/`

### Step 3: Trova l'APK

```bash
find ./android/app/build/outputs/apk/ -name "*.apk"
```

L'APK sarà qualcosa come:
```
android/app/build/outputs/apk/release/app-release.apk
```

## 🔧 Build Configurazioni Avanzate

### Build con Profilo Custom

Puoi modificare `eas.json` per aggiungere profili:

```json
{
  "build": {
    "testing": {
      "distribution": "internal",
      "android": {
        "buildType": "apk",
        "gradleCommand": ":app:assembleRelease"
      },
      "env": {
        "NODE_ENV": "staging"
      }
    }
  }
}
```

Poi builda con:
```bash
eas build --platform android --profile testing
```

### Build Firmata (Per Play Store)

Se vuoi pubblicare su Google Play Store:

```bash
eas build --platform android --profile production
```

EAS genera automaticamente keystore e firma l'APK.

### Build Multi-Piattaforma (Android + iOS)

```bash
eas build --platform all --profile production
```

## 📱 Distribuzione APK

### Opzione 1: Link Diretto

1. **Carica su Cloud:**
   ```bash
   # Upload su server
   scp app-release.apk user@server:/var/www/downloads/
   ```

2. **Condividi Link:**
   ```
   https://tuosito.com/downloads/smart-charger-v1.0.0.apk
   ```

### Opzione 2: QR Code

1. Usa servizio come https://www.qr-code-generator.com/
2. Inserisci URL dell'APK
3. Genera QR code
4. Utenti scansionano per scaricare

### Opzione 3: Firebase App Distribution

1. **Installa Firebase CLI:**
   ```bash
   npm install -g firebase-tools
   firebase login
   ```

2. **Distribuisci:**
   ```bash
   firebase appdistribution:distribute app-release.apk \
     --app 1:1234567890:android:abcdef \
     --groups testers \
     --release-notes "Prima versione Smart Charger"
   ```

### Opzione 4: TestFlight-like (Expo Updates)

1. **Setup OTA Updates:**
   ```bash
   eas update:configure
   ```

2. **Pubblica Update:**
   ```bash
   eas update --branch production --message "Bugfix batteria"
   ```

## 🐛 Troubleshooting Build

### Errore: "EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY is missing"

**Soluzione:**
```bash
# Verifica che .env esista e contenga:
cat .env | grep CLERK

# Se manca, aggiungi:
echo "EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_..." >> .env

# Rebuilda con --clear-cache
eas build --platform android --profile production --clear-cache
```

### Errore: "Gradle build failed"

**Soluzioni:**
1. Pulisci cache Gradle:
   ```bash
   cd android
   ./gradlew clean
   ```

2. Verifica versioni SDK in `android/build.gradle`:
   ```gradle
   compileSdkVersion 34
   targetSdkVersion 34
   minSdkVersion 23
   ```

3. Rebuilda

### Errore: "Out of memory"

**Soluzione:**
Aumenta memoria Gradle in `android/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=1024m
```

### Build Lenta

**Ottimizzazioni:**

1. **Usa Hermes Engine** (già abilitato in app.json):
   ```json
   {
     "expo": {
       "android": {
         "enableProguardInReleaseBuilds": true,
         "enableShrinkResourcesInReleaseBuilds": true
       }
     }
   }
   ```

2. **Parallelize Gradle:**
   ```properties
   # android/gradle.properties
   org.gradle.parallel=true
   org.gradle.daemon=true
   org.gradle.configureondemand=true
   ```

### APK Troppo Grande

**Riduci dimensioni:**

1. **Abilita ProGuard** (minificazione codice)
2. **Rimuovi risorse inutilizzate**
3. **Genera APK per architettura specifica:**
   ```bash
   eas build --platform android --profile production \
     --build-config=android.abiFilters=armeabi-v7a,arm64-v8a
   ```

## 📊 Checklist Pre-Build

Prima di fare la build finale, verifica:

- [ ] File `.env` contiene tutte le chiavi necessarie
- [ ] `app.json` ha `version` aggiornata (es. "1.0.1")
- [ ] `package.json` ha `version` corretta
- [ ] Icon e splash screen sono presenti in `assets/`
- [ ] Permessi Android sono corretti in `app.json`
- [ ] App funziona su emulatore/dispositivo fisico
- [ ] TypeScript compila senza errori: `npx tsc --noEmit`
- [ ] Nessun TODO o console.log critici nel codice

## 🎉 Post-Build

Dopo aver generato l'APK:

1. **Testa su Dispositivi Reali:**
   - Installa su almeno 2-3 dispositivi Android diversi
   - Prova tutte le funzionalità principali
   - Verifica notifiche, background tasks, ecc.

2. **Versioning:**
   - Rinomina APK con versione: `smart-charger-v1.0.0.apk`
   - Crea tag Git: `git tag v1.0.0`
   - Pusha tag: `git push --tags`

3. **Documentazione Release:**
   - Scrivi changelog con novità
   - Elenca bug fix
   - Aggiungi note installazione

4. **Distribuzione:**
   - Carica su server/cloud
   - Invia link a beta testers
   - Pubblica su Play Store (opzionale)

## 📞 Supporto

- **EAS Build Docs:** https://docs.expo.dev/build/introduction/
- **Expo Forums:** https://forums.expo.dev/
- **Stack Overflow:** Tag `expo`, `eas-build`

---

**Buona Build! 🚀**
