import { Rcon } from 'rcon-client'

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' })
  }

  const { code } = req.body

  if (!code || code.length !== 6 || !/^\d{6}$/.test(code)) {
    return res.status(400).json({ success: false, error: 'Código inválido. Debe ser 6 dígitos.' })
  }

  let rcon = null
  try {
    rcon = await Rcon.connect({
      host: process.env.RCON_HOST,
      port: parseInt(process.env.RCON_PORT),
      password: process.env.RCON_PASSWORD,
      timeout: 5000,
    })

    // Ask the server to validate the link code
    // The plugin should handle a command like: linkvalidate <code>
    const response = await rcon.send(`linkvalidate ${code}`)

    await rcon.end()

    // Parse response from plugin
    // Expected: "OK:<playername>" or "ERROR:<message>"
    if (response.startsWith('OK:')) {
      const playerName = response.substring(3).trim()
      return res.status(200).json({ success: true, playerName })
    } else if (response.startsWith('ERROR:')) {
      const errorMsg = response.substring(6).trim()
      return res.status(200).json({ success: false, error: errorMsg })
    } else {
      // Fallback: try to interpret any response
      if (response.includes('not found') || response.includes('invalid') || response.includes('Unknown')) {
        return res.status(200).json({ success: false, error: 'Código no válido o expirado. Genera uno nuevo con /linkmc' })
      }
      return res.status(200).json({ success: false, error: 'Respuesta inesperada del servidor' })
    }
  } catch (err) {
    console.error('RCON error:', err.message)
    if (rcon) {
      try { await rcon.end() } catch (_) {}
    }
    return res.status(500).json({
      success: false,
      error: 'No se pudo conectar al servidor. Inténtalo de nuevo más tarde.'
    })
  }
}
