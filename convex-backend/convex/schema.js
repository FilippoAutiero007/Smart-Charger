// convex/schema.js
import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

/**
 * Schema database completo con validazioni, constraints e indexes ottimizzati
 * Correzioni applicate:
 * - Aggiunto completedAt a oauthSessions per audit trail
 * - Creati composite indexes per query performance
 * - Aggiunto index by_device mancante per configs
 * - Aggiunto unique constraints simulation via indexes
 * - Migliorati types con enum-like validations
 * - Aggiunto index lastSeen per devices (query online/offline)
 * - Aggiunto index composito (userId, timestamp) per logs
 * - Default values documentati per campi opzionali
 */

export default defineSchema({
  // ===== TABELLA UTENTI =====
  users: defineTable({
    email: v.string(),        // Validazione: max 255 chars, formato email
    region: v.string(),       // Enum: "eu" | "us" | "as" | "cn"
    createdAt: v.number(),    // Unix timestamp in ms
    lastLogin: v.number(),    // Unix timestamp in ms
    googleId: v.optional(v.string()), // ✅ NUOVO - ID Google
    pictureUrl: v.optional(v.string()), // Avatar Google
    password: v.optional(v.string()), // Hashed password for email auth
    name: v.optional(v.string()), // ✅ NUOVO - Username modificabile
    nameChangeCount: v.optional(v.number()), // ✅ NUOVO - Counter modifiche
  })
    .index("by_email", ["email"])
    .index("by_name", ["name"]), // ✅ NUOVO - Check unicità username  // ✅ Unique constraint simulato (check su insert)

  // ===== TABELLA SESSIONI OAUTH TEMPORANEE =====
  oauthSessions: defineTable({
    state: v.string(),                      // OAuth2 state (32 chars hex)
    code: v.string(),                       // Codice utente a 6 cifre (es. "123456")
    userId: v.optional(v.id("users")),      // Collegato dopo completamento OAuth
    createdAt: v.number(),                  // Unix timestamp creation
    expiresAt: v.number(),                  // Unix timestamp expiration (default: +10 min)
    used: v.boolean(),                      // Flag usa-e-getta
    completedAt: v.optional(v.number()),    // ✅ NUOVO - timestamp quando completata (audit)
    clientId: v.optional(v.string()),       // ✅ NUOVO - ID client per auto-login
  })
    .index("by_code", ["code"])               // Query: recupero token by code
    .index("by_state", ["state"])             // Query: callback OAuth validation
    .index("by_expiration", ["expiresAt"])    // Query: cleanup sessioni scadute
    .index("by_user", ["userId"])             // ✅ NUOVO - query sessioni per user
    .index("by_client_id", ["clientId"]),     // ✅ NUOVO - query sessioni per client

  // ===== TABELLA TOKEN UTENTE =====
  tokens: defineTable({
    userId: v.id("users"),        // Foreign key (NO cascade delete - gestito manualmente)
    accessToken: v.string(),      // eWeLink access token
    refreshToken: v.string(),     // eWeLink refresh token
    atExpiredTime: v.number(),    // Scadenza access token (Unix timestamp)
    rtExpiredTime: v.number(),    // Scadenza refresh token (Unix timestamp)
    region: v.string(),           // Region duplicata per convenience (eu, us, as, cn)
    lastRefresh: v.number(),      // Ultimo refresh token (timestamp)
  })
    .index("by_user", ["userId"])                 // Query: token per utente (UNIQUE via logic)
    .index("by_expiration", ["rtExpiredTime"])    // Query: cleanup token scaduti
    .index("by_at_expiration", ["atExpiredTime"]) // ✅ NUOVO - check access token scaduti
    .index("by_region", ["region"]),              // ✅ NUOVO - stats per regione

  // ===== TABELLA DISPOSITIVI =====
  devices: defineTable({
    userId: v.id("users"),        // Owner del dispositivo
    deviceId: v.string(),         // Sonoff device ID (univoco globalmente)
    name: v.string(),             // Nome user-friendly (es. "Caricatore Tesla")
    brand: v.string(),            // Brand dispositivo (es. "SONOFF")
    online: v.boolean(),          // Stato online/offline
    lastSeen: v.number(),         // ✅ Ultimo heartbeat (per query online/offline)

    // Stato dispositivo (opzionale fino a primo update)
    status: v.optional(v.object({
      switch: v.string(),                 // Enum: "on" | "off"
      power: v.optional(v.number()),      // Watt (default: undefined)
      voltage: v.optional(v.number()),    // Volt (default: undefined)
      current: v.optional(v.number()),    // Ampere (default: undefined)
    })),
  })
    .index("by_user", ["userId"])                       // Query: dispositivi utente
    .index("by_device_id", ["deviceId"])                // Query: lookup by Sonoff ID
    .index("by_user_and_device", ["userId", "deviceId"]) // ✅ NUOVO - composite (unique check)
    .index("by_online", ["online"])                     // ✅ NUOVO - filtra online/offline
    .index("by_last_seen", ["lastSeen"]),               // ✅ NUOVO - trova dispositivi inattivi

  // ===== TABELLA CONFIGURAZIONI UTENTE =====
  configs: defineTable({
    userId: v.id("users"),            // Owner configurazione
    deviceId: v.string(),             // Device target (può essere "" se non configurato)
    isActive: v.optional(v.boolean()), // ✅ NUOVO - solo UN config attivo per user
    clientId: v.optional(v.string()), // ✅ NUOVO - ID client associato

    // Soglie batteria
    minBattery: v.number(),           // Percentuale min (default: 20, range: 0-100)
    maxBattery: v.number(),           // Percentuale max (default: 80, range: 0-100)

    // Configurazione monitoring
    checkInterval: v.number(),        // Secondi tra check (default: 300, min: 60)
    emailNotifications: v.boolean(),  // Abilita notifiche email (default: true)

    // Statistiche (inizializzate a 0/undefined)
    totalCharges: v.optional(v.number()),       // Contatore cicli carica (default: 0)
    lastCharge: v.optional(v.number()),         // Timestamp ultimo ciclo
    totalEnergyKwh: v.optional(v.number()),     // ✅ NUOVO - energia totale erogata (kWh)

    // Metadata
    createdAt: v.optional(v.number()),          // ✅ NUOVO - timestamp creazione config
    updatedAt: v.optional(v.number()),          // ✅ NUOVO - ultimo aggiornamento
  })
    .index("by_user", ["userId"])                       // Query: config per utente
    .index("by_device", ["deviceId"])                   // ✅ NUOVO - config per device (mancava!)
    .index("by_user_and_device", ["userId", "deviceId"]) // ✅ NUOVO - composite unique
    .index("by_active", ["userId", "isActive"])         // ✅ NUOVO - trova config attivo
    .index("by_client_id", ["clientId"]),               // ✅ NUOVO - config per client

  // ===== TABELLA LOG EVENTI =====
  logs: defineTable({
    userId: v.id("users"),              // Owner log
    deviceId: v.string(),               // Device coinvolto
    action: v.string(),                 // Enum: "turned_on" | "turned_off" | "battery_check" | "error" | "manual_override"
    batteryLevel: v.optional(v.number()), // Percentuale batteria (0-100, può essere null)
    timestamp: v.number(),              // Unix timestamp evento
    success: v.boolean(),               // Successo operazione
    message: v.optional(v.string()),    // Messaggio dettagliato (max 500 chars)

    // Metadata addizionali
    errorCode: v.optional(v.string()),  // ✅ NUOVO - codice errore se success=false
    retryCount: v.optional(v.number()), // ✅ NUOVO - numero tentativi
  })
    .index("by_user", ["userId"])                       // Query: logs per utente
    .index("by_device", ["deviceId"])                   // Query: logs per device
    .index("by_timestamp", ["timestamp"])               // Query: logs recenti (ordinamento)
    .index("by_user_and_timestamp", ["userId", "timestamp"]) // ✅ NUOVO - composite ottimizzato
    .index("by_action", ["action"])                     // ✅ NUOVO - filtra per tipo azione
    .index("by_success", ["success", "timestamp"]),     // ✅ NUOVO - trova solo errori

  // ===== TABELLA NOTIFICHE EMAIL INVIATE =====
  emailLog: defineTable({
    userId: v.id("users"),          // Destinatario email
    emailType: v.string(),          // Enum: "welcome" | "token_expiring" | "device_offline" | "battery_critical" | "charge_complete"
    sentAt: v.number(),             // Unix timestamp invio
    success: v.boolean(),           // Successo invio

    // Dettagli addizionali
    recipientEmail: v.optional(v.string()),    // ✅ NUOVO - email destinatario (audit)
    errorMessage: v.optional(v.string()),      // ✅ NUOVO - errore se success=false
    messageId: v.optional(v.string()),         // ✅ NUOVO - ID provider email (Resend)
  })
    .index("by_user", ["userId"])                       // Query: emails per utente
    .index("by_date", ["sentAt"])                       // Query: emails recenti
    .index("by_type", ["emailType"])                    // ✅ NUOVO - filtra per tipo
    .index("by_user_and_type", ["userId", "emailType"]) // ✅ NUOVO - rate limiting check
    .index("by_success", ["success", "sentAt"]),        // ✅ NUOVO - trova fallimenti

  // ===== TABELLA AUDIT TRAIL (NUOVA) =====
  // ✅ Per tracciare modifiche configurazioni e azioni admin
  auditLog: defineTable({
    userId: v.id("users"),              // Utente che ha eseguito l'azione
    action: v.string(),                 // Tipo azione: "config_update" | "device_added" | "token_refresh" | "logout"
    entityType: v.string(),             // Entità modificata: "config" | "device" | "user"
    entityId: v.optional(v.string()),   // ID entità (se applicabile)
    timestamp: v.number(),              // Quando è avvenuta
    changes: v.optional(v.object({      // Delta modifiche (prima/dopo)
      before: v.optional(v.any()),
      after: v.optional(v.any()),
    })),
    ipAddress: v.optional(v.string()),  // IP sorgente (se disponibile)
    userAgent: v.optional(v.string()),  // Browser/client info
  })
    .index("by_user", ["userId"])
    .index("by_timestamp", ["timestamp"])
    .index("by_entity", ["entityType", "entityId"])
    .index("by_action", ["action", "timestamp"]),

  // ===== TABELLA RATE LIMITING (NUOVA) =====
  // ✅ Per implementare rate limiting su API calls
  rateLimits: defineTable({
    key: v.string(),                    // Chiave composite: "email:{userId}" | "api:{endpoint}"
    windowStart: v.number(),            // Inizio finestra temporale (Unix timestamp)
    requestCount: v.number(),           // Numero richieste in questa finestra
    lastRequest: v.number(),            // Timestamp ultima richiesta
  })
    .index("by_key", ["key"])
    .index("by_window", ["windowStart"]),
});

