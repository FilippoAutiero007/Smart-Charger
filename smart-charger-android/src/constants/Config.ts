/**
 * ============================================
 * CONFIGURAZIONE APP
 * ============================================
 * Questo file contiene tutte le configurazioni e costanti dell'app.
 * 
 * Per principianti:
 * - Constants.expoConfig legge i valori da app.json e .env
 * - process.env legge direttamente dal file .env
 * - Usiamo || per fornire valori di fallback se la variabile non esiste
 * 
 * IMPORTANTE:
 * - Le variabili con EXPO_PUBLIC_ sono accessibili nel client
 * - Le altre variabili sono solo per il server (Convex backend)
 */

import Constants from 'expo-constants';

/**
 * CLERK AUTHENTICATION
 * Chiave pubblica per autenticazione con Clerk
 * Ottenibile da: https://dashboard.clerk.com/
 */
export const CLERK_PUBLISHABLE_KEY = 
  Constants.expoConfig?.extra?.clerkPublishableKey ||
  process.env.EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY;

if (!CLERK_PUBLISHABLE_KEY) {
  console.warn('⚠️ CLERK_PUBLISHABLE_KEY mancante! Controlla il file .env');
}

/**
 * CONVEX BACKEND
 * URL del backend Convex per gestione dati
 */
export const CONVEX_URL =
  Constants.expoConfig?.extra?.convexUrl ||
  process.env.EXPO_PUBLIC_CONVEX_URL ||
  'https://striped-shrimp-908.convex.site';

if (!CONVEX_URL.includes('convex')) {
  console.warn('⚠️ CONVEX_URL potrebbe non essere valido:', CONVEX_URL);
}

/**
 * EWELINK/SONOFF API
 * Credenziali per accesso API eWeLink
 */
export const EWELINK_CONFIG = {
  appId: process.env.APP_ID || 'lYPkZywzOtbxsMRNWJvhgCyXBDptIjOo',
  appSecret: process.env.APP_SECRET || 'mdPR25XfesDAiaB3pQbxWEklWT1EeK7v',
  region: process.env.EWELINK_REGION || 'eu',
};

/**
 * API ENDPOINTS
 * URL dei vari servizi usati dall'app
 */
export const API_ENDPOINTS = {
  convex: CONVEX_URL,
  ewelink: `https://${EWELINK_CONFIG.region}-apia.coolkit.cc`,
};

/**
 * IMPOSTAZIONI BATTERIA DEFAULT
 * Valori predefiniti per il monitoraggio batteria
 */
export const BATTERY_DEFAULTS = {
  minBattery: 20,    // Soglia minima (sotto questa soglia avvisa)
  maxBattery: 80,    // Soglia massima (sopra questa soglia avvisa)
  checkInterval: 300, // Intervallo controllo in secondi (5 minuti)
  emailNotifications: true, // Notifiche email abilitate
};

/**
 * IMPOSTAZIONI BACKGROUND TASK
 * Configurazione per monitoraggio in background
 */
export const BACKGROUND_TASK_CONFIG = {
  taskName: 'battery-monitor-task',
  minimumInterval: 60 * 30, // 30 minuti (minimo consigliato per Android)
  stopOnTerminate: false,
  startOnBoot: true,
};

/**
 * IMPOSTAZIONI NOTIFICHE
 * Configurazione notifiche push
 */
export const NOTIFICATION_CONFIG = {
  channelId: 'smart-charger-default',
  channelName: 'Smart Charger',
  importance: 'max' as const,
  sound: 'default',
  vibrationPattern: [0, 250, 250, 250],
  lightColor: '#667eea',
};

/**
 * AMBIENTE CORRENTE
 * development, staging, production
 */
export const NODE_ENV = process.env.NODE_ENV || 'development';
export const IS_DEV = NODE_ENV === 'development';
export const IS_PROD = NODE_ENV === 'production';

/**
 * INFO APP
 * Informazioni sull'app corrente
 */
export const APP_INFO = {
  name: Constants.expoConfig?.name || 'Smart Charger',
  version: Constants.expoConfig?.version || '1.0.0',
  slug: Constants.expoConfig?.slug || 'smart-charger-android',
};

/**
 * VALIDAZIONI
 * Funzioni helper per validare configurazioni
 */
export const ConfigValidator = {
  /**
   * Verifica se tutte le configurazioni obbligatorie sono presenti
   * 
   * @returns true se tutto è configurato correttamente
   */
  validateConfig: (): { valid: boolean; errors: string[] } => {
    const errors: string[] = [];

    if (!CLERK_PUBLISHABLE_KEY) {
      errors.push('CLERK_PUBLISHABLE_KEY mancante');
    }

    if (!CONVEX_URL) {
      errors.push('CONVEX_URL mancante');
    }

    if (!EWELINK_CONFIG.appId) {
      errors.push('EWELINK APP_ID mancante');
    }

    if (!EWELINK_CONFIG.appSecret) {
      errors.push('EWELINK APP_SECRET mancante');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  },

  /**
   * Stampa configurazione corrente (nascondendo valori sensibili)
   */
  logConfig: () => {
    console.log('📋 Configurazione App:');
    console.log('  - Nome:', APP_INFO.name);
    console.log('  - Versione:', APP_INFO.version);
    console.log('  - Ambiente:', NODE_ENV);
    console.log('  - Clerk:', CLERK_PUBLISHABLE_KEY ? '✅ Configurato' : '❌ Mancante');
    console.log('  - Convex:', CONVEX_URL ? '✅ Configurato' : '❌ Mancante');
    console.log('  - eWeLink:', EWELINK_CONFIG.appId ? '✅ Configurato' : '❌ Mancante');
  },
};

/**
 * STORAGE KEYS
 * Chiavi usate per salvare dati in SecureStore
 */
export const STORAGE_KEYS = {
  CLERK_TOKEN: 'clerk_token',
  ACCESS_TOKEN: 'access_token',
  REFRESH_TOKEN: 'refresh_token',
  USER_EMAIL: 'user_email',
  EWELINK_REGION: 'ewelink_region',
  MONITOR_CONFIG: 'monitor_config',
  SELECTED_DEVICE: 'selected_device_id',
  PUSH_TOKEN: 'expo_push_token',
  LAST_CHECK: 'last_monitor_check',
};

export default {
  CLERK_PUBLISHABLE_KEY,
  CONVEX_URL,
  EWELINK_CONFIG,
  API_ENDPOINTS,
  BATTERY_DEFAULTS,
  BACKGROUND_TASK_CONFIG,
  NOTIFICATION_CONFIG,
  APP_INFO,
  IS_DEV,
  IS_PROD,
  STORAGE_KEYS,
  ConfigValidator,
};
