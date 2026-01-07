/**
 * ============================================
 * BACKGROUND TASK SERVICE
 * ============================================
 * Questo file gestisce le operazioni in background.
 * 
 * Cos'è un Background Task?
 * - Un'operazione che continua anche quando l'app è chiusa
 * - Su Android, può girare periodicamente anche se l'utente non usa l'app
 * - Perfetto per monitorare batteria e gestire ricarica automatica
 * 
 * Come funziona?
 * 1. Registriamo un "task" che deve girare periodicamente
 * 2. Android/iOS eseguirà il task anche quando l'app è in background
 * 3. Il task controlla batteria e agisce di conseguenza
 * 
 * IMPORTANTE per Android:
 * - I task in background hanno limitazioni severe per risparmiare batteria
 * - Il sistema può ritardare o bloccare task se la batteria è scarica
 * - È meglio combinare background task + notifiche per utente
 */

import * as BackgroundFetch from 'expo-background-fetch';
import * as TaskManager from 'expo-task-manager';
import { BatteryService } from './battery';
import { NotificationService } from './notifications';
import * as SecureStore from 'expo-secure-store';

/**
 * COSTANTI
 */

// Nome univoco del task di monitoraggio batteria
const BATTERY_MONITOR_TASK = 'battery-monitor-task';

// Intervallo minimo di esecuzione (in secondi)
// Android: minimo 15 minuti, consigliato 30 minuti per evitare blocchi sistema
const MINIMUM_INTERVAL_SECONDS = 60 * 30; // 30 minuti

/**
 * INTERFACCE
 */

// Configurazione monitoraggio
export interface MonitorConfig {
  minBattery: number;  // Soglia minima batteria (es. 20%)
  maxBattery: number;  // Soglia massima batteria (es. 80%)
  enabled: boolean;    // Monitoraggio attivo o no
}

// Stato task in background
export interface TaskStatus {
  isRegistered: boolean;
  isAvailable: boolean;
  status: BackgroundFetch.BackgroundFetchStatus | null;
}

/**
 * DEFINIZIONE DEL TASK
 * Questa funzione viene eseguita periodicamente in background
 */
TaskManager.defineTask(BATTERY_MONITOR_TASK, async () => {
  console.log('🔄 Background Task: Inizio controllo batteria...');

  try {
    // 1. Leggi configurazione utente
    const configStr = await SecureStore.getItemAsync('monitor_config');
    const config: MonitorConfig = configStr 
      ? JSON.parse(configStr)
      : { minBattery: 20, maxBattery: 80, enabled: true };

    if (!config.enabled) {
      console.log('⏸️ Monitoraggio disabilitato dall\'utente');
      return BackgroundFetch.BackgroundFetchResult.NoData;
    }

    // 2. Ottieni stato batteria
    const batteryLevel = await BatteryService.getLevel();
    const isCharging = await BatteryService.isCharging();

    console.log(`🔋 Batteria: ${batteryLevel}%, Carica: ${isCharging}`);

    // 3. Salva timestamp ultimo controllo
    await SecureStore.setItemAsync('last_monitor_check', Date.now().toString());

    // 4. Controlla soglie e invia notifiche se necessario
    
    // Batteria troppo scarica
    if (batteryLevel < config.minBattery && !isCharging) {
      console.log(`⚠️ Batteria sotto soglia minima: ${batteryLevel}% < ${config.minBattery}%`);
      await NotificationService.notifyBatteryLow(batteryLevel);
    }

    // Batteria raggiunge soglia massima durante carica
    if (batteryLevel >= config.maxBattery && isCharging) {
      console.log(`✅ Batteria ha raggiunto soglia massima: ${batteryLevel}% >= ${config.maxBattery}%`);
      await NotificationService.notifyBatteryThreshold(batteryLevel, config.maxBattery);
    }

    // Batteria al 100%
    if (batteryLevel >= 100) {
      console.log('🎉 Batteria completamente carica!');
      await NotificationService.notifyBatteryFull();
    }

    // 5. Qui puoi aggiungere logica per controllare dispositivi Sonoff
    // Ad esempio: spegnere presa quando batteria raggiunge 80%
    // const deviceId = await SecureStore.getItemAsync('selected_device_id');
    // if (deviceId && batteryLevel >= config.maxBattery && isCharging) {
    //   await SonoffService.setDevicePower(deviceId, false);
    // }

    console.log('✅ Background Task: Controllo completato con successo');
    return BackgroundFetch.BackgroundFetchResult.NewData;

  } catch (error) {
    console.error('❌ Background Task: Errore durante esecuzione', error);
    return BackgroundFetch.BackgroundFetchResult.Failed;
  }
});

/**
 * SERVIZIO BACKGROUND TASK
 */
