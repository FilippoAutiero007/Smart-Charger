# 🔋 Smart Charger - Guida Completa

## 📌 Che cos'è Smart Charger?
**Smart Charger** è un ecosistema intelligente progettato per estendere la vita utile delle batterie (in particolare dei laptop) automatizzando il processo di ricarica. 

Il sistema monitora costantemente il livello di carica di un dispositivo e comanda una presa intelligente (**Sonoff**) per attivare o disattivare l'alimentazione basandosi su soglie di sicurezza personalizzabili (esempio tipico: ricarica tra il 20% e l'80%).

---

## 🏗️ Architettura del Sistema
Il progetto si compone di tre pilastri fondamentali che comunicano tra loro:

### 1. 📱 Applicazione Mobile (React Native + Expo)
L'interfaccia utente per il controllo totale in mobilità.
- **Dashboard**: Visualizzazione in tempo reale dello stato dei dispositivi e del livello della batteria.
- **Controllo Dispositivi**: Toggle manuali ON/OFF e configurazione delle automazioni.
- **Profilo**: Gestione account, cambio username, foto profilo e reset password.
- **Multi-Auth**: Supporto per Login Google, Email/Password e integrazione dedicata eWeLink.

### 2. 🌩️ Backend (Convex)
Il "cervello" centrale che gestisce i dati e la logica di business.
- **Database**: Memorizzazione configurazioni utente, token di sessione e storico dispositivi.
- **Actions & Mutations**: Gestione sicura dell'autenticazione, invio email via Resend e storage per le immagini.
- **Single Source of Truth**: Funge da ponte tra l'App Mobile e il Desktop Agent tramite API HTTP/Websockets.

### 3. 🐍 Desktop Agent (Python)
Il componente locale installato sul computer da monitorare.
- **Battery Monitor**: Legge il livello di carica del laptop direttamente via hardware.
- **Remote Control**: Invia aggiornamenti a Convex e riceve comandi per accendere/spegnere la presa Sonoff.
- **Efficienza**: Gira in background con un basso consumo di risorse.

---

## 🚀 Funzionalità Principali

### 🔋 Ottimizzazione Batteria
Evita che la batteria rimanga al 100% per lunghi periodi (causando stress termico) o che si scarichi completamente (causando degrado chimico). 
- **Soglia Minima**: Quando la batteria scende sotto il limite (es. 20%), la presa si accende.
- **Soglia Massima**: Quando la batteria raggiunge il limite (es. 80%), la presa si spegne.

### 🔌 Integrazione eWeLink (Sonoff)
Sincronizzazione diretta con l'ecosistema Sonoff. È possibile collegare il proprio account eWeLink all'app per gestire qualsiasi presa intelligente compatibile.

### 🔒 Sicurezza e Privacy
- Autenticazione sicura tramite Convex Auth.
- Criptazione dei token di accesso.
- Invio di codici temporanei per il reset della password via email.

---

## 🛠️ Come Iniziare

### Desktop (Monitoraggio)
1. Assicurarsi di aver configurato il file `.env` con le credenziali eWeLink.
2. Avviare l'agente Python: `python laptop-agent/main.py`.

### Backend (Sviluppo)
1. Entrare nella cartella: `cd convex-backend`.
2. Avviare il server di sviluppo: `npx convex dev`.

### Mobile (Controllo)
1. Entrare nella cartella: `cd mobile`.
2. Avviare Expo: `npx expo start`.

---

## 📈 Obiettivi Futuri
- **Statistiche Avanzate**: Analisi del degrado della batteria nel tempo.
- **Schedule**: Pianificazione oraria della ricarica (es. solo durante le ore notturne con tariffe agevolate).
- **Multi-Device**: Supporto per monitorare più laptop contemporaneamente da una singola app.

---
*Progetto sviluppato per garantire efficienza energetica e longevità hardware.*
