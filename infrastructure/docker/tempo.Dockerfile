FROM grafana/tempo:2.10.7@sha256:032b3acb51ed02c4b801473d54bb63e9e9f13738d215126d9843c30283794f4b
COPY infrastructure/tempo/tempo.yml /etc/tempo.yaml
USER 10001
ENTRYPOINT ["/tempo"]
CMD ["-config.file=/etc/tempo.yaml"]
