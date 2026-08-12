create table experiment_runs (
    workflow_id uuid primary key,
    experiment_plan_id uuid not null unique,
    scenario_id varchar(160) not null,
    payment_result_deliveries integer not null,
    fulfilment_behavior varchar(40) not null,
    expected_invariant varchar(500) not null,
    created_at timestamp with time zone not null,
    constraint experiment_runs_delivery_count check (payment_result_deliveries between 1 and 2)
);

create index experiment_runs_created_at_idx on experiment_runs (created_at desc);
