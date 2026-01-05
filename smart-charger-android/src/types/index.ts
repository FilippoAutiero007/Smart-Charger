export interface User { id: string; email: string; }
export interface Device { id: string; name: string; batteryLevel: number; }
export type RootStackParamList = {
    SignIn: undefined;
    SignUp: undefined;
    Dashboard: undefined;
    DeviceControl: { deviceId: string; deviceName: string; isOnline: boolean };
    Profile: undefined;
};

