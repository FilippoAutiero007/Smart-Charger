from datetime import datetime
import math
from .utils import logger, TOKEN_FILE
from .file_manager import FileManager

class TokenManager:
    """Gestisce token OAuth con validazione e logging"""
    
    @staticmethod
    def normalize_timestamp(value):
        if value is None:
            return None
        try:
            if isinstance(value, (int, float)):
                return int(value * 1000) if value < 1e12 else int(value)
            
            s = str(value).strip()
            if s.isdigit():
                val = int(s)
                return val * 1000 if val < 1e12 else val
            
            try:
                dt = datetime.fromisoformat(s)
                return int(dt.timestamp() * 1000)
            except:
                return None
        except:
            return None
    
    @staticmethod
    def save(access_token, refresh_token, at_expired=None, rt_expired=None, region="eu"):
        if not access_token or not refresh_token:
            logger.error("Token mancanti, impossibile salvare")
            return False
        
        data = {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "at_expired_time": TokenManager.normalize_timestamp(at_expired),
            "rt_expired_time": TokenManager.normalize_timestamp(rt_expired),
            "region": region,
            "last_update": datetime.now().isoformat()
        }
        
        if FileManager.save_json(TOKEN_FILE, data):
            logger.info("Token OAuth salvati")
            print("💾 Token salvati")
            return True
        return False
    
    @staticmethod
    def load():
        data = FileManager.load_json(TOKEN_FILE)
        if not data:
            # Info invece di Warning per il primo avvio
            logger.info("Nessun token disponibile (primo avvio o file mancante)")
            return None
        
        normalized = {
            "access_token": data.get("access_token") or data.get("accessToken"),
            "refresh_token": data.get("refresh_token") or data.get("refreshToken"),
            "at_expired_time": TokenManager.normalize_timestamp(
                data.get("at_expired_time") or data.get("atExpiredTime")
            ),
            "rt_expired_time": TokenManager.normalize_timestamp(
                data.get("rt_expired_time") or data.get("rtExpiredTime")
            ),
            "region": data.get("region", "eu"),
            "last_update": data.get("last_update")
        }
        
        if not (normalized["access_token"] and normalized["refresh_token"]):
            logger.error("Token invalidi in tokens.json")
            print("⚠️ Token non validi")
            return None
        
        logger.info("Token caricati correttamente")
        return normalized
    
    @staticmethod
    def check_expiration():
        tokens = TokenManager.load()
        if not tokens or not tokens.get("rt_expired_time"):
            return None
        
        try:
            rt_ms = tokens["rt_expired_time"]
            rt = datetime.fromtimestamp(rt_ms / 1000.0)
            now = datetime.now()
            delta = rt - now
            days_left = math.ceil(delta.total_seconds() / 86400)
            
            return {
                "days_left": days_left,
                "expired_date": rt.strftime("%Y-%m-%d %H:%M:%S"),
                "needs_renewal": days_left <= 2,
                "is_expired": days_left < 0
            }
        except Exception as e:
            logger.error(f"Errore controllo scadenza: {e}")
            return None
