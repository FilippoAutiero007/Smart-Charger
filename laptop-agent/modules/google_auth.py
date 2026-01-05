import os
import json
import requests
from google_auth_oauthlib.flow import InstalledAppFlow
from .utils import logger, BASE_DIR

# Scope indica a quali dati vuoi accedere
SCOPES = [
    'https://www.googleapis.com/auth/userinfo.profile', 
    'https://www.googleapis.com/auth/userinfo.email',
    'openid'
]

def find_client_secret():
    """Trova il file client_secret nella directory del progetto"""
    # Cerca prima nella root
    for file in os.listdir(BASE_DIR):
        if file.startswith("client_secret_") and file.endswith(".json"):
            return os.path.join(BASE_DIR, file)
    
    # Cerca in convex-backend/convex (dove l'utente lo aveva aperto)
    convex_dir = os.path.join(BASE_DIR, "convex-backend", "convex")
    if os.path.exists(convex_dir):
        for file in os.listdir(convex_dir):
            if file.startswith("client_secret_") and file.endswith(".json"):
                return os.path.join(convex_dir, file)
                
    return None

def login_google():
    """Esegue il login con Google usando flow locale"""
    try:
        client_secret_file = find_client_secret()
        if not client_secret_file:
            print("❌ File client_secret_*.json non trovato!")
            print("💡 Scaricalo dalla Google Cloud Console e mettilo nella cartella del progetto.")
            return None

        print(f"🔑 Trovato credenziali: {os.path.basename(client_secret_file)}")
        
        # Flow per desktop app
        flow = InstalledAppFlow.from_client_secrets_file(
            client_secret_file, SCOPES)
        
        # Apre automaticamente il browser per login
        print("🌐 Apertura browser per autenticazione Google...")
        creds = flow.run_local_server(port=0)
        
        print("✅ Autenticazione riuscita!")
        
        # Recupera info utente usando il token
        if creds and creds.token:
            try:
                response = requests.get(
                    'https://www.googleapis.com/oauth2/v2/userinfo',
                    headers={'Authorization': f'Bearer {creds.token}'}
                )
                
                if response.status_code == 200:
                    user_info = response.json()
                    email = user_info.get('email')
                    print(f"👤 Utente: {email}")
                    logger.info(f"Google Login locale successo: {email}")
                    return email
                else:
                    logger.error(f"Errore recupero userinfo: {response.text}")
            except Exception as e:
                logger.error(f"Eccezione recupero userinfo: {e}")
                
        return None
        
    except Exception as e:
        print(f"❌ Errore durante il login: {e}")
        logger.error(f"Errore login Google locale: {e}")
        return None
