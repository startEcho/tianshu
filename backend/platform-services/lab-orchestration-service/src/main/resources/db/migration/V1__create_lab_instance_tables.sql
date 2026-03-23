CREATE TABLE lab_instances (
    instance_id VARCHAR(180) PRIMARY KEY,
    vulnerability_id VARCHAR(120) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    owner_username VARCHAR(120) NOT NULL,
    access_url VARCHAR(512) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    terminated_at TIMESTAMPTZ
);

CREATE INDEX idx_lab_instances_owner_user_id ON lab_instances(owner_user_id);
CREATE INDEX idx_lab_instances_vulnerability_id ON lab_instances(vulnerability_id);
