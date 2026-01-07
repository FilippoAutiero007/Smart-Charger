import { ExpoConfig, ConfigContext } from '@expo/config';
import 'dotenv/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
    ...config,
    name: config.name || 'smart-charger-android',
    slug: config.slug || 'smart-charger-android',
    extra: {
        ...config.extra,
        clerkPublishableKey: process.env.EXPO_PUBLIC_CLERK_PUBLISHABLE_KEY,
    },
});
