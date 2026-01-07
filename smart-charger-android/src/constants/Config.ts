import Constants from 'expo-constants';

export const CLERK_PUBLISHABLE_KEY = Constants.expoConfig?.extra?.clerkPublishableKey;

if (!CLERK_PUBLISHABLE_KEY) {
  console.warn('Missing Clerk publishable key in expoConfig.extra');
}

export const API_URL = process.env.EXPO_PUBLIC_API_URL || 'https://api.smartcharger.com';