export const BackgroundTaskService = {
  /**
   * Registra il task di monitoraggio batteria
   * Deve essere chiamato all'avvio dell'app o dopo login utente
   * 
   * @param config - Configurazione soglie batteria
   * @returns true se registrazione riuscita
   */
  registerTask: async (config?: MonitorConfig): Promise<boolean> => {
    try {
      // 1. Verifica se background fetch è disponibile su questo dispositivo
      const status = await BackgroundFetch.getStatusAsync();
      console.log('📊 Background Fetch Status:', status);

      if (status === BackgroundFetch.BackgroundFetchStatus.Restricted) {
        console.warn('⚠️ Background fetch è limitato dal sistema');
        return false;
      }

      if (status === BackgroundFetch.BackgroundFetchStatus.Denied) {
        console.warn('⚠️ Background fetch è disabilitato dall\'utente');
        return false;
      }

      // 2. Salva configurazione
      if (config) {
        await SecureStore.setItemAsync('monitor_config', JSON.stringify(config));
      }

      // 3. Controlla se task è già registrato
      const isTaskRegistered = await TaskManager.isTaskRegisteredAsync(BATTERY_MONITOR_TASK);
      
      if (isTaskRegistered) {
        console.log('✅ Task già registrato, aggiorno configurazione...');
        await BackgroundFetch.unregisterTaskAsync(BATTERY_MONITOR_TASK);
      }

      // 4. Registra il task con opzioni
      await BackgroundFetch.registerTaskAsync(BATTERY_MONITOR_TASK, {
        minimumInterval: MINIMUM_INTERVAL_SECONDS, // 30 minuti
        stopOnTerminate: false, // Continua anche dopo chiusura app
        startOnBoot: true,      // Riavvia task dopo reboot dispositivo
      });

      console.log('✅ Background Task registrato con successo!');
      console.log(`⏰ Intervallo minimo: ${MINIMUM_INTERVAL_SECONDS / 60} minuti`);
      
      return true;
    } catch (error) {
      console.error('❌ Errore registrazione Background Task:', error);
      return false;
    }
  },

  /**
   * Disattiva il monitoraggio in background
   * 
   * @returns true se disattivazione riuscita
   */
  unregisterTask: async (): Promise<boolean> => {
    try {
      const isTaskRegistered = await TaskManager.isTaskRegisteredAsync(BATTERY_MONITOR_TASK);
      
      if (isTaskRegistered) {
        await BackgroundFetch.unregisterTaskAsync(BATTERY_MONITOR_TASK);
        console.log('✅ Background Task disattivato');
      } else {
        console.log('ℹ️ Nessun task da disattivare');
      }

      return true;
    } catch (error) {
      console.error('❌ Errore disattivazione Background Task:', error);
      return false;
    }
  },

  /**
   * Verifica se il task è attualmente registrato
   * 
   * @returns true se task è registrato
   */
  isTaskRegistered: async (): Promise<boolean> => {
    try {
      return await TaskManager.isTaskRegisteredAsync(BATTERY_MONITOR_TASK);
    } catch (error) {
      console.error('❌ Errore verifica task:', error);
      return false;
    }
  },

  /**
   * Ottieni stato completo del background task
   * 
   * @returns Oggetto con informazioni sullo stato
   */
  getTaskStatus: async (): Promise<TaskStatus> => {
    try {
      const isRegistered = await TaskManager.isTaskRegisteredAsync(BATTERY_MONITOR_TASK);
      const status = await BackgroundFetch.getStatusAsync();

      return {
        isRegistered,
        isAvailable: status === BackgroundFetch.BackgroundFetchStatus.Available,
        status,
      };
    } catch (error) {
      console.error('❌ Errore recupero stato task:', error);
      return {
        isRegistered: false,
        isAvailable: false,
        status: null,
      };
    }
  },

  /**
   * Aggiorna configurazione monitoraggio
   * 
   * @param config - Nuova configurazione
   * @returns true se aggiornamento riuscito
   */
  updateConfig: async (config: MonitorConfig): Promise<boolean> => {
    try {
      await SecureStore.setItemAsync('monitor_config', JSON.stringify(config));
      console.log('✅ Configurazione monitoraggio aggiornata:', config);
      
      // Se task è già registrato, ri-registra con nuova config
      const isRegistered = await BackgroundTaskService.isTaskRegistered();
      if (isRegistered && config.enabled) {
        await BackgroundTaskService.registerTask(config);
      } else if (isRegistered && !config.enabled) {
        await BackgroundTaskService.unregisterTask();
      }

      return true;
    } catch (error) {
      console.error('❌ Errore aggiornamento configurazione:', error);
      return false;
    }
  },

  /**
   * Ottieni configurazione corrente
   * 
   * @returns Configurazione salvata o default
   */
  getConfig: async (): Promise<MonitorConfig> => {
    try {
      const configStr = await SecureStore.getItemAsync('monitor_config');
      if (configStr) {
        return JSON.parse(configStr);
      }
    } catch (error) {
      console.error('❌ Errore lettura configurazione:', error);
    }

    // Configurazione default
    return {
      minBattery: 20,
      maxBattery: 80,
      enabled: true,
    };
  },

  /**
   * Ottieni timestamp ultimo controllo eseguito
   * 
   * @returns Timestamp in millisecondi o null
   */
  getLastCheckTime: async (): Promise<number | null> => {
    try {
      const timestamp = await SecureStore.getItemAsync('last_monitor_check');
      return timestamp ? parseInt(timestamp) : null;
    } catch (error) {
      console.error('❌ Errore lettura timestamp:', error);
      return null;
    }
  },
};

export default BackgroundTaskService;
