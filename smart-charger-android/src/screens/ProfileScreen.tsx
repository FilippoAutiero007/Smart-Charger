import React from 'react';
import { View, Text, StyleSheet, ScrollView, Image, TouchableOpacity } from 'react-native';
import { useUser, useAuth } from '@clerk/clerk-expo';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import { BaseButton } from '../components/BaseButton';
import theme from '../theme/theme';
import { RootStackParamList } from '../types';

type Props = NativeStackScreenProps<RootStackParamList, 'Profile'>;

export const ProfileScreen = ({ navigation }: Props) => {
    const { user } = useUser();
    const { signOut } = useAuth();

    return (
        <ScrollView style={styles.container}>
            <View style={styles.header}>
                <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
                    <Ionicons name="arrow-back" size={28} color={theme.colors.primary} />
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Profile</Text>
                <View style={{ width: 28 }} />
            </View>

            <View style={styles.profileCard}>
                {user?.imageUrl ? (
                    <Image source={{ uri: user.imageUrl }} style={styles.avatar} />
                ) : (
                    <View style={styles.avatarPlaceholder}>
                        <Ionicons name="person" size={50} color="#FFF" />
                    </View>
                )}
                <Text style={styles.name}>{user?.fullName || 'User'}</Text>
                <Text style={styles.email}>{user?.primaryEmailAddress?.emailAddress || 'No email'}</Text>
            </View>

            <View style={styles.section}>
                <Text style={styles.sectionTitle}>Account Information</Text>

                <View style={styles.infoRow}>
                    <Ionicons name="mail-outline" size={24} color={theme.colors.primary} />
                    <View style={styles.infoText}>
                        <Text style={styles.infoLabel}>Email</Text>
                        <Text style={styles.infoValue}>{user?.primaryEmailAddress?.emailAddress || 'Not set'}</Text>
                    </View>
                </View>

                <View style={styles.infoRow}>
                    <Ionicons name="calendar-outline" size={24} color={theme.colors.primary} />
                    <View style={styles.infoText}>
                        <Text style={styles.infoLabel}>Member Since</Text>
                        <Text style={styles.infoValue}>
                            {user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : 'Unknown'}
                        </Text>
                    </View>
                </View>
            </View>

            <View style={styles.section}>
                <Text style={styles.sectionTitle}>Actions</Text>
                <View style={{ marginBottom: theme.spacing.sm }}>
                    <BaseButton
                        title="Sign Out"
                        onPress={() => signOut()}
                        variant="outline"
                    />
                </View>
            </View>
        </ScrollView>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F2F2F7' },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: theme.spacing.lg,
        paddingTop: 60,
        backgroundColor: '#FFF',
        borderBottomWidth: 1,
        borderBottomColor: '#E5E5EA',
    },
    backBtn: { padding: 4 },
    headerTitle: { fontSize: 20, fontWeight: '600' },
    profileCard: {
        backgroundColor: '#FFF',
        padding: theme.spacing.xl,
        alignItems: 'center',
        marginBottom: theme.spacing.md,
    },
    avatar: {
        width: 100,
        height: 100,
        borderRadius: 50,
        marginBottom: theme.spacing.md,
    },
    avatarPlaceholder: {
        width: 100,
        height: 100,
        borderRadius: 50,
        backgroundColor: theme.colors.primary,
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: theme.spacing.md,
    },
    name: { fontSize: 24, fontWeight: 'bold', marginBottom: 4 },
    email: { fontSize: 16, color: '#8E8E93' },
    section: {
        backgroundColor: '#FFF',
        padding: theme.spacing.lg,
        marginBottom: theme.spacing.md,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: '600',
        marginBottom: theme.spacing.md,
    },
    infoRow: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: theme.spacing.md,
        borderBottomWidth: 1,
        borderBottomColor: '#F2F2F7',
    },
    infoText: { marginLeft: theme.spacing.md, flex: 1 },
    infoLabel: { fontSize: 14, color: '#8E8E93', marginBottom: 2 },
    infoValue: { fontSize: 16, color: '#000' },
});
