// DMA Messenger Application Logic
class DarkMessengerApp {
  constructor() {
    this.currentView = 'chats';
    this.currentChat = null;
    this.editingContactIndex = null;
    this.userData = null;

    // Inicializar con estructuras vacías (se llenarán desde Kotlin)
    this.chats = [];
    this.contacts = [];
    this.messages = {};
    this.settings = {};

    this.init();
  }

  async init() {
    // Cargar datos por defecto desde Kotlin
    await this.loadDefaultData();

    // Cargar datos del usuario desde Kotlin
    await this.loadUserData();

    // Cargar configuraciones desde Kotlin
    await this.loadSettings();

    // Cargar vistas iniciales
    this.loadChats();
    this.loadContacts();

    // Mostrar toast de bienvenida
    setTimeout(() => {
      this.showToast("Dark Messenger Started");
    }, 1000);
  }

  async loadDefaultData() {
    try {
      if (window.dma && dma.getDefaultData) {
        const defaultData = JSON.parse(dma.getDefaultData());
        
        // Cargar contactos por defecto
        if (defaultData.contacts && defaultData.contacts.length > 0) {
          this.contacts = defaultData.contacts.map(contact => ({
            id: contact.id,
            name: contact.name,
            onion: contact.onion,
            status: contact.status || "Online"
          }));
        }
        
        // Cargar chats por defecto
        if (defaultData.defaultChats && defaultData.defaultChats.length > 0) {
          this.chats = defaultData.defaultChats.map(chat => ({
            id: chat.id,
            contactId: chat.contactId,
            lastMessage: chat.lastMessage,
            timestamp: chat.timestamp || new Date().toISOString(),
            unread: chat.unread || false
          }));
        }
        
        // Cargar mensajes por defecto
        if (defaultData.defaultMessages) {
          // Asegurarse de que los mensajes tengan timestamp si no lo tienen
          Object.keys(defaultData.defaultMessages).forEach(chatId => {
            this.messages[chatId] = defaultData.defaultMessages[chatId].map(msg => ({
              ...msg,
              timestamp: msg.timestamp || new Date(Date.now() - 3600000).toISOString()
            }));
          });
        }
      }
    } catch (error) {
      console.error("Error loading default data from Kotlin:", error);
      // Si falla la carga, dejar estructuras vacías
      this.showToast("Error loading default data");
    }
  }

  async loadUserData() {
    try {
      if (window.dma && dma.getUserData) {
        const data = JSON.parse(dma.getUserData());
        this.userData = data;

        // Actualizar UI con datos del usuario
        if (data.username) {
          document.getElementById('username').textContent = data.username;
        }
        if (data.onionAddress) {
          document.getElementById('user-onion').textContent = data.onionAddress;
        }

        // Si el usuario tiene contactos guardados, usarlos (sobrescribiendo los por defecto)
        if (data.contacts && data.contacts.length > 0) {
          this.contacts = data.contacts.map((contact, index) => ({
            id: index,
            name: contact.name,
            onion: contact.onion,
            status: "Online" // TODO: Obtener estado real
          }));
        }
      }
    } catch (error) {
      console.error("Error loading user data from Kotlin:", error);
      this.showToast("Error loading user data");
    }
  }

  async loadSettings() {
    try {
      if (window.dma && dma.getSettings) {
        const settingsData = JSON.parse(dma.getSettings());
        if (settingsData && Object.keys(settingsData).length > 0) {
          this.settings = settingsData;
        } else {
          // Cargar configuraciones por defecto si no hay guardadas
          if (window.dma && dma.getDefaultSettings) {
            const defaultSettings = JSON.parse(dma.getDefaultSettings());
            this.settings = defaultSettings;
          }
        }
      }
    } catch (error) {
      console.error("Error loading settings from Kotlin:", error);
      this.showToast("Error loading settings");
    }
  }

  // Navegación
  showView(viewId) {
    // Ocultar todas las vistas
    document.querySelectorAll('.view').forEach(view => {
      view.classList.remove('active');
    });

    // Mostrar la vista solicitada
    const view = document.getElementById(viewId);
    if (view) {
      view.classList.add('active');
      this.currentView = viewId;
    }
  }

