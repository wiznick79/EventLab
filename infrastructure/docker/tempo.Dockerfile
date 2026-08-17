FROM grafana/tempo:3.0.3@sha256:0296560ac66f8a3600d7fb3014a52c189d4d9c3549ad6ff441bf2409855d68d5
COPY infrastructure/tempo/tempo.yml /etc/tempo.yaml
USER 10001
ENTRYPOINT ["/tempo"]
CMD ["-config.file=/etc/tempo.yaml"]
