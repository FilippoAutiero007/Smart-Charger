# 📋 Report Strategico & Roadmap dei Miglioramenti: VoltGuard Pro (Smart Charger)

> **Documento di pianificazione tecnica e architetturale**  
> Progetto: `Smart Charger / VoltGuard Pro`  
> Data di redazione: Novembre 2026  
> Versione baseline: v2.9  

---

## 🎯 Executive Summary
VoltGuard Pro ha raggiunto una solida maturità tecnica con il monitoraggio sticky in foreground, la suite telemetrica stile AccuBattery, il controllo cloud Sonoff/eWeLink e l'autenticazione dinamica.  
Questo report raccoglie e formalizza i **miglioramenti prioritari** per le prossime release, suddivisi per impatto utente, complessità tecnica e valore strategico.

---

## 📌 Indice dei Moduli di Miglioramento

1. [Automazione Segnalazioni & Bug Report su GitHub (Zero Login)](#1-automazione-segnalazioni--bug-report-su-github-zero-login)
2. [Controllo Locale LAN Sonoff (Zero-Cloud & Modalità Offline)](#2-controllo-locale-lan-sonoff-zero-cloud--modalità-offline)
3. [Protezione Termica Intelligente & Ricarica Notturna Adattiva](#3-protezione-termica-intelligente--ricarica-notturna-adattiva)
4. [Widget Schermata Home & Quick Settings Tile](#4-widget-schermata-home--quick-settings-tile)
5. [Supporto Multi-Presa con Rilevamento Intelligente Wi-Fi](#5-supporto-multi-presa-con-rilevamento-intelligente-wi-fi)
6. [Design System Material You & Raffinatezza UI/UX](#6-design-system-material-you--raffinatezza-ui-ux)
7. [Allarmi Vocali (TTS) e Notifiche Broadcast Telegram](#7-allarmi-vocali-tts-e-notifiche-broadcast-telegram)

---

## 1. Automazione Segnalazioni & Bug Report su GitHub (Zero Login)
*Priorità: ALTA | Complessità: BASSA | Impatto: ALTO*

### 💡 Obiettivo
Permettere l'apertura automatica di una Issue formattata su GitHub direttamente dall'app quando l'utente tocca *"Invia log"* o quando si verifica un crash imprevisto, senza che l'utente debba registrarsi o autenticarsi su GitHub.

### 🛠️ Architettura di Implementazione
1. **Endpoint Relay su Render (`auth-server`)**:
   - Creazione di `POST /api/report-issue`.
   - Il server custodisce un `GITHUB_TOKEN` (Fine-Grained Personal Access Token con soli permessi di scrittura Issues su `FilippoAutiero007/Smart-Charger`).
   - Rate limiting: massimo 1 segnalazione ogni 5 minuti per dispositivo/IP per evitare spam.
2. **App Android**:
   - Il pulsante *"Invia log"* raccoglie la diagnostica (modello, release Android, livello batteria, log sintetici mascherati).
   - Esegue la POST asincrona a Render.
   - Mostra un feedback visivo immediato: *"Invio in corso..."* → *"Segnalazione registrata (#IssueID)"*.
3. **Cattura Crash Automatica**:
   - Integrazione di `Thread.setDefaultUncaughtExceptionHandler` per intercettare crash non gestiti e generare una Issue con stacktrace prima della chiusura dell'app.

---

## 2. Controllo Locale LAN Sonoff (Zero-Cloud & Modalità Offline)
*Priorità: ALTA | Complessità: MEDIA | Impatto: CRITICO*

### 💡 Obiettivo
Permettere all'app di accendere e spegnere la presa Sonoff direttamente sulla rete Wi-Fi locale quando il telefono e la presa sono sotto lo stesso router, eliminando la dipendenza dai server cloud eWeLink.

### 🛠️ Architettura di Implementazione
1. **Protocollo DIY / LAN Sonoff**:
   - I dispositivi Sonoff supportano il controllo HTTP locale su porta `8081` tramite payload JSON:
     `POST http://<IP_SONOFF>:8081/zeroconf/switch` con body `{"switch": "on"}`.
2. **Scansione mDNS / Zeroconf**:
   - Rilevamento automatico dell'IP della presa sulla sottorete locale usando `_ewelink._tcp.local`.
3. **Fallback Ibrido Intelligente**:
   - Tentativo 1: Invio comando LAN locale (tempo di risposta < 50ms, zero consumo dati, funziona anche senza connessione internet).
   - Tentativo 2: Se la LAN fallisce (es. utente fuori casa su rete 4G/5G), fallback trasparente su cloud eWeLink.

---

## 3. Protezione Termica Intelligente & Ricarica Notturna Adattiva
*Priorità: ALTA | Complessità: MEDIA | Impatto: ALTO*

### 💡 Obiettivo
Preservare la salute chimica della batteria nel lungo periodo monitorando temperatura e orari di utilizzo.

### 🛠️ Funzionalità da Sviluppare
1. **Battery Temperature Guard**:
   - Se la batteria supera i **42°C** durante la carica (frequente con caricabatterie veloci da 67W/120W), l'app comanda temporaneamente lo spegnimento della presa per far raffreddare la cella.
   - Non appena la temperatura scende sotto i **38°C**, la ricarica riprende automaticamente fino al raggiungimento della soglia OFF stabilita.
2. **Ricarica Notturna Intelligente (Clock-Synced Charging)**:
   - Rileva la sveglia impostata su Android (`AlarmClock.ACTION_SHOW_ALARMS`).
   - Mantiene la carica bloccata all'**80%** per tutta la notte, riavviando la carica fino al 100% solo 30 minuti prima del risveglio dell'utente.

---

## 4. Widget Schermata Home & Quick Settings Tile
*Priorità: MEDIA | Complessità: BASSA | Impatto: ALTO*

### 💡 Obiettivo
Consentire all'utente di visualizzare lo stato della batteria e controllare la presa con un singolo tocco senza aprire l'applicazione.

### 🛠️ Funzionalità da Sviluppare
1. **Android App Widget (Jetpack Glance)**:
   - Widget 2x1 o 2x2 per la schermata home con:
     - Percentuale attuale e stato ricarica in tempo reale.
     - Indicatore di stato Sonoff (Icona verde ON / grigia OFF).
     - Pulsante rapido "Attiva / Stacca".
2. **Quick Settings Tile (Tendina Notifiche)**:
   - Tile nei comandi rapidi di sistema per accendere/spegnere la presa Sonoff istantaneamente da qualsiasi schermata.

---

## 5. Supporto Multi-Presa con Rilevamento Intelligente Wi-Fi
*Priorità: MEDIA | Complessità: MEDIA | Impatto: MEDIO-ALTO*

### 💡 Obiettivo
Supportare utenti che possiedono più prese Sonoff (es. una presa in camera da letto, una sulla scrivania in ufficio, una in salotto).

### 🛠️ Funzionalità da Sviluppare
1. **Archiviazione Multi-Device in Room**:
   - Creazione di una tabella `SonoffDeviceEntity` per memorizzare più dispositivi associati con nome personalizzato.
2. **Riconoscimento Automatico Basato su Wi-Fi (SSID)**:
   - Associazione regola: *Se connesso al Wi-Fi "Casa_5G" usa Presa Camera; se connesso a "Ufficio_WiFi" usa Presa Ufficio*.
   - Selezione dinamica automatica del target senza intervento manuale.

---

## 6. Design System Material You & Raffinatezza UI/UX
*Priorità: MEDIA | Complessità: BASSA-MEDIA | Impatto: MEDIO*

### 💡 Obiettivo
Allineare l'estetica dell'applicazione ai massimi standard di Material Design 3 e human interface.

### 🛠️ Dettagli Operativi
1. **Dynamic Theming (Monet Engine)**:
   - Su Android 12+ estrazione automatica dei token colore dallo sfondo dell'utente per un'integrazione perfetta con il sistema operativo.
2. **Haptic Feedback Coerente**:
   - Vibrazione aptica sottile (`HapticFeedbackType.LongPress` / `TextHandleMove`) quando si modificano le soglie e quando un comando Sonoff viene confermato.
3. **Grafici Interattivi Avanzati**:
   - Possibilità di fare pinch-to-zoom e scorrimento temporale sui grafici storici di carica/scarica, con evidenziazione dei picchi di assorbimento (W/mA).

---

## 7. Allarmi Vocali (TTS) e Notifiche Broadcast Telegram
*Priorità: BASSA-MEDIA | Complessità: BASSA | Impatto: MEDIO*

### 💡 Obiettivo
Informare l'utente del completamento della carica anche se si trova in un'altra stanza o sta usando un altro dispositivo (PC, tablet).

### 🛠️ Funzionalità da Sviluppare
1. **Sintesi Vocale TTS Locale**:
   - Annuncio vocale tramite `TextToSpeech` nativo: *"Batteria carica all'80%. Alimentazione interrotta."*
2. **Bot Telegram per Notifiche Remote**:
   - Configurazione opzionale di un token bot e chat ID Telegram per ricevere un messaggio istantaneo ovunque ci si trovi: *"VoltGuard: Telefono carico all'85%, presa spenta alle 14:32"*.

---

## 📊 Tabella Riepilogativa delle Milestone Future

| # | Funzionalità | Release Target | Beneficio Principale |
|---|---|:---:|---|
| **M1** | GitHub Issue Reporter Automatico (Zero Login) | `v3.0` | Assistenza rapida e monitoraggio bug in produzione senza attrito. |
| **M2** | Sonoff Local LAN Control (Zero-Cloud) | `v3.1` | Latenza zero e funzionamento anche senza connessione internet. |
| **M3** | Temperature Guard & Ricarica Notturna | `v3.2` | Massima longevità della batteria contro il degrado termico. |
| **M4** | Widget Home Screen & Quick Settings Tile | `v3.3` | Controllo e monitoraggio istantaneo senza aprire l'app. |
| **M5** | Multi-Presa con Wi-Fi Trigger | `v3.4` | Flessibilità totale per chi ha più postazioni di ricarica. |
| **M6** | Material You Dynamic Color & Feedback Aptico | `v3.5` | Look & feel moderno perfettamente integrato in Android 14/15. |
