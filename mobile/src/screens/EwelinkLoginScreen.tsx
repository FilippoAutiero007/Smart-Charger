import React, { useState } from 'react';
import { View, Text, StyleSheet, Alert, ActivityIndicator, Linking } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { StatusBar } from 'expo-status-bar';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useAction } from 'convex/react';

import { Colors, Spacing } from '../theme/theme';
import { GlassInput } from '../components/GlassInput';
import { GlassButton } from '../components/GlassButton';
import { useAuth } from '../context/AuthContext';

export default function EwelinkLoginScreen() {
    const navigation = useNavigation();
    const { login } = useAuth();
    const [code, setCode] = useState('');
    const [loading, setLoading] = useState(false);

    // Backend action to exchange code for tokens
    // We can reuse a similar action or create a new one. 
    // Assuming we need a new action or reuse an existing one exposed via API.
    // For now, let's assume we call a Convex action that handles the exchange.
    // Since `auth_flow.py` calls `/api/token` which is an HTTP endpoint, 
    // we should ideally use a Convex Action `auth:ewelinkLogin` that does the same logic.
    // If it doesn't exist, we will create it. I check that next.
    // For now, I'll assume `auth:ewelinkLogin` exists or I'll add it.
    const ewelinkLoginAction = useAction("auth:ewelinkLogin" as any);

    const convexUrl = "https://striped-shrimp-908.convex.site"; // Using .site for HTTP endpoints usually, but login page is there.

    const openLoginBrowser = () => {
        const url = `${convexUrl}/my-login`;
        Linking.openURL(url);
    };

    const handleLogin = async () => {
        if (!code || code.length !== 6) {
            Alert.alert('Invalid Code', 'Please enter the 6-character code from the browser.');
            return;
        }

        setLoading(true);
        try {
            const result = await ewelinkLoginAction({ code });

            if (result && result.token) {
                await login(result.token, {
                    name: result.user?.email?.split('@')[0] || "User",
                    email: result.user?.email,
                });
                // Login handles navigation usually, or we go back
                // navigation.navigate('Dashboard'); 
            } else {
                Alert.alert('Login Failed', result?.error || 'Unknown error');
            }
        } catch (e: any) {
            console.error("eWeLink Login Error:", e);
            Alert.alert('Error', e.message || 'Failed to login');
        } finally {
            setLoading(false);
        }
    };

    return (
        <LinearGradient
            colors={Colors.background === '#0F172A' ? ['#0F172A', '#1E293B'] : [Colors.background, Colors.background]}
            style={styles.container}
        >
            <StatusBar style="light" />
            <View style={styles.content}>
                <View style={styles.header}>
                    <View style={styles.iconContainer}>
                        <Ionicons name="link" size={32} color={Colors.primary} />
                    </View>
                    <Text style={styles.title}>eWeLink Login</Text>
                    <Text style={styles.subtitle}>Copy the code from the browser</Text>
                </View>

                <View style={styles.stepContainer}>
                    <View style={styles.stepNumber}><Text style={styles.stepText}>1</Text></View>
                    <View style={{ flex: 1 }}>
                        <Text style={styles.instruction}>Open the eWeLink login page in your browser.</Text>
                        <GlassButton
                            title="Open Browser"
                            onPress={openLoginBrowser}
                            style={{ marginTop: 8, height: 40 }}
                            textStyle={{ fontSize: 14 }}
                        />
                    </View>
                </View>

                <View style={styles.stepContainer}>
                    <View style={styles.stepNumber}><Text style={styles.stepText}>2</Text></View>
                    <View style={{ flex: 1 }}>
                        <Text style={styles.instruction}>Enter the 6-character code you received.</Text>
                        <GlassInput
                            value={code}
                            onChangeText={(text) => setCode(text.toUpperCase())}
                            placeholder="ex. ABC123"
                            autoCapitalize="characters"
                            maxLength={6}
                            containerStyle={{ marginTop: 8 }}
                        />
                    </View>
                </View>

                <GlassButton
                    title={loading ? "Verifying..." : "Verify & Login"}
                    onPress={handleLogin}
                    loading={loading}
                    style={{ marginTop: Spacing.xl }}
                />

                <GlassButton
                    title="Cancel"
                    onPress={() => navigation.goBack()}
                    colors={['transparent', 'transparent']}
                    textStyle={{ color: Colors.textSecondary }}
                    style={{ marginTop: Spacing.m, elevation: 0 }}
                />
            </View>
        </LinearGradient>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        padding: Spacing.l,
        justifyContent: 'center',
    },
    content: {
        width: '100%',
    },
    header: {
        alignItems: 'center',
        marginBottom: Spacing.xl,
    },
    iconContainer: {
        width: 64,
        height: 64,
        borderRadius: 32,
        backgroundColor: 'rgba(24, 144, 255, 0.2)', // eWeLink Blue transparent
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: Spacing.m,
        borderWidth: 1,
        borderColor: '#1890FF',
    },
    title: {
        fontSize: 24,
        fontWeight: 'bold',
        color: Colors.text,
        marginBottom: 4,
    },
    subtitle: {
        fontSize: 14,
        color: Colors.textSecondary,
    },
    stepContainer: {
        flexDirection: 'row',
        marginBottom: Spacing.l,
    },
    stepNumber: {
        width: 28,
        height: 28,
        borderRadius: 14,
        backgroundColor: Colors.glass,
        justifyContent: 'center',
        alignItems: 'center',
        marginRight: Spacing.m,
        marginTop: 2,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
    },
    stepText: {
        color: Colors.primary,
        fontWeight: 'bold',
    },
    instruction: {
        color: Colors.text,
        fontSize: 16,
        lineHeight: 24,
    },
});
