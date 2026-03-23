CREATE TABLE lab_launch_outbox (
    id UUID PRIMARY KEY,
    instance_id VARCHAR(180) NOT NULL,
    exchange_name VARCHAR(120) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

ALTER TABLE lab_launch_outbox
    ADD CONSTRAINT fk_lab_launch_outbox_instance
        FOREIGN KEY (instance_id) REFERENCES lab_instances(instance_id)
            ON DELETE CASCADE;

CREATE UNIQUE INDEX uk_lab_launch_outbox_instance_id
    ON lab_launch_outbox(instance_id);

CREATE INDEX idx_lab_launch_outbox_unpublished_created_at
    ON lab_launch_outbox(created_at)
    WHERE published_at IS NULL;
