from datetime import datetime
from .utils import logger, HISTORY_FILE
from .file_manager import FileManager

class DeviceHistory:
    """Gestisce lo storico delle azioni"""
    
    @staticmethod
    def log_action(device_id, action, success, battery_level=None):
        try:
            history = FileManager.load_json(HISTORY_FILE, [])
            
            entry = {
                "timestamp": datetime.now().isoformat(),
                "device_id": device_id,
                "action": action,
                "success": success,
                "battery_level": battery_level
            }
            
            history.append(entry)
            
            # Mantieni solo ultimi 1000 record
            if len(history) > 1000:
                history = history[-1000:]
            
            FileManager.save_json(HISTORY_FILE, history)
        except Exception as e:
            logger.error(f"Errore log history: {e}")
    
    @staticmethod
    def get_recent(limit=50):
        history = FileManager.load_json(HISTORY_FILE, [])
        return history[-limit:] if history else []
