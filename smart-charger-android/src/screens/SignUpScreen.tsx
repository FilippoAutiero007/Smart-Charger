import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import { useAuthManager } from '../hooks/useAuthManager';
import { BaseInput } from '../components/BaseInput';
import { BaseButton } from '../components/BaseButton';
import theme from '../theme/theme';

export const SignUpScreen = ({ navigation }: any) => {
  const { signUp, setSignUpActive, isSignUpLoaded } = useAuthManager();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [pendingVerification, setPendingVerification] = useState(false);
  const [loading, setLoading] = useState(false);

  const onSignUp = async () => {
    if (!isSignUpLoaded || !signUp) return;
    if (!email || !password) {
      Alert.alert('Errore', 'Inserisci email e password');
      return;
    }

    setLoading(true);
    try {
      await signUp.create({ emailAddress: email, password });
      await signUp.prepareEmailAddressVerification({ strategy: 'email_code' });
      setPendingVerification(true);
    } catch (err: any) {
      Alert.alert('Errore di Registrazione', err.errors?.[0]?.message || 'Registrazione fallita');
    } finally {
      setLoading(false);
    }
  };

  const onVerify = async () => {
    if (!isSignUpLoaded || !signUp) return;
    if (!code) {
      Alert.alert('Errore', 'Inserisci il codice di verifica');
      return;
    }

    setLoading(true);
    try {
      const result = await signUp.attemptEmailAddressVerification({ code });
      if (result.status === 'complete' && result.createdSessionId) {
        await setSignUpActive?.({ session: result.createdSessionId });
      } else {
        console.warn('Verification status not complete:', result.status);
      }
    } catch (err: any) {
      Alert.alert('Errore di Verifica', err.errors?.[0]?.message || 'Verifica fallita');
    } finally {
      setLoading(false);
    }
  };

  if (pendingVerification) {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Verifica Email</Text>
          <Text style={styles.subtitle}>Inserisci il codice inviato a {email}</Text>
        </View>
        <View style={styles.form}>
          <BaseInput 
            label="Codice" 
            placeholder="000000" 
            value={code} 
            onChangeText={setCode} 
            keyboardType="number-pad" 
            maxLength={6} 
          />
          <BaseButton title="Verifica" onPress={onVerify} loading={loading} />
          <BaseButton 
            title="Indietro" 
            onPress={() => setPendingVerification(false)} 
            variant="outline" 
          />
        </View>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView 
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.title}>Crea Account</Text>
          <Text style={styles.subtitle}>Inizia a ottimizzare la tua ricarica</Text>
        </View>

        <View style={styles.form}>
          <BaseInput 
            label="Email" 
            placeholder="esempio@email.it" 
            value={email} 
            onChangeText={setEmail} 
            keyboardType="email-address" 
            autoCapitalize="none" 
          />
          <BaseInput 
            label="Password" 
            placeholder="Minimo 8 caratteri" 
            value={password} 
            onChangeText={setPassword} 
            secureTextEntry 
          />
          
          <BaseButton 
            title="Registrati" 
            onPress={onSignUp} 
            loading={loading} 
          />
          
          <View style={styles.footer}>
            <Text style={styles.footerText}>Hai già un account?</Text>
            <BaseButton 
              title="Accedi" 
              onPress={() => navigation.navigate('SignIn')} 
              variant="outline" 
            />
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF', padding: theme.spacing.xl, justifyContent: 'center' },
  scrollContent: { flexGrow: 1, justifyContent: 'center' },
  header: { marginBottom: theme.spacing.xl, alignItems: 'center' },
  title: { fontSize: 32, fontWeight: 'bold', color: '#1C1C1E', marginBottom: theme.spacing.xs },
  subtitle: { fontSize: 16, color: '#8E8E93', textAlign: 'center' },
  form: { width: '100%' },
  footer: { marginTop: theme.spacing.xl, alignItems: 'center' },
  footerText: { fontSize: 14, color: '#8E8E93', marginBottom: theme.spacing.xs },
});
