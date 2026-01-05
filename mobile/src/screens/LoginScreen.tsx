import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Alert, ActivityIndicator, Image, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import * as Google from 'expo-auth-session/providers/google';
import { makeRedirectUri } from 'expo-auth-session';
import { useAuth } from '../context/AuthContext';
import { StatusBar } from 'expo-status-bar';
import { useNavigation } from '@react-navigation/native';
import { useAction } from 'convex/react';
import { SHA256 } from 'crypto-js';
import { LinearGradient } from 'expo-linear-gradient';
import { Colors, Spacing, BorderRadius } from '../theme/theme';
import { GlassInput } from '../components/GlassInput';
import { GlassButton } from '../components/GlassButton';
import { Ionicons } from '@expo/vector-icons';

WebBrowser.maybeCompleteAuthSession();

export default function LoginScreen() {
    const navigation = useNavigation<any>();
    const { login } = useAuth();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);

    // Convex actions
    const loginAction = useAction("auth:login" as any);
    const googleLoginAction = useAction("auth:googleLogin" as any);

    const redirectUri = makeRedirectUri({
        scheme: 'com.smartcharger.app',
        path: 'auth'
    });

    console.log("📢 GOOGLE REDIRECT URI:", redirectUri);

    const [request, response, promptAsync] = Google.useAuthRequest({
        androidClientId: '109571178360-sl36ub7l2rev2g735085a4ldf71mpa6i.apps.googleusercontent.com',
        webClientId: '109571178360-sl36ub7l2rev2g735085a4ldf71mpa6i.apps.googleusercontent.com',
        redirectUri,
        scopes: ['openid', 'profile', 'email'],
    });


    useEffect(() => {
        if (response?.type === 'success') {
            const { authentication } = response;
            handleGoogleLogin(authentication?.accessToken);
        } else if (response?.type === 'error') {
            Alert.alert('Google Sign-In Error', 'Could not sign in with Google.');
        }
    }, [response]);

    const handleGoogleLogin = async (token?: string) => {
        if (!token) return;
        setLoading(true);
        try {
            // 1. Fetch User Info from Google
            const userInfoResponse = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
                headers: { Authorization: `Bearer ${token}` },
            });

            if (!userInfoResponse.ok) {
                const errorText = await userInfoResponse.text();
                throw new Error(`Google UserInfo Failed: ${userInfoResponse.status} - ${errorText}`);
            }

            const user = await userInfoResponse.json();

            // 2. Auth with Backend (Convex)
            const result = await googleLoginAction({ token });
            if (result && result.token) {
                await login(result.token, {
                    name: user.name,
                    email: user.email,
                    photoUrl: user.picture
                });
            } else {
                Alert.alert('Error', 'Google login failed on backend. Check server logs.');
            }
        } catch (e: any) {
            console.error("Google Login Error:", e);
            Alert.alert('Login Error', e.message);
        } finally {
            setLoading(false);
        }
    };

    const handleLogin = async () => {
        if (!email || !password) {
            Alert.alert('Error', 'Please enter email and password');
            return;
        }

        setLoading(true);
        try {
            const hashedPassword = SHA256(password).toString();
            const result = await loginAction({
                email,
                password: hashedPassword
            });

            if (result) {
                // Generate premium avatar for manual users
                const name = email.split('@')[0];
                const photoUrl = `https://ui-avatars.com/api/?name=${name}&background=random&color=fff&size=200`;

                await login(result, {
                    name: name,
                    email: email,
                    photoUrl: photoUrl
                });
            } else {
                Alert.alert('Error', 'Invalid credentials');
            }
        } catch (e: any) {
            console.error(e);
            Alert.alert('Error', 'Login failed: ' + e.message);
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
                            <Ionicons name="flash" size={40} color={Colors.primary} />
                        </View>
                        <Text style={styles.title}>Smart Charger</Text>
                        <Text style={styles.subtitle}>Welcome back!</Text>
                    </View>

                    <View style={styles.form}>
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
                            placeholder="••••••••"
                            secureTextEntry
                        />

                        <GlassButton
                            title={loading ? "Verifying..." : "Sign In"}
                            onPress={handleLogin}
                            loading={loading}
                            style={styles.signInButton}
                        />

                        <View style={styles.divider}>
                            <View style={styles.line} />
                            <Text style={styles.dividerText}>or continue with</Text>
                            <View style={styles.line} />
                        </View>

                        <GlassButton
                            title="Login with eWeLink"
                            onPress={() => navigation.navigate('EwelinkLogin')}
                            colors={['#1890FF', '#096DD9']} // eWeLink Blue
                            icon={<Ionicons name="link" size={20} color="#fff" />}
                            style={{ marginBottom: Spacing.m }}
                        />

                        <GlassButton
                            title="Sign in with Google"
                            onPress={() => promptAsync()}
                            disabled={!request}
                            colors={['#DB4437', '#C53929']} // Google Red Gradient
                            icon={<Ionicons name="logo-google" size={20} color="#fff" />}
                        />

                        <GlassButton
                            title="Create Account"
                            onPress={() => navigation.navigate('Signup')}
                            colors={['transparent', 'transparent']} // Text only look
                            textStyle={{ color: Colors.primary }}
                            style={{ marginTop: Spacing.m, elevation: 0 }}
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
        width: 80,
        height: 80,
        borderRadius: 40,
        backgroundColor: Colors.glass,
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: Spacing.m,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
    },
    title: {
        fontSize: 32,
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
    signInButton: {
        marginTop: Spacing.m,
    },
    divider: {
        flexDirection: 'row',
        alignItems: 'center',
        marginVertical: Spacing.xl,
    },
    line: {
        flex: 1,
        height: 1,
        backgroundColor: Colors.glassBorder,
    },
    dividerText: {
        color: Colors.textSecondary,
        marginHorizontal: Spacing.m,
        fontSize: 14,
    },
});
