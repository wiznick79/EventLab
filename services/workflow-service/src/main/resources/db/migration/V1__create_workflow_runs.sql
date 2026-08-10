create table workflow_runs (
    id uuid primary key,
    scenario_id varchar(100) not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    state varchar(50) not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
