FROM golang:1-alpine3.24 AS builder

RUN apk add --no-cache git ca-certificates build-base su-exec olm-dev

ARG MAUTRIX_DISCORD_TAG=v0.7.7
WORKDIR /build
RUN git clone --depth 1 --branch ${MAUTRIX_DISCORD_TAG} https://github.com/mautrix/discord.git .

COPY 0001-emoji-fallback-shortcode.patch /tmp/velvet.patch
RUN git apply --check /tmp/velvet.patch && git apply /tmp/velvet.patch

RUN go build -o /usr/bin/mautrix-discord

FROM alpine:3.24

ENV UID=1000 \
    GID=1000

RUN apk add --no-cache ffmpeg su-exec ca-certificates olm bash jq curl yq-go lottieconverter

COPY --from=builder /usr/bin/mautrix-discord /usr/bin/mautrix-discord
COPY --from=builder /build/example-config.yaml /opt/mautrix-discord/example-config.yaml
COPY --from=builder /build/docker-run.sh /docker-run.sh
VOLUME /data
WORKDIR /data

CMD ["/docker-run.sh"]
