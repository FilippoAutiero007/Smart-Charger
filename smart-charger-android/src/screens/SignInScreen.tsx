import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import { useAuthManager } from '../hooks/useAuthManager';
import { BaseInput } from '../components/BaseInput';
import { BaseButton } from '../components/BaseButton';
import theme from '../theme/theme';

export const SignInScreen = ({ navigation }: any) => {
  const { signIn, setSignInActive, isSignInLoaded } = useAuthManager();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const onSignIn = async () => {
    if (!isSignInLoaded) return;
    if (!email || !password) {
      Alert.alert('Errore', 'Inserisci email e password');
      return;
    }

    setLoading(true);
    try {
      const result = await signIn.create({ identifier: email, password });
      if (result.status === 'complete') {
        await setSignInActive({ session: result.createdSessionId });
      } else {
        console.warn('Sign in status not complete:', result.status);
      }
    } catch (err: any) {
      Alert.alert('Errore di Accesso', err.errors?.[0]?.message || 'Accesso fallito');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView 
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.title}>Bentornato</Text>
          <Text style={styles.subtitle}>Accedi per gestire la tua ricarica</Text>
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
            placeholder="Inserisci la tua password" 
            value={password} 
            onChangeText={setPassword} 
            secureTextEntry 
          />
          
          <BaseButton 
            title="Accedi" 
            onPress={onSignIn} 
            loading={loading} 
          />
          
          <View style={styles.footer}>
            <Text style={styles.footerText}>Non hai un account?</Text>
            <BaseButton 
              title="Registrati" 
              onPress={() => navigation.navigate('SignUp')} 
              variant="outline" 
            />
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFF' },
  scrollContent: { flexGrow: 1, justifyContent: 'center', padding: theme.spacing.xl },
  header: { marginBottom: theme.spacing.xl, alignItems: 'center' },
  title: { fontSize: 32, fontWeight: 'bold', color: '#1C1C1E', marginBottom: theme.spacing.xs },
  subtitle: { fontSize: 16, color: '#8E8E93', textAlign: 'center' },
  form: { width: '100%' },
  footer: { marginTop: theme.spacing.xl, alignItems: 'center' },
  footerText: { fontSize: 14, color: '#8E8E93', marginBottom: theme.spacing.xs },
});
