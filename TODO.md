# TODO:

### List of pending changes:

- Make all icons and media (improve icons png)
- Make Android Interface
- Add option to publish user domain (if he wants to). Need to think where and how to publish them anonimously (to have a userbase public list)
- Fake message sending when the remote contact is not online and try to send it from a service running in the background
- Allow multiple accounts (multiple hidden service keys) need to add a button for it.
- Add password option to log in the account (Kotlin data encrypted)
- Allow users to be included into default_contacts.json if they want too
- Add in-app updates
- Add Tor bin build workflow for updates?
- Add License.md, faq.md, security.md, privacy.md legal.md contribute.md sponsors
- Add option to retrieve and load keys for hidden service (export account / import account)

### UI:
- Force Tor Checkbox Active (until other way to P2P without Tor available)
- Hidde New Chat Button outside main menu
- Remove extra toast from clipboard
- Fix full domain breaking contacts/newChat list in portrait not allowing for opening new conversation

### Right now:
- Add http server attached to hidden service.
- /send endpoint to read messages.
- /addme endpoint to allow remote user to automatically add you to contacts
- Allow chats with non contacts
- Special chat to talk to himself (detect if trying to talk to onion adress in use and cancel request)
- Launch Tor as service when app starts.
- Use a MVP extendable protocol to test simple message sending and update in app

### Documentation
- Link docu and todo on readme
- Add user basic usage guide (include in apk and display to user in webview)?

### Security:
- Block inline to avoid xss
- Remove port control binding by default
- Tor security settings by default
- Auth for tor port (add setting to manage it in case user wants to use tor for other apps)
- Add advanced ERK crypto (update it first and improve it)
- Settings to limit webview
- Ship a cromite fork webview instead of using system default webview
- Remove system previews, video recordings, etc.
- Local encrypted database

### Paid
- Option to multiacc. Manage / Swap / Delete multiple accounts. Logout/Login.
- Pin to access app
- Option to destroy app data.
- Private VPN.
- Private Tor relays. 
- In app keyboard
- In app videos
- Tasks
- Custom interface, themes, etc
- Courses, demos and extrusive access to extra content about privacy and security
- Block contacts
- Allow screenshoots
- Modal/Widget mode
- Custom app icon
- Voice messages (voice TTS to full privacy)
- Block users
- Notes
- Cloud Storage
- Groups
- Autodelete if app not accesed or wrong ping X times
- MFA
- Notifications
- Manage files in app (encrypted, files never touch phone storage decrypted)
- Streaming (Videocalls)
- Videocalls with TTS and custom camera that distort face features
- Language translation
- Paid messages (to recive messages, users have to pay you)
- 24/7 Direct support
