/**
 * ============================================
 * CONVEX SERVICE
 * ============================================
 * Questo file gestisce la comunicazione con il backend Convex.
 * 
 * Per app mobile, usiamo chiamate HTTP dirette invece del client Convex completo
 * perché è più semplice e non richiede generazione di codice API.
 */

import axios from 'axios';
import { CONVEX_URL } from '../constants/Config';

/**
 * INTERFACCE TYPESCRIPT
 */

export interface User {
  _id: string;
  email: string;
  region: string;
  googleId?: string;
  pictureUrl?: string;
  createdAt: number;
  lastLogin: number;
}

export interface TokenData {
  accessToken: string;
  refreshToken: string;
  atExpiredTime: number;
  rtExpiredTime: number;
  region: string;
}

export interface UserConfig {
  deviceId: string;
  minBattery: number;
  maxBattery: number;
  checkInterval: number;
  emailNotifications: boolean;
}

/**
 * Client HTTP per Convex
 */
const convexApi = axios.create({
  baseURL: CONVEX_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

/**
 * SERVIZIO CONVEX
 */
export const ConvexService = {
  /**
   * Crea una sessione OAuth
   */
  createOAuthSession: async (
    state: string,
    userCode: string,
    expiresAt: number,
    clientId?: string
  ) => {
    try {
      const response = await convexApi.post('/api/mutations/auth:createOAuthSession', {
        state,
        userCode,
        expiresAt,
        clientId,
      });
      return { success: true, data: response.data };
    } catch (error: any) {
      console.error('Errore creazione sessione OAuth:', error);
      return { success: false, error: error.message };
    }
  },

  /**
   * Completa il login OAuth
   */
  completeOAuthSession: async (
    sessionId: string,
    email: string,
    region: string,
    tokens: TokenData
  ) => {
    try {
      const response = await convexApi.post('/api/mutations/auth:completeOAuthSession', {
        sessionId,
        email,
        region,
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        atExpiredTime: tokens.atExpiredTime,
        rtExpiredTime: tokens.rtExpiredTime,
      });
      return { success: true, data: response.data };
    } catch (error: any) {
      console.error('Errore completamento OAuth:', error);
      return { success: false, error: error.message };
    }
  },

  /**
   * Recupera token usando il codice a 6 cifre
   */
  getTokenByCode: async (code: string) => {
    try {
      const response = await convexApi.post('/api/queries/auth:getTokenByCode', {
        code,
      });

      const result = response.data;
      
      if (!result.success) {
        return { success: false, error: result.error };
      }

      return {
        success: true,
        data: {
          accessToken: result.accessToken,
          refreshToken: result.refreshToken,
          atExpiredTime: result.atExpiredTime,
          rtExpiredTime: result.rtExpiredTime,
          region: result.region,
        },
      };
    } catch (error: any) {
      console.error('Errore recupero token:', error);
      return { success: false, error: error.message };
    }
  },

  /**
   * Recupera informazioni utente
   */
  getUserById: async (userId: string): Promise<User | null> => {
    try {
      const response = await convexApi.post('/api/queries/auth:getUserById', { userId });
      return response.data;
    } catch (error) {
      console.error('Errore recupero utente:', error);
      return null;
    }
  },

  /**
   * Invia email di benvenuto
   */
  sendWelcomeEmail: async (email: string, userName?: string) => {
    try {
      await convexApi.post('/api/actions/email:sendWelcomeEmail', {
        email,
        userName,
      });
      return { success: true };
    } catch (error: any) {
      console.error('Errore invio email benvenuto:', error);
      return { success: false, error: error.message };
    }
  },

  /**
   * Invia notifica scadenza token via email
   */
  sendTokenExpiringEmail: async (email: string, daysLeft: number) => {
    try {
      await convexApi.post('/api/actions/email:sendTokenExpiringEmail', {
        email,
        daysLeft,
      });
      return { success: true };
    } catch (error: any) {
      console.error('Errore invio email scadenza:', error);
      return { success: false, error: error.message };
    }
  },

  /**
   * Polling per verificare completamento login OAuth
   */
  pollToken: async (clientId: string) => {
    try {
      const response = await convexApi.post('/api/queries/auth:pollToken', {
        clientId,
      });
      return response.data;
    } catch (error: any) {
      console.error('Errore polling token:', error);
      return null;
    }
  },
};

export default ConvexService;
