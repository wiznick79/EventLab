alter table workflow_runs add column experiment_plan_id uuid;

update workflow_runs set experiment_plan_id = id where experiment_plan_id is null;

alter table workflow_runs alter column experiment_plan_id set not null;
create unique index ux_workflow_runs_experiment_plan_id on workflow_runs (experiment_plan_id);
