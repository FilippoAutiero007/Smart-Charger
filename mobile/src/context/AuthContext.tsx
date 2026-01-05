import React, { createContext, useState, useContext, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';
import { AuthTokens } from '../types';

interface UserProfile {
    name?: string;
    email?: string;
    photoUrl?: string;
}

interface AuthContextType {
    user: UserProfile | null;
    tokens: AuthTokens | null;
    userId: string | null;
    isLoading: boolean;
    login: (tokens: AuthTokens, userProfile?: UserProfile) => Promise<void>;
    logout: () => Promise<void>;
    isAuthenticated: boolean;
    updateUser: (profile: UserProfile) => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [tokens, setTokens] = useState<AuthTokens | null>(null);
    const [user, setUser] = useState<UserProfile | null>(null);
    const [userId, setUserId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        checkLogin();
    }, []);

    const checkLogin = async () => {
        try {
            const accessToken = await SecureStore.getItemAsync('access_token');
            const refreshToken = await SecureStore.getItemAsync('refresh_token');
            const region = await SecureStore.getItemAsync('region') || 'eu';
            const storedUserId = await SecureStore.getItemAsync('user_id');

            // Load user profile if exists
            const userStr = await SecureStore.getItemAsync('user_profile');
            if (userStr) {
                setUser(JSON.parse(userStr));
            }

            if (accessToken && refreshToken) {
                setTokens({
                    accessToken,
                    refreshToken,
                    atExpiredTime: 0,
                    rtExpiredTime: 0,
                    region,
                    userId: storedUserId || undefined
                });
                if (storedUserId) setUserId(storedUserId);
            }
        } catch (e) {
            console.error('Failed to load tokens', e);
        } finally {
            setIsLoading(false);
        }
    };

    const login = async (newTokens: AuthTokens, userProfile?: UserProfile) => {
        try {
            await SecureStore.setItemAsync('access_token', newTokens.accessToken);
            await SecureStore.setItemAsync('refresh_token', newTokens.refreshToken);
            await SecureStore.setItemAsync('region', newTokens.region);
            if (newTokens.userId) {
                await SecureStore.setItemAsync('user_id', newTokens.userId);
                setUserId(newTokens.userId);
            }
            setTokens(newTokens);

            if (userProfile) {
                await updateUser(userProfile);
            }
        } catch (e) {
            console.error('Failed to save tokens', e);
        }
    };

    const updateUser = async (profile: UserProfile) => {
        try {
            await SecureStore.setItemAsync('user_profile', JSON.stringify(profile));
            setUser(profile);
        } catch (e) {
            console.error('Failed to save user profile', e);
        }
    }

    const logout = async () => {
        try {
            await SecureStore.deleteItemAsync('access_token');
            await SecureStore.deleteItemAsync('refresh_token');
            await SecureStore.deleteItemAsync('region');
            await SecureStore.deleteItemAsync('user_profile');
            await SecureStore.deleteItemAsync('user_id');
            setTokens(null);
            setUser(null);
            setUserId(null);
        } catch (e) {
            console.error('Failed to remove tokens', e);
        }
    };

    return (
        <AuthContext.Provider value={{
            user,
            tokens,
            userId,
            isLoading,
            login,
            logout,
            isAuthenticated: !!tokens,
            updateUser
        }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
