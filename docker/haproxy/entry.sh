#!/bin/sh
if [ ! -f "$FULLCHAIN" ]; then
  PRIMARY="$(echo "$DOMAINS" | cut -d' ' -f1)"
  DOMAIN_ARGS=""
  IFS=' '
  for DN in $DOMAINS; do
    DOMAIN_ARGS="$DOMAIN_ARGS -d $DN";
  done

  trap 'echo bootstrap received hangup' HUP
  socat TCP-LISTEN:80,fork TCP:localhost:88 &
  acme.sh --issue --standalone $DOMAIN_ARGS --httpport 88 && \
  acme.sh --install-certs -d "$PRIMARY" \
    --fullchain-file "$FULLCHAIN" \
    --reloadcmd "kill -HUP 1"
  kill %1
fi

crond
exec /docker-entrypoint.sh "$@"
