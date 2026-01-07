import React from 'react';
import { ClerkProvider } from '@clerk/clerk-expo';
import { tokenCache } from './src/services/tokenCache';
import { RootNavigator } from './src/navigation';
import Constants from 'expo-constants';

const publishableKey = Constants.expoConfig?.extra?.clerkPublishableKey;
if (!publishableKey) throw new Error('Missing Clerk publishable key in expoConfig.extra');

export default function App() {
  return (
    <ClerkProvider publishableKey={publishableKey} tokenCache={tokenCache}>
      <RootNavigator />
    </ClerkProvider>
  );
}
