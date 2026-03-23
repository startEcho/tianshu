ALTER TABLE lab_instances
    ALTER COLUMN access_url DROP NOT NULL;

ALTER TABLE lab_instances
    ADD COLUMN launch_request_id VARCHAR(120);

CREATE UNIQUE INDEX uk_lab_instances_owner_launch_request_id
    ON lab_instances(owner_user_id, launch_request_id)
    WHERE launch_request_id IS NOT NULL;
