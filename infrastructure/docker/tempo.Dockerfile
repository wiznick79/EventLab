FROM grafana/tempo:2.8.2
COPY infrastructure/tempo/tempo.yml /etc/tempo.yaml
ENTRYPOINT ["/tempo"]
CMD ["-config.file=/etc/tempo.yaml"]
