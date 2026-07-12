package com.bn.aliagent.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** 校验契约文件可解析，且每个本地引用都能被解析。 */
public final class ContractValidator {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public void validate(Path contractsDirectory) throws IOException {
        try (Stream<Path> files = Files.walk(contractsDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(this::isContract).toList()) {
                validateFile(file.toAbsolutePath().normalize(), new HashSet<>());
            }
        }
    }

    private boolean isContract(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }

    private void validateFile(Path file, Set<Path> visited) throws IOException {
        if (!visited.add(file)) {
            return;
        }
        JsonNode document = mapperFor(file).readTree(file.toFile());
        if (document == null) {
            throw new IOException("契约为空: " + file);
        }
        validateReferences(document, document, file, visited);
    }

    private ObjectMapper mapperFor(Path file) {
        return file.getFileName().toString().endsWith(".json") ? jsonMapper : yamlMapper;
    }

    private void validateReferences(JsonNode node, JsonNode document, Path source, Set<Path> visited) throws IOException {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.isTextual()) {
                String value = reference.asText();
                String[] parts = value.split("#", 2);
                String referenceFile = parts[0];
                JsonNode referencedDocument = document;
                if (!referenceFile.isBlank()) {
                    Path target = source.getParent().resolve(referenceFile).normalize();
                    if (!Files.isRegularFile(target)) {
                        throw new IOException("无法解析 $ref " + value + "，来源: " + source);
                    }
                    validateFile(target, visited);
                    referencedDocument = mapperFor(target).readTree(target.toFile());
                }
                if (parts.length == 2 && !parts[1].isBlank() && referencedDocument.at(parts[1]).isMissingNode()) {
                    throw new IOException("无法解析 $ref 片段 " + value + "，来源: " + source);
                }
            }
            node.elements().forEachRemaining(child -> validateUnchecked(child, document, source, visited));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> validateUnchecked(child, document, source, visited));
        }
    }

    private void validateUnchecked(JsonNode node, JsonNode document, Path source, Set<Path> visited) {
        try {
            validateReferences(node, document, source, visited);
        } catch (IOException exception) {
            throw new ContractValidationException(exception);
        }
    }

    private static final class ContractValidationException extends RuntimeException {
        private ContractValidationException(IOException cause) {
            super(cause);
        }
    }
}
