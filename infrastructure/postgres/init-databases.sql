CREATE ROLE workflow_service LOGIN PASSWORD 'workflow-local-only';
CREATE ROLE payment_service LOGIN PASSWORD 'payment-local-only';
CREATE ROLE fulfilment_service LOGIN PASSWORD 'fulfilment-local-only';
CREATE ROLE lab_console LOGIN PASSWORD 'console-local-only';

CREATE DATABASE workflow_service OWNER workflow_service;
CREATE DATABASE payment_service OWNER payment_service;
CREATE DATABASE fulfilment_service OWNER fulfilment_service;
CREATE DATABASE lab_console OWNER lab_console;
