import React from 'react';
import { ClerkProvider } from '@clerk/clerk-expo';
import { tokenCache } from './src/services/tokenCache';
import { RootNavigator } from './src/navigation';
import { CLERK_PUBLISHABLE_KEY } from './src/constants/Config';
import { View, Text, StyleSheet } from 'react-native';

export default function App() {
  if (!CLERK_PUBLISHABLE_KEY) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorTitle}>Configurazione Mancante</Text>
        <Text style={styles.errorText}>
          La chiave EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY non è stata trovata. 
          Controlla il tuo file .env o la configurazione di Expo.
        </Text>
      </View>
    );
  }

  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY} tokenCache={tokenCache}>
      <RootNavigator />
    </ClerkProvider>
  );
}

const styles = StyleSheet.create({
  errorContainer: { 
    flex: 1, 
    justifyContent: 'center', 
    alignItems: 'center', 
    padding: 20,
    backgroundColor: '#FFF'
  },
  errorTitle: { 
    fontSize: 20, 
    fontWeight: 'bold', 
    color: '#FF3B30', 
    marginBottom: 10 
  },
  errorText: { 
    fontSize: 16, 
    textAlign: 'center', 
    color: '#8E8E93' 
  },
});