  showChatsView() {
    this.showView('chatsView');
    this.loadChats();
  }

  showChatView(chatId) {
    this.currentChat = chatId;
    const chat = this.chats.find(c => c.id === chatId);

    if (chat) {
      const contact = this.contacts.find(c => c.id === chat.contactId);
      if (contact) {
        document.getElementById('chatContactName').textContent = contact.name;
        document.getElementById('chatContactStatus').textContent = contact.status;
      }

      this.loadMessages(chatId);
      this.markChatAsRead(chatId);
      this.showView('chatView');
    }
  }

  showContactsView(mode = 'view') {
    this.showView('contactsView');
    const title = document.getElementById('contactsViewTitle');
    const actionBtn = document.getElementById('contactsActionBtn');

    if (mode === 'newChat') {
      title.textContent = 'New Chat';
      actionBtn.style.display = 'none';
    } else {
      title.textContent = 'Contacts';
      actionBtn.style.display = 'block';
    }

    this.loadContacts(mode);
  }

  showSettingsView() {
    this.showView('settingsView');
    this.loadSettings();
  }

  showNewChat() {
    this.showContactsView('newChat');
  }

  // Sidebar
  toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    sidebar.style.left = '0';
    overlay.style.display = 'block';
  }

  closeSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    sidebar.style.left = '-300px';
    overlay.style.display = 'none';
  }

  // Chats
  loadChats() {
    const chatsList = document.getElementById('chatsList');
    chatsList.innerHTML = '';

    this.chats.forEach(chat => {
      const contact = this.contacts.find(c => c.id === chat.contactId);
      if (!contact) return;

      const time = new Date(chat.timestamp);
      const timeStr = time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

      const chatItem = document.createElement('div');
      chatItem.className = `chat-item ${chat.unread ? 'unread' : ''}`;
      chatItem.onclick = () => this.showChatView(chat.id);

      chatItem.innerHTML = `
                <div class="chat-avatar">
                    <i class="fas fa-user-secret"></i>
                </div>
                <div class="chat-info">
                    <div class="chat-header-row">
                        <span class="chat-contact-name">${contact.name}</span>
                        <span class="chat-time">${timeStr}</span>
                    </div>
                    <div class="last-message">${chat.lastMessage}</div>
                </div>
                ${chat.unread ? '<div class="unread-badge">!</div>' : ''}
            `;

      chatsList.appendChild(chatItem);
    });
  }

  loadMessages(chatId) {
    const messagesContainer = document.getElementById('chatMessages');
    messagesContainer.innerHTML = '';

    const messages = this.messages[chatId] || [];

    messages.forEach(message => {
      const time = new Date(message.timestamp);
      const timeStr = time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

      const messageDiv = document.createElement('div');
      messageDiv.className = `message ${message.incoming ? 'incoming' : 'outgoing'}`;

      messageDiv.innerHTML = `
                <div class="message-text">${message.text}</div>
                <div class="message-time">${timeStr}</div>
            `;

      messagesContainer.appendChild(messageDiv);
    });

    // Scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  markChatAsRead(chatId) {
    const chat = this.chats.find(c => c.id === chatId);
    if (chat) {
      chat.unread = false;
      this.loadChats();
    }
  }

  // Contacts
  loadContacts(mode = 'view') {
    const contactsList = document.getElementById('contactsList');
    contactsList.innerHTML = '';

    this.contacts.forEach(contact => {
      const contactItem = document.createElement('div');
      contactItem.className = 'contact-item';

      contactItem.innerHTML = `
                <div class="contact-avatar">
                    <i class="fas fa-user-secret"></i>
                </div>
                <div class="contact-details">
                    <div class="contact-name">${contact.name}</div>
                    <div class="contact-onion">${contact.onion}</div>
                    <div class="contact-status">${contact.status}</div>
                </div>
                <div class="contact-actions">
                    ${mode === 'newChat' ?
                        `<button class="contact-btn" onclick="app.startChatWithContact(${contact.id})">
                            <i class="fas fa-comment"></i>
                        </button>` :
                        `<button class="contact-btn" onclick="app.editContact(${contact.id})">
                            <i class="fas fa-edit"></i>
                        </button>`
                    }
                </div>
            `;

      contactsList.appendChild(contactItem);
    });
  }

  startChatWithContact(contactId) {
    const contact = this.contacts.find(c => c.id === contactId);
    if (!contact) return;

    // Buscar si ya existe un chat con este contacto
    let existingChat = this.chats.find(chat => chat.contactId === contactId);

    if (!existingChat) {
      // Crear nuevo chat
      const newChatId = Math.max(...this.chats.map(c => c.id), 0) + 1;
      existingChat = {
        id: newChatId,
        contactId: contactId,
        lastMessage: "No messages yet",
        timestamp: new Date().toISOString(),
        unread: false
      };
      this.chats.push(existingChat);
      this.messages[newChatId] = [];
    }

    this.showChatView(existingChat.id);
    this.showToast(`Started chat with ${contact.name}`);
  }

  addNewContact() {
    document.getElementById('contactName').value = '';
    document.getElementById('contactOnion').value = '';
    this.showModal('newContactModal');
  }

  saveContact() {
    const name = document.getElementById('contactName').value.trim();
    const onion = document.getElementById('contactOnion').value.trim();

    if (!name || !onion) {
      this.showToast('Please fill in all fields');
      return;
    }

    const newContact = {
      id: Math.max(...this.contacts.map(c => c.id), 0) + 1,
      name: name,
      onion: onion,
      status: "Online"
    };

    this.contacts.push(newContact);
    this.loadContacts();
    this.closeModal('newContactModal');

    // Guardar en Kotlin
    this.saveContactsToKotlin();

    this.showToast('Contact added successfully');
  }

  editContact(contactId) {
    const contact = this.contacts.find(c => c.id === contactId);
    if (!contact) return;

    this.editingContactIndex = contactId;
    document.getElementById('editContactName').value = contact.name;
    document.getElementById('editContactOnion').value = contact.onion;

    this.showModal('editContactModal');
  }

  updateContact() {
    const name = document.getElementById('editContactName').value.trim();
    const onion = document.getElementById('editContactOnion').value.trim();

    if (!name || !onion) {
      this.showToast('Please fill in all fields');
      return;
    }

    const contactIndex = this.contacts.findIndex(c => c.id === this.editingContactIndex);
    if (contactIndex !== -1) {
      this.contacts[contactIndex].name = name;
      this.contacts[contactIndex].onion = onion;
    }

    this.loadContacts();
    this.closeModal('editContactModal');

    // Guardar en Kotlin
    this.saveContactsToKotlin();

    this.showToast('Contact updated successfully');
  }

  deleteContact() {
    if (confirm('Are you sure you want to delete this contact?')) {
      const contactIndex = this.contacts.findIndex(c => c.id === this.editingContactIndex);
      if (contactIndex !== -1) {
        this.contacts.splice(contactIndex, 1);
      }

      this.loadContacts();
      this.closeModal('editContactModal');

      // Guardar en Kotlin
      this.saveContactsToKotlin();

      this.showToast('Contact deleted');
    }
  }

  async saveContactsToKotlin() {
    if (window.dma && dma.updateContacts) {
      const contactsData = this.contacts.map(contact => ({
        name: contact.name,
        onion: contact.onion
      }));

      const jsonData = JSON.stringify({ contacts: contactsData });
      dma.updateContacts(jsonData);
    }
  }

  // Settings
  loadSettings() {
    const container = document.querySelector('.settings-container');
    container.innerHTML = '';

    // Verificar si hay configuraciones cargadas
    if (Object.keys(this.settings).length === 0) {
      container.innerHTML = '<p>Loading settings...</p>';
      return;
    }

    // darkmessenger.conf
    const darkmessengerSection = this.createSettingsSection('darkmessenger.conf', 'darkmessenger');
    container.appendChild(darkmessengerSection);

    // torrc.conf
    const torrcSection = this.createSettingsSection('torrc.conf', 'torrc');
    container.appendChild(torrcSection);

    // Save button
    const saveButton = document.createElement('button');
    saveButton.className = 'btn btn-primary';
    saveButton.style.width = '100%';
    saveButton.style.marginTop = '20px';
    saveButton.textContent = 'Save All Settings';
    saveButton.onclick = () => this.saveSettings();
    container.appendChild(saveButton);
  }

  createSettingsSection(title, sectionKey) {
    const section = document.createElement('div');
    section.className = 'settings-section';

    const header = document.createElement('div');
    header.className = 'settings-header';
    header.onclick = (e) => {
      const content = e.currentTarget.nextElementSibling;
      content.classList.toggle('active');
    };

    header.innerHTML = `
            <h3>${title}</h3>
            <i class="fas fa-chevron-down"></i>
        `;

    const content = document.createElement('div');
    content.className = 'settings-content active';

    const settings = this.settings[sectionKey];
    if (settings) {
      content.appendChild(this.createSettingsForm(settings, sectionKey));
    } else {
      content.innerHTML = '<p>No settings available</p>';
    }

    section.appendChild(header);
    section.appendChild(content);

    return section;
  }

  createSettingsForm(settings, prefix = '') {
    const form = document.createElement('div');

    for (const [key, value] of Object.entries(settings)) {
      if (typeof value === 'object' && value.type) {
        // Es un setting individual
        const settingItem = this.createSettingItem(key, value, prefix);
        form.appendChild(settingItem);
      } else if (typeof value === 'object') {
        // Es una subsección
        const subsection = document.createElement('div');
        subsection.className = 'settings-subsection';

        const subsectionTitle = document.createElement('h4');
        subsectionTitle.textContent = this.capitalizeFirstLetter(key);
        subsectionTitle.style.margin = '16px 0 8px 0';
        subsectionTitle.style.color = 'var(--text-secondary)';
        subsection.appendChild(subsectionTitle);

        subsection.appendChild(this.createSettingsForm(value, `${prefix}.${key}`));
        form.appendChild(subsection);
      }
    }

    return form;
  }

  createSettingItem(key, setting, prefix) {
    const item = document.createElement('div');
    item.className = 'setting-item';

    const label = document.createElement('div');
    label.className = 'setting-label';

    const labelText = document.createElement('h4');
    labelText.textContent = this.formatSettingName(key);
    label.appendChild(labelText);

    const defaultValue = document.createElement('p');
    defaultValue.textContent = `Default: ${setting.default}`;
    defaultValue.style.fontSize = '12px';
    label.appendChild(defaultValue);

    const control = document.createElement('div');
    control.className = 'setting-control';

    const inputId = `${prefix}.${key}`.replace(/\./g, '-');

    switch (setting.type) {
      case 'toggle':
        control.innerHTML = `
                    <label class="toggle-switch">
                        <input type="checkbox" id="${inputId}" ${setting.value ? 'checked' : ''}>
                        <span class="toggle-slider"></span>
                    </label>
                `;
        break;

      case 'text':
        control.innerHTML = `
                    <input type="text" id="${inputId}" class="text-input" value="${setting.value || ''}">
                `;
        break;

      case 'number':
        control.innerHTML = `
                    <input type="number" id="${inputId}" class="text-input number-input" value="${setting.value}">
                `;
        break;
    }

    item.appendChild(label);
    item.appendChild(control);

    return item;
  }

  async saveSettings() {
    // Actualizar settings desde los inputs
    this.updateSettingsFromUI();

    // Guardar en Kotlin
    if (window.dma && dma.updateSettings) {
      const jsonData = JSON.stringify(this.settings);
      dma.updateSettings(jsonData);
    }

    this.showToast('Settings saved successfully');
  }

  updateSettingsFromUI() {
    for (const [sectionKey, section] of Object.entries(this.settings)) {
      this.updateSectionSettings(section, sectionKey);
    }
  }

  updateSectionSettings(section, prefix) {
    for (const [key, value] of Object.entries(section)) {
      const inputId = `${prefix}.${key}`.replace(/\./g, '-');
      const input = document.getElementById(inputId);

      if (input) {
        if (value.type === 'toggle') {
          value.value = input.checked;
        } else if (value.type === 'number') {
          value.value = parseInt(input.value) || value.default;
        } else {
          value.value = input.value || value.default;
        }
      } else if (typeof value === 'object' && !value.type) {
        // Es una subsección
        this.updateSectionSettings(value, `${prefix}.${key}`);
      }
    }
  }

  // Mensajes
  sendMessage() {
    const input = document.getElementById('messageInput');
    const text = input.value.trim();

    if (!text || !this.currentChat) return;

    // Agregar mensaje
    const message = {
      id: Date.now(),
      senderId: null, // null = yo
      text: text,
      timestamp: new Date().toISOString(),
      incoming: false
    };

    if (!this.messages[this.currentChat]) {
      this.messages[this.currentChat] = [];
    }

    this.messages[this.currentChat].push(message);

    // Actualizar último mensaje en el chat
    const chat = this.chats.find(c => c.id === this.currentChat);
    if (chat) {
      chat.lastMessage = text;
      chat.timestamp = new Date().toISOString();
    }

    // Limpiar input y recargar
    input.value = '';
    this.loadMessages(this.currentChat);
    this.loadChats();

    // Enviar a través de DMA (simulado por ahora)
    if (window.dma && dma.toast) {
      dma.toast(`Message sent to chat ${this.currentChat}`);
    }
  }

  handleMessageInput(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  // UI Helpers
  showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.add('show');

    setTimeout(() => {
      toast.classList.remove('show');
    }, 3000);

    // También mostrar toast nativo
    /*if (window.dma && dma.toast) {
      dma.toast(message);
    }*/
  }

  showModal(modalId) {
    document.getElementById(modalId).classList.add('active');
  }

  closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
  }

  capitalizeFirstLetter(string) {
    return string.charAt(0).toUpperCase() + string.slice(1);
  }

  formatSettingName(key) {
    return key.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase());
  }

  logout() {
    if (confirm('Are you sure you want to logout?')) {
      this.showToast('Logged out');
      this.closeSidebar();
    }
  }

  searchChats() {
    this.showToast('Search functionality coming soon');
  }

  showArchivedChats() {
    this.showToast('Archived chats coming soon');
    this.closeSidebar();
  }

  toggleChatInfo() {
    this.showToast('Chat info coming soon');
  }

  showChatMenu() {
    this.showToast('Chat menu coming soon');
  }

  attachFile() {
    this.showToast('File attachment coming soon');
  }

  showEmojiPicker() {
    this.showToast('Emoji picker coming soon');
  }
}

