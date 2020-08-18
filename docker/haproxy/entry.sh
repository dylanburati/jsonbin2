#!/bin/sh
if [ ! -f "$FULLCHAIN" ]; then
  PRIMARY="$(echo "$DOMAINS" | cut -d' ' -f1)"
  DOMAIN_ARGS=""
  IFS=' '
  for DN in $DOMAINS; do
    DOMAIN_ARGS="$DOMAIN_ARGS -d $DN";
  done

  trap 'echo bootstrap: received hangup' HUP
  socat TCP-LISTEN:80,reuseaddr,fork TCP:localhost:88 &
  acme.sh --issue --standalone $DOMAIN_ARGS --httpport 88 || exit 1
  kill %1
  echo 'bootstrap: killed socat'
  while netstat -tln | grep '0.0.0.0:80'; do
    echo 'bootstrap: waiting for port 80 to be available'
    sleep 0.5
  done
  acme.sh --install-cert -d "$PRIMARY" \
    --fullchain-file "$FULLCHAIN" \
    --key-file "$FULLCHAIN.key" \
    --reloadcmd "kill -HUP 1"
fi

crond
exec /docker-entrypoint.sh "$@"
