import 'dotenv/config'
import express from 'express'
import cors from 'cors'
import eWeLink from 'ewelink-api-next'
import { Resend } from 'resend'

const app = express()
app.use(cors())
app.use(express.json())

const PORT = process.env.PORT || 3000
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`
const APP_ID = process.env.APP_ID
const APP_SECRET = process.env.APP_SECRET
const RESEND_API_KEY = process.env.RESEND_API_KEY
const FROM_EMAIL = process.env.FROM_EMAIL || 'noreply@ewelink-auth.com'

const resend = RESEND_API_KEY ? new Resend(RESEND_API_KEY) : null

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

    if (resend) {
      await resend.emails.send({
        from: FROM_EMAIL,
        to: email,
        subject: 'Autorizzazione Sonoff - Codice di accesso',
        html: emailHtml,
      })
    } else {
      console.log(`[email] To: ${email}`)
      console.log(`[email] Code: ${code}`)
      console.log(`[email] Link: ${loginUrl}`)
    }

    res.json({ code, status: 'pending', message: 'Email inviata' })
  } catch (err) {
    console.error('request-login error:', err)
    res.status(500).json({ error: err.message })
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
      requestRecord: true,
      accessToken,
    })

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
  res.json({ status: 'ok', pending: pendingLogins.size, completed: completedLogins.size })
})

app.listen(PORT, () => {
  console.log(`Auth server running at ${BASE_URL}`)
  console.log(`Health check: ${BASE_URL}/health`)
  if (!resend) console.warn('WARNING: RESEND_API_KEY not set — emails printed to console only')
})
