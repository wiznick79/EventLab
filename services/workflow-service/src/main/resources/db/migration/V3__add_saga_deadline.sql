alter table workflow_runs add column step_deadline timestamp with time zone;
create index workflow_saga_deadline_idx on workflow_runs (state, step_deadline);
