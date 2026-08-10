alter table timeline_events add column duplicate_delivery boolean not null default false;
alter table timeline_events add column logical_event_id uuid;
update timeline_events set logical_event_id = id;
alter table timeline_events alter column logical_event_id set not null;

create index timeline_events_logical_event_idx on timeline_events (logical_event_id);

create table inbox_messages (
    message_id uuid not null,
    handler_name varchar(100) not null,
    processed_at timestamp with time zone not null,
    primary key (message_id, handler_name)
);
