FROM haproxy:2.2-alpine

RUN apk update -f \
  && apk --no-cache add -f \
  openssl \
  curl \
  socat \
  tzdata \
  tar \
  && rm -rf /var/cache/apk/*

ENV LE_CONFIG_HOME /acme.sh

ENV AUTO_UPGRADE 1

RUN curl https://get.acme.sh | sh

RUN ln -s  /root/.acme.sh/acme.sh  /usr/local/bin/acme.sh && crontab -l | grep acme.sh | sed 's#> /dev/null##' | crontab -

COPY entry.sh /entry.sh
COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg

ENTRYPOINT ["/entry.sh"]
CMD ["haproxy", "-f", "/usr/local/etc/haproxy/haproxy.cfg"]
