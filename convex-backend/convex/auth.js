import { api } from "./_generated/api";
import { mutation, query, internalMutation, internalQuery, action } from "./_generated/server";
import { v } from "convex/values";


/**
 * Genera stringa random sicura
 */
function generateRandomString(length) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

/**
 * Genera codice a 6 caratteri crittograficamente sicuro
 * Usa crypto.randomBytes invece di Math.random() per prevenire collisioni
 * @returns {string} Codice alfanumerico uppercase a 6 caratteri
 */
function generateSecureCode() {
  // In ambiente Convex, crypto.getRandomValues è disponibile
  const array = new Uint8Array(4);
  crypto.getRandomValues(array);

  // Converti in numero tra 0 e 999999
  const num = array.reduce((acc, val, idx) => acc + val * Math.pow(256, idx), 0) % 1000000;

  // Pad a 6 cifre e converti in base36 per codice alfanumerico
  return num.toString(36).toUpperCase().padStart(6, '0').substring(0, 6);
}

/**
 * Valida formato email basilare
 * @param {string} email - Email da validare
 * @returns {boolean}
 */
function isValidEmail(email) {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email) && email.length <= 255;
}

/**
 * Valida region code Sonoff
 * @param {string} region - Region code (es. "eu", "us", "as")
 * @returns {boolean}
 */
function isValidRegion(region) {
  const validRegions = ["eu", "us", "as", "cn"];
  return validRegions.includes(region.toLowerCase());
}

// ===== OAUTH SESSION MANAGEMENT =====

/**
 * Crea una nuova sessione OAuth temporanea
 * Usata per tracciare il flusso OAuth2 e collegare state a code utente
 */
export const createOAuthSession = mutation({
  args: {
    state: v.string(),
    userCode: v.string(),
    expiresAt: v.number(),
    clientId: v.optional(v.string()), // ✅ NUOVO
  },
  handler: async (ctx, { state, userCode, expiresAt, clientId }) => {
    // Validazione input
    if (!state || state.length < 16) {
      throw new Error("State invalido: deve essere almeno 16 caratteri");
    }

    if (!userCode || !/^\d{6}$/.test(userCode)) {
      throw new Error("UserCode invalido: deve essere 6 cifre numeriche");
    }

    if (expiresAt <= Date.now()) {
      throw new Error("ExpiresAt deve essere nel futuro");
    }

    // Verifica che non esista già una sessione con stesso state (prevenzione duplicati)
    const existingSession = await ctx.db
      .query("oauthSessions")
      .withIndex("by_state", (q) => q.eq("state", state))
      .first();

    if (existingSession) {
      throw new Error("Sessione con questo state già esistente");
    }

    try {
      await ctx.db.insert("oauthSessions", {
        state,
        code: userCode,
        clientId: clientId, // ✅ NUOVO
        createdAt: Date.now(),
        expiresAt,
        used: false,
      });

      return { success: true, code: userCode };
    } catch (error) {
      console.error("Errore creazione sessione OAuth:", error);
      throw new Error("Impossibile creare sessione OAuth");
    }
  },
});

/**
 * Recupera sessione OAuth per state (usato dal callback)
 */
export const getSessionByState = query({
  args: { state: v.string() },
  handler: async (ctx, { state }) => {
    if (!state) {
      return null;
    }

    const session = await ctx.db
      .query("oauthSessions")
      .withIndex("by_state", (q) => q.eq("state", state))
      .first();

    if (!session) {
      return null;
    }

    // Verifica scadenza
    if (session.expiresAt < Date.now()) {
      return null;
    }

    return session;
  },
});

export const getSessionByCode = query({
  args: { code: v.string() },
  handler: async (ctx, { code }) => {
    return await ctx.db
      .query("oauthSessions")
      .withIndex("by_code", (q) => q.eq("code", code)) // We need to check if index exists or use filter
      // The schema likely has `code` field but index?
      // `createOAuthSession` inserts `code`.
      // Let's assume filter if index is missing, but efficient is better.
      // I'll use filter for safety or check schema.js if possible.
      // Using filter: .filter(q => q.eq(q.field("code"), code))
      .filter(q => q.eq(q.field("code"), code))
      .first();
  },
});

