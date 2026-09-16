import 'dotenv/config'
import express from 'express'
import cors from 'cors'
import eWeLink from 'ewelink-api-next'
import { Resend } from 'resend'
import nodemailer from 'nodemailer'

const app = express()
app.use(cors())
app.use(express.json())

const PORT = process.env.PORT || 3000
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`
const APP_ID = process.env.APP_ID
const APP_SECRET = process.env.APP_SECRET
const RESEND_API_KEY = process.env.RESEND_API_KEY
const FROM_EMAIL = process.env.FROM_EMAIL || 'noreply@ewelink-auth.com'
const GMAIL_USER = process.env.GMAIL_USER
// Google mostra la App Password come "yxyw gmlz llgs icqn" con spazi per leggibilità, ma SMTP vuole senza spazi.
// Il codice fa .replace(/\s/g,'') così puoi incollarla con o senza spazi, funziona comunque
const GMAIL_APP_PASSWORD = process.env.GMAIL_APP_PASSWORD ? process.env.GMAIL_APP_PASSWORD.replace(/\s/g, '') : null
// Nome visualizzato per nascondere info private (es. "VoltGuard Pro" invece di "pippo07pippo")
const MAIL_FROM_NAME = process.env.MAIL_FROM_NAME || process.env.GMAIL_FROM_NAME || 'VoltGuard Pro'
const MAIL_FROM = process.env.MAIL_FROM || null // es. "VoltGuard Pro <noreply@voltguard.app>" se dominio verificato

const resend = RESEND_API_KEY ? new Resend(RESEND_API_KEY) : null

function buildFromAddress() {
  // Priorità: MAIL_FROM env completo > MAIL_FROM_NAME + GMAIL_USER > default
  if (MAIL_FROM) return MAIL_FROM
  // Usa display name per nascondere nome privato Google + email reale richiesta da Gmail SMTP
  // Nota: l'immagine profilo Gmail non si può rimuovere via codice — va rimossa da https://myaccount.google.com/personal-info
  // impostando nome "VoltGuard Pro" e rimuovendo foto profilo
  return `"${MAIL_FROM_NAME}" <${GMAIL_USER || FROM_EMAIL}>`
}

let gmailTransporter = null
if (GMAIL_USER && GMAIL_APP_PASSWORD) {
  gmailTransporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: GMAIL_USER, pass: GMAIL_APP_PASSWORD },
  })
  gmailTransporter.verify().then(() => {
    console.log(`[gmail] transporter verificato per ${MAIL_FROM_NAME} <${GMAIL_USER}>`)
  }).catch(err => {
    console.error(`[gmail] verifica fallita per ${GMAIL_USER}:`, err.message)
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

function generateCode() {
  return String(Math.floor(10000 + Math.random() * 90000))
}

function randomState() {
  return [...Array(20)].map(() => (Math.random() * 36 | 0).toString(36)).join('')
}

app.post('/request-login', async (req, res) => {
  try {
    const { email } = req.body
    if (!email) {
      return res.status(400).json({ error: 'Email richiesta' })
    }

    const code = generateCode()
    const state = randomState()

    pendingLogins.set(code, { code, state, email, status: 'pending' })

    const loginUrl = `${BASE_URL}/login?code=${code}`
    const emailHtml = `
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
        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;"/>
        <p style="color: #aaa; font-size: 12px;">
          Se non hai richiesto questa email, ignorala.
        </p>
      </div>
    `

    // Risposta immediata per evitare timeout client (60s su app) — email in background
    res.json({ code, status: 'pending', message: 'Codice generato, email in invio', loginUrl })

    // Invio email asincrono fire-and-forget (non blocca la risposta)
    const fromAddr = buildFromAddress()
    if (gmailTransporter) {
      gmailTransporter.sendMail({
        from: fromAddr,
        to: email,
        subject: 'Autorizzazione Sonoff - Codice di accesso',
        html: emailHtml,
        // Disabilita tracciamento immagine profilo: Gmail usa avatar del mittente, va rimosso manualmente da account Google
        headers: { 'X-Mailer': 'VoltGuard Pro' },
      }).then(info => {
        console.log(`[email-gmail] inviata a ${email} code=${code} from=${fromAddr} messageId=${info.messageId}`)
      }).catch(err => {
        console.error(`[email-gmail] errore a ${email} code=${code}:`, err.message)
        console.log(`[email-fallback] To: ${email} Code: ${code} Link: ${loginUrl}`)
        // fallback a Resend se configurato
        if (resend) {
          resend.emails.send({ from: FROM_EMAIL, to: email, subject: 'Autorizzazione Sonoff - Codice di accesso', html: emailHtml })
            .then(r => console.log(`[email-resend-fallback] inviata a ${email} id=${r?.data?.id || r?.id || 'ok'}`))
            .catch(e => console.error(`[email-resend-fallback] errore:`, e.message))
        }
      })
    } else if (resend) {
      resend.emails.send({
        from: FROM_EMAIL,
        to: email,
        subject: 'Autorizzazione Sonoff - Codice di accesso',
        html: emailHtml,
      }).then(result => {
        console.log(`[email-resend] inviata a ${email} code=${code} id=${result?.data?.id || result?.id || 'ok'}`)
      }).catch(err => {
        console.error(`[email-resend] errore a ${email} code=${code}:`, err.message)
        console.log(`[email-fallback] To: ${email} Code: ${code} Link: ${loginUrl}`)
      })
    } else {
      console.log(`[email] To: ${email}`)
      console.log(`[email] Code: ${code}`)
      console.log(`[email] Link: ${loginUrl}`)
    }
  } catch (err) {
    console.error('request-login error:', err)
    res.status(500).json({ error: err.message })
  }
})

app.post('/test-mail', async (req, res) => {
  try {
    const { email } = req.body
    if (!email) return res.status(400).json({ error: 'Email richiesta per test' })
    const testHtml = `
      <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px; border: 1px solid #eee; border-radius: 12px;">
        <h2 style="color: #6C63FF;">VoltGuard Pro — Test Mail</h2>
        <p style="color: #333;">Questa è una mail di prova dal sistema Smart Charger.</p>
        <p style="color: #666; font-size: 13px;">Inviata il ${new Date().toLocaleString('it-IT')} da ${buildFromAddress()}</p>
        <p style="color: #888; font-size: 12px;">Se la ricevi, la configurazione email è corretta (mittente: ${MAIL_FROM_NAME}).</p>
        <hr style="border: none; border-top: 1px solid #eee; margin: 16px 0;"/>
        <p style="color: #aaa; font-size: 11px;">Server: ${BASE_URL} — versione v7</p>
      </div>
    `
    const fromAddr = buildFromAddress()
    if (gmailTransporter) {
      const info = await Promise.race([
        gmailTransporter.sendMail({ from: fromAddr, to: email, subject: 'VoltGuard Pro — Test Mail OK', html: testHtml }),
        new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout SMTP 10s')), 10000))
      ])
      console.log(`[test-mail-gmail] inviata a ${email} messageId=${info.messageId}`)
      return res.json({ success: true, mailer: 'gmail', from: fromAddr, messageId: info.messageId })
    } else if (resend) {
      const result = await Promise.race([
        resend.emails.send({ from: FROM_EMAIL, to: email, subject: 'VoltGuard Pro — Test Mail OK', html: testHtml }),
        new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout Resend 10s')), 10000))
      ])
      console.log(`[test-mail-resend] inviata a ${email} id=${result?.data?.id || result?.id}`)
      return res.json({ success: true, mailer: 'resend', id: result?.data?.id || result?.id })
    } else {
      console.log(`[test-mail] nessun mailer configurato, simulazione a ${email}`)
      return res.json({ success: false, error: 'Nessun mailer configurato (GMAIL_USER/RESEND_API_KEY mancanti)', mailer: 'none' })
    }
  } catch (err) {
    console.error('test-mail error:', err)
    return res.status(500).json({ success: false, error: err.message })
  }
})

app.get('/login', (req, res) => {
  const { code } = req.query
  if (!code || !pendingLogins.has(code)) {
    return res.status(400).send('<h3>Link non valido o scaduto</h3>')
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
      return res.status(400).send('<h3>Stato non valido o sessione scaduta</h3>')
    }

    const tokenResult = await api.oauth.getToken({
      region: region || 'eu',
      redirectUrl: `${BASE_URL}/callback`,
      code,
    })

    tokenResult.region = region || 'eu'

    completedLogins.set(foundCode, {
      region: tokenResult.region,
      accessToken: tokenResult.data.accessToken,
      refreshToken: tokenResult.data.refreshToken,
      atExpiryTime: tokenResult.data.atExpiredTime,
      rtExpiryTime: tokenResult.data.rtExpiredTime,
      status: 'completed',
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
      </style>
      </head>
      <body>
        <div class="card">
          <h2>Autorizzazione completata!</h2>
          <p class="hint">Inserisci questo codice nell'app:</p>
          <div class="code">${foundCode}</div>
          <p class="hint">Torna all'app e incolla il codice nel campo apposito.</p>
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
  const { code } = req.params

  if (completedLogins.has(code)) {
    const data = completedLogins.get(code)
    completedLogins.delete(code)
    return res.json(data)
  }

  if (pendingLogins.has(code)) {
    return res.json({ status: 'pending' })
  }

  res.json({ status: 'not_found' })
})

app.get('/devices', async (req, res) => {
  try {
    const { accessToken, region } = req.query
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

app.get('/health', (req, res) => {
  const mailer = gmailTransporter ? `gmail:${MAIL_FROM_NAME}` : resend ? 'resend' : 'none'
  res.json({ status: 'ok', pending: pendingLogins.size, completed: completedLogins.size, version: 'v7-gmail-testmail', mailer, fromName: MAIL_FROM_NAME })
})

app.listen(PORT, () => {
  console.log(`Auth server running at ${BASE_URL}`)
  console.log(`Health check: ${BASE_URL}/health`)
  console.log(`Mailer From: ${gmailTransporter ? buildFromAddress() : resend ? FROM_EMAIL : 'none (console only)'}`)
  if (gmailTransporter) console.log(`Mailer: Gmail via ${MAIL_FROM_NAME} (user nascosto)`)
  else if (resend) console.log(`Mailer: Resend via ${FROM_EMAIL}`)
  else console.warn('WARNING: nessun mailer configurato — GMAIL_USER/GMAIL_APP_PASSWORD o RESEND_API_KEY mancanti, email solo in console')
  console.log('NOTE immagine profilo: rimuovila da https://myaccount.google.com/personal-info -> imposta nome "VoltGuard Pro" e foto neutra/generica')
})