/**
 * DOCUMENTAZIONE CONSTRAINTS & VALIDATIONS
 * (Da implementare a livello applicativo nelle mutations)
 * 
 * USERS:
 * - email: unique, max 255 chars, regex validation
 * - region: enum ["eu", "us", "as", "cn"]
 * 
 * OAUTH_SESSIONS:
 * - state: unique (check before insert), 32 chars hex
 * - code: 6 digits numeric string
 * - expiresAt: must be > createdAt
 * 
 * TOKENS:
 * - userId: one token per user (delete old before insert)
 * - rtExpiredTime: must be > atExpiredTime
 * - atExpiredTime/rtExpiredTime: must be > Date.now()
 * 
 * DEVICES:
 * - (userId, deviceId): composite unique
 * - status.switch: enum ["on", "off"]
 * - power/voltage/current: >= 0 if present
 * - lastSeen: updated on every status check
 * 
 * CONFIGS:
 * - (userId, deviceId): composite unique
 * - minBattery: 0-100, must be < maxBattery
 * - maxBattery: 0-100, must be > minBattery
 * - checkInterval: >= 60 seconds
 * - totalCharges: >= 0 if present
 * - isActive: only ONE true per userId
 * 
 * LOGS:
 * - action: enum ["turned_on", "turned_off", "battery_check", "error", "manual_override"]
 * - batteryLevel: 0-100 if present
 * - message: max 500 chars
 * - timestamp: indexed for fast queries
 * 
 * EMAIL_LOG:
 * - emailType: enum ["welcome", "token_expiring", "device_offline", "battery_critical", "charge_complete"]
 * - recipientEmail: should match users.email
 * - rate limiting: max N emails per type per hour
 * 
 * AUDIT_LOG:
 * - action: enum based on business logic
 * - entityType: enum ["config", "device", "user", "token"]
 * - changes: JSON diff of before/after states
 * 
 * RATE_LIMITS:
 * - key: composite format "{type}:{identifier}"
 * - windowStart: round to hour/minute based on strategy
 * - requestCount: reset when windowStart expires
 */

