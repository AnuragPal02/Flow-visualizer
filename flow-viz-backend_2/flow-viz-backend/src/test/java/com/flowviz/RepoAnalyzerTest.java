package com.flowviz;

import com.flowviz.model.EndpointGraph;
import com.flowviz.model.EndpointSummary;
import com.flowviz.model.GraphEdge;
import com.flowviz.model.GraphNode;
import com.flowviz.model.NodeType;
import com.flowviz.parser.RepoAnalyzer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RepoAnalyzerTest {

    private static final Path SAMPLE = Path.of("src/test/resources/sample-repo");
    private static final Path SAMPLE_SRC = SAMPLE.resolve("src/main/java");

    private static final Set<String> LIBRARY_NAMES = Set.of(
            "List", "ArrayList", "Map", "String", "Optional", "Stream", "Collectors", "Logger", "LoggerFactory",
            "ResponseEntity", "JpaRepository", "CrudRepository", "RestController", "Math", "System");

    private static RepoAnalyzer.AnalysisResult analyze(Path p) {
        return new RepoAnalyzer().analyze(p);
    }

    private static EndpointGraph endpoint(RepoAnalyzer.AnalysisResult r, String method, String path) {
        return r.getEndpoints().stream()
                .filter(e -> e.getHttpMethod().equals(method) && e.getPath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("endpoint not found: " + method + " " + path));
    }

    private static Set<String> labels(EndpointGraph g) {
        return g.getNodes().stream()
                .map(n -> n.getClassName() + "." + n.getMethodName() + "()")
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void findsSpringAndQuarkusEndpoints() {
        Set<String> found = analyze(SAMPLE_SRC).getEndpoints().stream()
                .map(e -> e.getHttpMethod() + " " + e.getPath())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of("GET /orders/{id}", "GET /orders", "POST /orders", "GET /items/{id}"), found);
    }

    @Test
    void repoRootWorksToo_andTestSourcesAreIgnored() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE);
        assertEquals(4, r.getEndpoints().size());
        for (EndpointGraph g : r.getEndpoints()) {
            for (GraphNode n : g.getNodes()) {
                assertFalse(n.getFile().contains("src/test"), n.getFile());
            }
        }
    }

    @Test
    void getOrderShowsOnlyApplicationCode() {
        EndpointGraph g = endpoint(analyze(SAMPLE_SRC), "GET", "/orders/{id}");
        assertEquals(new TreeSet<>(List.of(
                "OrderController.getOrder()",
                "OrderServiceImpl.getOrder()",       // interface call resolved to the single implementation
                "OrderRepository.findById()",        // inherited from Spring Data: repository is the last app node
                "InventoryService.check()",          // service -> service
                "InventoryService.retries()",
                "InventoryService.recheck()",
                "InventoryRepository.countBySku()",
                "StripePaymentService.validatePayment()", // picked by variable name, not guessed
                "PaymentAudit.record()",
                "OrderServiceImpl.applyPricing()",
                "PriceCalculator.total()",
                "TaxHelper.rate()")), labels(g));
    }

    @Test
    void noLibraryOrFrameworkNodeEverAppears() {
        for (EndpointGraph g : analyze(SAMPLE_SRC).getEndpoints()) {
            Set<String> ids = g.getNodes().stream().map(GraphNode::getId).collect(Collectors.toSet());
            for (GraphNode n : g.getNodes()) {
                assertTrue(n.getFile().startsWith("src/main/java/com/example/"), "not app code: " + n.getFile());
                assertFalse(LIBRARY_NAMES.contains(n.getClassName()), "library node: " + n.getClassName());
                assertNotNull(n.getType());
            }
            for (GraphEdge e : g.getEdges()) {
                assertTrue(ids.contains(e.getFrom()) && ids.contains(e.getTo()), "dangling edge " + e.getFrom() + " -> " + e.getTo());
            }
        }
    }

    @Test
    void getterOnPlainDataClassIsNotAFlowNode() {
        for (EndpointGraph g : analyze(SAMPLE_SRC).getEndpoints()) {
            for (GraphNode n : g.getNodes()) {
                assertFalse(n.getClassName().equals("Order") || n.getClassName().equals("OrderDto"), n.getClassName());
            }
        }
    }

    @Test
    void ambiguousImplementationIsWarnedNotInvented() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE_SRC);
        Set<String> labels = labels(endpoint(r, "POST", "/orders"));
        assertTrue(labels.contains("PaymentService.validatePayment()"));
        assertFalse(labels.contains("StripePaymentService.validatePayment()"));
        assertFalse(labels.contains("PaypalPaymentService.validatePayment()"));
        assertTrue(r.getWarnings().contains("Multiple application implementations found for PaymentService.validatePayment()"),
                r.getWarnings().toString());
    }

    @Test
    void recursionTerminatesAndIsReported() {
        RepoAnalyzer.AnalysisResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> analyze(SAMPLE_SRC));
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.startsWith("Recursive call detected")), r.getWarnings().toString());
    }

    @Test
    void noWarningFloodForLibraryCalls() {
        List<String> warnings = analyze(SAMPLE_SRC).getWarnings();
        assertTrue(warnings.size() <= 5, warnings.toString());
        assertTrue(warnings.stream().noneMatch(w -> w.contains("Could not resolve call")), warnings.toString());
    }

    @Test
    void repositoryNodeIsClassifiedAsRepository() {
        GraphNode repo = endpoint(analyze(SAMPLE_SRC), "GET", "/items/{id}").getNodes().stream()
                .filter(n -> n.getClassName().equals("ItemRepository")).findFirst().orElseThrow();
        assertEquals("REPOSITORY", repo.getType().name());
        assertEquals("quarkus", repo.getFramework());
    }

    @Test
    void edgesCarryTheCallLineAndCode() throws Exception {
        EndpointGraph g = endpoint(analyze(SAMPLE_SRC), "GET", "/orders/{id}");
        GraphEdge e = g.getEdges().stream()
                .filter(x -> x.getFrom().endsWith("OrderController#getOrder/1") && x.getTo().endsWith("OrderServiceImpl#getOrder/1"))
                .findFirst().orElseThrow();

        List<String> lines = Files.readAllLines(SAMPLE_SRC.resolve("com/example/web/OrderController.java"));
        int expected = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("orderService.getOrder(id)")) expected = i + 1;
        }
        assertTrue(expected > 0);
        assertEquals(expected, e.getLine());
        assertTrue(e.getCode().contains("orderService.getOrder(id)"), e.getCode());
    }

    @Test
    void nodesCarryMethodLinesAndSourceIsReturned() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE_SRC);
        for (EndpointGraph g : r.getEndpoints()) {
            for (GraphNode n : g.getNodes()) {
                assertTrue(r.getSources().containsKey(n.getFile()), n.getFile());
                assertFalse(r.getSources().get(n.getFile()).isEmpty(), n.getFile());
                if (n.getStartLine() > 0) assertTrue(n.getEndLine() >= n.getStartLine());
            }
        }
        // findById is inherited from Spring Data: no method body in the repo, so no lines, but the class source exists
        GraphNode findById = endpoint(r, "GET", "/orders/{id}").getNodes().stream()
                .filter(n -> n.getClassName().equals("OrderRepository") && n.getMethodName().equals("findById"))
                .findFirst().orElseThrow();
        assertEquals(0, findById.getStartLine());
        // a real method has a range that contains its own declaration
        GraphNode getOrder = endpoint(r, "GET", "/orders/{id}").getNodes().get(0);
        assertTrue(getOrder.getStartLine() > 0 && getOrder.getEndLine() > getOrder.getStartLine());
    }

    private static GraphNode node(EndpointGraph g, String className, String methodName) {
        return g.getNodes().stream()
                .filter(n -> n.getClassName().equals(className) && n.getMethodName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("node not found: " + className + "." + methodName));
    }

    @Test
    void endpointContractComesFromTheControllerSignature() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE_SRC);
        EndpointGraph get = endpoint(r, "GET", "/orders/{id}");
        assertEquals("ResponseEntity<Order>", get.getReturns());
        assertEquals(1, get.getInputs().size());
        assertEquals("path", get.getInputs().get(0).kind);
        assertEquals("id", get.getInputs().get(0).name);
        assertEquals("Long", get.getInputs().get(0).type);
        assertTrue(get.getInputs().get(0).required);
        assertEquals("body", endpoint(r, "POST", "/orders").getInputs().get(0).kind);
        assertEquals("query", endpoint(r, "GET", "/orders").getInputs().get(0).kind);
    }

    @Test
    void stepsCarrySignatureDocAndBehaviourTags() {
        EndpointGraph g = endpoint(analyze(SAMPLE_SRC), "GET", "/orders/{id}");
        GraphNode service = node(g, "OrderServiceImpl", "getOrder");
        assertEquals("Order getOrder(Long id)", service.getSignature());
        assertEquals("Loads an order and prices it.", service.getDoc());
        assertTrue(service.getTags().stream()
                .anyMatch(t -> t.kind.equals("transaction") && t.text.equals("Transactional (read-only)")));
        GraphNode controller = node(g, "OrderController", "getOrder");
        assertEquals("Returns one order together with its price.", controller.getDoc());
        assertTrue(controller.getTags().stream()
                .anyMatch(t -> t.kind.equals("security") && t.text.equals("PreAuthorize: hasRole('USER')")));
    }

    @Test
    void repositoryStepsKnowTheirEntityAccessAndQuery() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE_SRC);
        GraphNode findById = node(endpoint(r, "GET", "/orders/{id}"), "OrderRepository", "findById");
        assertEquals("Order", findById.getEntity());
        assertEquals("READ", findById.getAccess());
        assertEquals("WRITE", node(endpoint(r, "POST", "/orders"), "OrderRepository", "save").getAccess());
        GraphNode expensive = node(endpoint(r, "GET", "/orders"), "OrderRepository", "findExpensive");
        assertEquals("select o from Order o where o.total > ?1", expensive.getQuery());
        assertEquals("READ", expensive.getAccess());
        assertEquals("Item", node(endpoint(r, "GET", "/items/{id}"), "ItemRepository", "findById").getEntity());
    }

    @Test
    void outboundCallsAreFactsOnTheStepNotNodes() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE_SRC);
        GraphNode pay = node(endpoint(r, "GET", "/orders/{id}"), "StripePaymentService", "validatePayment");
        assertTrue(pay.getIntegrations().stream().anyMatch(i -> i.kind.equals("http")
                && i.client.equals("RestTemplate") && i.detail.equals("postForObject") && i.line > 0
                && i.code.contains("restTemplate.postForObject")));

        GraphNode shipping = node(endpoint(r, "POST", "/orders"), "ShippingClient", "reserve");
        assertEquals(NodeType.CLIENT, shipping.getType());
        assertEquals("name=shipping-service, url=${shipping.url}", shipping.getIntegrations().get(0).detail);

        for (EndpointGraph g : r.getEndpoints()) {
            for (GraphNode n : g.getNodes()) assertNotEquals("RestTemplate", n.getClassName());
        }
    }

    @Test
    void summaryAnswersWhatTheEndpointDoes() {
        RepoAnalyzer.AnalysisResult r = analyze(SAMPLE_SRC);

        EndpointSummary get = endpoint(r, "GET", "/orders/{id}").getSummary();
        assertEquals(12, get.steps);
        assertTrue(get.transactional);
        assertEquals(List.of("Order", "Inventory"), get.reads);
        assertTrue(get.writes.isEmpty());
        assertTrue(get.security.contains("PreAuthorize: hasRole('USER')"));
        assertEquals("CONTROLLER", get.layers.get(0));
        assertEquals(1, get.integrations.size());
        assertEquals("RestTemplate", get.integrations.get(0).client);
        assertEquals(1, get.integrations.get(0).calls);

        EndpointSummary post = endpoint(r, "POST", "/orders").getSummary();
        assertEquals(List.of("Order"), post.writes);
        assertTrue(post.transactional);
        assertTrue(post.integrations.stream().anyMatch(i -> i.client.equals("Feign client") && i.calls == 1));
    }

    @Test
    void ambiguousInterfaceListsItsImplementations() {
        GraphNode n = node(endpoint(analyze(SAMPLE_SRC), "POST", "/orders"), "PaymentService", "validatePayment");
        assertEquals(List.of("PaypalPaymentService", "StripePaymentService"), n.getAlternatives());
    }
}
