FROM grafana/grafana:13.2.3@sha256:b28bae15e219c998fb0e0424ed724930cc61b1f61fb404d47c862f9a23f9e572
COPY infrastructure/grafana/provisioning /etc/grafana/provisioning
COPY infrastructure/grafana/dashboards /var/lib/grafana/dashboards
USER 472
