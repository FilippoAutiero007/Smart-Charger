import time
import random
from datetime import datetime
from .utils import logger, shutdown_event
from .device_history import DeviceHistory

try:
    import psutil
except ImportError:
    psutil = None

class BatteryMonitor:
    """Monitora batteria con supporto Android"""
    
    @staticmethod
    def get_level_android():
        try:
            import importlib
            jnius = importlib.import_module("jnius")
            autoclass = getattr(jnius, "autoclass")
            
            Context = autoclass('android.content.Context')
            BatteryManager = autoclass('android.os.BatteryManager')
            PythonActivity = autoclass('org.kivy.android.PythonActivity')
            
            activity = PythonActivity.mActivity
            battery_manager = activity.getSystemService(Context.BATTERY_SERVICE)
            level = battery_manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            logger.debug(f"Batteria Android: {level}%")
            return level
            
        except ImportError:
            logger.warning("jnius non installato - modalità simulazione")
            print("⚠️ jnius non disponibile")
            print("💡 Per Android: pip install jnius")
            return None
        except Exception as e:
            logger.error(f"Errore lettura batteria Android: {e}")
            return None
    
    @staticmethod
    def get_level_desktop():
        if psutil:
            try:
                battery = psutil.sensors_battery()
                if battery:
                    level = int(battery.percent)
                    logger.debug(f"Batteria rilevata (psutil): {level}%")
                    return level
            except Exception as e:
                logger.error(f"Errore psutil: {e}")
        
        # Fallback simulazione se psutil fallisce o non c'è batteria
        level = random.randint(15, 95)
        logger.warning(f"Batteria simulata (fallback): {level}%")
        return level
    
    @staticmethod
    def get_level():
        level = BatteryMonitor.get_level_android()
        if level is None:
            level = BatteryMonitor.get_level_desktop()
        return level
    
    @staticmethod
    def start_monitoring(controller, config):
        min_bat = config["min_battery"]
        max_bat = config["max_battery"]
        interval = config["check_interval"]
        
        print(f"\n{'='*60}")
        print("🔋 MONITORAGGIO AUTOMATICO AVVIATO")
        print(f"{'='*60}")
        print(f"📱 Dispositivo: {config['device_id']}")
        print(f"📊 Soglie: {min_bat}% - {max_bat}%")
        print(f"⏱️  Intervallo: {interval}s")
        print(f"💡 Premi Ctrl+C per fermare")
        print(f"{'='*60}\n")
        
        logger.info(f"Monitoraggio avviato: {min_bat}-{max_bat}%, intervallo {interval}s")
        
        charging = False
        error_count = 0
        max_errors = 5
        
        while not shutdown_event.is_set():
            try:
                battery = BatteryMonitor.get_level()
                timestamp = datetime.now().strftime("%H:%M:%S")
                
                print(f"[{timestamp}] 🔋 {battery}%", end=" ")
                
                if battery <= min_bat and not charging:
                    print(f"→ Accendo (≤{min_bat}%)")
                    if controller.set_device_power(True):
                        charging = True
                        error_count = 0
                        DeviceHistory.log_action(controller.device_id, "turned_on", True, battery)
                    else:
                        error_count += 1
                
                elif battery >= max_bat and charging:
                    print(f"→ Spengo (≥{max_bat}%)")
                    if controller.set_device_power(False):
                        charging = False
                        error_count = 0
                        DeviceHistory.log_action(controller.device_id, "turned_off", True, battery)
                    else:
                        error_count += 1
                
                else:
                    print("🔌" if charging else "⚡")
                
                if error_count >= max_errors:
                    print(f"\n⚠️ Troppi errori consecutivi ({error_count})")
                    print("🔄 Tentativo rinnovo token...")
                    if controller.refresh_access_token():
                        error_count = 0
                    else:
                        print("✗ Rinnovo fallito, arresto monitoraggio")
                        break
                
                # Sleep interrompibile
                for _ in range(interval):
                    if shutdown_event.is_set():
                        break
                    time.sleep(1)
                
            except KeyboardInterrupt:
                print("\n⚠️ Interruzione utente")
                break
            except Exception as e:
                logger.error(f"Errore monitoraggio: {e}")
                print(f"\n✗ Errore: {e}")
                time.sleep(10)
        
        # Cleanup
        print("\n🛑 Arresto monitoraggio...")
        if charging:
            print("🔌 Spengo dispositivo per sicurezza...")
            controller.set_device_power(False)
        
        logger.info("Monitoraggio terminato")
