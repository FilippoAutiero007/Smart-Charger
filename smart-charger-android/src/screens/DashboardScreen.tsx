import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useUser, useAuth } from '@clerk/clerk-expo';
import { AppleButton } from '../components/AppleButton';
import theme from '../theme/theme';

export const DashboardScreen = () => {
  const { user } = useUser();
  const { signOut } = useAuth();

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.greeting}>Hello,</Text>
        <Text style={styles.username}>{user?.firstName || 'User'}! 👋</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>🔋 Battery</Text>
        <Text style={styles.batteryLevel}>65%</Text>
        <Text style={styles.batteryStatus}>⚡ Charging</Text>
      </View>
      <View style={styles.card}>
        <Text style={styles.cardTitle}>📊 Stats</Text>
        <View style={styles.statsRow}>
          <View style={styles.stat}><Text style={styles.statValue}>2</Text><Text style={styles.statLabel}>Devices</Text></View>
          <View style={styles.stat}><Text style={styles.statValue}>12h</Text><Text style={styles.statLabel}>Runtime</Text></View>
          <View style={styles.stat}><Text style={styles.statValue}>98%</Text><Text style={styles.statLabel}>Health</Text></View>
        </View>
      </View>
      <AppleButton title="Sign Out" onPress={() => signOut()} variant="outline" />
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F2F2F7', padding: theme.spacing.lg },
  header: { marginBottom: theme.spacing.xl },
  greeting: { fontSize: 18, color: '#8E8E93' },
  username: { fontSize: 28, fontWeight: 'bold' },
  card: { backgroundColor: '#FFF', borderRadius: theme.borderRadius.lg, padding: theme.spacing.lg, marginBottom: theme.spacing.md, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.1, shadowRadius: 8, elevation: 3 },
  cardTitle: { fontSize: 18, fontWeight: '600', marginBottom: theme.spacing.md },
  batteryLevel: { fontSize: 48, fontWeight: 'bold', color: theme.colors.success, textAlign: 'center' },
  batteryStatus: { fontSize: 16, color: '#8E8E93', textAlign: 'center' },
  statsRow: { flexDirection: 'row', justifyContent: 'space-around' },
  stat: { alignItems: 'center' },
  statValue: { fontSize: 24, fontWeight: 'bold', color: theme.colors.primary },
  statLabel: { fontSize: 14, color: '#8E8E93' },
});
