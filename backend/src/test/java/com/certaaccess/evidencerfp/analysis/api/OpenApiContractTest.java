package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.EvidenceRfpApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract gate between the routes and DTOs exposed by Springdoc and the public,
 * versioned OpenAPI document used to generate the TypeScript client.
 *
 * <p>This deliberately calls the real {@code /v3/api-docs} endpoint. It checks
 * every path/method, operation id, parameter, body, response status/media type,
 * header and recursively resolved schema shape, including required fields and
 * documented constraints. Descriptions, tags and component names are allowed to
 * differ because they do not alter wire compatibility.</p>
 */
@SpringBootTest(classes = EvidenceRfpApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.task.scheduling.enabled=false")
class OpenApiContractTest {
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "patch", "options", "head", "trace");
    private static final List<String> CONSTRAINTS = List.of(
            "minLength", "maxLength", "minItems", "maxItems", "minimum", "maximum",
            "exclusiveMinimum", "exclusiveMaximum", "pattern", "minProperties", "maxProperties");

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @BeforeEach
    void configureMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void generatedSpringdocContractMatchesVersionedOpenApiDocument() throws Exception {
        String generatedJson = mvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        JsonNode actual = json.readTree(generatedJson);
        Path contractFile = contractFile();
        assertThat(contractFile).as("versionierter OpenAPI-Vertrag").isRegularFile();
        JsonNode expected = yaml.readTree(Files.readString(contractFile));

        assertThat(actual.path("openapi").asText()).isEqualTo(expected.path("openapi").asText());
        assertThat(actual.at("/info/title").asText()).isEqualTo(expected.at("/info/title").asText());
        assertThat(actual.at("/info/version").asText()).isEqualTo(expected.at("/info/version").asText());
        assertThat(actual.at("/info/description").asText())
                .isEqualTo(expected.at("/info/description").asText());

        Map<String, JsonNode> expectedOperations = operations(expected);
        Map<String, JsonNode> actualOperations = operations(actual);
        assertThat(actualOperations.keySet()).as("öffentliche Pfade und HTTP-Methoden")
                .containsExactlyInAnyOrderElementsOf(expectedOperations.keySet());

        for (Map.Entry<String, JsonNode> entry : expectedOperations.entrySet()) {
            String operation = entry.getKey();
            JsonNode expectedOperation = entry.getValue();
            JsonNode actualOperation = actualOperations.get(operation);
            assertThat(actualOperation).as(operation + " in /v3/api-docs").isNotNull();
            assertThat(actualOperation.path("operationId").asText()).as(operation + " operationId")
                    .isEqualTo(expectedOperation.path("operationId").asText());

            compareParameters(expected, actual, operation, expectedOperation, actualOperation);
            compareRequestBody(expected, actual, operation, expectedOperation, actualOperation);
            compareResponses(expected, actual, operation, expectedOperation, actualOperation);
        }
    }

    private Map<String, JsonNode> operations(JsonNode root) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        root.path("paths").fields().forEachRemaining(pathEntry ->
                pathEntry.getValue().fields().forEachRemaining(operationEntry -> {
                    if (HTTP_METHODS.contains(operationEntry.getKey())) {
                        result.put(operationEntry.getKey().toUpperCase() + " " + pathEntry.getKey(),
                                operationEntry.getValue());
                    }
                }));
        return result;
    }

    private Path contractFile() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                workingDirectory.resolve("../docs/openapi/evidence-rfp-v1.yaml").normalize(),
                workingDirectory.resolve("docs/openapi/evidence-rfp-v1.yaml").normalize());
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(candidates.getFirst());
    }

    private void compareParameters(JsonNode expectedRoot, JsonNode actualRoot, String operation,
                                   JsonNode expectedOperation, JsonNode actualOperation) {
        Map<String, JsonNode> expectedParameters = parameters(expectedRoot, expectedOperation, operation);
        Map<String, JsonNode> actualParameters = parameters(actualRoot, actualOperation, operation);
        assertThat(actualParameters.keySet()).as(operation + " Parameter")
                .containsExactlyInAnyOrderElementsOf(expectedParameters.keySet());
        expectedParameters.forEach((key, expectedParameter) -> {
            JsonNode actualParameter = actualParameters.get(key);
            assertThat(actualParameter.path("required").asBoolean(false))
                    .as(operation + " Parameter " + key + " required")
                    .isEqualTo(expectedParameter.path("required").asBoolean(false));
            compareSchema(expectedRoot, actualRoot, expectedParameter.path("schema"),
                    actualParameter.path("schema"), operation + " Parameter " + key, true,
                    new LinkedHashSet<>());
        });
    }

    private Map<String, JsonNode> parameters(JsonNode root, JsonNode operation, String operationKey) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        String path = operationKey.substring(operationKey.indexOf(' ') + 1);
        List<JsonNode> parameterNodes = new ArrayList<>();
        root.path("paths").path(path).path("parameters").forEach(parameterNodes::add);
        operation.path("parameters").forEach(parameterNodes::add);
        parameterNodes.forEach(parameterNode -> {
            JsonNode parameter = resolve(root, parameterNode);
            result.put(parameter.path("in").asText() + ":" + parameter.path("name").asText(), parameter);
        });
        return result;
    }

    private void compareRequestBody(JsonNode expectedRoot, JsonNode actualRoot, String operation,
                                    JsonNode expectedOperation, JsonNode actualOperation) {
        JsonNode expectedBody = resolve(expectedRoot, expectedOperation.path("requestBody"));
        JsonNode actualBody = resolve(actualRoot, actualOperation.path("requestBody"));
        if (expectedBody.isMissingNode()) {
            assertThat(actualBody.isMissingNode()).as(operation + " requestBody").isTrue();
            return;
        }
        assertThat(actualBody.isMissingNode()).as(operation + " requestBody").isFalse();
        assertThat(actualBody.path("required").asBoolean(false)).as(operation + " requestBody required")
                .isEqualTo(expectedBody.path("required").asBoolean(false));
        compareContent(expectedRoot, actualRoot, expectedBody.path("content"), actualBody.path("content"),
                operation + " requestBody", true);
    }

    private void compareResponses(JsonNode expectedRoot, JsonNode actualRoot, String operation,
                                  JsonNode expectedOperation, JsonNode actualOperation) {
        Map<String, JsonNode> expectedResponses = fields(expectedOperation.path("responses"));
        Map<String, JsonNode> actualResponses = fields(actualOperation.path("responses"));
        assertThat(actualResponses.keySet()).as(operation + " Response-Statuscodes")
                .containsExactlyInAnyOrderElementsOf(expectedResponses.keySet());

        expectedResponses.forEach((statusCode, expectedNode) -> {
            JsonNode expectedResponse = resolve(expectedRoot, expectedNode);
            JsonNode actualResponse = resolve(actualRoot, actualResponses.get(statusCode));
            String location = operation + " Response " + statusCode;
            Map<String, JsonNode> expectedHeaders = fields(expectedResponse.path("headers"));
            Map<String, JsonNode> actualHeaders = fields(actualResponse.path("headers"));
            assertThat(actualHeaders.keySet()).as(location + " Header")
                    .containsExactlyInAnyOrderElementsOf(expectedHeaders.keySet());
            expectedHeaders.forEach((name, expectedHeaderNode) -> {
                JsonNode expectedHeader = resolve(expectedRoot, expectedHeaderNode);
                JsonNode actualHeader = resolve(actualRoot, actualHeaders.get(name));
                compareSchema(expectedRoot, actualRoot, expectedHeader.path("schema"),
                        actualHeader.path("schema"), location + " Header " + name, false,
                        new LinkedHashSet<>());
            });
            compareContent(expectedRoot, actualRoot, expectedResponse.path("content"),
                    actualResponse.path("content"), location, false);
        });
    }

    private void compareContent(JsonNode expectedRoot, JsonNode actualRoot, JsonNode expectedContent,
                                JsonNode actualContent, String location, boolean exactInputConstraints) {
        Map<String, JsonNode> expectedMediaTypes = fields(expectedContent);
        Map<String, JsonNode> actualMediaTypes = fields(actualContent);
        assertThat(actualMediaTypes.keySet()).as(location + " Medientypen")
                .containsExactlyInAnyOrderElementsOf(expectedMediaTypes.keySet());
        expectedMediaTypes.forEach((mediaType, expectedMedia) ->
                compareSchema(expectedRoot, actualRoot, expectedMedia.path("schema"),
                        actualMediaTypes.get(mediaType).path("schema"), location + " " + mediaType,
                        exactInputConstraints, new LinkedHashSet<>()));
    }

    private void compareSchema(JsonNode expectedRoot, JsonNode actualRoot, JsonNode expectedNode,
                               JsonNode actualNode, String location, boolean exactInputConstraints,
                               Set<String> visited) {
        if (expectedNode.isMissingNode()) {
            assertThat(actualNode.isMissingNode()).as(location + " Schema").isTrue();
            return;
        }
        assertThat(actualNode.isMissingNode()).as(location + " Schema").isFalse();

        String visitKey = expectedNode.path("$ref").asText() + "|" + actualNode.path("$ref").asText();
        if (!visitKey.equals("|") && !visited.add(visitKey)) return;
        JsonNode expected = resolve(expectedRoot, expectedNode);
        JsonNode actual = resolve(actualRoot, actualNode);

        String expectedType = schemaType(expected);
        String actualType = schemaType(actual);
        assertThat(actualType).as(location + " type").isEqualTo(expectedType);
        if (expected.hasNonNull("format")) {
            assertThat(actual.path("format").asText()).as(location + " format")
                    .isEqualTo(expected.path("format").asText());
        }
        if (expected.has("enum")) {
            assertThat(textValues(actual.path("enum"))).as(location + " enum")
                    .containsExactlyElementsOf(textValues(expected.path("enum")));
        } else if (exactInputConstraints && actual.has("enum")) {
            assertThat(actual.path("enum").isEmpty()).as(location + " undokumentiertes enum").isTrue();
        }

        for (String constraint : CONSTRAINTS) {
            if (expected.has(constraint)) {
                assertConstraintEquals(expected.get(constraint), actual.get(constraint),
                        location + " " + constraint);
            } else if (exactInputConstraints && actual.has(constraint)
                    && !isNoOpConstraint(constraint, actual.get(constraint))) {
                throw new AssertionError(location + " enthält undokumentiertes " + constraint
                        + "=" + actual.get(constraint));
            }
        }

        if ("array".equals(expectedType)) {
            compareSchema(expectedRoot, actualRoot, expected.path("items"), actual.path("items"),
                    location + "[]", exactInputConstraints, visited);
            return;
        }
        if (!"object".equals(expectedType)) return;

        Map<String, JsonNode> expectedProperties = fields(expected.path("properties"));
        Map<String, JsonNode> actualProperties = fields(actual.path("properties"));
        assertThat(actualProperties.keySet()).as(location + " Felder")
                .containsExactlyInAnyOrderElementsOf(expectedProperties.keySet());
        assertThat(textValues(actual.path("required"))).as(location + " Pflichtfelder")
                .containsExactlyInAnyOrderElementsOf(textValues(expected.path("required")));
        expectedProperties.forEach((name, expectedProperty) ->
                compareSchema(expectedRoot, actualRoot, expectedProperty, actualProperties.get(name),
                        location + "." + name, exactInputConstraints, visited));
    }

    private JsonNode resolve(JsonNode root, JsonNode node) {
        JsonNode current = node;
        Set<String> seen = new LinkedHashSet<>();
        while (current != null && current.hasNonNull("$ref")) {
            String ref = current.path("$ref").asText();
            if (!seen.add(ref) || !ref.startsWith("#/")) {
                throw new AssertionError("Ungültige oder zyklische OpenAPI-Referenz: " + ref);
            }
            current = root.at(ref.substring(1));
            if (current.isMissingNode()) throw new AssertionError("Nicht auflösbare OpenAPI-Referenz: " + ref);
        }
        return current;
    }

    private String schemaType(JsonNode schema) {
        if (schema.hasNonNull("type")) return schema.path("type").asText();
        if (schema.has("properties")) return "object";
        if (schema.has("items")) return "array";
        return "";
    }

    private Map<String, JsonNode> fields(JsonNode node) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (node == null || !node.isObject()) return result;
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        iterator.forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private List<String> textValues(JsonNode array) {
        List<String> result = new ArrayList<>();
        if (array != null && array.isArray()) array.forEach(value -> result.add(value.asText()));
        return result;
    }

    private void assertConstraintEquals(JsonNode expected, JsonNode actual, String location) {
        assertThat(actual).as(location).isNotNull();
        if (expected.isNumber() && actual.isNumber()) {
            assertThat(new BigDecimal(actual.asText()).compareTo(new BigDecimal(expected.asText())))
                    .as(location).isZero();
        } else {
            assertThat(actual).as(location).isEqualTo(expected);
        }
    }

    private boolean isNoOpConstraint(String name, JsonNode value) {
        return ("minLength".equals(name) || "minItems".equals(name) || "minProperties".equals(name))
                && value.isNumber() && value.asInt() == 0;
    }
}
