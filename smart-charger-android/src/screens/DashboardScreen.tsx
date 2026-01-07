import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuthManager } from '../hooks/useAuthManager';
import { BaseButton } from '../components/BaseButton';
import { BatteryService } from '../services/battery';
import theme from '../theme/theme';

export const DashboardScreen = ({ navigation }: any) => {
  const { user, handleSignOut } = useAuthManager();
  const [batteryLevel, setBatteryLevel] = useState(0);
  const [isCharging, setIsCharging] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const loadBatteryData = async () => {
    const level = await BatteryService.getLevel();
    const charging = await BatteryService.isCharging();
    setBatteryLevel(level);
    setIsCharging(charging);
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadBatteryData();
    setRefreshing(false);
  };

  useEffect(() => {
    loadBatteryData();
    const levelSub = BatteryService.addLevelListener(setBatteryLevel);
    const stateSub = BatteryService.addStateListener(setIsCharging);
    return () => {
      levelSub.remove();
      stateSub.remove();
    };
  }, []);

  return (
    <ScrollView 
      style={styles.container}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
    >
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Ciao,</Text>
          <Text style={styles.username}>{user?.firstName || 'Utente'}! 👋</Text>
        </View>
        <TouchableOpacity onPress={() => navigation.navigate('Profile')} style={styles.profileBtn}>
          <Ionicons name="person-circle" size={44} color={theme.colors.primary} />
        </TouchableOpacity>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>🔋 Stato Batteria</Text>
        <View style={styles.batteryContainer}>
          <Text style={styles.batteryLevel}>{batteryLevel}%</Text>
          <Text style={[styles.batteryStatus, isCharging ? styles.charging : null]}>
            {isCharging ? '⚡ In Carica' : '🔌 Non in Carica'}
          </Text>
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>📊 Statistiche</Text>
        <View style={styles.statsRow}>
          <View style={styles.stat}>
            <Text style={styles.statValue}>0</Text>
            <Text style={styles.statLabel}>Dispositivi</Text>
          </View>
          <View style={styles.stat}>
            <Text style={styles.statValue}>0h</Text>
            <Text style={styles.statLabel}>Attività</Text>
          </View>
          <View style={styles.stat}>
            <Text style={styles.statValue}>{batteryLevel}%</Text>
            <Text style={styles.statLabel}>Salute</Text>
          </View>
        </View>
      </View>

      <BaseButton 
        title="Disconnetti" 
        onPress={handleSignOut} 
        variant="outline" 
        style={styles.signOutBtn}
      />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F2F2F7', padding: theme.spacing.lg },
  header: { marginTop: 40, marginBottom: theme.spacing.xl, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  greeting: { fontSize: 18, color: '#8E8E93' },
  username: { fontSize: 28, fontWeight: 'bold', color: '#1C1C1E' },
  profileBtn: { padding: 4 },
  card: { 
    backgroundColor: '#FFF', 
    borderRadius: theme.borderRadius.lg, 
    padding: theme.spacing.lg, 
    marginBottom: theme.spacing.md,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 2
  },
  cardTitle: { fontSize: 18, fontWeight: '600', marginBottom: theme.spacing.md, color: '#1C1C1E' },
  batteryContainer: { alignItems: 'center', paddingVertical: theme.spacing.sm },
  batteryLevel: { fontSize: 56, fontWeight: 'bold', color: theme.colors.primary },
  batteryStatus: { fontSize: 16, color: '#8E8E93', marginTop: theme.spacing.xs },
  charging: { color: theme.colors.success, fontWeight: '600' },
  statsRow: { flexDirection: 'row', justifyContent: 'space-around', marginTop: theme.spacing.sm },
  stat: { alignItems: 'center' },
  statValue: { fontSize: 22, fontWeight: 'bold', color: theme.colors.primary },
  statLabel: { fontSize: 12, color: '#8E8E93', marginTop: 4 },
  signOutBtn: { marginTop: theme.spacing.xl, marginBottom: theme.spacing.xl },
});
