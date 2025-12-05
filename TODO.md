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
- Add License.md, security.md, privacy.md legal.md contribute.md sponsors
- Add option to retrieve and load keys for hidden service (export account / import account)

### UI:
- Force Tor Checkbox Active (until other way to P2P without Tor available)
- Hidde New Chat Button outside main menu

### Right now:
- Debug Kotlin Data using toasts to create default info.
- Add Tor bins.
- Launch Tor as service when app starts.
- Check if hidden service exists (if not, create hidden service)
- Send hidden service domain to Kotlin Data
- Send hidden service domain to javascript to update the account data UI.
- Make sure data is preserved locally (in app or in dom storage??? which one is better)
- Use a MVP extendable protocol to test simple message sending and update in app


### Security:
- Block inline to avoid xss

### Paid
- Option to multiacc. Manage / Swap / Delete multiple accounts. 
