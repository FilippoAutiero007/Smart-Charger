// convex/email.js - Sistema Email con Resend
import { action } from "./_generated/server";
import { v } from "convex/values";
import { api } from "./_generated/api";

// Funzione base per inviare email con Resend
export const sendEmail = action({
  args: {
    to: v.string(),
    subject: v.string(),
    html: v.string(),
    type: v.string(), // "welcome", "token_expiring", "renewal_link"
  },
  handler: async (ctx, args) => {
    const RESEND_API_KEY = process.env.RESEND_API_KEY;
    const FROM_EMAIL = process.env.FROM_EMAIL || "onboarding@resend.dev";

    if (!RESEND_API_KEY) {
      console.warn("⚠️ Resend API key non configurata");
      return { success: false, error: "Resend not configured" };
    }

    try {
      const response = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${RESEND_API_KEY}`,
        },
        body: JSON.stringify({
          from: FROM_EMAIL,
          to: args.to,
          subject: args.subject,
          html: args.html,
        }),
      });

      const result = await response.json();

      if (response.ok) {
        console.log(`✉️ Email inviata: ${result.id}`);
        return { success: true, id: result.id };
      }

      console.error("✗ Errore Resend:", result);
      return { success: false, error: result.message };

    } catch (error) {
      console.error("✗ Eccezione email:", error);
      return { success: false, error: error.message };
    }
  },
});

// Email di benvenuto
export const sendWelcomeEmail = action({
  args: {
    email: v.string(),
    userName: v.optional(v.string()),
    renewalUrl: v.optional(v.string()), // URL dinamico per auto-login
  },
  handler: async (ctx, args) => {
    const userName = args.userName || "Utente";
    // Usa URL fornito o fallback
    const renewalLink = args.renewalUrl || "https://striped-shrimp-908.convex.site/my-login";

    const html = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4; }
          .container { max-width: 600px; margin: 0 auto; background-color: white; }
          .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 20px; text-align: center; }
          .header h1 { margin: 0; font-size: 28px; }
          .content { padding: 40px 30px; color: #333; line-height: 1.6; }
          .button { display: inline-block; background: #667eea; color: white; text-decoration: none; padding: 12px 30px; border-radius: 6px; margin: 20px 0; font-weight: 600; }
          .features { background: #f9f9f9; padding: 20px; border-radius: 8px; margin: 20px 0; }
          .features li { margin: 10px 0; }
          .footer { background: #f4f4f4; padding: 20px; text-align: center; font-size: 12px; color: #666; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>🔋 Benvenuto in Smart Charger!</h1>
          </div>
          <div class="content">
            <p>Ciao <strong>${userName}</strong>,</p>
            <p>Il tuo account è stato creato con successo! 🎉</p>
            <p><strong>Email registrata:</strong> ${args.email}</p>
            <p>Ora puoi gestire la ricarica intelligente dei tuoi dispositivi Sonoff direttamente dall'app.</p>
            
            <a href="${renewalLink}" class="button">📱 Apri Dashboard (Auto-Login)</a>
            
            <div class="features">
              <h3>✨ Cosa puoi fare:</h3>
              <ul>
                <li>🔌 Ricarica automatica basata su soglie batteria personalizzate</li>
                <li>📊 Monitoraggio dispositivi in tempo reale</li>
                <li>📧 Notifiche email per scadenza token</li>
                <li>🏠 Gestione multi-dispositivo dalla stessa app</li>
                <li>📈 Statistiche utilizzo e storico ricariche</li>
              </ul>
            </div>
            
            <p>Se hai domande o problemi, non esitare a contattarci!</p>
          </div>
          <div class="footer">
            <p><strong>Sonoff Smart Charger</strong> © 2025</p>
            <p>Questa è un'email automatica, per favore non rispondere.</p>
          </div>
        </div>
      </body>
      </html>
    `;

    return await ctx.runAction(api.email.sendEmail, {
      to: args.email,
      subject: "✅ Benvenuto in Smart Charger!",
      html,
      type: "welcome",
    });
  },
});

// Email scadenza token
export const sendTokenExpiringEmail = action({
  args: {
    email: v.string(),
    daysLeft: v.number(),
    renewalUrl: v.optional(v.string()), // URL dinamico per auto-login
  },
  handler: async (ctx, args) => {
    // Usa URL fornito o fallback al sito base con /my-login (zero-touch)
    const renewalLink = args.renewalUrl || "https://striped-shrimp-908.convex.site/my-login";

    const html = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4; }
          .container { max-width: 600px; margin: 0 auto; background-color: white; }
          .header { background: #ffc107; color: #000; padding: 30px 20px; text-align: center; }
          .content { padding: 30px; color: #333; line-height: 1.6; }
          .warning-box { background: #fff3cd; border-left: 4px solid #ffc107; padding: 20px; margin: 20px 0; border-radius: 4px; }
          .button { display: inline-block; background: #ffc107; color: #000; text-decoration: none; padding: 14px 32px; border-radius: 6px; margin: 20px 0; font-weight: bold; }
          .footer { background: #f4f4f4; padding: 20px; text-align: center; font-size: 12px; color: #666; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>⚠️ Token in Scadenza</h1>
          </div>
          <div class="content">
            <p>Ciao,</p>
            <div class="warning-box">
              <p style="margin: 0; font-size: 18px;"><strong>⏰ Attenzione!</strong></p>
              <p style="margin: 10px 0 0 0;">I tuoi token eWeLink scadranno tra <strong style="font-size: 20px; color: #d32f2f;">${args.daysLeft} giorni</strong>.</p>
            </div>
            
            <p>Per continuare a usare Smart Charger senza interruzioni, rinnova i tuoi token cliccando sul pulsante qui sotto:</p>
            
            <a href="${renewalLink}" class="button">🔄 Rinnova Token Ora</a>
            
            <p><strong>Cosa succede se non rinnovo?</strong></p>
            <ul>
              <li>L'app smetterà di funzionare automaticamente</li>
              <li>Dovrai rifare il login manualmente</li>
              <li>Il monitoraggio automatico si interromperà</li>
            </ul>
            
            <p style="font-size: 12px; color: #666; margin-top: 30px;">
              <strong>Nota:</strong> Questo link è valido per 7 giorni e può essere usato più volte.
            </p>
          </div>
          <div class="footer">
            <p><strong>Sonoff Smart Charger</strong> © 2025</p>
            <p>Email automatica - Non rispondere</p>
          </div>
        </div>
      </body>
      </html>
    `;

    return await ctx.runAction(api.email.sendEmail, {
      to: args.email,
      subject: `⚠️ Token in scadenza tra ${args.daysLeft} giorni - Rinnova ora!`,
      html,
      type: "token_expiring",
    });
  },
});

// Test rapido email (utile per verificare configurazione)
export const sendTestEmail = action({
  args: {
    to: v.string(),
  },
  handler: async (ctx, args) => {
    const html = `
      <html>
        <body style="font-family: Arial; padding: 40px; text-align: center;">
          <h1>🧪 Test Email</h1>
          <p>Se vedi questa email, Resend è configurato correttamente! ✅</p>
          <p style="color: #666; font-size: 12px;">Timestamp: ${new Date().toISOString()}</p>
        </body>
      </html>
    `;

    return await ctx.runAction(api.email.sendEmail, {
      to: args.to,
      subject: "🧪 Test Email - Smart Charger",
      html,
      type: "test",
    });
  },
});

// Email Reset Password
export const sendPasswordResetEmail = action({
  args: {
    email: v.string(),
    tempPassword: v.string(),
  },
  handler: async (ctx, args) => {
    const html = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4; }
          .container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 8px; overflow: hidden; }
          .header { background: #DC2626; color: white; padding: 30px 20px; text-align: center; }
          .content { padding: 40px 30px; color: #333; line-height: 1.6; }
          .password-box { background: #f3f4f6; border: 2px dashed #DC2626; padding: 20px; margin: 20px 0; text-align: center; border-radius: 8px; }
          .password { font-family: monospace; font-size: 32px; font-weight: bold; letter-spacing: 2px; color: #1F2937; }
          .footer { background: #f4f4f4; padding: 20px; text-align: center; font-size: 12px; color: #666; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <h1>🔒 Password Reset</h1>
          </div>
          <div class="content">
            <p>Ciao,</p>
            <p>Abbiamo ricevuto una richiesta di reset della password per il tuo account Smart Charger.</p>
            
            <p>Ecco la tua nuova password temporanea:</p>
            
            <div class="password-box">
              <div class="password">${args.tempPassword}</div>
            </div>
            
            <p><strong>Cosa fare ora:</strong></p>
            <ol>
              <li>Apri l'app Smart Charger</li>
              <li>Fai il logout se necessario</li>
              <li>Accedi con la tua email e questa password</li>
              <li>(Opzionale) Cambia la password dal tuo profilo</li>
            </ol>
            
            <p>Se non hai richiesto questo reset, contatta immediatamente il supporto.</p>
          </div>
          <div class="footer">
            <p><strong>Sonoff Smart Charger</strong> © 2025</p>
            <p>Se non sei stato tu, ignora questa email.</p>
          </div>
        </div>
      </body>
      </html>
    `;

    return await ctx.runAction(api.email.sendEmail, {
      to: args.email,
      subject: "🔒 Reset Password - Smart Charger",
      html,
      type: "password_reset",
    });
  },
});