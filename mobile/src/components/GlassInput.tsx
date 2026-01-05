import React from 'react';
import { TextInput, StyleSheet, TextInputProps, View, Text, ViewStyle } from 'react-native';
import { BlurView } from 'expo-blur';
import { Colors, BorderRadius, Spacing } from '../theme/theme';

interface GlassInputProps extends TextInputProps {
    label?: string;
    error?: string;
    containerStyle?: ViewStyle;
}

export const GlassInput: React.FC<GlassInputProps> = ({ label, error, style, containerStyle, ...props }) => {
    return (
        <View style={[styles.container, containerStyle]}>
            {label && <Text style={styles.label}>{label}</Text>}
            <View style={styles.inputWrapper}>
                <BlurView intensity={30} tint="dark" style={StyleSheet.absoluteFill} />
                <TextInput
                    placeholderTextColor={Colors.textSecondary}
                    style={[styles.input, style]}
                    {...props}
                />
            </View>
            {error && <Text style={styles.errorText}>{error}</Text>}
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        marginBottom: Spacing.m,
    },
    label: {
        color: Colors.text,
        fontSize: 14,
        fontWeight: '500',
        marginBottom: Spacing.s,
        marginLeft: Spacing.s / 2,
    },
    inputWrapper: {
        borderRadius: BorderRadius.m,
        overflow: 'hidden',
        borderWidth: 1,
        borderColor: Colors.glassBorder,
        backgroundColor: Colors.glass,
    },
    input: {
        padding: Spacing.m,
        color: Colors.text,
        fontSize: 16,
    },
    errorText: {
        color: Colors.error,
        fontSize: 12,
        marginTop: 4,
        marginLeft: Spacing.s / 2,
    },
});
