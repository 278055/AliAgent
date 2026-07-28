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

    @Test
    void aiReplyRequestedContractsKeepV1SeparateAndRequireV2StreamingIdentifiers() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode v1AsyncApi = new ObjectMapper(new YAMLFactory()).readTree(
                Path.of("..", "asyncapi", "ai-reply-requested-v1.yaml").toFile());
        JsonNode v2AsyncApi = new ObjectMapper(new YAMLFactory()).readTree(
                Path.of("..", "asyncapi", "ai-reply-requested-v2.yaml").toFile());
        JsonNode v1Envelope = json.readTree(Path.of("..", "json-schema", "event-envelope-v1.schema.json").toFile());
        JsonNode v2Envelope = json.readTree(Path.of("..", "json-schema", "event-envelope-v2.schema.json").toFile());
        JsonNode v1Payload = json.readTree(Path.of("..", "json-schema", "ai-reply-requested-v1.schema.json").toFile());
        JsonNode v2Payload = json.readTree(Path.of("..", "json-schema", "ai-reply-requested-v2.schema.json").toFile());

        assertEquals(1, v1AsyncApi.path("channels").size());
        assertEquals("ai.reply.requested.v1", v1AsyncApi.at("/channels/ai.reply.requested.v1/address").asText());
        assertEquals("ai.reply.requested.v2", v2AsyncApi.at("/channels/ai.reply.requested.v2/address").asText());
        assertEquals(1, v1Envelope.at("/properties/eventVersion/const").asInt());
        assertEquals(2, v2Envelope.at("/properties/eventVersion/const").asInt());
        assertEquals("[\"conversationId\",\"messageId\",\"requestId\"]", v1Payload.path("required").toString());
        assertTrue(v2Payload.path("required").toString().contains("replyMessageId"));
        assertTrue(v2Payload.path("required").toString().contains("generationId"));
    }

    @Test
    void internalGateVerificationContractRequiresTrustedHeadersAndDeclaresItsResponseShape() throws Exception {
        JsonNode contract = new ObjectMapper(new YAMLFactory()).readTree(
                Path.of("..", "openapi", "evaluation-v1.yaml").toFile());

        JsonNode operation = contract.at("/paths/~1internal~1api~1v1~1evaluation~1gate-proofs:verify/post");
        JsonNode parameters = operation.path("parameters");
        JsonNode response = operation.at("/responses/200/content/application~1json/schema");

        assertEquals(2, parameters.size());
        assertEquals("X-Service-Authorization", parameters.get(0).path("name").asText());
        assertTrue(parameters.get(0).path("required").asBoolean());
        assertEquals("X-Tenant-Id", parameters.get(1).path("name").asText());
        assertTrue(parameters.get(1).path("required").asBoolean());
        assertEquals("object", response.path("type").asText());
        assertTrue(response.path("required").toString().contains("code"));
        assertEquals("boolean", response.at("/properties/data/properties/accepted/type").asText());
        assertEquals("string", response.at("/properties/data/properties/reason/type").asText());
        assertTrue(operation.path("responses").has("400"));
        assertTrue(operation.at("/responses/400/description").asText().contains("tenant"));
        assertTrue(operation.path("responses").has("401"));
    }
}
