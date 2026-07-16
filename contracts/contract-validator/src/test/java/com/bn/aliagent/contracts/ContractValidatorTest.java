package com.bn.aliagent.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractValidatorTest {

    @Test
    void validatesAllPublishedContractsAndLocalReferences() {
        assertDoesNotThrow(() -> new ContractValidator().validate(Path.of("..")));
    }

    @Test
    void rejectsUnresolvedReferenceFragment(@TempDir Path temporaryDirectory) throws Exception {
        Files.writeString(temporaryDirectory.resolve("broken.yaml"), "components:\n  schemas:\n    Example:\n      $ref: '#/components/schemas/Missing'\n");

        assertThrows(Exception.class, () -> new ContractValidator().validate(temporaryDirectory));
    }

    @Test
    void knowledgeRetrievalContractDeclaresTheHeadersAndCitationRequiredByItsConsumers() throws Exception {
        JsonNode contract = new ObjectMapper(new YAMLFactory()).readTree(
                Path.of("..", "openapi", "knowledge-v1.yaml").toFile());

        JsonNode retrievalParameters = contract.at("/paths/~1api~1v1~1knowledge~1retrieval:query/post/parameters");
        JsonNode taskParameters = contract.at("/paths/~1api~1v1~1knowledge~1ingestion-tasks~1{taskId}/get/parameters");
        JsonNode publishParameters = contract.at("/paths/~1api~1v1~1knowledge~1versions~1{versionId}~1publish/post/parameters");
        JsonNode citation = contract.at("/components/schemas/RetrievalItem/properties/citation");

        assertEquals(8, retrievalParameters.size());
        assertEquals(9, taskParameters.size());
        assertEquals(9, publishParameters.size());
        assertEquals("#/components/parameters/ServiceAuthorization", retrievalParameters.get(7).path("$ref").asText());
        assertEquals("#/components/parameters/ServiceAuthorization", taskParameters.get(7).path("$ref").asText());
        assertEquals("#/components/parameters/ServiceAuthorization", publishParameters.get(7).path("$ref").asText());
        assertEquals("object", citation.path("type").asText());
        assertTrue(contract.at("/components/schemas/RetrievalItem/required").toString().contains("citation"));
    }

    @Test
    void knowledgeIngestionEventUsesTheSharedV1Envelope() throws Exception {
        JsonNode contract = new ObjectMapper(new YAMLFactory()).readTree(
                Path.of("..", "asyncapi", "knowledge-ingestion-requested-v1.yaml").toFile());

        JsonNode payload = contract.at("/components/messages/IngestionRequested/payload/allOf");

        assertEquals("../json-schema/event-envelope-v1.schema.json", payload.get(0).path("$ref").asText());
        assertEquals("KnowledgeIngestionRequested", payload.get(1).at("/properties/eventType/const").asText());
        assertEquals("../json-schema/knowledge-ingestion-requested-v1.schema.json",
                payload.get(1).at("/properties/payload/$ref").asText());
        assertEquals("knowledge-service", payload.get(1).at("/properties/producer/const").asText());
    }
}
