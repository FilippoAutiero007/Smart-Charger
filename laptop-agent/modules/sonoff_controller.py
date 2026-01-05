import requests
import time
import json
from .utils import logger
from .token_manager import TokenManager
from .device_history import DeviceHistory

class SonoffController:
    """Controller API Sonoff con retry e logging"""
    
    def __init__(self, app_id, app_secret, region="eu"):
        self.app_id = app_id
        self.app_secret = app_secret
        self.region = region
        self.base_url = f"https://{region}-apia.coolkit.cc"
        self.access_token = None
        self.refresh_token = None
        self.device_id = None
        self.device_name = ""
        
        self._load_tokens()
    
    def _load_tokens(self):
        tokens = TokenManager.load()
        if tokens:
            self.access_token = tokens["access_token"]
            self.refresh_token = tokens["refresh_token"]
            self.region = tokens.get("region", "eu")
            self.base_url = f"https://{self.region}-apia.coolkit.cc"
            logger.info("Token caricati nel controller")
    
    def _get_headers(self):
        return {
            "Authorization": f"Bearer {self.access_token}" if self.access_token else "",
            "Content-Type": "application/json",
            "X-CK-Appid": self.app_id
        }
    
    def set_device_id(self, device_id, device_name=""):
        self.device_id = device_id
        self.device_name = device_name
        logger.info(f"Dispositivo impostato: {device_name} ({device_id})")
    
    def refresh_access_token(self):
        if not self.refresh_token:
            logger.error("Refresh token mancante")
            print("✗ Refresh token non disponibile")
            return False
        
        url = f"{self.base_url}/v2/user/refresh"
        headers = {
            "Content-Type": "application/json",
            "X-CK-Appid": self.app_id
        }
        data = {"rt": self.refresh_token}
        
        try:
            logger.info("Rinnovo token...")
            response = requests.post(url, headers=headers, json=data, timeout=15)
            
            if response.status_code == 200:
                result = response.json()
                if result.get("error") == 0:
                    data = result["data"]
                    self.access_token = data.get("accessToken")
                    self.refresh_token = data.get("refreshToken")
                    
                    TokenManager.save(
                        self.access_token,
                        self.refresh_token,
                        data.get("atExpiredTime"),
                        data.get("rtExpiredTime"),
                        self.region
                    )
                    print("🔁 Token rinnovato")
                    logger.info("Token rinnovati con successo")
                    return True
                else:
                    # Errore nell'API anche se HTTP 200
                    error_code = result.get("error")
                    error_msg = result.get("msg", "Unknown error")
                    logger.error(f"API refresh error {error_code}: {error_msg}")
                    print(f"✗ Errore eWeLink: {error_msg}")
                    print("💡 Il refresh token è scaduto. Serve nuovo login (opzione 2 o 3)")
                    return False
            
            logger.error(f"Rinnovo fallito: HTTP {response.status_code}")
            print(f"✗ Errore HTTP {response.status_code}")
            return False
            
        except Exception as e:
            logger.error(f"Eccezione rinnovo: {e}")
            print(f"✗ Errore: {e}")
            return False

    
    def _api_request_with_retry(self, method, endpoint, max_retries=3, **kwargs):
        """API request con retry automatico"""
        url = f"{self.base_url}{endpoint}"
        headers = self._get_headers()
        kwargs.setdefault("timeout", 15)
        
        for attempt in range(max_retries):
            try:
                if method == "GET":
                    response = requests.get(url, headers=headers, **kwargs)
                elif method == "POST":
                    response = requests.post(url, headers=headers, **kwargs)
                else:
                    return None
                
                # Retry con refresh su 401
                if response.status_code == 401 and attempt == 0:
                    logger.warning("Token scaduto, rinnovo...")
                    if self.refresh_access_token():
                        headers = self._get_headers()
                        continue
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get("error") == 0:
                        return result.get("data")
                
                logger.warning(f"API error {response.status_code}: {response.text[:100]}")
                
            except requests.exceptions.Timeout:
                logger.warning(f"Timeout tentativo {attempt+1}/{max_retries}")
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)
            except Exception as e:
                logger.error(f"Eccezione API: {e}")
                if attempt < max_retries - 1:
                    time.sleep(2)
        
        print(f"✗ Richiesta API fallita dopo {max_retries} tentativi")
        return None
    
    def get_all_devices(self):
        logger.info("Recupero lista dispositivi...")
        data = self._api_request_with_retry("GET", "/v2/device/thing", params={"num": 0})
        
        if not data:
            logger.warning("Nessun dispositivo trovato (data is empty)")
            return []
        
        # DEBUG: Stampa risposta grezza per capire cosa arriva
        # print(f"\n📦 DEBUG RAW DATA: {json.dumps(data, indent=2)}")
        
        devices = []
        thing_list = data.get("thingList", [])
        # print(f"📋 Trovati {len(thing_list)} elementi totali")

        for item in thing_list:
            item_type = item.get("itemType")
            device_data = item.get("itemData", {})
            
            # 1=Device, 2=Group, 3=Home?
            if item_type == 1 or item_type == 2:
                devices.append({
                    "id": device_data.get("deviceid"),
                    "name": device_data.get("name", "Unknown"),
                    "online": device_data.get("online", False),
                    "brand": device_data.get("brandName", "Sonoff"),
                    "type": "Device" if item_type == 1 else "Group"
                })
            else:
                # print(f"⚠️ Skipped item type: {item_type}")
                pass
        
        logger.info(f"Trovati {len(devices)} dispositivi validi")
        return devices
    
    def get_device_status(self):
        if not self.device_id:
            print("✗ Nessun dispositivo selezionato")
            print("💡 Usa opzione 4 del menu per selezionarne uno")
            logger.warning("Tentativo get_status senza device_id")
            return None
        
        logger.info(f"Recupero stato dispositivo {self.device_id}")
        data = self._api_request_with_retry(
            "GET",
            "/v2/device/thing/status",
            params={"type": 1, "id": self.device_id}
        )
        
        return data.get("params") if data else None
    
    def set_device_power(self, power_on):
        if not self.device_id:
            print("✗ Nessun dispositivo selezionato")
            print("💡 Seleziona dispositivo dal menu (opzione 4)")
            logger.warning("Tentativo set_power senza device_id")
            return False
        
        action = "on" if power_on else "off"
        logger.info(f"Comando {action} a dispositivo {self.device_id}")
        
        data = self._api_request_with_retry(
            "POST",
            "/v2/device/thing/status",
            json={
                "type": 1,
                "id": self.device_id,
                "params": {"switch": action}
            }
        )
        
        if data:
            status = "ACCESO" if power_on else "SPENTO"
            print(f"✓ Dispositivo {status}")
            logger.info(f"Dispositivo {status}")
            
            # Log history
            DeviceHistory.log_action(self.device_id, action, True)
            return True
        
        print(f"✗ Errore comando {action}")
        logger.error(f"Comando {action} fallito")
        DeviceHistory.log_action(self.device_id, action, False)
        return False
