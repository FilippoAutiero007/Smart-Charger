import React, { useState, useCallback, useLayoutEffect } from 'react';
import { View, Text, FlatList, StyleSheet, RefreshControl, TouchableOpacity, ActivityIndicator, Image } from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { StatusBar } from 'expo-status-bar';
import { useFocusEffect } from '@react-navigation/native';
import { useQuery } from 'convex/react';
import { SonoffDevice, RootStackParamList } from '../types';
import { useAuth } from '../context/AuthContext';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { BlurView } from 'expo-blur';
import { Colors, Spacing, BorderRadius } from '../theme/theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Dashboard'>;

export default function DashboardScreen({ navigation }: Props) {
    const { logout, user, userId } = useAuth();

    // Convex Query - realtime updates
    // We pass userId explicitly. If null, query might return empty or wait.
    const devices = useQuery("devices:getUserDevices" as any, { userId: userId || "" });
    const isLoading = devices === undefined;

    const onRefresh = useCallback(() => {
        // Convex updates automatically, but we can simulate refresh feel
    }, []);

    useLayoutEffect(() => {
        navigation.setOptions({
            headerRight: () => (
                <View style={{ flexDirection: 'row', gap: 16 }}>
                    <TouchableOpacity onPress={() => {/* FUTURE: Navigate to add device logic? or just show info */ }} style={styles.iconButton}>
                        <Ionicons name="add" size={24} color={Colors.primary} />
                    </TouchableOpacity>
                    <TouchableOpacity onPress={logout} style={styles.iconButton}>
                        <Ionicons name="log-out-outline" size={24} color={Colors.primary} />
                    </TouchableOpacity>
                </View>
            ),
            headerTitle: "", // Hide default title
            headerTransparent: true,
            headerBackground: () => (
                <BlurView tint="dark" intensity={80} style={StyleSheet.absoluteFill} />
            ),
        });
    }, [navigation, logout]);

    const renderHeader = () => (
        <View style={styles.headerContainer}>
            <View>
                <Text style={styles.welcomeText}>Welcome back,</Text>
                <Text style={styles.userName}>{user?.name || 'User'}</Text>
            </View>
            <TouchableOpacity onPress={() => navigation.navigate('Profile')}>
                {user?.photoUrl ? (
                    <Image source={{ uri: user.photoUrl }} style={styles.avatar} />
                ) : (
                    <View style={[styles.avatar, styles.avatarPlaceholder]}>
                        <Ionicons name="person" size={24} color={Colors.text} />
                    </View>
                )}
            </TouchableOpacity>
        </View>
    );

    const renderDevice = ({ item }: { item: SonoffDevice }) => (
        <TouchableOpacity
            style={styles.cardWrapper}
            onPress={() => navigation.navigate('DeviceControl', {
                deviceId: item.deviceId || item.id,
                deviceName: item.name,
                isOnline: item.online
            })}
            activeOpacity={0.8}
        >
            <BlurView intensity={20} tint="dark" style={StyleSheet.absoluteFill} />
            <View style={styles.cardInner}>
                <View style={[styles.iconContainer, { backgroundColor: item.online ? 'rgba(59, 130, 246, 0.2)' : 'rgba(148, 163, 184, 0.1)' }]}>
                    <Ionicons
                        name="bulb"
                        size={28}
                        color={item.online ? Colors.primary : Colors.textSecondary}
                    />
                </View>
                <View style={styles.cardContent}>
                    <Text style={styles.deviceName} numberOfLines={1}>{item.name}</Text>
                    <View style={styles.statusContainer}>
                        <View style={[styles.statusDot, { backgroundColor: item.online ? Colors.success : Colors.textSecondary }]} />
                        <Text style={[styles.deviceStatus, { color: item.online ? Colors.success : Colors.textSecondary }]}>
                            {item.online ? 'Online' : 'Offline'}
                        </Text>
                    </View>
                </View>
                <Ionicons name="chevron-forward" size={20} color={Colors.textSecondary} />
            </View>
        </TouchableOpacity>
    );

    return (
        <LinearGradient
            colors={Colors.background === '#0F172A' ? ['#0F172A', '#1E293B'] : [Colors.background, Colors.background]}
            style={styles.container}
        >
            <StatusBar style="light" />
            {/* Add padding for header */}
            <View style={{ height: 100 }} />

            {isLoading ? (
                <ActivityIndicator size="large" color={Colors.primary} style={{ marginTop: 50 }} />
            ) : (
                <FlatList
                    data={devices || []}
                    renderItem={renderDevice}
                    keyExtractor={(item) => item._id || item.deviceId || item.id || Math.random().toString()}
                    contentContainerStyle={styles.list}
                    ListHeaderComponent={renderHeader}
                    refreshControl={
                        <RefreshControl refreshing={false} onRefresh={onRefresh} tintColor={Colors.text} />
                    }
                    ListEmptyComponent={
                        <View style={{ alignItems: 'center', marginTop: 50, padding: 20 }}>
                            <Text style={styles.emptyText}>No devices found</Text>
                            <Text style={{ color: Colors.textSecondary, textAlign: 'center', marginTop: 10 }}>
                                Use the Laptop Agent ("python laptop-agent/main.py") to register your devices.
                            </Text>
                        </View>
                    }
                />
            )}
        </LinearGradient>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    list: {
        padding: Spacing.m,
        paddingTop: 0,
    },
    iconButton: {
        width: 40,
        height: 40,
        borderRadius: 20,
        backgroundColor: Colors.glass,
        justifyContent: 'center',
        alignItems: 'center',
        borderWidth: 1,
        borderColor: Colors.glassBorder,
    },
    cardWrapper: {
        borderRadius: BorderRadius.l,
        overflow: 'hidden',
        marginBottom: Spacing.m,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
        backgroundColor: Colors.glass,
    },
    cardInner: {
        flexDirection: 'row',
        alignItems: 'center',
        padding: Spacing.m,
    },
    iconContainer: {
        width: 50,
        height: 50,
        borderRadius: 25,
        justifyContent: 'center',
        alignItems: 'center',
        marginRight: Spacing.m,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
    },
    cardContent: {
        flex: 1,
    },
    deviceName: {
        fontSize: 18,
        fontWeight: '600',
        color: Colors.text,
        marginBottom: 4,
    },
    statusContainer: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    statusDot: {
        width: 8,
        height: 8,
        borderRadius: 4,
        marginRight: 6,
    },
    deviceStatus: {
        fontSize: 14,
        fontWeight: '500',
    },
    emptyText: {
        textAlign: 'center',
        marginTop: 50,
        color: Colors.textSecondary,
        fontSize: 16,
    },
    headerContainer: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: Spacing.l,
        marginTop: Spacing.s,
    },
    welcomeText: {
        fontSize: 14,
        color: Colors.textSecondary,
    },
    userName: {
        fontSize: 24,
        fontWeight: 'bold',
        color: Colors.text,
    },
    avatar: {
        width: 50,
        height: 50,
        borderRadius: 25,
        borderWidth: 2,
        borderColor: Colors.primary,
    },
    avatarPlaceholder: {
        backgroundColor: Colors.glass,
        justifyContent: 'center',
        alignItems: 'center',
        borderColor: Colors.glassBorder,
    }
});
