create table payments (
    id uuid primary key,
    workflow_id uuid not null unique,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    status varchar(30) not null,
    authorized_at timestamp with time zone not null
);
