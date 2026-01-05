import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert } from 'react-native';
import { useSignIn } from '@clerk/clerk-expo';
import { AppleInput } from '../components/AppleInput';
import { AppleButton } from '../components/AppleButton';
import theme from '../theme/theme';

export const SignInScreen = ({ navigation }: any) => {
  const { signIn, setActive, isLoaded } = useSignIn();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const onSignIn = async () => {
    if (!isLoaded) return;
    setLoading(true);
    try {
      const result = await signIn.create({ identifier: email, password });
      await setActive({ session: result.createdSessionId });
    } catch (err: any) {
      Alert.alert('Error', err.errors?.[0]?.message || 'Sign in failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Welcome Back</Text>
      <Text style={styles.subtitle}>Sign in to continue</Text>
      <AppleInput label="Email" placeholder="your@email.com" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" />
      <AppleInput label="Password" placeholder="Enter password" value={password} onChangeText={setPassword} secureTextEntry />
      <AppleButton title="Sign In" onPress={onSignIn} loading={loading} />
      <AppleButton title="Create Account" onPress={() => navigation.navigate('SignUp')} variant="outline" />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: theme.spacing.xl, backgroundColor: '#FFF' },
  title: { fontSize: 32, fontWeight: 'bold', marginBottom: theme.spacing.sm, textAlign: 'center' },
  subtitle: { fontSize: 16, color: '#8E8E93', marginBottom: theme.spacing.xl, textAlign: 'center' },
});
