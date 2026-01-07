import requests
import time
import webbrowser
import uuid
from .utils import logger, CONVEX_URL
from .token_manager import TokenManager
from .email_service import EmailService

def open_google_login_browser(client_id):
    """Apre il browser per il login Google"""
    try:
        target = f"{CONVEX_URL}/google-login?client_id={client_id}"
        webbrowser.open(target)
        print(f"🌐 Browser aperto: {target}")
        print("💡 Completa il login con Google nel browser.")
        return True
    except Exception as e:
        print(f"⚠️ Impossibile aprire browser: {e}")
        print(f"📍 Apri manualmente: {target}")
        return False

def open_cloud_login_browser(client_id=None):
    """Apre il browser al sito cloud (Convex) che deve reindirizzare a eWeLink"""
    try:
        target = CONVEX_URL.rstrip('/') + "/my-login" # Usa route specifica
        if client_id:
            target += f"?client_id={client_id}"
            
        webbrowser.open(target)
        print(f"🌐 Browser aperto: {target}")
        print("💡 Se il sito è configurato, verrai reindirizzato al login eWeLink.")
        if client_id:
             print("🔄 Auto-Login attivo: l'app attenderà il token automaticamente.")
        else:
             print("   Dopo il login, cerca un file 'token.json' nella cartella del progetto o copia il codice di autorizzazione.")
        return True
    except Exception as e:
        print(f"⚠️ Impossibile aprire browser: {e}")
        print(f"📍 Apri manualmente: {target}")
        return False

