import React, { useEffect, useState, useRef } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Switch, Alert, ScrollView, StatusBar as RNStatusBar } from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { StatusBar } from 'expo-status-bar';
import { Ionicons } from '@expo/vector-icons';
import { RootStackParamList } from '../types';
import { SonoffService } from '../services/sonoff';
import { BatteryService } from '../services/battery';
import { LinearGradient } from 'expo-linear-gradient';
import { BlurView } from 'expo-blur';
import { Colors, Spacing, BorderRadius } from '../theme/theme';
import { GlassButton } from '../components/GlassButton';

type Props = NativeStackScreenProps<RootStackParamList, 'DeviceControl'>;

export default function DeviceControlScreen({ route, navigation }: Props) {
    const { deviceId, deviceName, isOnline: initialOnline } = route.params;
    const [isPowerOn, setIsPowerOn] = useState(false);
    const [batteryLevel, setBatteryLevel] = useState(0);
    const [isCharging, setIsCharging] = useState(false);
    const [monitoringEnabled, setMonitoringEnabled] = useState(false);

    // Thresholds
    const [minBattery, setMinBattery] = useState(20);
    const [maxBattery, setMaxBattery] = useState(80);

    // Refs for state accessed in listeners/callbacks
    const stateRef = useRef({
        minBattery,
        maxBattery,
        monitoringEnabled,
        isCharging,
        isPowerOn
    });

    // Update refs whenever state changes
    useEffect(() => {
        stateRef.current = {
            minBattery,
            maxBattery,
            monitoringEnabled,
            isCharging,
            isPowerOn
        };
    }, [minBattery, maxBattery, monitoringEnabled, isCharging, isPowerOn]);

    useEffect(() => {
        navigation.setOptions({
            title: deviceName,
            headerTitleStyle: { color: Colors.text },
            headerTransparent: true,
            headerBackground: () => (
                <BlurView tint="dark" intensity={80} style={StyleSheet.absoluteFill} />
            ),
            headerTintColor: Colors.text,
        });
        fetchStatus();
        updateBattery();

        // Battery level listener
        const batSub = BatteryService.addLevelListener((level) => {
            setBatteryLevel(level);
            // Use ref to get fresh state
            checkThresholds(level, stateRef.current.isCharging);
        });

        // Charging state listener
        const stateSub = BatteryService.addStateListener((charging) => {
            setIsCharging(charging);
            // Use ref to get fresh state
            checkThresholds(stateRef.current.minBattery, charging); // level might be slightly stale if not passed, but we can fetch or store level too. 
            // Better: update ref immediately or just rely on next level update? 
            // Let's keep it simple: just update state. The level update usually comes with state change or periodic.
        });

        return () => {
            batSub.remove();
            stateSub.remove();
        };
    }, []);

    const fetchStatus = async () => {
        const params = await SonoffService.getDeviceStatus(deviceId);
        if (params) {
            setIsPowerOn(params.switch === 'on');
        }
    };

    const updateBattery = async () => {
        const level = await BatteryService.getLevel();
        const charging = await BatteryService.isCharging();
        setBatteryLevel(level);
        setIsCharging(charging);
    };

    const checkThresholds = async (level: number, charging: boolean) => {
        const { minBattery, maxBattery, monitoringEnabled } = stateRef.current;

        if (!monitoringEnabled) return;

        console.log(`Checking: Level ${level}%, Charging: ${charging}`);

        if (level <= minBattery && !charging) {
            await togglePower(true);
        } else if (level >= maxBattery && charging) {
            await togglePower(false);
        }
    };

    const togglePower = async (targetState: boolean) => {
        setIsPowerOn(targetState);
        const success = await SonoffService.setDevicePower(deviceId, targetState);
        if (!success) {
            setIsPowerOn(!targetState);
            Alert.alert('Error', 'Failed to switch device');
        }
    };

    return (
        <LinearGradient
            colors={Colors.background === '#0F172A' ? ['#0F172A', '#1E293B'] : [Colors.background, Colors.background]}
            style={styles.container}
        >
            <StatusBar style="light" />
            {/* Header Padding */}
            <View style={{ height: 100 }} />

            <ScrollView contentContainerStyle={styles.scrollContent}>

                {/* Device Status Card */}
                <View style={styles.cardWrapper}>
                    <BlurView intensity={20} tint="dark" style={StyleSheet.absoluteFill} />
                    <View style={styles.cardContent}>
                        <View style={[styles.iconContainer, { backgroundColor: isPowerOn ? 'rgba(52, 199, 89, 0.2)' : 'rgba(142, 142, 147, 0.1)' }]}>
                            <Ionicons name="power" size={40} color={isPowerOn ? Colors.success : Colors.textSecondary} />
                        </View>
                        <Text style={styles.statusText}>{isPowerOn ? 'DEVICE ON' : 'DEVICE OFF'}</Text>

                        <GlassButton
                            title={isPowerOn ? 'TURN OFF' : 'TURN ON'}
                            onPress={() => togglePower(!isPowerOn)}
                            colors={isPowerOn ? [Colors.error, '#B91C1C'] : [Colors.success, '#15803D']}
                            style={{ width: '100%', elevation: 0 }}
                        />
                    </View>
                </View>

                {/* Battery Monitor Card */}
                <View style={styles.cardWrapper}>
                    <BlurView intensity={20} tint="dark" style={StyleSheet.absoluteFill} />
                    <View style={styles.cardContent}>
                        <View style={styles.row}>
                            <Ionicons name="battery-charging" size={24} color={Colors.primary} />
                            <Text style={styles.cardTitle}>Smart Charging</Text>
                            <View style={{ flex: 1 }} />
                            <Switch
                                value={monitoringEnabled}
                                onValueChange={setMonitoringEnabled}
                                trackColor={{ false: '#767577', true: Colors.primary }}
                                thumbColor={monitoringEnabled ? '#fff' : '#f4f3f4'}
                            />
                        </View>

                        <View style={styles.batteryDisplay}>
                            <Text style={styles.batteryText}>{batteryLevel}%</Text>
                            <Text style={styles.chargingText}>{isCharging ? 'Charging' : 'Not Charging'}</Text>
                        </View>

                        {monitoringEnabled && (
                            <View style={styles.thresholdsContainer}>
                                <View style={styles.thresholdRow}>
                                    <Text style={styles.label}>Turn ON at: {minBattery}%</Text>
                                    <View style={styles.controlRow}>
                                        <TouchableOpacity onPress={() => setMinBattery(Math.max(0, minBattery - 5))} style={styles.adjBtn}>
                                            <Ionicons name="remove" size={24} color={Colors.text} />
                                        </TouchableOpacity>
                                        <TouchableOpacity onPress={() => setMinBattery(Math.min(maxBattery - 5, minBattery + 5))} style={styles.adjBtn}>
                                            <Ionicons name="add" size={24} color={Colors.text} />
                                        </TouchableOpacity>
                                    </View>
                                </View>

                                <View style={styles.thresholdRow}>
                                    <Text style={styles.label}>Turn OFF at: {maxBattery}%</Text>
                                    <View style={styles.controlRow}>
                                        <TouchableOpacity onPress={() => setMaxBattery(Math.max(minBattery + 5, maxBattery - 5))} style={styles.adjBtn}>
                                            <Ionicons name="remove" size={24} color={Colors.text} />
                                        </TouchableOpacity>
                                        <TouchableOpacity onPress={() => setMaxBattery(Math.min(100, maxBattery + 5))} style={styles.adjBtn}>
                                            <Ionicons name="add" size={24} color={Colors.text} />
                                        </TouchableOpacity>
                                    </View>
                                </View>

                                <Text style={styles.hint}>
                                    Keep the app open to monitor battery.
                                </Text>
                            </View>
                        )}
                    </View>
                </View>
            </ScrollView>
        </LinearGradient>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    scrollContent: {
        padding: Spacing.m,
        paddingTop: 0,
    },
    cardWrapper: {
        borderRadius: BorderRadius.l,
        overflow: 'hidden',
        marginBottom: Spacing.m,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
        backgroundColor: Colors.glass,
    },
    cardContent: {
        padding: Spacing.l,
        alignItems: 'center',
    },
    iconContainer: {
        width: 80,
        height: 80,
        borderRadius: 40,
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: Spacing.m,
        borderWidth: 1,
        borderColor: Colors.glassBorder,
    },
    statusText: {
        fontSize: 20,
        fontWeight: '600',
        marginBottom: Spacing.l,
        color: Colors.text,
        letterSpacing: 1,
    },
    row: {
        flexDirection: 'row',
        alignItems: 'center',
        width: '100%',
        marginBottom: Spacing.m,
    },
    cardTitle: {
        fontSize: 18,
        fontWeight: '600',
        marginLeft: 12,
        color: Colors.text,
    },
    batteryDisplay: {
        alignItems: 'center',
        marginBottom: Spacing.l,
    },
    batteryText: {
        fontSize: 48,
        fontWeight: 'bold',
        color: Colors.primary,
        textShadowColor: Colors.primary,
        textShadowOffset: { width: 0, height: 0 },
        textShadowRadius: 10,
    },
    chargingText: {
        fontSize: 16,
        color: Colors.textSecondary,
    },
    thresholdsContainer: {
        width: '100%',
    },
    thresholdRow: {
        marginBottom: Spacing.m,
    },
    label: {
        fontSize: 16,
        marginBottom: 8,
        color: Colors.text,
    },
    controlRow: {
        flexDirection: 'row',
        gap: 16,
        justifyContent: 'center', // Center buttons
    },
    adjBtn: {
        backgroundColor: Colors.glass,
        borderColor: Colors.glassBorder,
        borderWidth: 1,
        width: 44,
        height: 44,
        justifyContent: 'center',
        alignItems: 'center',
        borderRadius: 22, // Circular
    },
    // adjText removed
    hint: {
        fontSize: 12,
        color: Colors.textSecondary,
        textAlign: 'center',
        marginTop: 8,
    },
});
