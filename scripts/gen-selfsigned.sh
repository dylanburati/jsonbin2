#!/bin/sh
set -e
mkdir -p ~/local-certs
cd ~/local-certs

openssl req \
  -newkey rsa:2048 -nodes -keyout fullchain.pem.key \
  -x509 -days 365 -out fullchain.pem \
  -subj "/C=US/O=localhost/OU=Development/CN=*.localhost.dev"
