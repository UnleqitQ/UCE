-- Generic UIMA Domain/Association graph storage.
-- Requires a PostgreSQL image with Apache AGE installed.
CREATE EXTENSION IF NOT EXISTS age;
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM ag_catalog.ag_graph
        WHERE name = 'uce_domain_graph'
    ) THEN
        PERFORM ag_catalog.create_graph('uce_domain_graph');
    END IF;
END
$$;
