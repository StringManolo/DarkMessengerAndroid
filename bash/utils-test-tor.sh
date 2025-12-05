#!/usr/bin/env bash

if torsocks lynx --dump https://check.torproject.org | grep -q 'Congratulations'; then
  echo 'Dark Messenger Tor Working'
else
  echo 'Dark Messenger Tor Not Working'
fi
