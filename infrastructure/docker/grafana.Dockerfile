FROM grafana/grafana:13.2.0@sha256:3fd54ae1214669f8355f065ec9f6445d5279a3d77095ab048ca045685272429b
COPY infrastructure/grafana/provisioning /etc/grafana/provisioning
COPY infrastructure/grafana/dashboards /var/lib/grafana/dashboards
USER 472
