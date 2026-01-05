import json
import os
from .utils import logger

class FileManager:
    """Gestisce salvataggio e caricamento di file JSON con validazione"""
    
    @staticmethod
    def save_json(filepath, data):
        try:
            # Backup del file esistente
            if os.path.exists(filepath):
                backup = f"{filepath}.backup"
                try:
                    with open(filepath, 'r') as f:
                        with open(backup, 'w') as b:
                            b.write(f.read())
                except:
                    pass
            
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            logger.info(f"File salvato: {os.path.basename(filepath)}")
            return True
        except Exception as e:
            logger.error(f"Errore salvataggio {filepath}: {e}")
            print(f"✗ Errore salvataggio: {e}")
            return False
    
    @staticmethod
    def load_json(filepath, default=None):
        try:
            if not os.path.exists(filepath):
                logger.warning(f"File non trovato: {os.path.basename(filepath)}")
                if default is not None:
                    FileManager.save_json(filepath, default)
                return default
            
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)
            logger.info(f"File caricato: {os.path.basename(filepath)}")
            return data
            
        except json.JSONDecodeError as e:
            logger.error(f"File {filepath} corrotto: {e}")
            print(f"⚠️ File {os.path.basename(filepath)} corrotto")
            
            # Prova a caricare il backup
            backup = f"{filepath}.backup"
            if os.path.exists(backup):
                print(f"🔄 Ripristino backup...")
                try:
                    with open(backup, 'r') as f:
                        data = json.load(f)
                    FileManager.save_json(filepath, data)
                    return data
                except:
                    pass
            
            if default is not None:
                print(f"📝 Creazione file default...")
                FileManager.save_json(filepath, default)
                return default
            
            return None
            
        except Exception as e:
            logger.error(f"Errore caricamento {filepath}: {e}")
            return default
