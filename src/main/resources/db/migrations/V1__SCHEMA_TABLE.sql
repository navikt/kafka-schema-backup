CREATE TABLE IF NOT EXISTS kafka_schema (
    id varchar(26) UNIQUE PRIMARY KEY,
    topic text,
    schema_data jsonb,
    schema_key text,
    created timestamp with timezone NOT NULL,
    superseded_at timestamp with timezone DEFAULT NULL
    superseded_by varchar(26) REFERENCES schema(id)
)