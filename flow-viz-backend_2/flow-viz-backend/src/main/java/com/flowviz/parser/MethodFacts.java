package com.flowviz.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.flowviz.model.EndpointInput;
import com.flowviz.model.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Facts read straight from a method declaration: signature, Javadoc, behaviour tags, inputs, query text. */
public final class MethodFacts {

    private static final String[] READ_PREFIXES = {"find", "get", "read", "query", "search", "count", "exists",
            "list", "load", "select", "stream", "fetch"};
    private static final String[] WRITE_PREFIXES = {"save", "persist", "insert", "update", "delete", "remove",
            "merge", "flush", "create", "add", "store", "upsert", "put", "clear"};

    private static final Map<String, String> INPUT_KIND = Map.ofEntries(
            Map.entry("PathVariable", "path"), Map.entry("PathParam", "path"),
            Map.entry("RequestParam", "query"), Map.entry("QueryParam", "query"),
            Map.entry("ModelAttribute", "query"), Map.entry("BeanParam", "query"),
            Map.entry("RequestBody", "body"),
            Map.entry("RequestHeader", "header"), Map.entry("HeaderParam", "header"),
            Map.entry("CookieValue", "cookie"), Map.entry("CookieParam", "cookie"),
            Map.entry("RequestPart", "form"), Map.entry("FormParam", "form"));

    // injected by the framework, not something the caller sends
    private static final Set<String> IGNORED_PARAM_TYPES = Set.of(
            "HttpServletRequest", "HttpServletResponse", "ServletRequest", "ServletResponse", "Principal",
            "Authentication", "Model", "ModelMap", "BindingResult", "Errors", "UriInfo", "SecurityContext",
            "HttpHeaders", "Locale", "WebRequest", "HttpSession", "RedirectAttributes", "UriComponentsBuilder",
            "ContainerRequestContext", "SecurityIdentity");

    private MethodFacts() {
    }

    // ------------------------------------------------------------------ signature / doc

    /** "Order getOrder(Long id)" - without annotations, so it stays readable. */
    public static String signature(MethodDeclaration m) {
        if (m == null) return null;
        String params = m.getParameters().stream()
                .map(p -> p.getType().asString() + (p.isVarArgs() ? "..." : "") + " " + p.getNameAsString())
                .collect(Collectors.joining(", "));
        return m.getType().asString() + " " + m.getNameAsString() + "(" + params + ")";
    }

