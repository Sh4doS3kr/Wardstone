import { useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Link2, ArrowLeft, Check, X, Loader2, Shield, Sparkles } from 'lucide-react'
import Head from 'next/head'

function Particles() {
  const [particles, setParticles] = useState([])
  useEffect(() => {
    const p = Array.from({ length: 30 }, (_, i) => ({
      id: i,
      left: Math.random() * 100,
      delay: Math.random() * 8,
      duration: 6 + Math.random() * 6,
      size: 1 + Math.random() * 3,
    }))
    setParticles(p)
  }, [])
  return (
    <div className="particles">
      {particles.map(p => (
        <div key={p.id} className="particle" style={{
          left: `${p.left}%`,
          width: `${p.size}px`,
          height: `${p.size}px`,
          animationDelay: `${p.delay}s`,
          animationDuration: `${p.duration}s`,
        }} />
      ))}
    </div>
  )
}

export default function LinkPage() {
  const [code, setCode] = useState(['', '', '', '', '', ''])
  const [status, setStatus] = useState('idle') // idle, loading, success, error
  const [message, setMessage] = useState('')
  const [playerName, setPlayerName] = useState('')
  const inputRefs = useRef([])

  const handleChange = (index, value) => {
    if (!/^\d*$/.test(value)) return
    const newCode = [...code]
    newCode[index] = value.slice(-1)
    setCode(newCode)

    // Auto-focus next input
    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus()
    }
  }

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus()
    }
    if (e.key === 'Enter') {
      handleSubmit()
    }
  }

  const handlePaste = (e) => {
    e.preventDefault()
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    const newCode = [...code]
    for (let i = 0; i < pasted.length; i++) {
      newCode[i] = pasted[i]
    }
    setCode(newCode)
    if (pasted.length > 0) {
      const focusIndex = Math.min(pasted.length, 5)
      inputRefs.current[focusIndex]?.focus()
    }
  }

  const handleSubmit = async () => {
    const fullCode = code.join('')
    if (fullCode.length !== 6) {
      setStatus('error')
      setMessage('Introduce los 6 dígitos del código')
      return
    }

    setStatus('loading')
    setMessage('Verificando código...')

    try {
      const res = await fetch('/api/link', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: fullCode }),
      })
      const data = await res.json()

      if (data.success) {
        setStatus('success')
        setPlayerName(data.playerName || 'Jugador')
        setMessage(`¡Cuenta vinculada exitosamente!`)
      } else {
        setStatus('error')
        setMessage(data.error || 'Código inválido o expirado')
      }
    } catch (err) {
      setStatus('error')
      setMessage('Error de conexión con el servidor')
    }
  }

  const resetForm = () => {
    setCode(['', '', '', '', '', ''])
    setStatus('idle')
    setMessage('')
    setPlayerName('')
    inputRefs.current[0]?.focus()
  }

  return (
    <div className="bg-animated min-h-screen flex items-center justify-center px-6">
      <Head>
        <title>Vincular Cuenta — Wardstone RPG</title>
      </Head>

      <Particles />

      <div className="relative z-10 w-full max-w-lg">
        {/* Back button */}
        <motion.a
          href="/"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          className="inline-flex items-center gap-2 text-gray-400 hover:text-white transition-colors mb-8"
        >
          <ArrowLeft className="w-4 h-4" /> Volver al inicio
        </motion.a>

        <motion.div
          initial={{ opacity: 0, y: 30, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.6, type: 'spring' }}
          className="glow-border rounded-3xl p-8 md:p-10 bg-mc-dark/90 backdrop-blur-xl"
        >
          {/* Header */}
          <div className="text-center mb-8">
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ delay: 0.2, type: 'spring', stiffness: 200 }}
              className="w-20 h-20 rounded-2xl bg-gradient-to-br from-mc-purple to-mc-blue flex items-center justify-center mx-auto mb-5 shadow-lg shadow-mc-purple/30"
            >
              <Link2 className="w-10 h-10 text-white" />
            </motion.div>
            <h1 className="text-2xl font-black text-white mb-2">Vincular Cuenta</h1>
            <p className="text-gray-400">
              Escribe <code className="text-mc-purple font-mono bg-mc-purple/10 px-2 py-0.5 rounded">/linkmc</code> en el servidor
              y pega aquí tu código de 6 dígitos.
            </p>
          </div>

          <AnimatePresence mode="wait">
            {status === 'success' ? (
              <motion.div
                key="success"
                initial={{ opacity: 0, scale: 0.8 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.8 }}
                className="text-center py-8"
              >
                <motion.div
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  transition={{ type: 'spring', stiffness: 200, delay: 0.1 }}
                  className="w-24 h-24 rounded-full bg-green-500/20 border-2 border-green-500 flex items-center justify-center mx-auto mb-6"
                >
                  <Check className="w-12 h-12 text-green-500" />
                </motion.div>
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.3 }}
                >
                  <h2 className="text-2xl font-bold text-green-400 mb-2">¡Vinculado!</h2>
                  <p className="text-gray-400 mb-2">Cuenta conectada como:</p>
                  <p className="text-xl font-bold text-white flex items-center justify-center gap-2">
                    <Sparkles className="w-5 h-5 text-mc-gold" /> {playerName}
                  </p>
                </motion.div>
                <motion.button
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: 0.5 }}
                  onClick={resetForm}
                  className="mt-8 px-6 py-3 bg-white/5 border border-white/10 rounded-xl text-white hover:bg-white/10 transition-all"
                >
                  Vincular otra cuenta
                </motion.button>
              </motion.div>
            ) : (
              <motion.div
                key="form"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                {/* Code Input */}
                <div className="flex justify-center gap-3 mb-6">
                  {code.map((digit, i) => (
                    <motion.input
                      key={i}
                      ref={el => inputRefs.current[i] = el}
                      type="text"
                      inputMode="numeric"
                      maxLength={1}
                      value={digit}
                      onChange={e => handleChange(i, e.target.value)}
                      onKeyDown={e => handleKeyDown(i, e)}
                      onPaste={i === 0 ? handlePaste : undefined}
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.3 + i * 0.05 }}
                      className={`w-12 h-14 md:w-14 md:h-16 text-center text-2xl font-bold rounded-xl
                        bg-mc-darker border-2 transition-all duration-200 outline-none code-input
                        ${digit ? 'border-mc-purple text-white shadow-lg shadow-mc-purple/20' : 'border-white/10 text-gray-500'}
                        focus:border-mc-purple focus:shadow-lg focus:shadow-mc-purple/30`}
                    />
                  ))}
                </div>

                {/* Error message */}
                <AnimatePresence>
                  {status === 'error' && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      className="flex items-center gap-2 text-red-400 text-sm justify-center mb-4"
                    >
                      <X className="w-4 h-4" /> {message}
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Submit button */}
                <motion.button
                  onClick={handleSubmit}
                  disabled={status === 'loading'}
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  className="w-full py-4 bg-gradient-to-r from-mc-purple to-mc-blue rounded-xl text-white font-bold text-lg
                    hover:opacity-90 transition-all disabled:opacity-50 disabled:cursor-not-allowed
                    flex items-center justify-center gap-2 shadow-lg shadow-mc-purple/25"
                >
                  {status === 'loading' ? (
                    <><Loader2 className="w-5 h-5 animate-spin" /> Verificando...</>
                  ) : (
                    <><Shield className="w-5 h-5" /> Vincular Cuenta</>
                  )}
                </motion.button>

                {/* Help text */}
                <div className="mt-6 p-4 bg-mc-darker/50 rounded-xl border border-white/5">
                  <h4 className="text-sm font-semibold text-white mb-2">¿Cómo vincular?</h4>
                  <ol className="text-xs text-gray-400 space-y-1">
                    <li>1. Entra al servidor de Minecraft</li>
                    <li>2. Escribe <code className="text-mc-purple">/linkmc</code> en el chat</li>
                    <li>3. Copia el código de 6 dígitos que aparece</li>
                    <li>4. Pégalo aquí arriba y pulsa "Vincular"</li>
                  </ol>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </div>
    </div>
  )
}
