import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Sword, Shield, Wand2, Target, Skull, Users, Trophy, Map, Link2, ChevronRight, Sparkles, Zap } from 'lucide-react'
import Head from 'next/head'

const classes = [
  { name: 'Guerrero', icon: Sword, color: '#e74c3c', subs: ['Tank', 'DPS', 'Berserker'] },
  { name: 'Mago', icon: Wand2, color: '#3498db', subs: ['Elemental', 'Healer', 'Arcane'] },
  { name: 'Arquero', icon: Target, color: '#2ecc71', subs: ['Ranger', 'Hunter', 'Assassin'] },
  { name: 'Pícaro', icon: Zap, color: '#f39c12', subs: ['Thief', 'Saboteur', 'Envenenador'] },
  { name: 'Paladín', icon: Shield, color: '#f1c40f', subs: ['Holy', 'Retribution', 'Protection'] },
]

const features = [
  { icon: Sword, title: '5 Clases RPG', desc: '15 subclases con árboles de habilidades únicos', color: '#e74c3c' },
  { icon: Sparkles, title: '45 Habilidades', desc: 'Cada subclase con 3 habilidades activas con efectos visuales', color: '#6c5ce7' },
  { icon: Skull, title: 'World Bosses', desc: 'Bosses semanales con loot legendario y recompensas épicas', color: '#e74c3c' },
  { icon: Trophy, title: 'Logros', desc: '50+ logros graciosos desbloqueables por completar retos', color: '#f39c12' },
  { icon: Users, title: 'Sistema de Núcleos', desc: 'Protege tu base con 20 niveles de mejoras comprables', color: '#2ecc71' },
  { icon: Map, title: 'Oleadas de Mobs', desc: 'Defiende tu núcleo de oleadas automáticas cada 45 min', color: '#9b59b6' },
]

