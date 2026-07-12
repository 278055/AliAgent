package com.bn.aliagent.contracts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
