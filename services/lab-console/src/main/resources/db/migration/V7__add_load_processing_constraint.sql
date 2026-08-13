alter table load_experiments
    add column constrained_stage varchar(24) not null default 'NONE';

alter table load_experiments
    add column processing_delay_millis integer not null default 0;

alter table load_experiments
    add constraint load_experiments_processing_delay
    check (processing_delay_millis between 0 and 500);
