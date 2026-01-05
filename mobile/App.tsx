import React from 'react';
import { ActivityIndicator, View, StatusBar as RNStatusBar } from 'react-native';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { AuthProvider, useAuth } from './src/context/AuthContext';
import { RootStackParamList } from './src/types';
import { Colors } from './src/theme/theme';

import LoginScreen from './src/screens/LoginScreen';
import SignupScreen from './src/screens/SignupScreen';
import DashboardScreen from './src/screens/DashboardScreen';
import DeviceControlScreen from './src/screens/DeviceControlScreen';
import EwelinkLoginScreen from './src/screens/EwelinkLoginScreen';

import ProfileScreen from './src/screens/ProfileScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

const PremiumTheme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    background: Colors.background,
    primary: Colors.primary,
    card: Colors.glass,
    text: Colors.text,
    border: Colors.glassBorder,
  },
};

function Navigation() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: Colors.background }}>
        <ActivityIndicator size="large" color={Colors.primary} />
      </View>
    );
  }

  return (
    <NavigationContainer theme={PremiumTheme}>
      <Stack.Navigator
        screenOptions={{
          headerStyle: {
            backgroundColor: Colors.background,
          },
          headerTintColor: Colors.text,
          headerTitleStyle: {
            fontWeight: 'bold',
          },
          headerShadowVisible: false,
        }}
      >
        {isAuthenticated ? (
          <>
            <Stack.Screen
              name="Dashboard"
              component={DashboardScreen}
              options={{ title: 'Smart Charger' }}
            />
            <Stack.Screen
              name="DeviceControl"
              component={DeviceControlScreen}
              options={{ title: 'Device Control' }}
            />
            <Stack.Screen
              name="Profile"
              component={ProfileScreen}
              options={{ headerShown: false }}
            />
          </>
        ) : (
          <>
            <Stack.Screen
              name="Login"
              component={LoginScreen}
              options={{ headerShown: false }}
            />
            <Stack.Screen
              name="Signup"
              component={SignupScreen}
              options={{ headerShown: false }}
            />
            <Stack.Screen
              name="EwelinkLogin"
              component={EwelinkLoginScreen}
              options={{ headerShown: false }}
            />
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}

import { ConvexProvider, ConvexReactClient } from 'convex/react';
import { convex } from './src/services/convex';

export default function App() {
  return (
    <ConvexProvider client={convex}>
      <AuthProvider>
        <Navigation />
      </AuthProvider>
    </ConvexProvider>
  );
}
