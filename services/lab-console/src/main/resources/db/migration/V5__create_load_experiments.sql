create table load_experiments (
    id uuid primary key,
    status varchar(24) not null,
    traffic_pattern varchar(16) not null,
    requested_workflows integer not null,
    duplicate_percentage integer not null,
    interval_millis integer not null,
    workflow_ids text not null,
    launch_failures integer not null,
    created_at timestamp with time zone not null,
    launched_at timestamp with time zone,
    completed_at timestamp with time zone,
    constraint load_experiments_workflow_count check (requested_workflows between 1 and 100),
    constraint load_experiments_duplicate_percentage check (duplicate_percentage between 0 and 100)
);

create index load_experiments_created_at_idx on load_experiments (created_at desc);
