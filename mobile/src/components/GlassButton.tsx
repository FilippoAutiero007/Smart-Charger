import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ViewStyle, TextStyle, ActivityIndicator } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Colors, BorderRadius, Spacing } from '../theme/theme';

interface GlassButtonProps {
    title: string;
    onPress: () => void;
    style?: ViewStyle;
    textStyle?: TextStyle;
    icon?: React.ReactNode;
    loading?: boolean;
    disabled?: boolean;
    colors?: string[]; // Optional gradient override
}

export const GlassButton: React.FC<GlassButtonProps> = ({
    title,
    onPress,
    style,
    textStyle,
    icon,
    loading = false,
    disabled = false,
    colors = Colors.primaryGradient,
}) => {
    return (
        <TouchableOpacity
            onPress={onPress}
            disabled={loading || disabled}
            style={[styles.container, style, (disabled || loading) && styles.disabled]}
        >
            <LinearGradient
                colors={colors as [string, string, ...string[]]}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 1 }}
                style={styles.gradient}
            >
                {loading ? (
                    <ActivityIndicator color="#fff" />
                ) : (
                    <>
                        {icon && <>{icon}</>}
                        <Text style={[styles.text, textStyle]}>{title}</Text>
                    </>
                )}
            </LinearGradient>
        </TouchableOpacity>
    );
};

const styles = StyleSheet.create({
    container: {
        borderRadius: BorderRadius.m,
        overflow: 'hidden',
        elevation: 5,
        shadowColor: Colors.primary,
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 10,
    },
    gradient: {
        paddingVertical: Spacing.m,
        paddingHorizontal: Spacing.l,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: Spacing.s,
    },
    text: {
        color: Colors.text,
        fontSize: 16,
        fontWeight: '600',
        letterSpacing: 0.5,
    },
    disabled: {
        opacity: 0.6,
    },
});
