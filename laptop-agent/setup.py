import sys
import os
from modules.utils import logger, APP_ID, APP_SECRET
from modules.sonoff_controller import SonoffController
from modules.auth_flow import login_with_cloud_code

def main():
    print("\n🔧 SMART CHARGER - SETUP")
    print("========================")
    print("This script will help you log in to your Sonoff/eWeLink account.")
    
    controller = SonoffController(APP_ID, APP_SECRET)
    
    if controller.access_token:
        print("\n✅ Existing tokens found.")
        print("Checking validity...")
        if controller.refresh_access_token():
            print("Token is valid! You don't need to log in again.")
            print("Run 'python main.py' to start the monitor.")
            return

    print("\n⚠️ Authenticating...")
    if login_with_cloud_code():
        print("\n✅ Setup Complete!")
        print("You can now run 'python main.py' to start the battery monitor.")
    else:
         print("\n❌ Setup Failed. Please try again.")

if __name__ == "__main__":
    main()
