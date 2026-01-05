import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import theme from '../theme/theme';

interface Props {
  title: string;
  onPress: () => void;
  loading?: boolean;
  variant?: 'primary' | 'outline';
}

export const AppleButton: React.FC<Props> = ({ title, onPress, loading, variant = 'primary' }) => {
  if (variant === 'primary') {
    return (
      <TouchableOpacity onPress={onPress} disabled={loading} style={styles.button}>
        <LinearGradient colors={['#5AC8FA', theme.colors.primary]} style={styles.gradient}>
          {loading ? <ActivityIndicator color="#FFF" /> : <Text style={styles.text}>{title}</Text>}
        </LinearGradient>
      </TouchableOpacity>
    );
  }
  return (
    <TouchableOpacity onPress={onPress} disabled={loading} style={styles.outlineButton}>
      {loading ? <ActivityIndicator /> : <Text style={styles.outlineText}>{title}</Text>}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: { borderRadius: theme.borderRadius.md, overflow: 'hidden', marginVertical: theme.spacing.sm },
  gradient: { padding: theme.spacing.md, alignItems: 'center' },
  text: { color: '#FFF', fontSize: 16, fontWeight: '600' },
  outlineButton: { borderRadius: theme.borderRadius.md, borderWidth: 2, borderColor: theme.colors.primary, padding: theme.spacing.md, alignItems: 'center', marginVertical: theme.spacing.sm },
  outlineText: { color: theme.colors.primary, fontSize: 16, fontWeight: '600' },
});
