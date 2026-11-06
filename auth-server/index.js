import 'dotenv/config'
import express from 'express'
import cors from 'cors'
import eWeLink from 'ewelink-api-next'
import { Resend } from 'resend'
import nodemailer from 'nodemailer'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const app = express()
app.use(cors())
app.use(express.json())

const PORT = process.env.PORT || 3000
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`
const APP_ID = process.env.APP_ID || 'SCsgZWXTjQexBjOWRcxoFNO0cR0mubZ1'
const APP_SECRET = process.env.APP_SECRET || 'jFKMccmdu3lkxmfRycGjMIr2daKgLHTJ'
const RESEND_API_KEY = process.env.RESEND_API_KEY
const BREVO_API_KEY = process.env.BREVO_API_KEY
const SMTP_HOST = process.env.SMTP_HOST || 'smtp-relay.brevo.com'
const SMTP_PORT = parseInt(process.env.SMTP_PORT, 10) || 587
const SMTP_USER = process.env.SMTP_USER
const SMTP_PASS = process.env.SMTP_PASS
const SMTP_SECURE = process.env.SMTP_SECURE === 'true'
const FROM_EMAIL = process.env.FROM_EMAIL || null
const MAIL_FROM_NAME = process.env.MAIL_FROM_NAME || 'VoltGuard Pro'
const MAIL_FROM = process.env.MAIL_FROM || null
const GMAIL_USER = process.env.GMAIL_USER || null
const GMAIL_APP_PASSWORD = process.env.GMAIL_APP_PASSWORD ? process.env.GMAIL_APP_PASSWORD.replace(/\s/g, '') : null

const resend = RESEND_API_KEY ? new Resend(RESEND_API_KEY) : null

function buildGmailFrom() {
  return `"${MAIL_FROM_NAME}" <${GMAIL_USER}>`
}
function buildSmtpFrom() {
  if (MAIL_FROM) return MAIL_FROM
  const addr = FROM_EMAIL || SMTP_USER
  if (!addr) return `"${MAIL_FROM_NAME}" <noreply@localhost>`
  return `"${MAIL_FROM_NAME}" <${addr}>`
}
function buildResendFrom() {
  if (MAIL_FROM) return MAIL_FROM
  if (FROM_EMAIL && FROM_EMAIL.includes('@')) return `${MAIL_FROM_NAME} <${FROM_EMAIL}>`
  return `${MAIL_FROM_NAME} <${SMTP_USER || 'noreply@voltguard.app'}>`
}
function buildFromAddress() {
  if (MAIL_FROM) return MAIL_FROM
  return `"${MAIL_FROM_NAME}" <${GMAIL_USER || FROM_EMAIL || SMTP_USER || 'noreply@localhost'}>`
}

let smtpTransporter = null
let smtpVerifyOk = false
let smtpActivePort = SMTP_PORT

function createSmtpTransport(port) {
  const secure = SMTP_SECURE || port === 465
  return nodemailer.createTransport({
    host: SMTP_HOST,
    port,
    secure,
    requireTLS: !secure,
    auth: SMTP_USER && SMTP_PASS ? { user: SMTP_USER, pass: SMTP_PASS } : undefined,
    connectionTimeout: 10000,
    greetingTimeout: 10000,
    socketTimeout: 15000,
  })
}

if (SMTP_USER && SMTP_PASS) {
  smtpTransporter = createSmtpTransport(SMTP_PORT)
  smtpTransporter.verify().then(() => {
    smtpVerifyOk = true
    console.log(`[smtp] transporter verificato per ${MAIL_FROM_NAME} via ${SMTP_HOST}:${SMTP_PORT}`)
  }).catch(err => {
    console.error(`[smtp] verifica ${SMTP_PORT} fallita:`, err.message, err.code || '')
    // Fallback: 587->2525 (Brevo) oppure 465 SSL
    const altPort = SMTP_PORT === 587 ? 2525 : 465
    console.log(`[smtp] tentativo fallback porta ${altPort} ...`)
    const alt = createSmtpTransport(altPort)
    alt.verify().then(() => {
      console.log(`[smtp] fallback porta ${altPort} OK, sostituisco transporter`)
      smtpTransporter = alt
      smtpActivePort = altPort
      smtpVerifyOk = true
    }).catch(err2 => {
      console.error(`[smtp] fallback porta ${altPort} fallito:`, err2.message, err2.code || '')
      console.error(`[smtp] suggerimento: verifica SMTP_USER/SMTP_PASS (SMTP key Brevo) e mittente verificato`)
    })
  })
}

// Gmail SMTP (App Password) — mittente reale Gmail, migliore consegna verso Gmail
let gmailTransporter = null
let gmailVerifyOk = false
let gmailActivePort = 587

function createGmailTransport(port) {
  const secure = port === 465
  return nodemailer.createTransport({
    host: 'smtp.gmail.com',
    port,
    secure,
    requireTLS: !secure,
    auth: { user: GMAIL_USER, pass: GMAIL_APP_PASSWORD },
    connectionTimeout: 10000,
    greetingTimeout: 10000,
    socketTimeout: 15000,
  })
}

if (GMAIL_USER && GMAIL_APP_PASSWORD) {
  gmailTransporter = createGmailTransport(587)
  gmailTransporter.verify().then(() => {
    gmailVerifyOk = true
    console.log(`[gmail] transporter verificato per ${GMAIL_USER} via smtp.gmail.com:587`)
  }).catch(err => {
    console.error(`[gmail] verifica 587 fallita:`, err.message, err.code || '')
    console.log('[gmail] tentativo fallback porta 465 secure ...')
    const alt = createGmailTransport(465)
    alt.verify().then(() => {
      console.log('[gmail] fallback 465 OK, sostituisco transporter')
      gmailTransporter = alt
      gmailActivePort = 465
      gmailVerifyOk = true
    }).catch(err2 => {
      console.error('[gmail] fallback 465 fallito:', err2.message, err2.code || '')
      console.error('[gmail] suggerimento: verifica GMAIL_APP_PASSWORD (16 caratteri, 2FA attiva)')
    })
  })
}

const ewelinkConfig = {
  appId: APP_ID,
  appSecret: APP_SECRET,
  region: 'eu',
  requestRecord: true,
}
const api = new eWeLink.WebAPI(ewelinkConfig)

const pendingLogins = new Map()
const completedLogins = new Map()

// --- Persistence for renewal subscriptions ---
const RENEWAL_FILE = path.join(__dirname, 'renewals.json')
const renewalSubscriptions = new Map() // email(lower) -> subscription object

function loadRenewals() {
  try {
    if (fs.existsSync(RENEWAL_FILE)) {
      const raw = fs.readFileSync(RENEWAL_FILE, 'utf-8')
      const obj = JSON.parse(raw)
      for (const [k, v] of Object.entries(obj)) {
        renewalSubscriptions.set(k, v)
      }
      console.log(`[renewal] caricati ${renewalSubscriptions.size} abbonamenti da renewals.json`)
    }
  } catch (e) {
    console.error('[renewal] errore caricamento renewals.json:', e.message)
  }
}
function saveRenewals() {
  try {
    const obj = Object.fromEntries(renewalSubscriptions)
    fs.writeFileSync(RENEWAL_FILE, JSON.stringify(obj, null, 2))
  } catch (e) {
    console.error('[renewal] errore salvataggio renewals.json:', e.message)
  }
}
loadRenewals()

function generateCode() {
  return String(Math.floor(10000 + Math.random() * 90000))
}
function randomState() {
  return [...Array(20)].map(() => (Math.random() * 36 | 0).toString(36)).join('')
}
function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

// Cleanup expired pending codes (15 min TTL) every 5 min
const PENDING_TTL_MS = 15 * 60 * 1000
setInterval(() => {
  const now = Date.now()
  for (const [code, p] of pendingLogins) {
    if (p.createdAt && now - p.createdAt > PENDING_TTL_MS) {
      pendingLogins.delete(code)
      console.log(`[cleanup] pending code ${code} scaduto rimosso`)
    }
  }
  // Also cleanup completed not yet fetched after 30 min
  for (const [code, c] of completedLogins) {
    if (c.completedAt && now - c.completedAt > 30 * 60 * 1000) {
      completedLogins.delete(code)
      console.log(`[cleanup] completed code ${code} scaduto rimosso`)
    }
  }
}, 5 * 60 * 1000)

// Unified mail sender with fallback and timeout
async function sendEmail({ to, subject, html, text }) {
  const textBody = text || html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()

  const attemptBrevoApi = async () => {
    if (!BREVO_API_KEY) return { success: false, error: 'brevo api not configured' }
    const from = FROM_EMAIL || SMTP_USER
    if (!from || !from.includes('@')) return { success: false, error: 'from email non configurata' }
    const resp = await Promise.race([
      fetch('https://api.brevo.com/v3/smtp/email', {
        method: 'POST',
        headers: {
          'api-key': BREVO_API_KEY,
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify({
          sender: { name: MAIL_FROM_NAME, email: from },
          to: [{ email: to }],
          subject,
          htmlContent: html,
          textContent: textBody,
        }),
      }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout Brevo API 12s')), 12000))
    ])
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error(`Brevo API ${resp.status}: ${data.message || JSON.stringify(data)}`)
    }
    const id = data.messageId || 'ok'
    console.log(`[email-brevo] inviata a ${to} from=${from} id=${id}`)
    return { success: true, mailer: 'brevo', messageId: id, from }
  }

  const attemptGmail = async () => {
    if (!gmailTransporter) return { success: false, error: 'gmail not configured' }
    const from = buildGmailFrom()
    const info = await Promise.race([
      gmailTransporter.sendMail({
        from,
        to,
        subject,
        html,
        text: textBody,
        headers: { 'X-Mailer': 'VoltGuard Pro' },
      }),
      new Promise((_, reject) => setTimeout(() => reject(new Error(`Timeout Gmail ${gmailActivePort}`)), 12000))
    ])
    console.log(`[email-gmail] inviata a ${to} from=${from} messageId=${info.messageId}`)
    return { success: true, mailer: 'gmail', messageId: info.messageId, from }
  }

  const attemptSmtp = async () => {
    if (!smtpTransporter) return { success: false, error: 'smtp not configured' }
    const from = buildSmtpFrom()
    const info = await Promise.race([
      smtpTransporter.sendMail({
        from,
        to,
        subject,
        html,
        text: textBody,
        headers: { 'X-Mailer': 'VoltGuard Pro' },
      }),
      new Promise((_, reject) => setTimeout(() => reject(new Error(`Timeout SMTP ${smtpActivePort}`)), 12000))
    ])
    console.log(`[email-smtp] inviata a ${to} from=${from} messageId=${info.messageId}`)
    return { success: true, mailer: 'smtp', messageId: info.messageId, from }
  }

  const attemptResend = async () => {
    if (!resend) return { success: false, error: 'resend not configured' }
    const from = buildResendFrom()
    const result = await Promise.race([
      resend.emails.send({ from, to, subject, html, text: text || undefined }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout Resend 10s')), 10000))
    ])
    // Resend returns { data: {id}, error }
    if (result?.error) throw new Error(result.error.message || JSON.stringify(result.error))
    const id = result?.data?.id || result?.id || 'ok'
    console.log(`[email-resend] inviata a ${to} from=${from} id=${id}`)
    return { success: true, mailer: 'resend', messageId: id, from }
  }

  const attempts = []
  if (gmailTransporter && gmailVerifyOk) attempts.push(['gmail', attemptGmail])
  if (smtpTransporter && smtpVerifyOk) attempts.push(['smtp', attemptSmtp])
  if (BREVO_API_KEY) attempts.push(['brevo', attemptBrevoApi])
  if (gmailTransporter && !gmailVerifyOk) attempts.push(['gmail', attemptGmail])
  if (smtpTransporter && !smtpVerifyOk) attempts.push(['smtp', attemptSmtp])
  if (resend) attempts.push(['resend', attemptResend])

  if (attempts.length === 0) {
    const error = 'nessun mailer configurato (GMAIL_USER/GMAIL_APP_PASSWORD, BREVO_API_KEY, SMTP_USER/SMTP_PASS o RESEND_API_KEY mancanti)'
    console.log(`[email-none] nessun transporter, simulo invio a ${to}`)
    return { success: false, mailer: 'none', error }
  }

  let lastError = null
  for (const [name, attempt] of attempts) {
    try {
      const r = await attempt()
      if (r.success) return r
    } catch (e) {
      lastError = e.message
      console.error(`[email-${name}] errore a ${to}:`, e.message, e.code || '')
    }
  }

  // All failed
  console.log(`[email-fallback] tutte le mail fallite a ${to} lastError=${lastError}`)
  return { success: false, mailer: 'none', error: lastError }
}

function buildLoginEmailHtml(loginUrl, code) {
  return `
      <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px;">
        <h2 style="color: #333;">Autorizzazione Sonoff</h2>
        <p style="color: #555; font-size: 15px; line-height: 1.5;">
          Clicca il pulsante qui sotto per autorizzare l'app al controllo del tuo dispositivo Sonoff:
        </p>
        <a href="${loginUrl}" style="
          display: inline-block;
          padding: 14px 28px;
          margin: 16px 0;
          background-color: #6C63FF;
          color: white;
          text-decoration: none;
          border-radius: 8px;
          font-size: 16px;
          font-weight: bold;
        ">Autorizza Sonoff</a>
        <p style="color: #888; font-size: 13px;">
          Dopo l'autorizzazione, ti verrà mostrato un codice a 5 cifre.<br/>
          Inseriscilo nell'app per completare la configurazione.
        </p>
        <p style="color: #888; font-size: 13px;">
          Codice: <strong style="font-size: 18px; color: #333;">${code}</strong>
        </p>
        <p style="color: #888; font-size: 13px; margin-top: 12px;">
          Se il pulsante non funziona, copia e incolla questo link:<br/>
          <a href="${loginUrl}" style="color:#6C63FF; word-break:break-all;">${loginUrl}</a>
        </p>
        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;"/>
        <p style="color: #aaa; font-size: 12px;">
          Se non hai richiesto questa email, ignorala. Codice valido 15 minuti.
        </p>
      </div>
    `
}
function buildRenewalEmailHtml(loginUrl, code, email, daysUntilExpiry) {
  const expiryNote = daysUntilExpiry != null ? `Il token scade tra circa ${daysUntilExpiry} giorni.` : 'Il token sta per scadere.'
  return `
      <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 24px; border: 1px solid #eee; border-radius: 12px;">
        <h2 style="color: #6C63FF; margin-top:0;">VoltGuard Pro — Rinnovo richiesto</h2>
        <p style="color: #333; font-size: 15px; line-height: 1.5;">
          Ciao!<br/>
          ${expiryNote} Per continuare a controllare il tuo dispositivo Sonoff senza interruzioni, rinnova l'autorizzazione.
        </p>
        <p style="color: #555; font-size: 14px;">
          Puoi farlo <strong>da qualsiasi dispositivo</strong>: basta aprire il link qui sotto, accedere a eWeLink e inserire il codice che comparirà nell'app.
        </p>
        <a href="${loginUrl}" style="
          display: inline-block;
          padding: 14px 28px;
          margin: 16px 0;
          background-color: #6C63FF;
          color: white;
          text-decoration: none;
          border-radius: 8px;
          font-size: 16px;
          font-weight: bold;
        ">Rinnova autorizzazione</a>
        <p style="color: #555; font-size: 14px;">
          Codice di rinnovo: <strong style="font-size: 20px; color: #333; letter-spacing: 4px;">${code}</strong><br/>
          Inserisci questo codice nell'app VoltGuard Pro (tab Sonoff → Verifica).
        </p>
        <p style="color: #888; font-size: 13px;">
          Se il pulsante non funziona, copia questo link:<br/>
          <a href="${loginUrl}" style="color:#6C63FF; word-break:break-all;">${loginUrl}</a>
        </p>
        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;"/>
        <p style="color: #aaa; font-size: 12px;">
          Hai ricevuto questa mail perché hai attivato il rinnovo automatico ogni 25-30 giorni per <strong>${email}</strong>.<br/>
          Se non vuoi più riceverla, disattiva l'opzione nell'app (Automazione → Rinnovo automatico).
        </p>
        <p style="color: #aaa; font-size: 11px;">Server: ${BASE_URL} — codice valido 15 minuti</p>
      </div>
    `
}

app.post('/request-login', async (req, res) => {
  try {
    const { email, autoRenew, intervalDays, deviceId, region } = req.body
    if (!email) {
      return res.status(400).json({ error: 'Email richiesta' })
    }
    if (!isValidEmail(email)) {
      return res.status(400).json({ error: 'Email non valida' })
    }

    const code = generateCode()
    const state = randomState()

    pendingLogins.set(code, { code, state, email, status: 'pending', createdAt: Date.now(), type: 'login' })

    const loginUrl = `${BASE_URL}/login?code=${code}`
    const emailHtml = buildLoginEmailHtml(loginUrl, code)
    const emailText = `Autorizzazione Sonoff\n\nClicca qui: ${loginUrl}\nCodice: ${code}\nValido 15 minuti. Se non hai richiesto questa email, ignorala.`

    // Send email synchronously with fallback, await result (max 12s) then respond
    const mailResult = await sendEmail({
      to: email,
      subject: 'Autorizzazione Sonoff - Codice di accesso',
      html: emailHtml,
      text: emailText,
    })

    // If mail failed, still return code+link so app fallback works, but report failure
    if (!mailResult.success) {
      console.log(`[request-login] mail fallita a ${email} code=${code} link=${loginUrl} err=${mailResult.error}`)
    }

    // Opportunistically register for auto-renewal if requested or if existing subscription
    const emailKey = email.toLowerCase().trim()
    if (autoRenew) {
      const interval = parseInt(intervalDays) || 25
      const existing = renewalSubscriptions.get(emailKey)
      renewalSubscriptions.set(emailKey, {
        email: email,
        emailKey,
        deviceId: deviceId || existing?.deviceId || '',
        region: region || existing?.region || 'eu',
        atExpiry: existing?.atExpiry || 0,
        rtExpiry: existing?.rtExpiry || 0,
        autoRenew: true,
        intervalDays: Math.min(Math.max(interval, 7), 60),
        createdAt: existing?.createdAt || Date.now(),
        lastRenewalSent: existing?.lastRenewalSent || 0,
        updatedAt: Date.now(),
      })
      saveRenewals()
      console.log(`[renewal] autoRenew attivato per ${email} interval=${interval}gg`)
    }

    return res.json({
      code,
      status: 'pending',
      loginUrl,
      mailSent: mailResult.success,
      mailer: mailResult.mailer || 'none',
      mailError: mailResult.error || null,
      from: mailResult.from || buildFromAddress(),
      message: mailResult.success ? 'Codice generato, email inviata' : 'Codice generato ma email non inviata - usa il link diretto',
    })
  } catch (err) {
    console.error('request-login error:', err)
    res.status(500).json({ error: err.message })
  }
})

app.post('/test-mail', async (req, res) => {
  try {
    const { email } = req.body
    if (!email) return res.status(400).json({ error: 'Email richiesta per test' })
    if (!isValidEmail(email)) return res.status(400).json({ error: 'Email non valida' })
    const testHtml = `
      <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px; border: 1px solid #eee; border-radius: 12px;">
        <h2 style="color: #6C63FF;">VoltGuard Pro — Test Mail</h2>
        <p style="color: #333;">Questa è una mail di prova dal sistema Smart Charger.</p>
        <p style="color: #666; font-size: 13px;">Inviata il ${new Date().toLocaleString('it-IT')} da ${buildFromAddress()}</p>
        <p style="color: #888; font-size: 12px;">Se la ricevi, la configurazione email è corretta (mittente: ${MAIL_FROM_NAME}).</p>
        <hr style="border: none; border-top: 1px solid #eee; margin: 16px 0;"/>
        <p style="color: #aaa; font-size: 11px;">Server: ${BASE_URL} — versione v11-check-code-retry</p>
      </div>
    `
    const testText = `VoltGuard Pro - Test Mail OK\nInviata il ${new Date().toLocaleString('it-IT')} da ${buildFromAddress()}\nSe la ricevi, configurazione OK. Server ${BASE_URL}`
    const result = await sendEmail({ to: email, subject: 'VoltGuard Pro — Test Mail OK', html: testHtml, text: testText })
    if (result.success) {
      return res.json({ success: true, mailer: result.mailer, from: result.from, messageId: result.messageId })
    } else {
      return res.status(500).json({ success: false, mailer: result.mailer || 'none', error: result.error, hint: 'Verifica SMTP_USER/SMTP_PASS (SMTP key Brevo), mittente verificato e che la mail non sia in spam' })
    }
  } catch (err) {
    console.error('test-mail error:', err.message, err.code || '', err.response || '')
    return res.status(500).json({ success: false, error: err.message + (err.code ? ` (code ${err.code})` : ''), hint: 'Verifica SMTP_USER/SMTP_PASS (SMTP key Brevo), mittente verificato e che la mail non sia in spam' })
  }
})

app.get('/login', (req, res) => {
  const { code } = req.query
  if (!code || !pendingLogins.has(code)) {
    return res.status(400).send('<h3>Link non valido o scaduto (15 minuti). Richiedi un nuovo codice dall\'app.</h3>')
  }

  const pending = pendingLogins.get(code)
  const redirectUrl = `${BASE_URL}/callback`

  const loginUrl = api.oauth.createLoginUrl({
    redirectUrl,
    grantType: 'authorization_code',
    state: pending.state,
  })

  res.redirect(loginUrl)
})

app.get('/callback', async (req, res) => {
  try {
    const { code, state, region } = req.query

    let foundCode = null
    for (const [c, p] of pendingLogins) {
      if (p.state === state) {
        foundCode = c
        break
      }
    }

    if (!foundCode) {
      return res.status(400).send('<h3>Stato non valido o sessione scaduta. Richiedi un nuovo link.</h3>')
    }

    const tokenResult = await api.oauth.getToken({
      region: region || 'eu',
      redirectUrl: `${BASE_URL}/callback`,
      code,
    })

    tokenResult.region = region || 'eu'

    const tokenData = tokenResult?.data || {}
    const accessToken = tokenData.accessToken || tokenData.at || tokenResult?.accessToken || null
    const refreshToken = tokenData.refreshToken || tokenData.rt || tokenResult?.refreshToken || null

    if (tokenResult?.error && tokenResult.error !== 0) {
      console.error(`[callback] errore eWeLink ${tokenResult.error}: ${tokenResult.msg} per code=${foundCode}:`, JSON.stringify(tokenResult))
      const isDebug = req.query.debug === '1'
      const isExpired = tokenResult.error === 408 || String(tokenResult.msg || '').includes('expired')
      return res.status(502).send(`
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Connessione eWeLink — VoltGuard Pro</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background: #0f172a; color: #f8fafc; }
          .card { background: #1e293b; padding: 32px 24px; border-radius: 16px; max-width: 400px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.3); border: 1px solid #334155; text-align: center; }
          .icon { font-size: 40px; margin-bottom: 12px; }
          h2 { color: #f1f5f9; margin: 8px 0 12px; font-size: 20px; }
          p { color: #94a3b8; font-size: 14px; line-height: 1.5; margin: 0 0 20px; }
          .btn { display: inline-block; background: #6366f1; color: white; padding: 12px 24px; border-radius: 10px; font-weight: 600; text-decoration: none; transition: background 0.2s; }
          .btn:hover { background: #4f46e5; }
          .debug { background: #0b1120; padding: 12px; border-radius: 8px; border: 1px solid #334155; font-size: 11px; text-align: left; color: #f43f5e; margin-top: 20px; font-family: monospace; word-break: break-all; }
        </style>
        </head>
        <body>
          <div class="card">
            <div class="icon">⚡</div>
            <h2>Connessione non riuscita</h2>
            <p>I server di autenticazione eWeLink sono temporaneamente occupati o non hanno confermato la sessione.<br><br>Riprova tra qualche minuto tornando all'app <b>VoltGuard Pro</b>.</p>
            <a class="btn" href="/login?code=${foundCode}">Riprova l'accesso</a>
            ${isDebug ? `<div class="debug">Debug Admin: err=${tokenResult.error} msg=${tokenResult.msg} (isExpired=${isExpired})</div>` : ''}
          </div>
        </body>
        </html>
      `)
    }

    if (!accessToken || !refreshToken) {
      console.error(`[callback] token eWeLink mancanti per code=${foundCode}:`, JSON.stringify(tokenResult))
      return res.status(502).send('<h3>Autorizzazione eWeLink non riuscita (token mancanti). Richiedi un nuovo codice dall\'app.</h3>')
    }

    // Persist renewal info if email associated
    const pending = pendingLogins.get(foundCode)
    const emailKey = pending?.email ? pending.email.toLowerCase().trim() : null
    if (emailKey && renewalSubscriptions.has(emailKey)) {
      const sub = renewalSubscriptions.get(emailKey)
      sub.atExpiry = tokenData.atExpiredTime || (Date.now() + 2592000000)
      sub.rtExpiry = tokenData.rtExpiredTime || (Date.now() + 5184000000)
      sub.region = tokenResult.region
      sub.updatedAt = Date.now()
      // lastRenewalSent set only if this was a renewal type, but update anyway
      renewalSubscriptions.set(emailKey, sub)
      saveRenewals()
      console.log(`[renewal] token aggiornato per ${emailKey} via callback`)
    } else if (emailKey) {
      // auto-create subscription for future renewals if email known and not yet subscribed? Optional - create disabled
      // For now, if not subscribed, don't auto-create, but store for potential future manual subscribe
    }

    completedLogins.set(foundCode, {
      region: tokenResult.region,
      accessToken,
      refreshToken,
      atExpiryTime: tokenData.atExpiredTime || tokenData.atExpiryTime || (Date.now() + 2592000000),
      rtExpiryTime: tokenData.rtExpiredTime || tokenData.rtExpiryTime || (Date.now() + 5184000000),
      status: 'completed',
      completedAt: Date.now(),
      email: pending?.email || null,
    })

    pendingLogins.delete(foundCode)

    res.send(`
      <!DOCTYPE html>
      <html>
      <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <style>
        body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center;
               min-height: 100vh; margin: 0; background: #f5f5f5; }
        .card { background: white; padding: 32px; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.1);
                text-align: center; max-width: 360px; }
        .code { font-size: 48px; font-weight: bold; color: #6C63FF; letter-spacing: 8px; margin: 20px 0; }
        .hint { color: #666; font-size: 14px; }
        .small { color: #999; font-size: 12px; margin-top: 12px; }
      </style>
      </head>
      <body>
        <div class="card">
          <h2>Autorizzazione completata!</h2>
          <p class="hint">Inserisci questo codice nell'app:</p>
          <div class="code">${foundCode}</div>
          <p class="hint">Torna all'app e incolla il codice nel campo apposito (tab Sonoff → Verifica).</p>
          <p class="small">Puoi chiudere questa pagina. Se hai attivato il rinnovo automatico, riceverai una mail tra 25-30 giorni per il prossimo rinnovo da qualsiasi dispositivo.</p>
        </div>
      </body>
      </html>
    `)
  } catch (err) {
    console.error('callback error:', err)
    res.status(500).send(`<h3>Errore: ${err.message}</h3>`)
  }
})

app.get('/check-code/:code', (req, res) => {
  const code = String(req.params.code || '').trim()

  if (completedLogins.has(code)) {
    const data = completedLogins.get(code)
    // NON eliminare qui: la verifica deve essere ripetibile (es. se il caricamento
    // dispositivi fallisce lato app il codice deve restare valido fino alla TTL).
    // La pulizia avviene nel cleanup periodico dopo 30 minuti.
    console.log(`[check-code] code=${code} consegnato (retry ok)`)
    return res.json(data)
  }

  if (pendingLogins.has(code)) {
    return res.json({ status: 'pending' })
  }

  res.json({ status: 'not_found' })
})

app.get('/devices', async (req, res) => {
  try {
    const accessToken = String(req.query.accessToken || '').trim()
    const { region } = req.query
    if (!accessToken) {
      return res.status(400).json({ error: 'accessToken richiesto' })
    }

    const userApi = new eWeLink.WebAPI({
      appId: APP_ID,
      appSecret: APP_SECRET,
      region: region || 'eu',
    })
    userApi.at = accessToken

    const result = await userApi.device.getAllThings()
    if (result?.error && result.error !== 0) {
      console.error(`[devices] errore eWeLink ${result.error}: ${result.msg || ''}`)
      return res.status(502).json({ error: result.msg || `Errore eWeLink ${result.error}` })
    }
    const things = result?.data?.thingList || []
    const devices = things
      .filter(t => t.itemType === 1 || t.itemType === 2)
      .map(t => t.itemData)
    res.json(devices)
  } catch (err) {
    console.error('devices error:', err)
    res.status(500).json({ error: err.message })
  }
})

// --- Renewal subscription endpoints ---

app.post('/subscribe-renewal', async (req, res) => {
  try {
    const { email, deviceId, region, atExpiry, rtExpiry, intervalDays, autoRenew } = req.body
    if (!email || !isValidEmail(email)) return res.status(400).json({ error: 'Email valida richiesta' })
    const emailKey = email.toLowerCase().trim()
    const interval = parseInt(intervalDays) || 25
    const enabled = autoRenew !== false // default true
    const now = Date.now()
    const existing = renewalSubscriptions.get(emailKey) || {}
    const sub = {
      email,
      emailKey,
      deviceId: deviceId || existing.deviceId || '',
      region: region || existing.region || 'eu',
      atExpiry: atExpiry ? Number(atExpiry) : (existing.atExpiry || 0),
      rtExpiry: rtExpiry ? Number(rtExpiry) : (existing.rtExpiry || 0),
      autoRenew: enabled,
      intervalDays: Math.min(Math.max(interval, 7), 60),
      createdAt: existing.createdAt || now,
      updatedAt: now,
      lastRenewalSent: existing.lastRenewalSent || 0,
      lastRenewalCode: existing.lastRenewalCode || null,
    }
    renewalSubscriptions.set(emailKey, sub)
    saveRenewals()
    console.log(`[subscribe-renewal] ${email} interval=${sub.intervalDays} autoRenew=${sub.autoRenew} atExpiry=${sub.atExpiry}`)
    res.json({ success: true, subscription: sub })
  } catch (e) {
    console.error('subscribe-renewal error', e)
    res.status(500).json({ error: e.message })
  }
})

app.post('/unsubscribe-renewal', async (req, res) => {
  try {
    const { email } = req.body
    if (!email || !isValidEmail(email)) return res.status(400).json({ error: 'Email valida richiesta' })
    const key = email.toLowerCase().trim()
    if (renewalSubscriptions.has(key)) {
      renewalSubscriptions.delete(key)
      saveRenewals()
      console.log(`[unsubscribe-renewal] rimosso ${email}`)
      return res.json({ success: true, message: 'Disiscritto' })
    }
    return res.json({ success: false, message: 'Email non trovata' })
  } catch (e) {
    console.error('unsubscribe error', e)
    res.status(500).json({ error: e.message })
  }
})

app.get('/renewal-status', (req, res) => {
  const { email } = req.query
  if (!email) return res.status(400).json({ error: 'email query required' })
  const key = String(email).toLowerCase().trim()
  const sub = renewalSubscriptions.get(key)
  if (!sub) return res.json({ subscribed: false })
  const now = Date.now()
  // compute next due
  let nextDue = null
  if (sub.atExpiry) nextDue = sub.atExpiry - 5 * 24 * 60 * 60 * 1000
  else if (sub.lastRenewalSent) nextDue = sub.lastRenewalSent + sub.intervalDays * 24 * 60 * 60 * 1000
  else nextDue = sub.createdAt + sub.intervalDays * 24 * 60 * 60 * 1000
  const daysUntil = nextDue ? Math.round((nextDue - now) / (24 * 60 * 60 * 1000)) : null
  res.json({ subscribed: true, subscription: sub, nextRenewalDue: nextDue, daysUntilRenewal: daysUntil })
})

app.post('/trigger-renewal', async (req, res) => {
  try {
    const { email } = req.body
    if (!email || !isValidEmail(email)) return res.status(400).json({ error: 'Email valida richiesta' })
    const key = email.toLowerCase().trim()
    const sub = renewalSubscriptions.get(key)
    if (!sub) return res.status(404).json({ error: 'Nessun abbonamento per questa email. Attiva il rinnovo automatico prima.' })
    if (!sub.autoRenew) return res.status(400).json({ error: 'Rinnovo automatico disattivato per questa email. Riattivalo.' })
    const result = await sendRenewalToEmail(key)
    // Anche se mail fallisce, restituisci code+link come fallback (come request-login) - mailSent indica esito
    return res.json({
      success: result.success,
      mailSent: result.success,
      mailer: result.mailer,
      mailError: result.error || null,
      code: result.code,
      loginUrl: result.loginUrl,
      from: result.from || null,
      message: result.success ? 'Mail di rinnovo inviata' : 'Codice generato ma mail non inviata - usa link diretto'
    })
  } catch (e) {
    console.error('trigger-renewal error', e)
    res.status(500).json({ error: e.message })
  }
})

// internal function to send renewal email for a subscription
async function sendRenewalToEmail(emailKey) {
  const sub = renewalSubscriptions.get(emailKey)
  if (!sub) return { success: false, error: 'subscription not found' }
  const email = sub.email
  const code = generateCode()
  const state = randomState()
  pendingLogins.set(code, { code, state, email, status: 'pending', createdAt: Date.now(), type: 'renewal' })
  const loginUrl = `${BASE_URL}/login?code=${code}`
  const daysUntilExpiry = sub.atExpiry ? Math.max(0, Math.round((sub.atExpiry - Date.now()) / (24*60*60*1000))) : null
  const html = buildRenewalEmailHtml(loginUrl, code, email, daysUntilExpiry)
  const text = `Rinnovo VoltGuard Pro\nIl token scade tra ${daysUntilExpiry ?? 'pochi'} giorni.\nApri: ${loginUrl}\nCodice: ${code}\nValido 15 minuti. Puoi rinnovare da qualsiasi dispositivo.`

  const mailResult = await sendEmail({ to: email, subject: 'VoltGuard Pro — Rinnovo autorizzazione Sonoff', html, text })

  sub.lastRenewalSent = Date.now()
  sub.lastRenewalCode = code
  sub.updatedAt = Date.now()
  renewalSubscriptions.set(emailKey, sub)
  saveRenewals()

  console.log(`[renewal-send] a ${email} code=${code} mailSent=${mailResult.success} mailer=${mailResult.mailer} err=${mailResult.error||'none'}`)

  return { success: mailResult.success, code, loginUrl, mailer: mailResult.mailer, error: mailResult.error, from: mailResult.from }
}

// Periodic renewal checker every 6 hours
async function checkAndSendRenewals() {
  const now = Date.now()
  console.log(`[renewal-cron] check started - subscriptions=${renewalSubscriptions.size} now=${new Date().toISOString()}`)
  for (const [key, sub] of renewalSubscriptions) {
    if (!sub.autoRenew) continue
    if (!isValidEmail(sub.email)) continue
    let nextDue
    if (sub.atExpiry && sub.atExpiry > now) {
      // renew 5 days before AT expiry (approx 25 days after issue)
      nextDue = sub.atExpiry - 5 * 24 * 60 * 60 * 1000
    } else if (sub.atExpiry && sub.atExpiry <= now) {
      // already expired, overdue - send now if not sent in last 24h
      nextDue = now - 1
    } else if (sub.lastRenewalSent) {
      nextDue = sub.lastRenewalSent + sub.intervalDays * 24 * 60 * 60 * 1000
    } else {
      nextDue = sub.createdAt + sub.intervalDays * 24 * 60 * 60 * 1000
    }

    const sinceLast = now - (sub.lastRenewalSent || 0)
    const minInterval = 24 * 60 * 60 * 1000 // anti-spam: not more than 1 per day
    if (now >= nextDue && sinceLast >= minInterval) {
      console.log(`[renewal-cron] invio rinnovo a ${sub.email} nextDue=${new Date(nextDue).toISOString()} interval=${sub.intervalDays}`)
      try {
        await sendRenewalToEmail(key)
      } catch (e) {
        console.error(`[renewal-cron] errore invio a ${sub.email}:`, e.message)
      }
    } else {
      const daysLeft = Math.round((nextDue - now) / (24*60*60*1000))
      console.log(`[renewal-cron] skip ${sub.email} daysLeft=${daysLeft} sinceLast=${Math.round(sinceLast/3600000)}h`)
    }
  }
}

// Run cron at startup after 30s and every 6h
setTimeout(() => {
  checkAndSendRenewals().catch(e => console.error('[renewal-cron] startup error', e))
}, 30 * 1000)
setInterval(() => {
  checkAndSendRenewals().catch(e => console.error('[renewal-cron] interval error', e))
}, 6 * 60 * 60 * 1000)

// Also expose manual cron trigger for admin (simple token - BASE_URL? for now open but rate-limited by 1/day per email)
app.post('/_cron/renewals', async (req, res) => {
  const auth = req.headers['x-cron-secret'] || req.query.secret
  const expected = process.env.CRON_SECRET
  if (expected && auth !== expected) return res.status(403).json({ error: 'forbidden' })
  await checkAndSendRenewals()
  res.json({ ok: true, count: renewalSubscriptions.size })
})

app.get('/app-config', (req, res) => {
  res.json({
    appId: APP_ID || 'SCsgZWXTjQexBjOWRcxoFNO0cR0mubZ1',
    baseUrl: BASE_URL,
    version: 'v12-ewelink-active',
  })
})

app.get('/health', (req, res) => {
  const mailer = gmailTransporter && gmailVerifyOk ? `gmail:${MAIL_FROM_NAME}:verified`
    : smtpTransporter && smtpVerifyOk ? `smtp:${MAIL_FROM_NAME}:verified`
    : BREVO_API_KEY ? `brevo-api:${MAIL_FROM_NAME}`
    : gmailTransporter ? `gmail:${MAIL_FROM_NAME}:pending`
    : smtpTransporter ? `smtp:${MAIL_FROM_NAME}:pending`
    : resend ? 'resend' : 'none'
  const smtpConfigured = !!(SMTP_USER && SMTP_PASS)
  const gmailConfigured = !!(GMAIL_USER && GMAIL_APP_PASSWORD)
  const resendConfigured = !!RESEND_API_KEY
  const brevoApiConfigured = !!BREVO_API_KEY
  res.json({
    status: 'ok',
    pending: pendingLogins.size,
    completed: completedLogins.size,
    subscriptions: renewalSubscriptions.size,
    version: 'v12-ewelink-error-detail',
    appIdConfigured: !!APP_ID,
    mailer,
    gmailConfigured,
    gmailVerifyOk,
    gmailHost: gmailConfigured ? 'smtp.gmail.com' : null,
    gmailPort: gmailConfigured ? gmailActivePort : null,
    gmailFrom: gmailConfigured ? buildGmailFrom() : null,
    smtpConfigured,
    smtpVerifyOk,
    smtpHost: SMTP_HOST,
    smtpPort: smtpActivePort,
    resendConfigured,
    brevoApiConfigured,
    fromName: MAIL_FROM_NAME,
    fromAddr: buildFromAddress(),
    smtpFrom: smtpConfigured ? buildSmtpFrom() : null,
    resendFrom: buildResendFrom(),
    baseUrl: BASE_URL,
    uptimeSec: Math.round(process.uptime()),
  })
})

app.listen(PORT, () => {
  console.log(`Auth server running at ${BASE_URL}`)
  console.log(`Health check: ${BASE_URL}/health`)
  console.log(`Mailer From: ${gmailTransporter ? buildGmailFrom() : smtpTransporter ? buildSmtpFrom() : resend ? buildResendFrom() : 'none (console only)'}`)
  if (gmailTransporter) console.log(`Mailer: Gmail SMTP smtp.gmail.com:${gmailActivePort} (${MAIL_FROM_NAME}) verified=${gmailVerifyOk}${smtpTransporter ? ' + Brevo SMTP fallback' : ''}${BREVO_API_KEY ? ' + Brevo API fallback' : ''}`)
  else if (smtpTransporter) console.log(`Mailer: SMTP ${SMTP_HOST}:${smtpActivePort} (${MAIL_FROM_NAME}) verified=${smtpVerifyOk}${BREVO_API_KEY ? ' + Brevo API fallback' : ''}`)
  else if (BREVO_API_KEY) console.log(`Mailer: Brevo API (${FROM_EMAIL || SMTP_USER})`)
  else if (resend) console.log(`Mailer: Resend via ${buildResendFrom()}`)
  else console.warn('WARNING: nessun mailer configurato — GMAIL_USER/GMAIL_APP_PASSWORD, SMTP_USER/SMTP_PASS, BREVO_API_KEY o RESEND_API_KEY mancanti, email solo in console')
  console.log(`Renewal: ${renewalSubscriptions.size} subscriptions loaded, cron ogni 6h, TTL pending 15min`)
})
