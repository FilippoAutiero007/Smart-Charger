import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert, ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import { useMutation } from 'convex/react';
import { api } from '../convex-shim';
import { SHA256 } from 'crypto-js';
import { useNavigation } from '@react-navigation/native';
import { StatusBar } from 'expo-status-bar';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { Colors, Spacing } from '../theme/theme';
import { GlassInput } from '../components/GlassInput';
import { GlassButton } from '../components/GlassButton';

export default function SignupScreen() {
    const navigation = useNavigation<any>();
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [loading, setLoading] = useState(false);

    const createUser = useMutation("users:create" as any);

    const handleSignup = async () => {
        if (!username || !email || !password || !confirmPassword) {
            Alert.alert('Error', 'Please fill in all fields');
            return;
        }

        if (password !== confirmPassword) {
            Alert.alert('Error', 'Passwords do not match');
            return;
        }

        setLoading(true);
        try {
            const hashedPassword = SHA256(password).toString();

            await createUser({
                name: username,
                email: email,
                password: hashedPassword,
            });

            Alert.alert('Success', 'Account created! Please login.', [
                { text: 'OK', onPress: () => navigation.navigate('Login') }
            ]);
        } catch (e: any) {
            console.error(e);
            Alert.alert('Error', e.message || 'Failed to create account');
        } finally {
            setLoading(false);
        }
    };

    return (
        <LinearGradient
            colors={Colors.background === '#0F172A' ? ['#0F172A', '#1E293B', '#000000'] : [Colors.background, Colors.background]}
            style={styles.container}
        >
            <StatusBar style="light" />
            <KeyboardAvoidingView
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                style={{ flex: 1 }}
            >
                <ScrollView contentContainerStyle={styles.scrollContent}>
                    <View style={styles.header}>
                        <View style={styles.iconContainer}>
                            <Ionicons name="person-add" size={32} color={Colors.primary} />
                        </View>
                        <Text style={styles.title}>Create Account</Text>
                        <Text style={styles.subtitle}>Join Smart Charger today</Text>
                    </View>

                    <View style={styles.form}>
                        <GlassInput
                            label="Username"
                            value={username}
                            onChangeText={setUsername}
                            placeholder="Choose a username"
                            autoCapitalize="none"
                        />
                        <GlassInput
                            label="Email"
                            value={email}
                            onChangeText={setEmail}
                            placeholder="name@example.com"
                            keyboardType="email-address"
                            autoCapitalize="none"
                        />
                        <GlassInput
                            label="Password"
                            value={password}
                            onChangeText={setPassword}
                            placeholder="Create a password"
                            secureTextEntry
                        />
                        <GlassInput
                            label="Confirm Password"
                            value={confirmPassword}
                            onChangeText={setConfirmPassword}
                            placeholder="Confirm your password"
                            secureTextEntry
                        />

                        <GlassButton
                            title={loading ? "Creating Account..." : "Sign Up"}
                            onPress={handleSignup}
                            loading={loading}
                            style={styles.signUpButton}
                        />

                        <GlassButton
                            title="Back to Login"
                            onPress={() => navigation.navigate('Login')}
                            colors={['transparent', 'transparent']}
                            textStyle={{ color: Colors.primary }}
                            style={{ marginTop: Spacing.s, elevation: 0 }}
                        />
                    </View>
                </ScrollView>
            </KeyboardAvoidingView>
        </LinearGradient>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    scrollContent: {
        flexGrow: 1,
        justifyContent: 'center',
        padding: Spacing.l,
    },
    header: {
        alignItems: 'center',
        marginBottom: Spacing.xl,
    },
    iconContainer: {
        width: 64,
        height: 64,
        borderRadius: 32,
        backgroundColor: Colors.glass,
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: Spacing.m,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
    },
    title: {
        fontSize: 28,
        fontWeight: 'bold',
        color: Colors.text,
        marginBottom: Spacing.s,
    },
    subtitle: {
        fontSize: 16,
        color: Colors.textSecondary,
    },
    form: {
        width: '100%',
    },
    signUpButton: {
        marginTop: Spacing.m,
    },
});
