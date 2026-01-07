/**
 * ============================================
 * NOTIFICATION SERVICE
 * ============================================
 * Questo file gestisce le notifiche push nell'app Android.
 * 
 * Cosa sono le notifiche push?
 * - Messaggi che appaiono nella barra notifiche del telefono
 * - Possono arrivare anche quando l'app è chiusa
 * - Utili per avvisare l'utente di eventi importanti
 * 
 * In questa app usiamo le notifiche per:
 * - Avvisare quando la batteria è completamente carica
 * - Avvisare quando la batteria è troppo scarica
 * - Notificare problemi con i dispositivi Sonoff
 * - Notificare scadenza token eWeLink
 */

import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import { Platform } from 'react-native';
import Constants from 'expo-constants';

/**
 * CONFIGURAZIONE NOTIFICHE
 * Imposta come devono apparire le notifiche quando l'app è aperta
 */
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,      // Mostra alert visivo
    shouldPlaySound: true,       // Riproduce suono
    shouldSetBadge: true,        // Mostra badge con numero su icona app
  }),
});

/**
 * INTERFACCE
 */

// Dati della notifica
export interface NotificationData {
  title: string;
  body: string;
  data?: any;
  sound?: boolean;
  vibrate?: boolean;
}

// Risultato richiesta permessi
export interface PermissionResult {
  granted: boolean;
  canAskAgain: boolean;
  status: string;
}

/**
 * SERVIZIO NOTIFICHE
 */