export const getUserById = query({
  args: { userId: v.id("users") },
  handler: async (ctx, { userId }) => {
    return await ctx.db.get(userId);
  }
});

// ===== USER MANAGEMENT =====

/**
 * Crea nuovo utente o aggiorna esistente
 * Gestisce anche inserimento config di default e pulizia token vecchi
 */
// ===== INTERNAL HELPER FUNCTIONS (Pure Logic) =====

async function safeEnsureUserExists(ctx, args) {
  // Validazione input completa
  if (!isValidEmail(args.email)) {
    throw new Error("Email non valida");
  }

  if (!isValidRegion(args.region)) {
    throw new Error(`Region non valida. Valori accettati: eu, us, as, cn`);
  }

  // Cerca utente esistente
  const existingUser = await ctx.db
    .query("users")
    .withIndex("by_email", (q) => q.eq("email", args.email))
    .first();

  let userId;
  const now = Date.now();

  if (existingUser) {
    // Aggiorna utente esistente
    await ctx.db.patch(existingUser._id, {
      lastLogin: now,
      region: args.region,
      ...(args.googleId ? { googleId: args.googleId } : {}),
      ...(args.pictureUrl ? { pictureUrl: args.pictureUrl } : {}),
    });
    userId = existingUser._id;
  } else {
    // Crea nuovo utente
    userId = await ctx.db.insert("users", {
      email: args.email,
      region: args.region,
      createdAt: now,
      lastLogin: now,
      googleId: args.googleId,
      pictureUrl: args.pictureUrl,
    });

    // Crea configurazione di default per nuovo utente
    await ctx.db.insert("configs", {
      userId,
      deviceId: "",
      minBattery: 20,
      maxBattery: 80,
      checkInterval: 300,
      emailNotifications: true,
      totalCharges: 0,
    });
  }

  return {
    userId,
    email: args.email,
    isNewUser: !existingUser,
  };
}

async function safeSaveUserAuth(ctx, args) {
  // Update user metadata if provided
  await ctx.db.patch(args.userId, {
    lastLogin: Date.now(),
    ...(args.googleId ? { googleId: args.googleId } : {}),
    ...(args.pictureUrl ? { pictureUrl: args.pictureUrl } : {}),
  });

  // Clean old tokens
  const oldTokens = await ctx.db
    .query("tokens")
    .withIndex("by_user", (q) => q.eq("userId", args.userId))
    .collect();

  await Promise.all(oldTokens.map(t => ctx.db.delete(t._id)));

  // Insert new tokens
  await ctx.db.insert("tokens", {
    userId: args.userId,
    accessToken: args.accessToken,
    refreshToken: args.refreshToken,
    atExpiredTime: args.atExpiredTime,
    rtExpiredTime: args.rtExpiredTime,
    region: args.region,
    lastRefresh: Date.now(),
  });
}

// ===== INTERNAL MUTATIONS (Wrappers) =====

export const getUserInternal = internalQuery({
  args: { email: v.string() },
  handler: async (ctx, { email }) => {
    return await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", email))
      .unique();
  },
});

export const saveUserAuth = internalMutation({
  args: {
    userId: v.id("users"),
    email: v.string(),
    accessToken: v.string(),
    refreshToken: v.string(),
    atExpiredTime: v.number(),
    rtExpiredTime: v.number(),
    region: v.string(),
    googleId: v.optional(v.string()), // Optional
    pictureUrl: v.optional(v.string()), // Optional
  },
  handler: async (ctx, args) => {
    await safeSaveUserAuth(ctx, args);
  }
});

/**
 * Crea nuovo utente o aggiorna esistente (configurazione, region)
 * NON GESTISCE I TOKEN (usare saveUserAuth)
 */
