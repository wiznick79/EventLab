FROM grafana/grafana:13.2.4@sha256:dbbc38ea21103cefb50396340cd88afec779c7e070b0a7bf5a75b4ce46651d3f
COPY infrastructure/grafana/provisioning /etc/grafana/provisioning
COPY infrastructure/grafana/dashboards /var/lib/grafana/dashboards
USER 472
