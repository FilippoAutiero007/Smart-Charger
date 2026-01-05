import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useUser, useAuth } from '@clerk/clerk-expo';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import { AppleButton } from '../components/AppleButton';
import { BatteryService } from '../services/battery';
import theme from '../theme/theme';
import { RootStackParamList } from '../types';

type Props = NativeStackScreenProps<RootStackParamList, 'Dashboard'>;

export const DashboardScreen = ({ navigation }: Props) => {
  const { user } = useUser();
  const { signOut } = useAuth();
  const [batteryLevel, setBatteryLevel] = useState(0);
  const [isCharging, setIsCharging] = useState(false);

  useEffect(() => {
    loadBatteryData();

    const levelSub = BatteryService.addLevelListener(setBatteryLevel);
    const stateSub = BatteryService.addStateListener(setIsCharging);

    return () => {
      levelSub.remove();
      stateSub.remove();
    };
  }, []);

  const loadBatteryData = async () => {
    const level = await BatteryService.getLevel();
    const charging = await BatteryService.isCharging();
    setBatteryLevel(level);
    setIsCharging(charging);
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Hello,</Text>
          <Text style={styles.username}>{user?.firstName || 'User'}! 👋</Text>
        </View>
        <TouchableOpacity onPress={() => navigation.navigate('Profile')} style={styles.profileBtn}>
          <Ionicons name="person-circle" size={40} color={theme.colors.primary} />
        </TouchableOpacity>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>🔋 Battery</Text>
        <Text style={styles.batteryLevel}>{batteryLevel}%</Text>
        <Text style={styles.batteryStatus}>{isCharging ? '⚡ Charging' : '🔌 Not Charging'}</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>📊 Stats</Text>
        <View style={styles.statsRow}>
          <View style={styles.stat}><Text style={styles.statValue}>0</Text><Text style={styles.statLabel}>Devices</Text></View>
          <View style={styles.stat}><Text style={styles.statValue}>0h</Text><Text style={styles.statLabel}>Runtime</Text></View>
          <View style={styles.stat}><Text style={styles.statValue}>{batteryLevel}%</Text><Text style={styles.statLabel}>Health</Text></View>
        </View>
      </View>

      <AppleButton title="Sign Out" onPress={() => signOut()} variant="outline" />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F2F2F7', padding: theme.spacing.lg },
  header: { marginBottom: theme.spacing.xl, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  greeting: { fontSize: 18, color: '#8E8E93' },
  username: { fontSize: 28, fontWeight: 'bold' },
  profileBtn: { padding: 4 },
  card: { backgroundColor: '#FFF', borderRadius: theme.borderRadius.lg, padding: theme.spacing.lg, marginBottom: theme.spacing.md, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 8, elevation: 3 },
  cardTitle: { fontSize: 18, fontWeight: '600', marginBottom: theme.spacing.md },
  batteryLevel: { fontSize: 48, fontWeight: 'bold', color: theme.colors.success, textAlign: 'center' },
  batteryStatus: { fontSize: 16, color: '#8E8E93', textAlign: 'center' },
  statsRow: { flexDirection: 'row', justifyContent: 'space-around' },
  stat: { alignItems: 'center' },
  statValue: { fontSize: 24, fontWeight: 'bold', color: theme.colors.primary },
  statLabel: { fontSize: 14, color: '#8E8E93' },
});
