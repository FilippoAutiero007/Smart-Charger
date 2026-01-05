import time
import sys
import os
from modules.utils import logger, shutdown_event, APP_ID, APP_SECRET
from modules.sonoff_controller import SonoffController
from modules.battery_monitor import BatteryMonitor

# Configuration
CONFIG = {
    "min_battery": 20,
    "max_battery": 80,
    "check_interval": 30,
    "device_id": None
}

def main():
    print("\n🔋 SMART CHARGER - LAPTOP AGENT")
    print("===============================")
    
    # 1. Initialize Controller
    controller = SonoffController(APP_ID, APP_SECRET)
    
    # 2. Check Tokens or Refresh
    if not controller.access_token:
        print("\n❌ No tokens found.")
        print("💡 Please run 'python setup.py' to log in.")
        return
    elif not controller.refresh_access_token():
         print("\n❌ Token expired and refresh failed.")
         print("💡 Please run 'python setup.py' to log in again.")
         return

    # 3. Select Device (if not cached or config)
    # Ideally reuse last used device or ask user
    # For now, let's list devices and pick first one if not set
    if not controller.device_id:
        print("\nFetching devices...")
        devices = controller.get_all_devices()
        if not devices:
            print("No devices found.")
            return
        
        # Simple selection: Pick the first one for now or ask user
        # Let's try to load from config if we had one.
        # But for this simple script, let's just pick the first one and notify.
        device = devices[0]
        controller.set_device_id(device["id"], device["name"])
        CONFIG["device_id"] = device["id"]
        print(f"Selected Device: {device['name']} ({device['id']})")
    
    # 4. Start Monitor
    try:
        BatteryMonitor.start_monitoring(controller, CONFIG)
    except KeyboardInterrupt:
        print("\nStopped.")

if __name__ == "__main__":
    main()
