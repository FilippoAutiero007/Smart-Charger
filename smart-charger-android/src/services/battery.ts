/**
 * ============================================
 * BATTERY SERVICE
 * ============================================
 * Questo file gestisce il monitoraggio della batteria del dispositivo.
 * 
 * Per principianti:
 * - expo-battery è una libreria che accede alle info della batteria
 * - Possiamo leggere: livello carica, stato carica, se è in carica
 * - Possiamo anche "ascoltare" cambiamenti in tempo reale
 * 
 * Come funziona:
 * 1. getBatteryLevelAsync() restituisce un valore tra 0 e 1 (es. 0.75 = 75%)
 * 2. getBatteryStateAsync() dice se la batteria è in carica, piena, scarica, ecc.
 * 3. I listener sono "osservatori" che vengono notificati quando cambiano i valori
 */

import * as Battery from 'expo-battery';

/**
 * SERVIZIO BATTERIA
 * Raggruppa tutte le funzioni per interagire con la batteria
 */
export const BatteryService = {
  /**
   * Ottiene il livello attuale della batteria
   * 
   * @returns Percentuale batteria (0-100)
   */
  getLevel: async (): Promise<number> => {
    try {
      const level = await Battery.getBatteryLevelAsync();
      // Converti da 0-1 a 0-100 e arrotonda
      return Math.round(level * 100);
    } catch (error) {
      console.error('❌ Errore lettura livello batteria:', error);
      return 0;
    }
  },

  /**
   * Controlla se il dispositivo è attualmente in carica
   * 
   * @returns true se in carica o batteria piena
   */
  isCharging: async (): Promise<boolean> => {
    try {
      const state = await Battery.getBatteryStateAsync();
      // La batteria è "in carica" se sta caricando o è già piena
      return (
        state === Battery.BatteryState.CHARGING ||
        state === Battery.BatteryState.FULL
      );
    } catch (error) {
      console.error('❌ Errore lettura stato carica:', error);
      return false;
    }
  },

  /**
   * Ottiene lo stato dettagliato della batteria
   * 
   * @returns Oggetto con tutte le info batteria
   */
  getFullState: async () => {
    try {
      const level = await BatteryService.getLevel();
      const isCharging = await BatteryService.isCharging();
      const state = await Battery.getBatteryStateAsync();
      const lowPowerMode = await Battery.getPowerStateAsync();

      return {
        level,
        isCharging,
        state,
        isLowPowerMode: lowPowerMode.lowPowerMode,
      };
    } catch (error) {
      console.error('❌ Errore lettura stato completo batteria:', error);
      return {
        level: 0,
        isCharging: false,
        state: Battery.BatteryState.UNKNOWN,
        isLowPowerMode: false,
      };
    }
  },

  /**
   * Aggiunge un listener per il livello della batteria
   * Quando il livello cambia, chiama automaticamente la callback
   * 
   * IMPORTANTE: Ricordati di rimuovere il listener con .remove()
   * quando non serve più (es. quando esci dalla schermata)
   * 
   * @param callback - Funzione chiamata quando cambia il livello
   * @returns Subscription con metodo .remove() per rimuovere listener
   */
  addLevelListener: (callback: (level: number) => void) => {
    return Battery.addBatteryLevelListener(({ batteryLevel }) => {
      callback(Math.round(batteryLevel * 100));
    });
  },

  /**
   * Aggiunge un listener per lo stato di carica
   * Quando lo stato cambia (inizia/finisce carica), chiama la callback
   * 
   * @param callback - Funzione chiamata quando cambia lo stato
   * @returns Subscription con metodo .remove() per rimuovere listener
   */
  addStateListener: (callback: (isCharging: boolean) => void) => {
    return Battery.addBatteryStateListener(({ batteryState }) => {
      const isCharging =
        batteryState === Battery.BatteryState.CHARGING ||
        batteryState === Battery.BatteryState.FULL;
      callback(isCharging);
    });
  },

  /**
   * Verifica se la batteria è sotto una certa soglia
   * 
   * @param threshold - Soglia percentuale (es. 20)
   * @returns true se batteria è sotto la soglia
   */
  isBelowThreshold: async (threshold: number): Promise<boolean> => {
    const level = await BatteryService.getLevel();
    return level < threshold;
  },

  /**
   * Verifica se la batteria è sopra una certa soglia
   * 
   * @param threshold - Soglia percentuale (es. 80)
   * @returns true se batteria è sopra la soglia
   */
  isAboveThreshold: async (threshold: number): Promise<boolean> => {
    const level = await BatteryService.getLevel();
    return level >= threshold;
  },
};