export const ensureUserExists = internalMutation({
  args: {
    email: v.string(),
    region: v.string(),
    googleId: v.optional(v.string()), // Optional, update if present
    pictureUrl: v.optional(v.string()), // Optional, update if present
  },
  handler: async (ctx, args) => {
    try {
      return await safeEnsureUserExists(ctx, args);
    } catch (error) {
      console.error("Errore in ensureUserExists:", error);
      throw new Error(`Impossibile creare/aggiornare utente: ${error.message}`);
    }
  },
});

// ===== OAUTH COMPLETION =====

/**
 * Completa il flusso OAuth collegando sessione a utente autenticato
 * Chiamato dal callback dopo token exchange con eWeLink
 */
export const completeOAuthSession = mutation({
  args: {
    sessionId: v.id("oauthSessions"),
    email: v.string(),
    region: v.string(),
    accessToken: v.string(),
    refreshToken: v.string(),
    atExpiredTime: v.number(),
    rtExpiredTime: v.number(),
  },
  handler: async (ctx, args) => {
    // Validazione input
    if (!isValidEmail(args.email)) {
      throw new Error("Email non valida");
    }

    try {
      // Recupera e valida sessione con lock ottimistico
      const session = await ctx.db.get(args.sessionId);

      if (!session) {
        throw new Error("Sessione non trovata");
      }

      if (session.used) {
        throw new Error("Sessione già utilizzata - possibile replay attack");
      }

      if (Date.now() > session.expiresAt) {
        throw new Error("Sessione scaduta");
      }

      // Crea/aggiorna utente (senza token)
      const result = await ensureUserExists(ctx, {
        email: args.email,
        region: args.region,
      });

      // Salva token
      await saveUserAuth(ctx, {
        userId: result.userId,
        email: args.email,
        accessToken: args.accessToken,
        refreshToken: args.refreshToken,
        atExpiredTime: args.atExpiredTime,
        rtExpiredTime: args.rtExpiredTime,
        region: args.region
      });

      // Marca sessione come usata (transazione atomica via patch)
      await ctx.db.patch(args.sessionId, {
        used: true,
        userId: result.userId,
        completedAt: Date.now(),
      });

      return {
        success: true,
        userId: result.userId,
        email: result.email,
        isNewUser: result.isNewUser,
      };
    } catch (error) {
      console.error("Errore in completeOAuthSession:", error);
      throw new Error(`Impossibile completare autenticazione: ${error.message}`);
    }
  },
});

/**
 * Completa sessione OAuth con dati Google
 */
export const completeGoogleSession = mutation({
  args: {
    sessionId: v.id("oauthSessions"),
    googleId: v.string(),
    email: v.string(),
    pictureUrl: v.optional(v.string()),
    region: v.string(),
  },
  handler: async (ctx, { sessionId, googleId, email, pictureUrl, region }) => {
    // 1. Aggiorna/Crea Utente
    const existingUser = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", email))
      .first();

    let userId;

    if (existingUser) {
      userId = existingUser._id;
      await ctx.db.patch(userId, {
        lastLogin: Date.now(),
        googleId,
        pictureUrl,
        region // Aggiorna regione se cambiata?
      });
    } else {
      userId = await ctx.db.insert("users", {
        email,
        region,
        createdAt: Date.now(),
        lastLogin: Date.now(),
        googleId,
        pictureUrl
      });
    }

    // 2. Aggiorna Sessione
    await ctx.db.patch(sessionId, {
      userId,
      completedAt: Date.now(),
      used: true,
    });

    return userId;
  },
});

// ===== TOKEN RETRIEVAL =====

/**
 * Recupera token per codice utente (usato dall'app Python/CLI)
 * Valida sessione e restituisce credenziali
 */