export const NotificationService = {
  /**
   * Richiede i permessi per inviare notifiche
   * Su Android 13+ è obbligatorio chiedere permesso esplicito
   * 
   * @returns Risultato richiesta permesso
   */
  requestPermissions: async (): Promise<PermissionResult> => {
    // Verifica se siamo su un dispositivo fisico Android
    if (!Device.isDevice) {
      console.warn('Le notifiche funzionano solo su dispositivi fisici');
      return {
        granted: false,
        canAskAgain: false,
        status: 'denied-emulator',
      };
    }

    // Android richiede permessi espliciti da versione 13 (API 33)
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;

      // Se non abbiamo ancora il permesso, lo chiediamo
      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }

      return {
        granted: finalStatus === 'granted',
        canAskAgain: finalStatus !== 'denied',
        status: finalStatus,
      };
    }

    // Per Android < 13, i permessi sono concessi automaticamente
    return {
      granted: true,
      canAskAgain: true,
      status: 'granted-auto',
    };
  },

  /**
   * Registra il dispositivo per ricevere notifiche push remote (da server)
   * Questa funzione restituisce un "Expo Push Token" univoco per questo dispositivo
   * 
   * @returns Token push o null se fallito
   */
  registerForPushNotifications: async (): Promise<string | null> => {
    try {
      if (!Device.isDevice) {
        console.warn('Push token funziona solo su dispositivi fisici');
        return null;
      }

      // Richiedi permessi
      const permResult = await NotificationService.requestPermissions();
      if (!permResult.granted) {
        console.warn('Permesso notifiche negato');
        return null;
      }

      // Ottieni il token Expo Push
      const projectId = Constants.expoConfig?.extra?.eas?.projectId;
      const tokenData = await Notifications.getExpoPushTokenAsync({
        projectId,
      });

      const token = tokenData.data;
      console.log('📱 Expo Push Token:', token);

      // Su Android, configura il canale di notifica
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('default', {
          name: 'Smart Charger',
          importance: Notifications.AndroidImportance.MAX,
          vibrationPattern: [0, 250, 250, 250],
          lightColor: '#667eea',
          sound: 'default',
          enableVibrate: true,
          showBadge: true,
        });
      }

      return token;
    } catch (error) {
      console.error('Errore registrazione push token:', error);
      return null;
    }
  },

  /**
   * Invia una notifica LOCALE (non dal server, ma dall'app stessa)
   * Utile per notifiche immediate senza bisogno di un server
   * 
   * @param notification - Dati della notifica
   * @returns ID della notifica schedulata
   */
  sendLocalNotification: async (
    notification: NotificationData
  ): Promise<string | null> => {
    try {
      const notificationId = await Notifications.scheduleNotificationAsync({
        content: {
          title: notification.title,
          body: notification.body,
          data: notification.data || {},
          sound: notification.sound !== false, // default true
          vibrate: notification.vibrate !== false ? [0, 250, 250, 250] : undefined,
        },
        trigger: null, // null = invia subito
      });

      console.log('📬 Notifica locale inviata:', notificationId);
      return notificationId;
    } catch (error) {
      console.error('Errore invio notifica locale:', error);
      return null;
    }
  },

  /**
   * Invia una notifica schedulata (ritardata nel tempo)
   * 
   * @param notification - Dati notifica
   * @param seconds - Secondi di ritardo prima dell'invio
   * @returns ID notifica schedulata
   */
  scheduleNotification: async (
    notification: NotificationData,
    seconds: number
  ): Promise<string | null> => {
    try {
      const notificationId = await Notifications.scheduleNotificationAsync({
        content: {
          title: notification.title,
          body: notification.body,
          data: notification.data || {},
          sound: notification.sound !== false,
          vibrate: notification.vibrate !== false ? [0, 250, 250, 250] : undefined,
        },
        trigger: seconds,
      });

      console.log(`⏰ Notifica schedulata tra ${seconds}s:`, notificationId);
      return notificationId;
    } catch (error) {
      console.error('Errore scheduling notifica:', error);
      return null;
    }
  },

  /**
   * Cancella una notifica schedulata
   * 
   * @param notificationId - ID della notifica da cancellare
   */
  cancelNotification: async (notificationId: string): Promise<void> => {
    try {
      await Notifications.cancelScheduledNotificationAsync(notificationId);
      console.log('🚫 Notifica cancellata:', notificationId);
    } catch (error) {
      console.error('Errore cancellazione notifica:', error);
    }
  },

  /**
   * Cancella tutte le notifiche schedulate
   */
  cancelAllNotifications: async (): Promise<void> => {
    try {
      await Notifications.cancelAllScheduledNotificationsAsync();
      console.log('🚫 Tutte le notifiche cancellate');
    } catch (error) {
      console.error('Errore cancellazione notifiche:', error);
    }
  },

  /**
   * Ottieni tutte le notifiche schedulate
   * 
   * @returns Array di notifiche schedulate
   */
  getAllScheduledNotifications: async () => {
    try {
      const notifications = await Notifications.getAllScheduledNotificationsAsync();
      return notifications;
    } catch (error) {
      console.error('Errore recupero notifiche:', error);
      return [];
    }
  },

  /**
   * Aggiungi listener per notifiche ricevute (quando app è aperta)
   * 
   * @param callback - Funzione da chiamare quando arriva una notifica
   * @returns Subscription da rimuovere con .remove()
   */
  addNotificationReceivedListener: (
    callback: (notification: Notifications.Notification) => void
  ) => {
    return Notifications.addNotificationReceivedListener(callback);
  },

  /**
   * Aggiungi listener per tap su notifica
   * 
   * @param callback - Funzione da chiamare quando utente tocca notifica
   * @returns Subscription da rimuovere con .remove()
   */
  addNotificationResponseListener: (
    callback: (response: Notifications.NotificationResponse) => void
  ) => {
    return Notifications.addNotificationResponseReceivedListener(callback);
  },

  /**
   * NOTIFICHE PREDEFINITE PER SMART CHARGER
   * Funzioni helper per casi d'uso specifici
   */

  // Notifica batteria carica al 100%
  notifyBatteryFull: async () => {
    return NotificationService.sendLocalNotification({
      title: '🔋 Batteria Carica!',
      body: 'La batteria ha raggiunto il 100%. Puoi scollegare il caricatore.',
      data: { type: 'battery_full' },
    });
  },

  // Notifica batteria scarica
  notifyBatteryLow: async (level: number) => {
    return NotificationService.sendLocalNotification({
      title: '⚠️ Batteria Scarica',
      body: `La batteria è al ${level}%. Collega il caricatore.`,
      data: { type: 'battery_low', level },
    });
  },

  // Notifica dispositivo Sonoff offline
  notifyDeviceOffline: async (deviceName: string) => {
    return NotificationService.sendLocalNotification({
      title: '🔌 Dispositivo Offline',
      body: `Il dispositivo "${deviceName}" non è raggiungibile.`,
      data: { type: 'device_offline', deviceName },
    });
  },

  // Notifica token eWeLink in scadenza
  notifyTokenExpiring: async (daysLeft: number) => {
    return NotificationService.sendLocalNotification({
      title: '⏰ Token in Scadenza',
      body: `I tuoi token eWeLink scadranno tra ${daysLeft} giorni. Rinnovali dall'app.`,
      data: { type: 'token_expiring', daysLeft },
    });
  },

  // Notifica soglia batteria raggiunta (es. 80% per ottimizzazione)
  notifyBatteryThreshold: async (level: number, threshold: number) => {
    return NotificationService.sendLocalNotification({
      title: '✅ Soglia Raggiunta',
      body: `La batteria ha raggiunto ${level}% (soglia: ${threshold}%).`,
      data: { type: 'battery_threshold', level, threshold },
    });
  },
};

export default NotificationService;
