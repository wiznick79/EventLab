alter table fulfilments add column stale_event_due_at timestamp with time zone;
alter table fulfilments add column stale_event_sent boolean not null default false;
create index fulfilment_stale_event_due_idx on fulfilments (stale_event_sent, stale_event_due_at);