function Particles() {
  const [particles, setParticles] = useState([])
  useEffect(() => {
    const p = Array.from({ length: 40 }, (_, i) => ({
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

function Navbar() {
  return (
    <motion.nav
      initial={{ y: -80, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.6 }}
      className="fixed top-0 w-full z-50 backdrop-blur-xl bg-mc-darker/80 border-b border-mc-purple/20"
    >
      <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-mc-purple to-mc-blue flex items-center justify-center">
            <Shield className="w-6 h-6 text-white" />
          </div>
          <span className="text-xl font-bold text-white">Wardstone<span className="text-mc-purple">RPG</span></span>
        </div>
        <div className="hidden md:flex items-center gap-8">
          <a href="#classes" className="text-gray-400 hover:text-white transition-colors">Clases</a>
          <a href="#features" className="text-gray-400 hover:text-white transition-colors">Features</a>
          <a href="/link" className="px-5 py-2 bg-gradient-to-r from-mc-purple to-mc-blue rounded-lg text-white font-semibold hover:opacity-90 transition-opacity flex items-center gap-2">
            <Link2 className="w-4 h-4" /> Vincular Cuenta
          </a>
        </div>
      </div>
    </motion.nav>
  )
}

export default function Home() {
  return (
    <div className="bg-animated min-h-screen">
      <Head>
        <title>Wardstone RPG — Dashboard</title>
        <meta name="description" content="Dashboard RPG para el servidor Wardstone de Minecraft" />
        <link rel="icon" href="/favicon.ico" />
      </Head>

      <Particles />
      <Navbar />

      {/* Hero */}
      <section className="relative min-h-screen flex items-center justify-center px-6 pt-20">
        <div className="absolute inset-0 bg-gradient-to-b from-mc-purple/5 via-transparent to-transparent" />
        <div className="relative z-10 text-center max-w-4xl">
          <motion.div
            initial={{ scale: 0.5, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ duration: 0.8, type: 'spring' }}
            className="mb-6"
          >
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-mc-purple/20 rounded-full border border-mc-purple/30 mb-8">
              <Sparkles className="w-4 h-4 text-mc-purple" />
              <span className="text-mc-purple text-sm font-semibold">Sistema RPG Completo</span>
            </div>
          </motion.div>

          <motion.h1
            initial={{ y: 40, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="text-5xl md:text-7xl font-black mb-6"
          >
            <span className="shimmer-text">WARDSTONE</span>
            <br />
            <span className="text-white">RPG</span>
          </motion.h1>

          <motion.p
            initial={{ y: 30, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="text-xl text-gray-400 mb-10 max-w-2xl mx-auto"
          >
            5 clases, 15 subclases, 45 habilidades, bosses semanales, logros, oleadas de mobs
            y mucho más. El RPG más completo de Minecraft.
          </motion.p>

          <motion.div
            initial={{ y: 30, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.6 }}
            className="flex flex-col sm:flex-row gap-4 justify-center"
          >
            <a href="/link" className="px-8 py-4 bg-gradient-to-r from-mc-purple to-mc-blue rounded-xl text-white font-bold text-lg hover:opacity-90 transition-all hover:scale-105 flex items-center justify-center gap-2 shadow-lg shadow-mc-purple/25">
              <Link2 className="w-5 h-5" /> Vincular Cuenta
            </a>
            <a href="#classes" className="px-8 py-4 bg-white/5 border border-white/10 rounded-xl text-white font-bold text-lg hover:bg-white/10 transition-all flex items-center justify-center gap-2">
              Ver Clases <ChevronRight className="w-5 h-5" />
            </a>
          </motion.div>
        </div>
      </section>

      {/* Classes */}
      <section id="classes" className="py-24 px-6">
        <div className="max-w-7xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-center mb-16"
          >
            <h2 className="text-4xl font-black text-white mb-4">⚔️ Clases RPG</h2>
            <p className="text-gray-400 text-lg">Elige tu camino. Cada clase tiene 3 subclases con habilidades únicas.</p>
          </motion.div>

          <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-6">
            {classes.map((cls, i) => (
              <motion.div
                key={cls.name}
                initial={{ opacity: 0, y: 40 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.1 }}
                whileHover={{ y: -8, scale: 1.03 }}
                className="glow-border rounded-2xl p-6 bg-mc-dark/80 backdrop-blur text-center cursor-pointer group"
              >
                <div className="w-16 h-16 rounded-xl mx-auto mb-4 flex items-center justify-center transition-all group-hover:scale-110"
                  style={{ background: `${cls.color}20`, border: `1px solid ${cls.color}40` }}>
                  <cls.icon className="w-8 h-8" style={{ color: cls.color }} />
                </div>
                <h3 className="text-lg font-bold text-white mb-3">{cls.name}</h3>
                <div className="space-y-1">
                  {cls.subs.map(sub => (
                    <div key={sub} className="text-sm text-gray-400 flex items-center justify-center gap-1">
                      <ChevronRight className="w-3 h-3" style={{ color: cls.color }} />
                      {sub}
                    </div>
                  ))}
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="py-24 px-6 bg-mc-dark/50">
        <div className="max-w-7xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-center mb-16"
          >
            <h2 className="text-4xl font-black text-white mb-4">🚀 Features</h2>
            <p className="text-gray-400 text-lg">Todo lo que incluye el sistema RPG de Wardstone.</p>
          </motion.div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {features.map((feat, i) => (
              <motion.div
                key={feat.title}
                initial={{ opacity: 0, y: 40 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.08 }}
                whileHover={{ y: -5 }}
                className="glow-border rounded-2xl p-8 bg-mc-darker/80 backdrop-blur"
              >
                <div className="w-12 h-12 rounded-lg mb-4 flex items-center justify-center"
                  style={{ background: `${feat.color}15`, border: `1px solid ${feat.color}30` }}>
                  <feat.icon className="w-6 h-6" style={{ color: feat.color }} />
                </div>
                <h3 className="text-xl font-bold text-white mb-2">{feat.title}</h3>
                <p className="text-gray-400">{feat.desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Stats Banner */}
      <section className="py-16 px-6">
        <div className="max-w-5xl mx-auto">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center"
          >
            {[
              { label: 'Clases', value: '5', color: '#e74c3c' },
              { label: 'Habilidades', value: '45', color: '#6c5ce7' },
              { label: 'Logros', value: '50+', color: '#f39c12' },
              { label: 'Niveles', value: '100', color: '#2ecc71' },
            ].map((stat, i) => (
              <motion.div
                key={stat.label}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.1 }}
              >
                <div className="text-4xl font-black mb-2" style={{ color: stat.color }}>{stat.value}</div>
                <div className="text-gray-400 text-sm uppercase tracking-wider">{stat.label}</div>
              </motion.div>
            ))}
          </motion.div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-24 px-6">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="max-w-3xl mx-auto text-center glow-border rounded-3xl p-12 bg-gradient-to-br from-mc-purple/10 to-mc-blue/10"
        >
          <h2 className="text-3xl font-black text-white mb-4">¿Listo para jugar?</h2>
          <p className="text-gray-400 mb-8">Vincula tu cuenta de Minecraft y empieza tu aventura RPG.</p>
          <a href="/link" className="inline-flex items-center gap-2 px-8 py-4 bg-gradient-to-r from-mc-purple to-mc-blue rounded-xl text-white font-bold text-lg hover:opacity-90 transition-all hover:scale-105 shadow-lg shadow-mc-purple/25">
            <Link2 className="w-5 h-5" /> Vincular Cuenta
          </a>
        </motion.div>
      </section>

      {/* Footer */}
      <footer className="py-8 px-6 border-t border-white/5">
        <div className="max-w-7xl mx-auto text-center text-gray-500 text-sm">
          © 2026 Wardstone RPG — MoonlightMC
        </div>
      </footer>
    </div>
  )
}
