#!/usr/bin/env bash

source ./parseCLI
parse_cli "$@"

# Output functions
exit() {
  local msg="$1"
  echo -e "$msg"
  builtin exit 0
}

debug() {
  local msg="$1"

  # Si no está activado el debug, salir
  [[ "$d" != "true" ]] && return

  if [[ "$debug_with_time" == "true" ]]; then
    local now=$(date +"%H:%M:%S.%3N")
    # macOS alt
    if [[ "$now" == *"%3N"* ]]; then
      local now=$(date +"%H:%M:%S")".$(printf "%03d" $((RANDOM % 1000)))"
    fi

    echo "$(cli color bold blue "[DEBUG-$now]") $msg"
  else
    echo "$(cli color bold blue "[DEBUG]") $msg"
  fi
}

error() {
  local msg="$1"
  echo -e "$(cli color bold red "[ERROR]") $msg"
  builtin exit 0
}



# Logic functions
add() {
  debug "Getting alias and address from cli args ..."
  shift
  local alias="$1"
  local address="$2"
  debug "Alias $alias, Address $address"

  debug "Testing if alias is a valid format ..."
  if ! [[ "$alias" =~ ^[a-zA-Z0-9_.@-]{1,99}$ ]]; then
    error "Alias can only use alphanumeric characters and be 1 to 99 characters long\nThis characters are also allowed: - _ . @"
    exit
  fi

  debug "Testing if onion address is valid ..."
  if ! [[ "$address" =~ ^([a-z2-7]{16}|[a-z2-7]{56})\.onion$ ]]; then
    error "The onion address is not valid. Make sure you adding a real address"
  fi

  debug "Add implementation is not finished yet"
}

: << 'TODO'
const add = async (cli) => {

  let addressBook = [];
  try {
    debug(`Reading ./address_book/list.txt ... `);
    const data = await fs.promises.readFile('./address_book/list.txt', 'utf8');
    debug(`Splitting entries by line ... `);
    addressBook = data.split('\n').map(line => line.trim()).filter(line => line !== '');
    debug(`A total of ${addressBook.length} contacts found in your addresses list`);
  } catch (err) {
    error('Error reading file:', err);
    process.exit(0);
  }

  /* Check for duplicates to prevent username spoofing */
  for (let i in addressBook) {
    const auxAlias = addressBook[i].split(" ")[0];
    if (auxAlias == alias) {
      error(`Alias "${alias}" already exists, use a different alias`);
      process.exit(0);
    }
  }

  debug(`Adding new entry to in-memory address book ... `);
  addressBook.push(`${alias} ${address}`);
  debug(`Removing all duplicates (if there is any) ... `);
  const uniqueEntries = new Set(addressBook);
  debug(`Casting addressBook array to a string (ready to dump in file)... `);
  const updatedText = Array.from(uniqueEntries).join('\n');

  try {
    debug(`Rewrite address_book/list.txt with the new ssv alias address pair`);
    await fs.promises.writeFile('./address_book/list.txt', updatedText);
    console.log("Added to your address book");
  } catch (err) {
    error(`Error writting on the address book: ${err}`);
  }
  debug(`Done`);
}
TODO



addme() {
  local onion="$1"
  echo "Solicitando agregarme en: $onion"
}

contacts() {
  local alias="$1"
  if [[ -n "$alias" ]]; then
    echo "Mostrando contacto: $alias"
  else
    echo "Mostrando todos los contactos"
  fi
}

send() {
  local alias="$1"
  local message="$2"
  echo "Enviando mensaje a $alias: $message"
}

show() {
  local alias="$1"
  if [[ -n "$alias" ]]; then
    echo "Mostrando mensajes de: $alias"
  else
    echo "Mostrando todos los mensajes"
  fi
}

del() {
  local id="$1"
  echo "Eliminando mensaje con ID: $id"
}

wcdyu() {
  echo "Consultando criptografía disponible en el servidor"
}

getPublicKey() {
  echo "Obteniendo clave pública ERK del servidor"
}


# Logic
if cli noArgs || cli s h || cli c help; then
    echo ""
    echo "Usage:"
    echo ""
    echo "  $(cli color bold yellow "add") $(cli color italic white "[alias] [domain.onion]")           $(cli color white "Add an address to your Address Book")"
    echo "  $(cli color bold yellow "addme") $(cli color italic white "[domain.onion]")                 $(cli color white "Tell remote server to add you")"
    echo "  $(cli color bold yellow "contacts") $(cli color italic white "<alias>")                     $(cli color white "Show a contact address or all contacts")"
    echo "  $(cli color bold yellow "send") $(cli color italic white "[alias] [message]")               $(cli color white "Send a message")"
    echo "  $(cli color bold yellow "show") $(cli color italic white "<alias>")                         $(cli color white "Show messages from someone")"
    echo "  $(cli color bold yellow "delete") $(cli color italic white "[id]")                          $(cli color white "Delete messages from someone")"
    echo ""
    echo "  $(cli color bold yellow "-v --verbose")"
    echo "  $(cli color bold yellow "-d --debug")"
    echo ""
    
    exit
fi



v=false
d=false
debug_with_time=false
# Enable verbose
if cli s v || cli c verbose; then
  v=true
fi

# Enable debug 
if cli s d || cli c debug; then
  d=true
  if cli c debug-with-time; then
    debug_with_time=true
  fi
fi

# Parse commands
first_arg=$(cli o | head -1)
if [[ "$first_arg" == *"add"* ]]; then
  add "$@"
  exit 
elif [[ "$first_arg" == *"addme"* ]]; then
  addme "$@"
  exit 
elif [[ "$first_arg" == *"contacts"* ]]; then
  contacts "$@"
  exit 
elif [[ "$first_arg" == *"send"* ]]; then
  send "$@"
  exit 
elif [[ "$first_arg" == *"show"* ]]; then
  show "$@"
  exit 
elif [[ "$first_arg" == *"delete"* ]]; then
  del "$@"
  exit 
elif [[ "$first_arg" == *"wcdyu"* ]]; then
  wcdyu "$@"
  exit 
elif [[ "$first_arg" == *"erk"* ]]; then
  getPublicKey "$@"
  exit 
else
  exit 
fi
