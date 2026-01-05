import * as Battery from 'expo-battery';

export const BatteryService = {
    getLevel: async (): Promise<number> => {
        const level = await Battery.getBatteryLevelAsync();
        return Math.round(level * 100);
    },

    isCharging: async (): Promise<boolean> => {
        const state = await Battery.getBatteryStateAsync();
        return state === Battery.BatteryState.CHARGING || state === Battery.BatteryState.FULL;
    },

    addLevelListener: (callback: (level: number) => void) => {
        return Battery.addBatteryLevelListener(({ batteryLevel }) => {
            callback(Math.round(batteryLevel * 100));
        });
    },

    addStateListener: (callback: (isCharging: boolean) => void) => {
        return Battery.addBatteryStateListener(({ batteryState }) => {
            callback(batteryState === Battery.BatteryState.CHARGING || batteryState === Battery.BatteryState.FULL);
        });
    }
};
