#!/usr/bin/env bash

: << 'Dependencies'

- bash (run the code)
- jq   (work with json)

end of:
Dependencies


source ./parseCLI
parse_cli "$@"

# Install cli dependencies
install_pkg_on_unknown_distro() {
  if [ -z "$1" ]; then
    return 1
  fi

  PKG_NAME="$1"
  declare -a MANAGERS=(
    "apt install -y"
    "yum install -y"
    "dnf install -y"
    "pacman -S --noconfirm"
    "zypper install -y"
    "apk add"
    "pkg install -y"
    "xbps-install -y"
  )

  for MANAGER_BASE in "${MANAGERS[@]}"; do
    for PREFIX in "sudo" ""; do
      INSTALL_CMD="$PREFIX $MANAGER_BASE $PKG_NAME"
      $INSTALL_CMD >/dev/null 2>&1

      if [ $? -eq 0 ]; then
        return 0
      fi
    done
  done

  return 1
}

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
    exit 1
  fi

  debug "Testing if onion address is valid ..."
  if ! [[ "$address" =~ ^([a-z2-7]{16}|[a-z2-7]{56})\.onion$ ]]; then
    error "The onion address is not valid. Make sure you adding a real address"
    exit 1
  fi

  local list_file="./address_book/list.txt"
  local addressBook=()
  
  debug "Reading $list_file ..."
  if [[ -f "$list_file" ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
      line=$(echo "$line" | tr -d '\r')
      if [[ -n "$line" ]]; then
        addressBook+=("$line")
      fi
    done < "$list_file"
  fi
  
  debug "A total of ${#addressBook[@]} contacts found in your addresses list"
  
  # Check for duplicates to prevent username spoofing
  debug "Checking for duplicate aliases ..."
  for entry in "${addressBook[@]}"; do
    local auxAlias=$(echo "$entry" | cut -d' ' -f1)
    if [[ "$auxAlias" == "$alias" ]]; then
      error "Alias \"$alias\" already exists, use a different alias"
      exit 1
    fi
  done

  debug "Adding new entry to in-memory address book ..."
  addressBook+=("$alias $address")
  
  debug "Removing all duplicates (if there is any) ..."
  # Use associative array to remove duplicates while preserving order
  declare -A seen
  local uniqueEntries=()
  for entry in "${addressBook[@]}"; do
    if [[ -z "${seen[$entry]}" ]]; then
      uniqueEntries+=("$entry")
      seen["$entry"]=1
    fi
  done

  debug "Writing to $list_file ..."
  # Create directory if it doesn't exist
  mkdir -p "$(dirname "$list_file")"
  
  # Write all entries to file
  printf "%s\n" "${uniqueEntries[@]}" > "$list_file"
  
  echo "Added to your address book"
  debug "Done"
}

addme() {
  debug "Loading config..."
  local config
  if ! config=$(loadConfig "./config/dark-messenger.json"); then
    error "Failed to load config"
    return 1
  fi
  
  debug "Loading hostname..."
  local hostname
  if ! hostname=$(loadFile "./hidden_service/hostname"); then
    error "Failed to load hostname"
    return 1
  fi
  hostname=$(echo "$hostname" | tr -d '\r' | xargs)
  config=$(echo "$config" | jq --arg hostname "$hostname" '.hidden_service_hostname = $hostname')
  
  debug "Extracting onion domain from cli"
  local remote_onion
  remote_onion="${2:-}"
  if [[ -z "$remote_onion" ]]; then
    error "Use \"./dma-cli.sh addme domain.onion\" to provide the address of the user you want to add you"
    return 1
  fi
  
  # Avoid making useless requests client side
  local username
  username=$(echo "$config" | jq -r '.username // ""')
  debug "Configured username is $username "
  if [[ ! "$username" =~ ^[a-zA-Z0-9_.@-]{1,99}$ ]]; then
    error "Your username is not valid. Only alphanumeric characters.\nNext characters are also allowed: - _ . @"
    return 1
  fi
  
  if ! [[ "$remote_onion" =~ ^([a-z2-7]{16}|[a-z2-7]{56})\.onion$ ]]; then
    error "Onion address is not valid, preventing useless request ..."
    return 1
  fi
  
  local api_data
  api_data=$(jq -n --arg alias "$username" --arg address "$hostname" '{"alias": $alias, "address": $address}')
  local api="addme -d '$api_data' -H 'Content-Type: application/json'"
  
  debug "Fetching remote config..."
  local remoteConfig
  if ! remoteConfig=$(wcdyu "$hostname"); then
    error "Failed to fetch remote config"
    return 1
  fi
  
  debug "REMOTE CONFIG: $remoteConfig"
  
  local use_erk
  use_erk=$(echo "$remoteConfig" | jq -r '.use_erk // false')
  
  if [[ "$use_erk" == "true" ]]; then
    debug "Using encryption for username ..."
    
    local public_keys
    if ! public_keys=$(getPublicKey "$hostname"); then
      error "Failed to get public key"
      return 1
    fi
    
    IFS=',' read -r eciesKey rsaKey kyberKey <<< "$public_keys"
    
    # Check if remote supports encryption methods
    local add_me_ecies add_me_rsa add_me_kyber
    add_me_ecies=$(echo "$remoteConfig" | jq -r '.add_me.ecies // false')
    add_me_rsa=$(echo "$remoteConfig" | jq -r '.add_me.rsa // false')
    add_me_kyber=$(echo "$remoteConfig" | jq -r '.add_me.crystal_kyber // false')
    
    if [[ "$add_me_ecies" == "true" ]]; then
      debug "Encrypting [[$username]] with ECIES"
      if ! username=$(ECIES_encrypt "$username" "$eciesKey"); then
        error "Failed to encrypt with ECIES"
        return 1
      fi
      username=$(jq -n --arg encrypted "$username" '$encrypted')
    fi
    
    if [[ "$add_me_rsa" == "true" ]]; then
      if ! username=$(RSA_encrypt "$username" "$rsaKey"); then
        error "Failed to encrypt with RSA"
        return 1
      fi
      username=$(jq -n --arg encrypted "$username" '$encrypted')
    fi
    
    if [[ "$add_me_kyber" == "true" ]]; then
      if ! username=$(KYBER_encrypt "$username" "$kyberKey"); then
        error "Failed to encrypt with KYBER"
        return 1
      fi
      username=$(jq -n --arg encrypted "$username" '$encrypted')
    fi
    
    # Base64 encode the encrypted username
    username=$(echo -n "$username" | base64)
    api_data=$(jq -n --arg alias "$username" --arg address "$hostname" '{"alias": $alias, "address": $address}')
    api="addme -d '$api_data' -H 'Content-Type: application/json'"
  fi
  
  debug "Sending request to $remote_onion"
  local result
  if ! result=$(curl "$remote_onion" "$api"); then
    . #error "Error "
  fi
  
  echo "$result"
  
  local add_back
  add_back=$(echo "$config" | jq -r '.add_back // false')
  
  if [[ "$add_back" == "true" ]]; then
    read -p "Do you want to add the address to your contact list too? [Y/N] -> " response
    response=$(echo "$response" | tr '[:lower:]' '[:upper:]')
    
    if [[ "$response" == "Y" ]]; then
      local tmpUsername=""
      while [[ ! "$tmpUsername" =~ ^[a-zA-Z0-9_.@-]{1,99}$ ]]; do
        read -p "Please provide a username / alias for the contact -> " tmpUsername
      done
      
      # Call add function with username and address
      add "dummy" "$tmpUsername" "$remote_onion"
    fi
  fi
}

# TODO:
loadConfig() {
  local file="$1"
  if [[ -f "$file" ]]; then
    cat "$file"
  else
    return 1
  fi
}

loadFile() {
  local file="$1"
  if [[ -f "$file" ]]; then
    cat "$file"
  else
    return 1
  fi
}

wcdyu() {
  local hostname="$1"
  # This function should fetch remote config
  # For now, return a dummy JSON
  echo '{"use_erk": false, "add_me": {"ecies": false, "rsa": false, "crystal_kyber": false}}'
}

getPublicKey() {
  local hostname="$1"
  # This function should fetch public keys
  # For now, return dummy keys
  echo "ecies_key,rsa_key,kyber_key"
}

ECIES_encrypt() {
  local data="$1"
  local key="$2"
  # Implement ECIES encryption
  echo "encrypted_ecies_$data"
}

RSA_encrypt() {
  local data="$1"
  local key="$2"
  # Implement RSA encryption
  echo "encrypted_rsa_$data"
}

KYBER_encrypt() {
  local data="$1"
  local key="$2"
  # Implement KYBER encryption
  echo "encrypted_kyber_$data"
}

curl() {
  echo "wtf"
  local url="$1"
  local args="$2"
  
  debug "URL: $url"
  debug "Args: $args"
  
  local cmd="/usr/bin/env curl --silent --socks5-hostname 127.0.0.1:9050 http://$url:9001/$args"
  debug "Full command: $cmd"
  
  local output
  output=$(eval "$cmd" 2>&1)
  local exit_code=$?
  
  debug "Exit code: $exit_code"
  debug "Curl output: $output"
  
  echo -n "$output"
  return $exit_code
}

: << 'TODO'
const addme = async (cli) => { //TODO: Add Verbose and Debug outputs
    debug(`Loading config...`);
    config = await loadConfig("./config/dark-messenger.json");
    debug(`Loading hostname...`);
    let hostname = config.hidden_service_hostname = (await loadFile("./hidden_service/hostname")).trim();
    debug(`Extracting onion domain from cli`);
    if (! cli.o?.[1]?.[0] ) {
      error(`Use "./DarkMessenger addme domain.onion" to provide the address of the user you want to add you`);
      process.exit(0);
    }

    /* Avoid making useless requests client side */
    if (! /^[a-zA-Z0-9\-_.@]{1,99}$/.test(config?.username)) {
      error(`Your username is not valid. Only alphanumeric characters.\nNext characters are also allowed: - _ . @`);
      process.exit(0);
    }

    if (! /^(?:[a-z2-7]{16}|[a-z2-7]{56})\.onion$/.test(cli.o[1][0])) {
      error(`Onion address is not valid, preventing useless request ...`);
      process.exit(0);
    }



    let api = `addme -d '{ "alias":"${config.username}", "address":"${hostname}" }' -H 'Content-Type: application/json'`;

    const remoteConfig = await wcdyu(hostname);

    debug(`REMOTE CONFIG: ${JSON.stringify(remoteConfig)}`);
    if (remoteConfig?.use_erk && (remoteConfig?.add_me?.ecies || remoteConfig?.add_me?.rsa || remoteConfig?.add_me?.crystal_kyber)) {
      debug(`Using ern for username ... `);
      // TODO:
      const [ eciesKey, rsaKey, kyberKey ] = (await getPublicKey(hostname)).split(",");
      if (remoteConfig?.add_me?.ecies) {
        // TODO: encrypt username with exies and change api to post and '/'
        debug(`Encrypting [[${config.username}]] with ECIES`);
        config.username = JSON.stringify(ECIES_encrypt(config.username, eciesKey));
        //hostname = JSON.stringify(ECIES_encrypt(hostname, eciesKey));

      }
      if (remoteConfig?.add_me?.rsa) {
        //debug(`Encrypting [[${config.username}]] with RSA`);
        config.username = JSON.stringify(RSA_encrypt(config.username, rsaKey));
      }
      if (remoteConfig?.add_me?.crystal_kyber) {
        //debug(`Encrypting [[${config.username}]] with CRYSTAL-KYBER`);
        config.username = JSON.stringify(await KYBER_encrypt(config.username, kyberKey));
        //debug(`Encrypted Username is; [[${config.username}]].`);
      }

      config.username = Buffer.from(config.username).toString("base64");
      api = `addme -d '{ "alias":"${config.username}", "address":"${hostname}" }' -H 'Content-Type: application/json'`;


    }


    const result = await curl(`${cli.o[1][0]}`, api);
    debug(`Result: ${result}`);
  } catch(err) {
    error(`Error on Add() : ${err}`);
    process.exit(0);
  }

  if (config?.add_back) {
    if ( (await ask("Do you want to add the address to your contact list too? [Y/N] -> ")).toUpperCase() === "Y" ) {
      let tmpUsername = "";
      do {
        tmpUsername = await ask("Please provide a username / alias for the contact -> ");
      } while (! /^[a-zA-Z0-9\-_.@]{1,99}$/.test(tmpUsername));
      cli.o[2] = [cli.o[1][0]]; // set address
      cli.o[1][0] = tmpUsername; // set username
      await add(cli);
    }
  }
i}
TODO




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

# Install jq
if ! install_pkg_on_unknown_distro jq; then
  echo "Unable to find package manager to install jq, please do manually."
fi

# Parse args 
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
if [[ "$first_arg" == *"addme"* ]]; then
  addme "$@"
  exit 
elif [[ "$first_arg" == *"add"* ]]; then
  add "$@"
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
