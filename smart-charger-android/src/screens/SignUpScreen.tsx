import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert } from 'react-native';
import { useSignUp } from '@clerk/clerk-expo';
import { AppleInput } from '../components/AppleInput';
import { AppleButton } from '../components/AppleButton';
import theme from '../theme/theme';

export const SignUpScreen = ({ navigation }: any) => {
  const { signUp, setActive, isLoaded } = useSignUp();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [pendingVerification, setPendingVerification] = useState(false);
  const [loading, setLoading] = useState(false);

  const onSignUp = async () => {
    if (!isLoaded) return;
    setLoading(true);
    try {
      await signUp.create({ emailAddress: email, password });
      await signUp.prepareEmailAddressVerification({ strategy: 'email_code' });
      setPendingVerification(true);
    } catch (err: any) {
      Alert.alert('Error', err.errors?.[0]?.message || 'Sign up failed');
    } finally {
      setLoading(false);
    }
  };

  const onVerify = async () => {
    if (!isLoaded) return;
    setLoading(true);
    try {
      const result = await signUp.attemptEmailAddressVerification({ code });
      await setActive({ session: result.createdSessionId });
    } catch (err: any) {
      Alert.alert('Error', err.errors?.[0]?.message || 'Verification failed');
    } finally {
      setLoading(false);
    }
  };

  if (pendingVerification) {
    return (
      <View style={styles.container}>
        <Text style={styles.title}>Verify Email</Text>
        <Text style={styles.subtitle}>Enter code sent to {email}</Text>
        <AppleInput label="Code" placeholder="000000" value={code} onChangeText={setCode} keyboardType="number-pad" maxLength={6} />
        <AppleButton title="Verify" onPress={onVerify} loading={loading} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Create Account</Text>
      <Text style={styles.subtitle}>Join Smart Charger</Text>
      <AppleInput label="Email" placeholder="your@email.com" value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" />
      <AppleInput label="Password" placeholder="Min 8 characters" value={password} onChangeText={setPassword} secureTextEntry />
      <AppleButton title="Sign Up" onPress={onSignUp} loading={loading} />
      <AppleButton title="Already have account?" onPress={() => navigation.navigate('SignIn')} variant="outline" />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: theme.spacing.xl, backgroundColor: '#FFF' },
  title: { fontSize: 32, fontWeight: 'bold', marginBottom: theme.spacing.sm, textAlign: 'center' },
  subtitle: { fontSize: 16, color: '#8E8E93', marginBottom: theme.spacing.xl, textAlign: 'center' },
});
