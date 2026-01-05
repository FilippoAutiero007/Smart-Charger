from .utils import logger, CONFIG_FILE
from .file_manager import FileManager

class ConfigManager:
    """Gestisce configurazione con validazione"""
    
    DEFAULT_CONFIG = {
        "device_id": "",
        "device_name": "",
        "min_battery": 20,
        "max_battery": 80,
        "check_interval": 300,
        "user_email": "",
        "region": "eu",
        "retry_attempts": 3,
        "retry_delay": 5
    }
    
    @staticmethod
    def load():
        config = FileManager.load_json(CONFIG_FILE, ConfigManager.DEFAULT_CONFIG.copy())
        
        # Assicura presenza di tutte le chiavi
        for key, value in ConfigManager.DEFAULT_CONFIG.items():
            if key not in config:
                config[key] = value
        
        # Valida configurazione
        ConfigManager.validate(config)
        return config
    
    @staticmethod
    def validate(config):
        """Valida i valori di configurazione"""
        errors = []
        
        if config["min_battery"] >= config["max_battery"]:
            errors.append("min_battery deve essere < max_battery (valori scambiati)")
            # Scambia i valori invece di resettare
            config["min_battery"], config["max_battery"] = config["max_battery"], config["min_battery"]
        
        if not (0 <= config["min_battery"] <= 100):
            errors.append("min_battery deve essere tra 0 e 100")
            config["min_battery"] = 20
        
        if not (0 <= config["max_battery"] <= 100):
            errors.append("max_battery deve essere tra 0 e 100")
            config["max_battery"] = 80
        
        if config["check_interval"] < 10:
            errors.append("check_interval deve essere >= 10 secondi")
            config["check_interval"] = 10
        
        if errors:
            logger.warning(f"Configurazione corretta automaticamente: {', '.join(errors)}")
            print(f"⚠️ Configurazione corretta: {', '.join(errors)}")
            FileManager.save_json(CONFIG_FILE, config)
        
        return len(errors) == 0
    
    @staticmethod
    def save(config):
        if ConfigManager.validate(config):
            if FileManager.save_json(CONFIG_FILE, config):
                print("💾 Configurazione salvata")
                return True
        return False
