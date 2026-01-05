export interface SonoffDevice {
    _id?: string;
    id: string; // Used for UI key mostly
    deviceId?: string; // Convex field
    name: string;
    online: boolean;
    brand: string;
    type: 'Device' | 'Group';
}

export interface AuthTokens {
    accessToken: string;
    refreshToken: string;
    atExpiredTime: number;
    rtExpiredTime: number;
    region: string;
    userId?: string;
}

export type RootStackParamList = {
    Login: undefined;
    Signup: undefined;
    EwelinkLogin: undefined;
    Dashboard: undefined;
    Profile: undefined;
    DeviceControl: { deviceId: string; deviceName: string; isOnline: boolean };
};
