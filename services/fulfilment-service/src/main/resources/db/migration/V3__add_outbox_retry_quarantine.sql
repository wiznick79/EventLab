alter table outbox_messages add column next_attempt_at timestamp with time zone;
alter table outbox_messages add column quarantined_at timestamp with time zone;
drop index if exists fulfilment_outbox_pending_idx;
create index fulfilment_outbox_pending_idx
    on outbox_messages (published_at, quarantined_at, next_attempt_at, created_at);
