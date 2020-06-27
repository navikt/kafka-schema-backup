CREATE INDEX IF NOT EXISTS kafka_schema_subject ON kafka_schema(subject);
CREATE INDEX IF NOT EXISTS kafka_schema_registry_id ON kafka_schema(registry_id);
CREATE UNIQUE INDEX IF NOT EXISTS kafka_schema_uniqueness ON kafka_schema(subject, registry_id, version, deleted);