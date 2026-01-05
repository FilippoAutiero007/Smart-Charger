# 🔋 Smart Charger - Android App

Applicazione Android con design Apple-inspired per monitoraggio batteria e controllo Sonoff.

## ✨ Features
- 🔐 Clerk Authentication (SignIn/SignUp/Verify)
- 🎨 Apple-inspired UI (iOS colors, gradient buttons)
- 📱 3 Screens (SignIn, SignUp, Dashboard)
- 🔋 Battery monitoring dashboard
- 📊 Statistics display

## 🚀 Quick Start

### 1. Install
```bash
cd smart-charger-android
npm install
```

### 2. Configure Clerk
Create `.env.local`:
```env
EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_YOUR_KEY
```
Get key from: https://dashboard.clerk.com

### 3. Run
```bash
npm start
# Press 'a' for Android
```

## 📁 Structure
```
src/
├── components/  (AppleButton, AppleInput)
├── screens/     (SignIn, SignUp, Dashboard)
├── navigation/  (RootNavigator with auth flow)
├── services/    (tokenCache - SecureStore)
├── theme/       (Design system)
└── types/       (TypeScript definitions)
```

## 🎨 Design
- Primary: #007AFF (iOS Blue)
- Success: #34C759
- Error: #FF3B30

## 🔧 Tech Stack
- React Native + Expo
- TypeScript
- Clerk Auth
- React Navigation v7
- Linear Gradient

## 📄 License
MIT