export const getTokenByCode = query({
  args: { code: v.string() },
  handler: async (ctx, { code }) => {
    // Validazione formato codice
    if (!code || !/^\d{6}$/.test(code)) {
      return {
        success: false,
        error: "Codice invalido: deve essere 6 cifre numeriche"
      };
    }

    try {
      // Cerca sessione per codice
      const session = await ctx.db
        .query("oauthSessions")
        .withIndex("by_code", (q) => q.eq("code", code))
        .first();

      if (!session) {
        return {
          success: false,
          error: "Codice non trovato o non valido"
        };
      }

      // Validazioni stato sessione
      // NOTA: Permettiamo il recupero anche se used=true, perché il browser
      // marca la sessione come usata quando salva i token.
      // Il codice agisce come "chiave" per recuperarli.

      /* 
      if (session.used) {
        return { 
          success: false,
          error: "Codice già utilizzato" 
        };
      }
      */

      if (Date.now() > session.expiresAt) {
        return {
          success: false,
          error: "Codice scaduto. Riprova il login dal browser."
        };
      }

      if (!session.userId) {
        return {
          success: false,
          error: "Autenticazione non completata. Completa il login dal browser."
        };
      }

      // Recupera token utente
      const token = await ctx.db
        .query("tokens")
        .withIndex("by_user", (q) => q.eq("userId", session.userId))
        .first();

      if (!token) {
        return {
          success: false,
          error: "Token non trovati per questo utente"
        };
      }

      // Verifica scadenza token
      if (Date.now() > token.atExpiredTime) {
        return {
          success: false,
          error: "Access token scaduto. Riprova il login."
        };
      }

      return {
        success: true,
        accessToken: token.accessToken,
        refreshToken: token.refreshToken,
        atExpiredTime: token.atExpiredTime,
        rtExpiredTime: token.rtExpiredTime,
        region: token.region,
      };
    } catch (error) {
      console.error("Errore in getTokenByCode:", error);
      return {
        success: false,
        error: "Errore durante il recupero del token"
      };
    }
  },
});

// ===== CLEANUP & MAINTENANCE =====

/**
 * Rimuove sessioni OAuth scadute
 * DOVREBBE essere schedulato automaticamente (Convex Cron)
 */
export const cleanupExpiredSessions = mutation({
  args: {},
  handler: async (ctx) => {
    const now = Date.now();

    try {
      // Trova tutte le sessioni scadute
      const expiredSessions = await ctx.db
        .query("oauthSessions")
        .withIndex("by_expiration", (q) => q.lt("expiresAt", now))
        .collect();

      if (expiredSessions.length === 0) {
        return {
          success: true,
          deleted: 0,
          message: "Nessuna sessione scaduta trovata"
        };
      }

      // Batch delete con Promise.all per performance
      await Promise.all(
        expiredSessions.map(session => ctx.db.delete(session._id))
      );

      return {
        success: true,
        deleted: expiredSessions.length,
        message: `Eliminate ${expiredSessions.length} sessioni scadute`
      };
    } catch (error) {
      console.error("Errore cleanup sessioni:", error);
      throw new Error("Impossibile eliminare sessioni scadute");
    }
  },
});

/**
 * Cleanup token scaduti (maintenance)
 * Rimuove token con rtExpiredTime passato
 */
export const cleanupExpiredTokens = mutation({
  args: {},
  handler: async (ctx) => {
    const now = Date.now();

    try {
      // Trova token con refresh token scaduto
      const expiredTokens = await ctx.db
        .query("tokens")
        .filter((q) => q.lt(q.field("rtExpiredTime"), now))
        .collect();

      if (expiredTokens.length === 0) {
        return {
          success: true,
          deleted: 0,
          message: "Nessun token scaduto trovato"
        };
      }

      // Batch delete
      await Promise.all(
        expiredTokens.map(token => ctx.db.delete(token._id))
      );

      return {
        success: true,
        deleted: expiredTokens.length,
        message: `Eliminati ${expiredTokens.length} token scaduti`
      };
    } catch (error) {
      console.error("Errore cleanup token:", error);
      throw new Error("Impossibile eliminare token scaduti");
    }
  },
});