    /** First sentence of the method's Javadoc, or null when there is none. */
    public static String doc(MethodDeclaration m) {
        if (m == null) return null;
        return m.getJavadoc()
                .map(j -> firstSentence(j.getDescription().toText()))
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    private static String firstSentence(String text) {
        String t = text.replaceAll("\\{@(?:code|link|linkplain|literal)\\s+([^}]*)\\}", "$1")
                .replaceAll("\\s+", " ").trim();
        int end = t.indexOf(". ");
        if (end > 0) t = t.substring(0, end + 1);
        return t.length() > 200 ? t.substring(0, 197) + "..." : t;
    }

    // ------------------------------------------------------------------ behaviour tags

    /** Annotations that change how the method behaves; the method's own win over the class's. */
    public static List<Tag> tags(ClassOrInterfaceDeclaration cls, MethodDeclaration m) {
        Map<String, Tag> found = new LinkedHashMap<>();
        if (m != null) collect(m, found);
        collect(cls, found);
        return new ArrayList<>(found.values());
    }

    private static void collect(NodeWithAnnotations<?> node, Map<String, Tag> out) {
        for (AnnotationExpr a : node.getAnnotations()) {
            Tag tag = tagFor(a.getNameAsString(), a);
            if (tag != null) out.putIfAbsent(tag.kind + ":" + a.getNameAsString(), tag);
        }
    }

    private static Tag tagFor(String name, AnnotationExpr a) {
        switch (name) {
            case "Transactional":
                return new Tag("transaction", flag(a, "readOnly", "true") ? "Transactional (read-only)" : "Transactional");
            case "Cacheable":
            case "CacheResult":
                return new Tag("cache", "Result is cached");
            case "CachePut":
                return new Tag("cache", "Updates the cache");
            case "CacheEvict":
            case "CacheInvalidate":
            case "CacheInvalidateAll":
                return new Tag("cache", "Evicts the cache");
            case "Async":
            case "Asynchronous":
                return new Tag("async", "Runs asynchronously");
            case "Retryable":
            case "Retry":
                return new Tag("resilience", "Retries on failure");
            case "CircuitBreaker":
                return new Tag("resilience", "Circuit breaker");
            case "Timeout":
                return new Tag("resilience", "Timeout");
            case "Fallback":
                return new Tag("resilience", "Has a fallback");
            case "PreAuthorize":
            case "PostAuthorize":
            case "Secured":
            case "RolesAllowed": {
                String arg = firstArgument(a);
                return new Tag("security", arg == null ? name : name + ": " + arg);
            }
            case "PermitAll":
                return new Tag("security", "Public (PermitAll)");
            case "DenyAll":
                return new Tag("security", "DenyAll");
            case "Authenticated":
                return new Tag("security", "Authenticated users only");
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ endpoint inputs

    public static List<EndpointInput> inputs(MethodDeclaration m, String framework) {
        List<EndpointInput> out = new ArrayList<>();
        for (Parameter p : m.getParameters()) {
            if (hasAnnotation(p, "Context")) continue;
            String type = p.getType().asString();
            if (IGNORED_PARAM_TYPES.contains(type.replaceAll("<.*>", ""))) continue;

            String kind = null;
            AnnotationExpr source = null;
            for (AnnotationExpr a : p.getAnnotations()) {
                String k = INPUT_KIND.get(a.getNameAsString());
                if (k != null) {
                    kind = k;
                    source = a;
                    break;
                }
            }
            if (kind == null) kind = "quarkus".equals(framework) ? "body" : "query"; // JAX-RS entity vs Spring binding

            String name = source == null ? p.getNameAsString()
                    : AnnotationUtils.extractStringValue(source, "value", "name").orElse(p.getNameAsString());
            boolean optional = type.startsWith("Optional") || hasAnnotation(p, "DefaultValue") || markedOptional(source);
            out.add(new EndpointInput(kind, name, type, !optional));
        }
        return out;
    }

    private static boolean markedOptional(AnnotationExpr a) {
        if (a == null || !a.isNormalAnnotationExpr()) return false;
        for (MemberValuePair pair : a.asNormalAnnotationExpr().getPairs()) {
            String n = pair.getNameAsString();
            if (n.equals("defaultValue") || (n.equals("required") && pair.getValue().toString().equals("false"))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ repositories

    /** READ or WRITE (or null when we cannot tell) for a repository method. */
    public static String access(String methodName, MethodDeclaration decl) {
        if (decl != null) {
            if (hasAnnotation(decl, "Modifying")) return "WRITE";
            String q = query(decl);
            if (q != null) {
                String s = q.trim().toLowerCase();
                if (s.startsWith("select") || s.startsWith("from")) return "READ";
                if (s.startsWith("insert") || s.startsWith("update") || s.startsWith("delete")) return "WRITE";
            }
        }
        String n = methodName.toLowerCase();
        for (String p : READ_PREFIXES) if (n.startsWith(p)) return "READ";
        for (String p : WRITE_PREFIXES) if (n.startsWith(p)) return "WRITE";
        return null;
    }

    /** The text of a Spring Data @Query, if the method has one. */
    public static String query(MethodDeclaration decl) {
        if (decl == null) return null;
        Optional<AnnotationExpr> q = decl.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals("Query")).findFirst();
        if (q.isEmpty()) return null;

        AnnotationExpr a = q.get();
        Expression value = null;
        if (a.isSingleMemberAnnotationExpr()) {
            value = a.asSingleMemberAnnotationExpr().getMemberValue();
        } else if (a.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : a.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("value")) value = pair.getValue();
            }
        }
        if (value == null) return null;

        String text = value.isStringLiteralExpr()
                ? value.asStringLiteralExpr().asString()
                : value.toString().replaceAll("\"\\s*\\+\\s*\"", "").replaceAll("^\"|\"$", "");
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 297) + "..." : text;
    }

    // ------------------------------------------------------------------ feign / rest client interfaces

    public static String clientKind(ClassOrInterfaceDeclaration cls) {
        if (hasAnnotation(cls, "FeignClient")) return "Feign client";
        if (hasAnnotation(cls, "RegisterRestClient")) return "REST client";
        return "Client";
    }

    /** name/url of the remote service, e.g. "name=shipping-service, url=${shipping.url}". */
    public static String clientDetail(ClassOrInterfaceDeclaration cls) {
        for (AnnotationExpr a : cls.getAnnotations()) {
            String n = a.getNameAsString();
            if (!n.equals("FeignClient") && !n.equals("RegisterRestClient")) continue;
            if (a.isSingleMemberAnnotationExpr()) return clean(a.asSingleMemberAnnotationExpr().getMemberValue().toString());
            if (a.isNormalAnnotationExpr()) {
                return a.asNormalAnnotationExpr().getPairs().stream()
                        .map(p -> p.getNameAsString() + "=" + clean(p.getValue().toString()))
                        .collect(Collectors.joining(", "));
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ small helpers

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String name) {
        return node.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(name));
    }

    private static boolean flag(AnnotationExpr a, String member, String value) {
        if (!a.isNormalAnnotationExpr()) return false;
        for (MemberValuePair pair : a.asNormalAnnotationExpr().getPairs()) {
            if (pair.getNameAsString().equals(member) && pair.getValue().toString().equals(value)) return true;
        }
        return false;
    }

    /** The main argument of an annotation as plain text: hasRole('ADMIN'), ADMIN, ... */
    private static String firstArgument(AnnotationExpr a) {
        String raw = null;
        if (a.isSingleMemberAnnotationExpr()) {
            raw = a.asSingleMemberAnnotationExpr().getMemberValue().toString();
        } else if (a.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : a.asNormalAnnotationExpr().getPairs()) {
                if (raw == null || pair.getNameAsString().equals("value")) raw = pair.getValue().toString();
            }
        }
        if (raw == null) return null;
        String text = clean(raw);
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    /** Annotation text without quotes; array braces {"A","B"} are dropped, but ${placeholders} stay intact. */
    private static String clean(String s) {
        String t = s.replace("\"", "").replaceAll("\\s+", " ").trim();
        if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1).trim();
        return t;
    }
}