def login_with_cloud_code():
    """Login con codice Convex OAuth"""
    print("\n" + "="*60)
    print("🌐 LOGIN CON CONVEX CLOUD")
    print("="*60)
    print(f"📱 Apri nel browser: {CONVEX_URL}")
    print("="*60)
    
    print("\n📋 PROCEDURA:")
    print("1. Clicca 'Accedi con eWeLink'")
    print("2. Autorizza l'applicazione")
    print("3. Copia il codice a 6 cifre")
    print("4. Incollalo qui sotto")
    print("-"*60)
    
    max_retries = 3
    for attempt in range(max_retries):
        code = input(f"\n🔑 Codice (6 caratteri) [Tentativo {attempt+1}/{max_retries}]: ").strip().upper()
        
        if not code:
            print("⚠️ Codice vuoto")
            continue
            
        if len(code) != 6:
            print("⚠️ Il codice deve essere di 6 caratteri")
            continue
        
        break
    else:
        print("\n❌ Tentativi esauriti")
        return False
    
    logger.info(f"Tentativo login con codice: {code}")
    
    try:
        print(f"🔗 Connessione a: {CONVEX_URL}/api/token")
        response = requests.post(
            f"{CONVEX_URL}/api/token",
            json={"code": code},
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        
        print(f"📊 Status: {response.status_code}")
        # print(f"📝 Response: {response.text[:200]}")
        
        if response.status_code == 200:
            result = response.json()
            if not result.get("error"):
                data = result["data"]
                # Supporta sia camelCase (Convex) che snake_case
                access_token = data.get("accessToken") or data.get("access_token")
                refresh_token = data.get("refreshToken") or data.get("refresh_token")
                at_expired = data.get("atExpiredTime") or data.get("at_expired_time")
                rt_expired = data.get("rtExpiredTime") or data.get("rt_expired_time")
                region = data.get("region", "eu")

                TokenManager.save(
                    access_token,
                    refresh_token,
                    at_expired,
                    rt_expired,
                    region
                )
                print("\n✅ Login completato!")
                logger.info("Login OAuth completato")
                return True
            else:
                error_msg = result.get('message', 'Errore sconosciuto')
        else:
            try:
                result = response.json()
                error_msg = result.get('message', f'Errore HTTP {response.status_code}')
            except:
                error_msg = f"Errore HTTP {response.status_code}"
        
        print(f"\n✗ {error_msg}")
        logger.error(f"Login fallito: {error_msg}")
        return False
        
    except Exception as e:
        print(f"\n✗ Errore: {e}")
        logger.error(f"Eccezione login: {e}")
        return False

def wait_for_auto_login(client_id, timeout=300):
    """Polls Convex for tokens associated with client_id"""
    print(f"\n⏳ Attesa Login (ID: {client_id[:8]}...)...")
    print("   Completa il login nel browser. L'app rileverà automaticamente il successo.")
    print(f"   Timeout: {timeout}s")
    
    deadline = time.time() + timeout
    poll_interval = 2  # secondi tra ogni polling
    dots = 0
    
    while time.time() < deadline:
        try:
            response = requests.post(
                f"{CONVEX_URL}/api/poll-token",
                json={"clientId": client_id},
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            
            if response.status_code == 200:
                result = response.json()
                
                # Check if tokens are ready
                if result.get("status") == "success" and result.get("data"):
                    data = result["data"]
                    
                    # Handle User Info (Google Login)
                    user_info = data.get("user")
                    if user_info:
                        from .config_manager import ConfigManager
                        config = ConfigManager.load()
                        if user_info.get("email"):
                            config["user_email"] = user_info["email"]
                            ConfigManager.save(config)
                            print(f"\n✅ Benvenuto {user_info['email']}!")
                            logger.info(f"Login Google completato: {user_info['email']}")
                    
                    # Handle Tokens (eWeLink Login)
                    tokens = data.get("tokens")
                    # Backward compatibility check
                    if not tokens and data.get("accessToken"):
                        tokens = data
                    
                    if tokens:
                        access_token = tokens.get("accessToken")
                        refresh_token = tokens.get("refreshToken")
                        at_expired = tokens.get("atExpiredTime")
                        rt_expired = tokens.get("rtExpiredTime")
                        region = tokens.get("region", "eu")
                        
                        if access_token and refresh_token:
                            TokenManager.save(
                                access_token,
                                refresh_token,
                                at_expired,
                                rt_expired,
                                region
                            )
                            print("\n✅ Token eWeLink ricevuti!")
                            logger.info("Token eWeLink ricevuti")
                            return True
                    
                    # If we got user info but no tokens, it's a partial success (Google Login only)
                    if user_info:
                        return "USER_ONLY"

                elif result.get("status") == "pending":
                    # Still waiting for user to complete login
                    dots = (dots + 1) % 4
                    print(f"\r   Attesa" + "." * dots + " " * (3 - dots), end="", flush=True)
                else:
                    logger.warning(f"Risposta inattesa: {result}")
            
            elif response.status_code == 404:
                # Endpoint not found - likely backend not deployed
                print("\n⚠️ Errore: Endpoint polling non trovato (404)")
                print("💡 Il backend Convex potrebbe non essere aggiornato.")
                print("   Esegui 'npx convex deploy' nella cartella convex-backend.")
                return False
                
            else:
                logger.warning(f"Polling error: {response.status_code}")
                
        except requests.exceptions.Timeout:
            logger.warning("Timeout polling richiesta")
        except Exception as e:
            logger.error(f"Errore polling: {e}")
        
        time.sleep(poll_interval)
    
    print("\n✖ Timeout: login non completato nel browser")
    logger.warning("Auto-Login timeout")
    return False

def import_tokens_from_nodejs():
    """Importa token da file Node.js"""
    import os
    # Assuming BASE_DIR is available or passed. For now, let's assume it's relative to current dir
    # or we import BASE_DIR from utils.
    from .utils import BASE_DIR
    nodejs_token_file = os.path.join(BASE_DIR, "token.json")
    
    try:
        with open(nodejs_token_file, "r") as f:
            data = json.load(f)
        
        TokenManager.save(
            data["data"].get("accessToken"),
            data["data"].get("refreshToken"),
            data["data"].get("atExpiredTime"),
            data["data"].get("rtExpiredTime"),
            data.get("region", "eu")
        )
        
        print("✓ Token importati")
        logger.info("Token importati da token.json")
        return True
        
    except FileNotFoundError:
        print("⚠️ File token.json non trovato")
        logger.warning("token.json non trovato")
        return False
    except Exception as e:
        print(f"✗ Errore: {e}")
        logger.error(f"Errore importazione: {e}")
        return False
