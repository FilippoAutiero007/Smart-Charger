import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

const APP_ID = "lYPkZywzOtbxsMRNWJvhgCyXBDptIjOo";
const APP_SECRET = "mdPR25XfesDAiaB3pQbxWEklWT1EeK7v";

let currentRegion = 'eu';
let baseUrl = `https://${currentRegion}-apia.coolkit.cc`;

const api = axios.create({
    baseURL: baseUrl,
    headers: {
        'Content-Type': 'application/json',
        'X-CK-Appid': APP_ID,
    },
    timeout: 15000,
});

// Update base URL when region changes
const updateBaseUrl = (region: string) => {
    currentRegion = region;
    baseUrl = `https://${region}-apia.coolkit.cc`;
    api.defaults.baseURL = baseUrl;
};

// Request interceptor to add token
api.interceptors.request.use(async (config) => {
    const token = await SecureStore.getItemAsync('access_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Response interceptor for token refresh
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            try {
                const refreshToken = await SecureStore.getItemAsync('refresh_token');
                if (!refreshToken) throw new Error('No refresh token');

                const response = await axios.post(`${baseUrl}/v2/user/refresh`, {
                    rt: refreshToken,
                }, {
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CK-Appid': APP_ID,
                    }
                });

                if (response.data.error === 0) {
                    const { accessToken, refreshToken: newRefreshToken } = response.data.data;

                    await SecureStore.setItemAsync('access_token', accessToken);
                    await SecureStore.setItemAsync('refresh_token', newRefreshToken);

                    api.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
                    return api(originalRequest);
                }
            } catch (refreshError) {
                console.error('Token refresh failed', refreshError);
                return Promise.reject(refreshError);
            }
        }
        return Promise.reject(error);
    }
);

export interface SonoffDevice {
    id: string;
    name: string;
    online: boolean;
    brand: string;
    type: string;
}

export const SonoffService = {
    setRegion: (region: string) => updateBaseUrl(region),

    getDevices: async (): Promise<SonoffDevice[]> => {
        try {
            const response = await api.get('/v2/device/thing', {
                params: { num: 0 },
            });

            if (response.data.error === 0) {
                const thingList = response.data.data.thingList || [];
                return thingList
                    .filter((item: any) => item.itemType === 1 || item.itemType === 2)
                    .map((item: any) => ({
                        id: item.itemData.deviceid,
                        name: item.itemData.name || 'Unknown',
                        online: item.itemData.online || false,
                        brand: item.itemData.brandName || 'Sonoff',
                        type: item.itemType === 1 ? 'Device' : 'Group',
                    }));
            }
            return [];
        } catch (error) {
            console.error('Error fetching devices', error);
            return [];
        }
    },

    setDevicePower: async (deviceId: string, on: boolean): Promise<boolean> => {
        try {
            const action = on ? 'on' : 'off';
            const response = await api.post('/v2/device/thing/status', {
                type: 1,
                id: deviceId,
                params: { switch: action },
            });

            return response.data.error === 0;
        } catch (error) {
            console.error(`Error turning device ${on ? 'on' : 'off'}`, error);
            return false;
        }
    },

    getDeviceStatus: async (deviceId: string) => {
        try {
            const response = await api.get('/v2/device/thing/status', {
                params: {
                    type: 1,
                    id: deviceId
                }
            });
            if (response.data.error === 0) {
                return response.data.data.params;
            }
            return null;
        } catch (error) {
            console.error('Error getting device status', error);
            return null;
        }
    }
};