// Inicializar la aplicación
let app;
document.addEventListener('DOMContentLoaded', () => {
  app = new DarkMessengerApp();
  window.app = app;

  // Mostrar toast inicial
  setTimeout(() => {
    if (window.dma && dma.toast) {
      dma.toast("Dark Messenger Interface Loaded");
    }
  }, 500);
});

// Exponer funciones globales para onclick handlers
function toggleSidebar() { app.toggleSidebar(); }
function closeSidebar() { app.closeSidebar(); }
function showChatsView() { app.showChatsView(); }
function showContacts() { app.showContactsView('view'); }
function showSettings() { app.showSettingsView(); }
function showNewChat() { app.showNewChat(); }
function sendMessage() { app.sendMessage(); }
function handleMessageInput(event) { app.handleMessageInput(event); }
function addNewContact() { app.addNewContact(); }
function saveContact() { app.saveContact(); }
function editContact(id) { app.editContact(id); }
function updateContact() { app.updateContact(); }
function deleteContact() { app.deleteContact(); }
function closeModal(modalId) { app.closeModal(modalId); }
function logout() { app.logout(); }
function searchChats() { app.searchChats(); }
function showArchivedChats() { app.showArchivedChats(); }
function toggleChatInfo() { app.toggleChatInfo(); }
function showChatMenu() { app.showChatMenu(); }
function attachFile() { app.attachFile(); }
function showEmojiPicker() { app.showEmojiPicker(); }
