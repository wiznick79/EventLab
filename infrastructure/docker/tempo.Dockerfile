FROM grafana/tempo:3.1.0@sha256:3076b8dcdfb32fd6bc5ccef85e7b7313e6199b9cb84366257fc17ecb696db5fd
COPY infrastructure/tempo/tempo.yml /etc/tempo.yaml
USER 10001
ENTRYPOINT ["/tempo"]
CMD ["-config.file=/etc/tempo.yaml"]
