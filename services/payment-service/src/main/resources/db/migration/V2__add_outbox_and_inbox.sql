create table outbox_messages (
    outbox_id uuid primary key,
    event_id uuid not null,
    destination_type varchar(20) not null,
    destination_name varchar(200) not null,
    payload_json text not null,
    trace_headers_json text not null,
    created_at timestamp with time zone not null,
    published_at timestamp with time zone,
    attempts integer not null,
    last_error varchar(1000)
);

create index payment_outbox_pending_idx
    on outbox_messages (published_at, created_at);

create table inbox_messages (
    message_id uuid not null,
    handler_name varchar(100) not null,
    processed_at timestamp with time zone not null,
    primary key (message_id, handler_name)
);
