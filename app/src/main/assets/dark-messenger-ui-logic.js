// DMA JavaScript Interface
const DMA = {
  // Función para mostrar toast
  showToast: function(message) {
    if (window.dma && dma.toast) {
      dma.toast(message);
      this.addLog('Toast sent: ' + message);
    } else {
      this.addLog('Error: DMA interface not available');
      alert('Toast would show: ' + message);
    }
  },

  // Funciones para la consola
  addLog: function(message) {
    const consoleEl = document.getElementById('console');
    const timestamp = new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit', second:'2-digit'});

    const logLine = document.createElement('div');
    logLine.className = 'console-line';
    logLine.innerHTML = '<span class="timestamp">[' + timestamp + ']</span><span class="message"> ' + message + '</span>';

    consoleEl.appendChild(logLine);
    consoleEl.scrollTop = consoleEl.scrollHeight;
  },

  clearConsole: function() {
    const consoleEl = document.getElementById('console');
    consoleEl.innerHTML = '<div class="console-line"><span class="timestamp">[14:00:01]</span><span class="message"> System initialized...</span></div>';
    this.addLog('Console cleared');
    this.showToast('Console cleared');
  },

  testToast: function() {
    this.showToast('Dark Messenger Android Test Message');
  },

  showWelcome: function() {
    this.showToast('Dark Messenger Android Started');
  },

  sendCustomToast: function() {
    const input = document.getElementById('messageInput');
    const message = input.value.trim();

    if (message) {
      this.showToast(message);
      input.value = '';
    } else {
      this.showToast('Please enter a message');
    }
  },

  // Inicialización
  init: function() {
    // Mostrar toast de inicio
    setTimeout(() => {
      this.showToast('Dark Messenger Android Interface Loaded');
      this.addLog('DMA interface ready');
    }, 1000);

    // Permitir Enter en el input
    document.getElementById('messageInput').addEventListener('keypress', function(e) {
      if (e.key === 'Enter') {
        DMA.sendCustomToast();
      }
    });

    // Exponer funciones globalmente para los eventos onclick
    window.testToast = () => this.testToast();
    window.showWelcome = () => this.showWelcome();
    window.sendCustomToast = () => this.sendCustomToast();
    window.addLog = (msg) => this.addLog(msg);
    window.clearConsole = () => this.clearConsole();

    this.addLog('DMA JavaScript initialized');
  }
};

// Inicializar cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', function() {
  DMA.init();
});

// Para debugging
console.log('Dark Messenger Android UI loaded');
