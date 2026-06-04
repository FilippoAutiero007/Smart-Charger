import { client } from './config.js'
import * as fs from 'fs'
import * as readline from 'readline'

const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
const ask = (q) => new Promise(r => rl.question(q, r))

async function loadToken() {
  if (!fs.existsSync('./token.json')) {
    console.error('token.json non trovato. Prima esegui: npm run start e fai il login su http://127.0.0.1:8000/login')
    process.exit(1)
  }

  let data = JSON.parse(fs.readFileSync('./token.json', 'utf-8'))
  client.at = data.data?.accessToken
  client.region = data?.region || 'eu'
  client.setUrl(data?.region || 'eu')

  const now = Date.now()

  if (data.data?.atExpiredTime > now) return data

  if (data.data?.rtExpiredTime > now) {
    console.log('Token scaduto, refresh in corso...')
    const res = await client.user.refreshToken({ rt: data.data.refreshToken })
    if (res.error !== 0) {
      console.error('Refresh fallito:', res)
      process.exit(1)
    }
    data = {
      status: 200, responseTime: 0, error: 0, msg: '',
      data: {
        accessToken: res.data.at,
        atExpiredTime: now + 2592000000,
        refreshToken: res.data.rt,
        rtExpiredTime: now + 5184000000,
      },
      region: client.region,
    }
    fs.writeFileSync('./token.json', JSON.stringify(data))
    client.at = data.data.accessToken
    console.log('Token aggiornato con successo!')
    return data
  }

  console.error('Token scaduto definitivamente. Rilanciare il server e rifare il login.')
  process.exit(1)
}

async function main() {
  await loadToken()
  console.log('Token valido, pronto a controllare il dispositivo.\n')

  const deviceId = await ask('Inserisci ID del dispositivo Sonoff: ')

  while (true) {
    const cmd = await ask('\n[s] Accendi  [n] Spegni  [q] Esci: ')
    const c = cmd.trim().toLowerCase()

    if (c === 'q') break
    if (c !== 's' && c !== 'n') {
      console.log('Comando non valido.')
      continue
    }

    const res = await client.device.setThingStatus({
      type: 1,
      id: deviceId,
      params: { switch: c === 's' ? 'on' : 'off' },
    })

    if (res.error === 0) {
      console.log(c === 's' ? 'Dispositivo acceso' : 'Dispositivo spento')
    } else {
      console.log('Errore:', JSON.stringify(res))
    }
  }

  rl.close()
  console.log('Uscito.')
}

main().catch(console.error)
