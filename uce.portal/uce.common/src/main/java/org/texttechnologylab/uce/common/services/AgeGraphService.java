package org.texttechnologylab.uce.common.services;

import org.texttechnologylab.uce.common.exceptions.DatabaseOperationException;
import org.texttechnologylab.uce.common.exceptions.DocumentAccessDeniedException;

import java.util.List;

public class AgeGraphService {
    public static final String GRAPH_NAME = "uce_domain_graph";
    public static final String NODE_LABEL = "FeatureStructure";
    public static final String EDGE_LABEL = "Association";

    private final PostgresqlDataInterface_Impl db;

    public AgeGraphService(PostgresqlDataInterface_Impl db) {
        this.db = db;
    }

    public void ensureGraph() throws DatabaseOperationException, DocumentAccessDeniedException {
        db.executeNativeStatements(List.of(
                "LOAD 'age'",
                "SET search_path = ag_catalog, \"$user\", public",
                """
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
                """
        ));
    }

    public void upsertDomainNode(DomainNode node) throws DatabaseOperationException, DocumentAccessDeniedException {
        executeCypher("""
                MERGE (n:%s {uid: %s})
                SET n.uimaType = %s,
                    n.corpusId = %d,
                    n.documentId = %d,
                    n.name = %s,
                    n.uri = %s,
                    n.metadata = %s,
                    n.features = %s
                RETURN n
                """.formatted(
                NODE_LABEL,
                cypherString(node.uid()),
                cypherString(node.uimaType()),
                node.corpusId(),
                node.documentId(),
                cypherNullableString(node.name()),
                cypherNullableString(node.uri()),
                cypherNullableString(node.metadata()),
                cypherNullableString(node.featuresJson())
        ));
    }

    public void upsertAssociationEdge(AssociationEdge edge) throws DatabaseOperationException, DocumentAccessDeniedException {
        executeCypher("""
                MATCH (a:%s {uid: %s}), (b:%s {uid: %s})
                MERGE (a)-[r:%s {uid: %s}]->(b)
                SET r.uimaType = %s,
                    r.corpusId = %d,
                    r.documentId = %d,
                    r.name = %s,
                    r.metadata = %s,
                    r.features = %s
                RETURN r
                """.formatted(
                NODE_LABEL,
                cypherString(edge.leftUid()),
                NODE_LABEL,
                cypherString(edge.rightUid()),
                EDGE_LABEL,
                cypherString(edge.uid()),
                cypherString(edge.uimaType()),
                edge.corpusId(),
                edge.documentId(),
                cypherNullableString(edge.name()),
                cypherNullableString(edge.metadata()),
                cypherNullableString(edge.featuresJson())
        ));
    }

    private void executeCypher(String cypher) throws DatabaseOperationException, DocumentAccessDeniedException {
        db.executeNativeStatements(List.of(
                "LOAD 'age'",
                "SET search_path = ag_catalog, \"$user\", public",
                """
                SELECT *
                FROM cypher('%s', $cypher$
                %s
                $cypher$) AS (result agtype)
                """.formatted(GRAPH_NAME, cypher)
        ));
    }

    private static String cypherNullableString(String value) {
        return value == null ? "null" : cypherString(value);
    }

    private static String cypherString(String value) {
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "'";
    }

    public record DomainNode(
            String uid,
            String uimaType,
            long corpusId,
            long documentId,
            String name,
            String uri,
            String metadata,
            String featuresJson
    ) {
    }

    public record AssociationEdge(
            String uid,
            String uimaType,
            long corpusId,
            long documentId,
            String leftUid,
            String rightUid,
            String name,
            String metadata,
            String featuresJson
    ) {
    }
}
