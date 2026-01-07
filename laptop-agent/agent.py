import time
import sys
import os
from core.utils import logger, APP_ID, APP_SECRET
from services.sonoff_controller import SonoffController
from core.battery_monitor import BatteryMonitor

# Configurazione predefinita
DEFAULT_CONFIG = {
    "min_battery": 20,
    "max_battery": 80,
    "check_interval": 30,
    "device_id": None
}

def main():
    print("\n🔋 SMART CHARGER - AGENTE LAPTOP")
    print("===============================")
    
    # 1. Inizializzazione Controller
    controller = SonoffController(APP_ID, APP_SECRET)
    
    # 2. Verifica Token
    if not controller.access_token:
        print("\n❌ Token non trovati.")
        print("💡 Esegui 'python setup.py' per effettuare l'accesso.")
        return
    
    if not controller.refresh_access_token():
         print("\n❌ Token scaduti e aggiornamento fallito.")
         print("💡 Esegui 'python setup.py' per accedere di nuovo.")
         return

    # 3. Selezione Dispositivo
    if not controller.device_id:
        print("\nRecupero dispositivi...")
        devices = controller.get_all_devices()
        if not devices:
            print("Nessun dispositivo trovato.")
            return
        
        # Selezione automatica del primo dispositivo disponibile
        device = devices[0]
        controller.set_device_id(device["id"], device["name"])
        print(f"Dispositivo Selezionato: {device['name']} ({device['id']})")
    
    # 4. Avvio Monitoraggio
    try:
        print(f"Monitoraggio avviato (Soglie: {DEFAULT_CONFIG['min_battery']}% - {DEFAULT_CONFIG['max_battery']}%)")
        BatteryMonitor.start_monitoring(controller, DEFAULT_CONFIG)
    except KeyboardInterrupt:
        print("\nMonitoraggio interrotto dall'utente.")
    except Exception as e:
        print(f"\n❌ Errore durante il monitoraggio: {e}")

if __name__ == "__main__":
    main()
