import React from 'react';
import { TextInput, View, Text, StyleSheet, TextInputProps, ViewStyle } from 'react-native';
import theme from '../theme/theme';

interface Props extends TextInputProps {
  label?: string;
  error?: string;
  containerStyle?: ViewStyle;
}

export const BaseInput: React.FC<Props> = ({ label, error, containerStyle, ...props }) => (
  <View style={[styles.container, containerStyle]}>
    {label && <Text style={styles.label}>{label}</Text>}
    <TextInput 
      style={[styles.input, error ? styles.inputError : null]} 
      placeholderTextColor="#8E8E93" 
      {...props} 
    />
    {error && <Text style={styles.errorText}>{error}</Text>}
  </View>
);

const styles = StyleSheet.create({
  container: { marginBottom: theme.spacing.md },
  label: { fontSize: 14, fontWeight: '500', marginBottom: theme.spacing.xs, color: '#1C1C1E' },
  input: { 
    backgroundColor: '#F2F2F7', 
    borderRadius: theme.borderRadius.md, 
    padding: theme.spacing.md, 
    fontSize: 16, 
    borderWidth: 1.5, 
    borderColor: '#C6C6C8',
    color: '#000'
  },
  inputError: { borderColor: theme.colors.error },
  errorText: { fontSize: 12, color: theme.colors.error, marginTop: theme.spacing.xs },
});
