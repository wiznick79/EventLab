FROM grafana/grafana:13.3.1@sha256:0615ec4fbd4072004b7d86b46ded63013a74b7d6eb1119273dd89e8fa25c13cf
USER root
# Refresh security packages on every CI run, even when the pinned base is cached.
ARG APK_UPGRADE_CACHE_BUST=local
RUN printf '%s' "$APK_UPGRADE_CACHE_BUST" >/dev/null && apk upgrade --no-cache
# The lab uses only Tempo; exclude unused datasource binaries from the runtime image.
RUN find /usr/share/grafana/data/plugins-bundled -mindepth 1 -maxdepth 1 \
        ! -name tempo -exec rm -rf {} +
# Keep runtime plugins identical to the signed copies included in the scanned image.
ENV GF_PLUGINS_PREINSTALL_DISABLED=true \
    GF_PLUGINS_PREINSTALL_AUTO_UPDATE=false
COPY infrastructure/grafana/provisioning /etc/grafana/provisioning
COPY infrastructure/grafana/dashboards /var/lib/grafana/dashboards
USER 472
