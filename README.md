# 🔋 Smart Charger

Smart Charger è un sistema intelligente per la gestione della ricarica dei dispositivi, composto da un'app mobile, un agente per laptop e un backend centralizzato.

## 🚀 Struttura del Progetto

Il progetto è organizzato nelle seguenti cartelle:

-   **/smart-charger-android**: Applicazione mobile sviluppata con React Native ed Expo.
-   **/laptop-agent**: Script Python per il monitoraggio della batteria del laptop e il controllo dei dispositivi Sonoff.
-   **/convex-backend**: Backend serverless gestito con Convex.

## 📱 App Android

L'app mobile permette di monitorare lo stato della batteria e gestire i dispositivi di ricarica.

### Configurazione
1. Entra nella cartella `smart-charger-android`.
2. Copia `.env.example` in `.env`.
3. Inserisci la tua `EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY`.
4. Installa le dipendenze con `npm install`.
5. Avvia l'app con `npx expo start`.

## 💻 Agente Laptop

L'agente monitora la batteria del laptop e comunica con le prese intelligenti Sonoff per ottimizzare i cicli di ricarica (es. mantenendo la carica tra il 20% e l'80%).

### Configurazione
1. Entra nella cartella `laptop-agent`.
2. Installa i requisiti: `pip install -r requirements.txt`.
3. Configura le tue credenziali e-WeLink eseguendo `python setup.py`.
4. Avvia l'agente: `python main.py`.

## 🛠️ Tecnologie Utilizzate

-   **Frontend**: React Native, Expo, Clerk (Autenticazione).
-   **Backend**: Convex, Node.js.
-   **Agent**: Python, e-WeLink API.

## 📄 Licenza

Questo progetto è distribuito sotto licenza MIT. Vedi il file [LICENSE](LICENSE) per i dettagli.
