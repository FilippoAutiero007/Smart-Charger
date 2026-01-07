import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ActivityIndicator, ViewStyle, TextStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import theme from '../theme/theme';

interface Props {
  title: string;
  onPress: () => void;
  loading?: boolean;
  variant?: 'primary' | 'outline' | 'danger';
  style?: ViewStyle;
  textStyle?: TextStyle;
  disabled?: boolean;
}

export const BaseButton: React.FC<Props> = ({ 
  title, 
  onPress, 
  loading, 
  variant = 'primary', 
  style, 
  textStyle,
  disabled 
}) => {
  const isDisabled = loading || disabled;

  if (variant === 'primary') {
    return (
      <TouchableOpacity 
        onPress={onPress} 
        disabled={isDisabled} 
        style={[styles.button, style, isDisabled && styles.disabled]}
      >
        <LinearGradient 
          colors={isDisabled ? ['#C6C6C8', '#8E8E93'] : ['#5AC8FA', theme.colors.primary]} 
          style={styles.gradient}
        >
          {loading ? <ActivityIndicator color="#FFF" /> : <Text style={[styles.text, textStyle]}>{title}</Text>}
        </LinearGradient>
      </TouchableOpacity>
    );
  }

  const isOutline = variant === 'outline';
  const buttonStyle = isOutline ? styles.outlineButton : styles.dangerButton;
  const textColor = isOutline ? theme.colors.primary : theme.colors.error;

  return (
    <TouchableOpacity 
      onPress={onPress} 
      disabled={isDisabled} 
      style={[buttonStyle, style, isDisabled && styles.disabled]}
    >
      {loading ? (
        <ActivityIndicator color={textColor} />
      ) : (
        <Text style={[isOutline ? styles.outlineText : styles.dangerText, textStyle]}>{title}</Text>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: { borderRadius: theme.borderRadius.md, overflow: 'hidden', marginVertical: theme.spacing.sm },
  gradient: { padding: theme.spacing.md, alignItems: 'center' },
  text: { color: '#FFF', fontSize: 16, fontWeight: '600' },
  outlineButton: { 
    borderRadius: theme.borderRadius.md, 
    borderWidth: 2, 
    borderColor: theme.colors.primary, 
    padding: theme.spacing.md, 
    alignItems: 'center', 
    marginVertical: theme.spacing.sm 
  },
  outlineText: { color: theme.colors.primary, fontSize: 16, fontWeight: '600' },
  dangerButton: { 
    borderRadius: theme.borderRadius.md, 
    borderWidth: 2, 
    borderColor: theme.colors.error, 
    padding: theme.spacing.md, 
    alignItems: 'center', 
    marginVertical: theme.spacing.sm 
  },
  dangerText: { color: theme.colors.error, fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.6 },
});
