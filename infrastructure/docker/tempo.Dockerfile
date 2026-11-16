FROM grafana/tempo:2.10.10@sha256:12b0820f13ffa10ce956b6e9acc3b03f3cffded99109be6cc09d106fe56e5d38
COPY infrastructure/tempo/tempo.yml /etc/tempo.yaml
USER 10001
ENTRYPOINT ["/tempo"]
CMD ["-config.file=/etc/tempo.yaml"]