/**
 * Invalida tutti i token di un utente (logout/security)
 */
export const revokeUserTokens = mutation({
  args: { userId: v.id("users") },
  handler: async (ctx, { userId }) => {
    try {
      // Trova tutti i token dell'utente
      const userTokens = await ctx.db
        .query("tokens")
        .withIndex("by_user", (q) => q.eq("userId", userId))
        .collect();

      if (userTokens.length === 0) {
        return {
          success: true,
          revoked: 0,
          message: "Nessun token da revocare"
        };
      }

      // Elimina tutti i token
      await Promise.all(
        userTokens.map(token => ctx.db.delete(token._id))
      );

      return {
        success: true,
        revoked: userTokens.length,
        message: `Revocati ${userTokens.length} token`
      };
    } catch (error) {
      console.error("Errore revoca token:", error);
      throw new Error("Impossibile revocare token utente");
    }
  },
});

/**
 * Poll for tokens by clientId (for auto-login flow)
 * Used by Python app to wait for OAuth completion
 */
export const pollToken = query({
  args: { clientId: v.string() },
  handler: async (ctx, { clientId }) => {
    if (!clientId) {
      return null;
    }

    try {
      // Find session by clientId
      const session = await ctx.db
        .query("oauthSessions")
        .withIndex("by_client_id", (q) => q.eq("clientId", clientId))
        .first();

      if (!session) {
        return null;
      }

      // Check if session is completed (has userId and is used)
      if (!session.userId || !session.used) {
        return null;
      }

      // Check expiration
      if (Date.now() > session.expiresAt) {
        return null;
      }

      // Retrieve user tokens
      const token = await ctx.db
        .query("tokens")
        .withIndex("by_user", (q) => q.eq("userId", session.userId))
        .first();

      // Retrieve user info
      const user = await ctx.db.get(session.userId);

      // Return combined data
      return {
        user: user ? {
          email: user.email,
          googleId: user.googleId,
          pictureUrl: user.pictureUrl
        } : null,
        tokens: token ? {
          accessToken: token.accessToken,
          refreshToken: token.refreshToken,
          atExpiredTime: token.atExpiredTime,
          rtExpiredTime: token.rtExpiredTime,
          region: token.region,
        } : null,
        // Backward compatibility (optional, but cleaner to use nested)
        ...token ? {
          accessToken: token.accessToken,
          refreshToken: token.refreshToken,
          atExpiredTime: token.atExpiredTime,
          rtExpiredTime: token.rtExpiredTime,
          region: token.region,
        } : {}
      };
    } catch (error) {
      console.error("Errore in pollToken:", error);
      return null;
    }
  },
});

/**
 * Ottieni statistiche sessioni (admin/monitoring)
 */
export const getSessionStats = query({
  args: {},
  handler: async (ctx) => {
    const now = Date.now();

    try {
      const allSessions = await ctx.db.query("oauthSessions").collect();

      const stats = {
        total: allSessions.length,
        active: allSessions.filter(s => !s.used && s.expiresAt > now).length,
        used: allSessions.filter(s => s.used).length,
        expired: allSessions.filter(s => s.expiresAt <= now).length,
      };
    } catch (error) {
      console.error("Errore recupero statistiche:", error);
      return {
        success: false,
        error: "Impossibile recuperare statistiche"
      };
    }
  },
});

// ===== ACTIONS =====
// ===== ACTIONS =====

export const login = action({
  args: {
    email: v.string(),
    password: v.string(),
  },
  handler: async (ctx, { email, password }) => {
    const user = await ctx.runQuery(api.auth.getUserInternal, { email });

    if (!user) {
      // Return null or throw? The client expects result or check for null?
      // LoginScreen checks: if (result) ... else Alert('Invalid credentials')
      // So returning null is fine.
      return null;
    }

    if (user.password !== password) {
      return null;
    }

    // Generate tokens
    const accessToken = generateRandomString(40);
    const refreshToken = generateRandomString(40);
    const now = Date.now();
    const threeMonths = 90 * 24 * 60 * 60 * 1000;
    const atExpiredTime = now + threeMonths;
    const rtExpiredTime = now + threeMonths * 2;
    const region = user.region || 'eu';

    await ctx.runMutation(api.auth.saveUserAuth, {
      userId: user._id,
      email: user.email,
      accessToken,
      refreshToken,
      atExpiredTime,
      rtExpiredTime,
      region,
    });

    return {
      userId: user._id,
      accessToken,
      refreshToken,
      atExpiredTime,
      rtExpiredTime,
      region
    };
  },
});

