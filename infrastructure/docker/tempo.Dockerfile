FROM grafana/tempo:2.10.8@sha256:f0561deb1c68ec44d6e6e7e4487f30106c4e5e768642077695b37958b105812a
COPY infrastructure/tempo/tempo.yml /etc/tempo.yaml
USER 10001
ENTRYPOINT ["/tempo"]
CMD ["-config.file=/etc/tempo.yaml"]
