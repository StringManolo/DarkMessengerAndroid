#!/usr/bin/env bash

curl --socks5-hostname 127.0.0.1:9050 "http://$1:9001/addme" \
  -H "Content-Type: application/json" \
  -d '{"alias": "CurlTest", "address": "testoibuxofswjutlougnbqjs2bkpgkyjoh6b3ok37wv6xnuiyv4kbqd.onion"}'
