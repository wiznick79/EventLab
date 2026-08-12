alter table load_experiments add column consumer_concurrency integer not null default 1;
alter table load_experiments add constraint load_experiments_consumer_concurrency
    check (consumer_concurrency in (1, 4, 8));
