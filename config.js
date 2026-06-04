import eWeLink from 'ewelink-api-next'

// https://dev.ewelink.cc/
// Login
// Apply to become a developer
// Create an application

const _config = {
  appId: 'lYPkZywzOtbxsMRNWJvhgCyXBDptIjOo',
  appSecret: 'mdPR25XfesDAiaB3pQbxWEklWT1EeK7v',
  region: 'eu',
  requestRecord: true,
}

if (!_config.appId || !_config.appSecret) {
  throw new Error('Please configure appId and appSecret')
}

export const client = new eWeLink.WebAPI(_config)
export const wsClient = new eWeLink.Ws(_config);

export const redirectUrl = 'http://127.0.0.1:8000/callback'

// Generate random strings
export const randomString = (length) => {
  return [...Array(length)].map(_=>(Math.random()*36|0).toString(36)).join('');
}

