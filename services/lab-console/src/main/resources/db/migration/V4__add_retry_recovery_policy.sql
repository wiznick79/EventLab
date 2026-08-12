alter table experiment_runs add column fulfilment_max_attempts integer not null default 4;
alter table experiment_runs add column recovery_mode varchar(20) not null default 'MANUAL';
alter table experiment_runs add column recovery_claimed boolean not null default false;

alter table experiment_runs add constraint experiment_runs_attempt_budget
    check (fulfilment_max_attempts between 2 and 6);

create index experiment_runs_recovery_policy_idx
    on experiment_runs (recovery_mode, recovery_claimed);
