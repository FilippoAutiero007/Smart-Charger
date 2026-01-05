import requests
import time
from .utils import logger, CONVEX_URL

class EmailService:
    """Invia email tramite Convex + Resend con retry"""
    
    @staticmethod
    def send_with_retry(endpoint, data, max_retries=3):
        """Invia richiesta con retry automatico"""
        for attempt in range(max_retries):
            try:
                response = requests.post(
                    f"{CONVEX_URL}{endpoint}",
                    json=data,
                    headers={"Content-Type": "application/json"},
                    timeout=10
                )
                
                if response.status_code == 200:
                    return True
                
                logger.warning(f"Tentativo {attempt+1}/{max_retries} fallito: {response.status_code}")
                
            except requests.exceptions.Timeout:
                logger.warning(f"Timeout tentativo {attempt+1}/{max_retries}")
            except Exception as e:
                logger.error(f"Errore tentativo {attempt+1}: {e}")
            
            if attempt < max_retries - 1:
                time.sleep(2 ** attempt)  # Backoff esponenziale
        
        return False
    
    @staticmethod
    def send_welcome_email(user_email, user_name="Utente"):
        import uuid
        logger.info(f"Invio email benvenuto a {user_email}")
        
        # Genera client_id per link Auto-Login (anche se non attivo subito, il link deve essere corretto)
        client_id = str(uuid.uuid4())
        renewal_url = f"{CONVEX_URL.rstrip('/')}/my-login?client_id={client_id}"
        
        if EmailService.send_with_retry("/api/email/welcome", {
            "email": user_email,
            "userName": user_name,
            "renewalUrl": renewal_url
        }):
            print(f"✉️ Email benvenuto inviata a {user_email}")
            return True
        print(f"⚠️ Impossibile inviare email")
        return False
    
    @staticmethod
    def send_expiration_warning(user_email, days_left, client_id=None):
        logger.info(f"Invio email scadenza a {user_email}")
        
        # Costruisci URL dinamico se client_id è presente (come in open_cloud_login_browser)
        renewal_url = None
        if client_id:
            # Usa lo stesso formato di auth_flow.py:open_cloud_login_browser
            renewal_url = f"{CONVEX_URL.rstrip('/')}/my-login?client_id={client_id}"
            logger.info(f"URL rinnovo generato: {renewal_url}")
            
        if EmailService.send_with_retry("/api/email/expiring", {
            "email": user_email,
            "daysLeft": days_left,
            "renewalUrl": renewal_url
        }):
            print(f"✉️ Email scadenza inviata")
            return True
        return False
    
    @staticmethod
    def send_test_email(user_email):
        print(f"📤 Invio email di test a {user_email}...")
        if EmailService.send_with_retry("/api/email/test", {"to": user_email}):
            print(f"✅ Email di test inviata!")
            print("📬 Controlla la casella (anche spam)")
            return True
        print(f"✗ Errore invio email")
        print("💡 Verifica configurazione Resend su Convex Dashboard")
        return False
