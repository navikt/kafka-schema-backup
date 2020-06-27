CREATE TABLE IF NOT EXISTS kafka_schema (
    id varchar(26) UNIQUE PRIMARY KEY,
    subject text,
    version bigint,
    registry_id bigint,
    schema_data jsonb,
    deleted boolean DEFAULT false,
    created TIMESTAMP WITH TIME ZONE NOT NULL,
    superseded_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    superseded_by varchar(26) REFERENCES kafka_schema(id) DEFAULT NULL
)