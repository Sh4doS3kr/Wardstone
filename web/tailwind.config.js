/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './pages/**/*.{js,jsx}',
    './components/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      colors: {
        'mc-dark': '#1a1a2e',
        'mc-darker': '#0f0f1a',
        'mc-purple': '#6c5ce7',
        'mc-gold': '#f39c12',
        'mc-red': '#e74c3c',
        'mc-green': '#2ecc71',
        'mc-blue': '#3498db',
        'mc-cyan': '#00cec9',
      },
      fontFamily: {
        minecraft: ['"Press Start 2P"', 'monospace'],
      },
      animation: {
        'float': 'float 3s ease-in-out infinite',
        'glow': 'glow 2s ease-in-out infinite',
        'pulse-slow': 'pulse 3s ease-in-out infinite',
        'slide-up': 'slideUp 0.6s ease-out',
        'shimmer': 'shimmer 2s linear infinite',
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        glow: {
          '0%, 100%': { boxShadow: '0 0 5px rgba(108, 92, 231, 0.5)' },
          '50%': { boxShadow: '0 0 25px rgba(108, 92, 231, 0.8)' },
        },
        slideUp: {
          '0%': { transform: 'translateY(30px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
      },
    },
  },
  plugins: [],
}
