import React from 'react';
import { ClerkProvider } from '@clerk/clerk-expo';
import { tokenCache } from './src/services/tokenCache';
import { RootNavigator } from './src/navigation';

const publishableKey = process.env.EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY!;
if (!publishableKey) throw new Error('Missing EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY in .env.local');

export default function App() {
  return (
    <ClerkProvider publishableKey={publishableKey} tokenCache={tokenCache}>
      <RootNavigator />
    </ClerkProvider>
  );
}