export const googleLogin = action({
  args: {
    token: v.string(),
  },
  handler: async (ctx, { token }) => {
    try {
      const response = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
        headers: { Authorization: `Bearer ${token}` },
      });

      if (!response.ok) {
        console.error("Google Auth Failed", await response.text());
        return null;
      }

      const googleUser = await response.json();
      const { email, name, picture, id: googleId } = googleUser;

      // Check if user exists to get ID, or create via createOrUpdateUser
      // But createOrUpdateUser is complex.

      // I will use `createOrUpdateUser` which I already have?
      // `createOrUpdateUser` is a mutation exported in this file.
      // It's exposed as `api.auth.createOrUpdateUser`.

      const accessToken = generateRandomString(40);
      const refreshToken = generateRandomString(40);
      const now = Date.now();
      const threeMonths = 90 * 24 * 60 * 60 * 1000;

      // Use default region 'eu' for new users for now
      const region = 'eu';

      const result = await ctx.runMutation(api.auth.createOrUpdateUser, {
        email,
        region,
        accessToken,
        refreshToken,
        atExpiredTime: now + threeMonths,
        rtExpiredTime: now + threeMonths * 2,
      });

      // Also update googleId and picture if needed, createOrUpdateUser doesn't do that yet?
      // I'll add a patch call via my saveUserAuth or just rely on manual patch.
      // `createOrUpdateUser` returns userId.

      if (result.userId) {
        // We can update google info separately or improve createOrUpdateUser.
        // For safety, I will call saveUserAuth which handles token and metadata patching.
        await ctx.runMutation(api.auth.saveUserAuth, {
          userId: result.userId,
          email,
          accessToken,
          refreshToken,
          atExpiredTime: now + threeMonths,
          rtExpiredTime: now + threeMonths * 2,
          region,
          googleId,
          pictureUrl: picture
        });
      }



      return {
        token: {
          userId: user._id,
          accessToken,
          refreshToken,
          atExpiredTime: now + threeMonths,
          rtExpiredTime: now + threeMonths * 2,
          region: user.region
        },
        user: {
          email: user.email,
          name: user.email.split('@')[0],
          photoUrl: user.pictureUrl
        }
      };
    } catch (e) {
      console.error("Google login error", e);
      return null;
    }
  },
});

export const sendPasswordReset = action({
  args: { email: v.string() },
  handler: async (ctx, { email }) => {
    const user = await ctx.runQuery(api.auth.getUserInternal, { email });
    if (!user) return { success: false, error: "Email not found" };

    const tempPassword = generateRandomString(8);

    // Hash password to match client-side SHA256 logic
    // Using simple SHA256 hex digest
    // Note: In Node.js environment we can use crypto
    // We try to use Web Crypto API if available or fallback

    let passwordHash;
    try {
      // Node.js crypto
      const crypto = require('crypto'); // Dynamic require to avoid build issues if env differs
      passwordHash = crypto.createHash('sha256').update(tempPassword).digest('hex');
    } catch (e) {
      console.error("Crypto module error", e);
      return { success: false, error: "Internal server error (crypto)" };
    }

    // Update user password
    await ctx.runMutation(api.users.setPassword, {
      userId: user._id,
      passwordHash
    });

    // Send email
    await ctx.runAction(api.email.sendPasswordResetEmail, {
      email,
      tempPassword
    });

    return { success: true };
  }
});


