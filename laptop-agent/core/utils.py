import os
import logging
from threading import Event

# ==============================================================  
# CONFIGURAZIONE LOGGING
# ==============================================================  

LOG_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs", "agent.log")
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE, encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("SonoffCharger")

# ==============================================================  
# CONFIGURAZIONE BASE
# ==============================================================  

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOKEN_FILE = os.path.join(BASE_DIR, "data", "tokens.json")
CONFIG_FILE = os.path.join(BASE_DIR, "data", "config.json")
HISTORY_FILE = os.path.join(BASE_DIR, "data", "device_history.json")

# Flag per terminazione pulita
shutdown_event = Event()

# =============================================================
# CARICA VARIABILI D'AMBIENTE
# ==============================================================

if os.path.exists(os.path.join(BASE_DIR, ".env")):
    from dotenv import load_dotenv
    load_dotenv(os.path.join(BASE_DIR, ".env"))

APP_ID = os.environ.get("SONOFF_APP_ID", "lYPkZywzOtbxsMRNWJvhgCyXBDptIjOo")
APP_SECRET = os.environ.get("SONOFF_APP_SECRET", "mdPR25XfesDAiaB3pQbxWEklWT1EeK7v")
CONVEX_URL = os.getenv("CONVEX_URL", "https://striped-shrimp-908.convex.cloud")
if "convex.cloud" in CONVEX_URL:
    CONVEX_URL = CONVEX_URL.replace("convex.cloud", "convex.site")

if not APP_ID or not APP_SECRET or not CONVEX_URL:
    raise ValueError(
        "Le variabili d'ambiente SONOFF_APP_ID, SONOFF_APP_SECRET e CONVEX_URL devono essere configurate"
    )
