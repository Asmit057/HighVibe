// Wildcard preview proxy: routes project-<id>.<preview-domain> to the runner pod that
// workspace-service registered in Redis under "route:<host>".
const http = require('http')
const httpProxy = require('http-proxy')
const Redis = require('ioredis')

const redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379', {
  maxRetriesPerRequest: null,
  retryStrategy: (times) => Math.min(times * 100, 2000),
})
redis.on('error', (err) => console.error('redis error:', err.message))

const proxy = httpProxy.createProxyServer({ ws: true, xfwd: true, changeOrigin: true })

async function targetFor(req) {
  const host = (req.headers.host || '').split(':')[0]
  if (!host) return null
  const route = await redis.get(`route:${host}`).catch(() => null)
  return route ? `http://${route}` : null
}

const server = http.createServer(async (req, res) => {
  if (req.url === '/healthz') {
    res.writeHead(200)
    return res.end('ok')
  }
  const target = await targetFor(req)
  if (!target) {
    res.writeHead(404, { 'Content-Type': 'text/plain' })
    return res.end('Preview not found or still starting.')
  }
  proxy.web(req, res, { target }, () => {
    if (!res.headersSent) res.writeHead(502)
    res.end('Preview server is starting, retry in a moment.')
  })
})

// Vite HMR uses WebSockets
server.on('upgrade', async (req, socket, head) => {
  const target = await targetFor(req)
  if (!target) return socket.destroy()
  proxy.ws(req, socket, head, { target }, () => socket.destroy())
})

const port = Number(process.env.PORT || 80)
server.listen(port, () => console.log(`preview proxy listening on ${port}`))