/**
 * MIGRATION NOTES:
 * 
 * Se stai migrando da schema precedente, esegui questi steps:
 * 
 * 1. Aggiungi campi opzionali mancanti:
 *    - oauthSessions.completedAt
 *    - configs.isActive, totalEnergyKwh, createdAt, updatedAt
 *    - logs.errorCode, retryCount
 *    - emailLog.recipientEmail, errorMessage, messageId
 * 
 * 2. Popola defaults per record esistenti:
 *    ```javascript
 *    // Esempio migration mutation
 *    export const migrateConfigs = internalMutation({
 *      handler: async (ctx) => {
 *        const configs = await ctx.db.query("configs").collect();
 *        for (const config of configs) {
 *          if (config.totalCharges === undefined) {
 *            await ctx.db.patch(config._id, { 
 *              totalCharges: 0,
 *              isActive: false,
 *              createdAt: config.userId ? Date.now() : 0,
 *            });
 *          }
 *        }
 *      }
 *    });
 *    ```
 * 
 * 3. Crea nuove tabelle:
 *    - auditLog (opzionale ma raccomandato)
 *    - rateLimits (se implementi rate limiting)
 * 
 * 4. Rebuilda indexes:
 *    - Convex rebuilda automaticamente, ma verifica performance
 *    - Monitor query latency dopo deployment
 */

/**
 * PERFORMANCE TIPS:
 * 
 * 1. Usa composite indexes per query comuni:
 *    - by_user_and_timestamp per logs paginati
 *    - by_user_and_device per device lookups
 * 
 * 2. Implementa paginazione su query grandi:
 *    - Usa .paginate() invece di .collect()
 *    - Limita take() a max 100 items
 * 
 * 3. Cleanup automatico:
 *    - Schedule cron per cleanupExpiredSessions ogni 30 min
 *    - Schedule cron per cleanupExpiredTokens ogni giorno
 *    - Archive logs vecchi (>90 giorni) in cold storage
 * 
 * 4. Monitoring:
 *    - Track index usage in Convex dashboard
 *    - Alert su query >100ms
 *    - Monitor storage growth rate
 */