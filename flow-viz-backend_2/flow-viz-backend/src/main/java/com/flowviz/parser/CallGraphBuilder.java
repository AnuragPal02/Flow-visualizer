package com.flowviz.parser;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.flowviz.adapter.AdapterRegistry;
import com.flowviz.adapter.FrameworkAdapter;
import com.flowviz.model.EndpointGraph;
import com.flowviz.model.EndpointSummary;
import com.flowviz.model.GraphNode;
import com.flowviz.model.Integration;
import com.flowviz.model.NodeType;
import com.flowviz.model.Tag;
import com.flowviz.parser.SourceIndex.ClassInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the flow for one endpoint by following calls that land on application classes.
 * A call whose receiver is not an application class (JDK, Spring, Jackson, ...) is simply not followed:
 * there is no node for it. Types are worked out from declarations in the source (fields, parameters,
 * locals), so no classpath or symbol solver is needed.
 */
public class CallGraphBuilder {

    private static final int MAX_NODES = 300;
    private static final int MAX_DEPTH = 8;

    // Types that mean "this method reaches outside the application". They are shown as facts on the step,
    // never as graph nodes.
    private static final Map<String, String> INTEGRATIONS = Map.ofEntries(
            Map.entry("RestTemplate", "http"), Map.entry("WebClient", "http"), Map.entry("RestClient", "http"),
            Map.entry("HttpClient", "http"), Map.entry("OkHttpClient", "http"),
            Map.entry("KafkaTemplate", "messaging"), Map.entry("KafkaProducer", "messaging"),
            Map.entry("JmsTemplate", "messaging"), Map.entry("JmsMessagingTemplate", "messaging"),
            Map.entry("RabbitTemplate", "messaging"), Map.entry("AmqpTemplate", "messaging"),
            Map.entry("StreamBridge", "messaging"), Map.entry("Emitter", "messaging"),
            Map.entry("MutinyEmitter", "messaging"),
            Map.entry("ApplicationEventPublisher", "event"),
            Map.entry("JdbcTemplate", "database"), Map.entry("NamedParameterJdbcTemplate", "database"),
            Map.entry("JdbcClient", "database"), Map.entry("EntityManager", "database"),
            Map.entry("MongoTemplate", "database"), Map.entry("RedisTemplate", "database"),
            Map.entry("StringRedisTemplate", "database"),
            Map.entry("JavaMailSender", "email"), Map.entry("MailSender", "email"));

    private final SourceIndex index;
    private final AdapterRegistry registry;
    private final Set<String> warnings;
    private final Map<String, NodeType> typeCache = new HashMap<>();

    private final EndpointGraph graph = new EndpointGraph();
    private final Set<String> expanded = new HashSet<>();
    private final Deque<String> stack = new ArrayDeque<>();
    private boolean truncated;

    public CallGraphBuilder(SourceIndex index, AdapterRegistry registry, Set<String> warnings) {
        this.index = index;
        this.registry = registry;
        this.warnings = warnings;
    }

    public EndpointGraph build(EntryPoint entryPoint, ClassInfo entryClass, FrameworkAdapter adapter) {
        MethodDeclaration entry = entryPoint.getMethod();
        graph.setHttpMethod(entryPoint.getHttpMethod());
        graph.setPath(entryPoint.getPath());
        graph.setFramework(adapter.frameworkName());

        graph.setInputs(MethodFacts.inputs(entry, adapter.frameworkName()));
        graph.setReturns(entry.getType().asString());

        Target root = new Target(entryClass, entry, entry.getNameAsString(), entry.getParameters().size());
        GraphNode rootNode = new GraphNode(root.id(), entryClass.simpleName(), root.name, entryClass.file,
                NodeType.CONTROLLER, adapter.frameworkName(), startLine(entry), endLine(entry));
        decorate(rootNode, entryClass, entry, List.of());
        graph.addNode(rootNode);
        expanded.add(root.id());
        expand(entryClass, entry, root.id());

        if (truncated) {
            warnings.add(entryPoint.getHttpMethod() + " " + entryPoint.getPath()
                    + ": graph truncated at " + MAX_NODES + " nodes");
        }
        graph.setSummary(summarize());
        return graph;
    }

    // ------------------------------------------------------------------ walking

    private void expand(ClassInfo cls, MethodDeclaration method, String callerId) {
        stack.push(callerId);
        for (CallSite site : callSites(method)) {
            detectIntegration(site, cls, graph.node(callerId));
            Target t = resolveSite(site, cls, 0);
            if (t == null) continue;

            // getters/setters on plain data classes are noise, not flow
            if (nodeType(t.owner) == NodeType.OTHER && isTrivialAccessor(t.name)) continue;

            String id = t.id();
            if (id.equals(callerId)) {
                warnings.add("Recursive call detected in " + t.label());
                continue;
            }
            if (!graph.containsNode(id)) {
                if (graph.nodeCount() >= MAX_NODES) {
                    truncated = true;
                    continue;
                }
                graph.addNode(toNode(t));
            }
            graph.addEdge(callerId, id, site.line, index.lineText(cls.file, site.line));

            if (stack.contains(id)) {
                warnings.add("Recursive call detected: " + t.label() + " is called again further down its own flow");
                continue;
            }
            if (t.decl != null && t.decl.getBody().isPresent() && expanded.add(id)) {
                expand(t.owner, t.decl, id);
            }
        }
        stack.pop();
    }

    private GraphNode toNode(Target t) {
        String framework = registry.resolve(t.owner.decl).map(FrameworkAdapter::frameworkName).orElse(null);
        GraphNode node = new GraphNode(t.id(), t.owner.simpleName(), t.name, t.owner.file, nodeType(t.owner), framework,
                startLine(t.decl), endLine(t.decl));
        decorate(node, t.owner, t.decl, t.alternatives);
        return node;
    }

    /** Everything we can say about a step without running it: signature, docs, tags, data and client facts. */
    private void decorate(GraphNode n, ClassInfo owner, MethodDeclaration decl, List<String> alternatives) {
        n.setSignature(MethodFacts.signature(decl));
        n.setDoc(MethodFacts.doc(decl));
        n.setTags(MethodFacts.tags(owner.decl, decl));
        n.setAlternatives(alternatives);

        if (n.getType() == NodeType.REPOSITORY) {
            n.setEntity(entityOf(owner));
            n.setAccess(MethodFacts.access(n.getMethodName(), decl));
            n.setQuery(MethodFacts.query(decl));
        }
        if (n.getType() == NodeType.CLIENT) {
            n.getIntegrations().add(new Integration("http", MethodFacts.clientKind(owner.decl),
                    MethodFacts.clientDetail(owner.decl), n.getStartLine(), ""));
        }
    }

    /** JpaRepository<Order, Long> / PanacheRepository<Item> -> the first type argument. */
    private String entityOf(ClassInfo owner) {
        Deque<ClassInfo> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            ClassInfo c = queue.poll();
            if (!seen.add(c.qualifiedName)) continue;
            List<ClassOrInterfaceType> supers = new ArrayList<>(c.decl.getExtendedTypes());
            supers.addAll(c.decl.getImplementedTypes());
            for (ClassOrInterfaceType t : supers) {
                Optional<NodeList<Type>> args = t.getTypeArguments();
                if (args.isPresent() && !args.get().isEmpty() && args.get().get(0).isClassOrInterfaceType()) {
                    return args.get().get(0).asClassOrInterfaceType().getNameAsString();
                }
            }
            queue.addAll(index.supertypes(c));
        }
        return null;
    }

    /** restTemplate.postForObject(...), kafkaTemplate.send(...): a step reaching outside the app. */
    private void detectIntegration(CallSite site, ClassInfo cls, GraphNode owner) {
        if (site.scope == null || owner == null) return;
        String varName = variableName(site.scope);
        if (varName == null) return;

        Declared d = null;
        if (site.scope.isNameExpr()) {
            d = findDeclared(varName, site.node, cls);
        } else if (site.scope.isFieldAccessExpr() && site.scope.asFieldAccessExpr().getScope().isThisExpr()) {
            d = findField(varName, cls, new HashSet<>());
        }
        if (d == null || d.type == null || !d.type.isClassOrInterfaceType()) return;

        String client = d.type.asClassOrInterfaceType().getNameAsString();
        String kind = INTEGRATIONS.get(client);
        if (kind != null) {
            owner.getIntegrations().add(new Integration(kind, client, site.name, site.line,
                    index.lineText(cls.file, site.line)));
        }
    }

    private EndpointSummary summarize() {
        EndpointSummary s = new EndpointSummary();
        s.steps = graph.nodeCount();
        Map<String, EndpointSummary.IntegrationCount> counts = new LinkedHashMap<>();

        for (GraphNode n : graph.getNodes()) {
            addDistinct(s.layers, n.getType().name());
            if (n.getType() == NodeType.REPOSITORY) {
                String what = n.getEntity() != null ? n.getEntity() : n.getClassName();
                if ("READ".equals(n.getAccess())) addDistinct(s.reads, what);
                else if ("WRITE".equals(n.getAccess())) addDistinct(s.writes, what);
                else addDistinct(s.touches, what);
            }
            for (Tag t : n.getTags()) {
                if (t.kind.equals("transaction")) s.transactional = true;
                if (t.kind.equals("security")) addDistinct(s.security, t.text);
            }
            for (Integration i : n.getIntegrations()) {
                counts.computeIfAbsent(i.kind + "|" + i.client, k -> new EndpointSummary.IntegrationCount(i.kind, i.client)).calls++;
            }
        }
        s.integrations.addAll(counts.values());
        return s;
    }

    private static void addDistinct(List<String> list, String value) {
        if (!list.contains(value)) list.add(value);
    }

    private NodeType nodeType(ClassInfo c) {
        return typeCache.computeIfAbsent(c.qualifiedName, k -> registry.classify(c.decl));
    }

    private static boolean isTrivialAccessor(String name) {
        return name.matches("^(get|set|is)[A-Z].*");
    }

    // ------------------------------------------------------------------ resolving one call

    private Target resolveSite(CallSite site, ClassInfo cls, int depth) {
        if (depth > MAX_DEPTH) return null;

        List<ClassInfo> receivers = new ArrayList<>();
        String varName = null;

        if (site.scope == null || site.scope.isThisExpr()) {
            for (ClassInfo c = cls; c != null; c = index.enclosing(c)) receivers.add(c);
        } else if (site.scope.isSuperExpr()) {
            receivers.addAll(index.supertypes(cls));
        } else {
            ClassInfo r = typeOf(site.scope, site.node, cls, depth + 1);
            if (r == null) return null; // receiver is not application code -> not followed
            receivers.add(r);
            varName = variableName(site.scope);
        }

        for (ClassInfo recv : receivers) {
            Target t = targetIn(recv, site, varName);
            if (t != null) return t;
        }
        return null;
    }

    private Target targetIn(ClassInfo recv, CallSite site, String varName) {
        Lookup found = lookup(recv, site.name, site.argc);
        if (found == null) {
            // Not declared in the app: e.g. findById() inherited from JpaRepository. Keep the repository
            // itself as the last application node; anything else is library/generated (lombok) -> ignored.
            return nodeType(recv) == NodeType.REPOSITORY ? new Target(recv, null, site.name, Math.max(site.argc, 0)) : null;
        }
        if (found.method.getBody().isPresent()) {
            return new Target(found.owner, found.method, site.name, found.method.getParameters().size());
        }

        // abstract/interface method: go to the implementation if there is exactly one
        List<ClassInfo> impls = new ArrayList<>();
        for (ClassInfo impl : index.implementations(recv)) {
            Lookup l = lookup(impl, site.name, site.argc);
            if (l != null && l.method.getBody().isPresent()) impls.add(impl);
        }
        ClassInfo chosen = null;
        List<String> alternatives = List.of();
        if (impls.size() == 1) {
            chosen = impls.get(0);
        } else if (impls.size() > 1) {
            chosen = pickByVariableName(impls, varName);
            if (chosen == null) {
                warnings.add("Multiple application implementations found for " + recv.simpleName() + "." + site.name + "()");
                alternatives = impls.stream().map(ClassInfo::simpleName).collect(Collectors.toList());
            }
        }
        if (chosen != null) {
            Lookup l = lookup(chosen, site.name, site.argc);
            return new Target(l.owner, l.method, site.name, l.method.getParameters().size());
        }
        return new Target(found.owner, found.method, site.name, found.method.getParameters().size())
                .withAlternatives(alternatives);
    }

    /** private final PaymentService stripePaymentService -> StripePaymentService */
    private ClassInfo pickByVariableName(List<ClassInfo> impls, String varName) {
        if (varName == null) return null;
        for (ClassInfo impl : impls) {
            String simple = impl.simpleName();
            if ((Character.toLowerCase(simple.charAt(0)) + simple.substring(1)).equalsIgnoreCase(varName)) return impl;
        }
        return null;
    }

    private Lookup lookup(ClassInfo start, String name, int argc) {
        Deque<ClassInfo> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            ClassInfo c = queue.poll();
            if (!seen.add(c.qualifiedName)) continue;

            List<MethodDeclaration> byName = c.decl.getMethodsByName(name);
            List<MethodDeclaration> matching = new ArrayList<>();
            for (MethodDeclaration m : byName) {
                if (argc < 0 || arityMatches(m, argc)) matching.add(m);
            }
            if (matching.isEmpty() && byName.size() == 1) matching = byName;
            if (!matching.isEmpty()) {
                if (matching.size() > 1 && argc >= 0) {
                    warnings.add("Ambiguous overloads of " + c.simpleName() + "." + name + "(" + argc
                            + " args) - using the first declaration");
                }
                return new Lookup(c, matching.get(0));
            }
            queue.addAll(index.supertypes(c));
        }
        return null;
    }

    private static boolean arityMatches(MethodDeclaration m, int argc) {
        int params = m.getParameters().size();
        if (params == argc) return true;
        return params > 0 && m.getParameter(params - 1).isVarArgs() && argc >= params - 1;
    }

    // ------------------------------------------------------------------ working out types from source

    /** The application class an expression evaluates to, or null (library type / unknown). */
    private ClassInfo typeOf(Expression e, Node site, ClassInfo cls, int depth) {
        if (depth > MAX_DEPTH) return null;

        if (e.isNameExpr()) {
            String name = e.asNameExpr().getNameAsString();
            Declared d = findDeclared(name, site, cls);
            if (d != null) return declaredType(d, depth);
            return index.resolveTypeName(name, cls); // static call on a class: OrderUtil.compute()
        }
        if (e.isThisExpr()) return cls;
        if (e.isFieldAccessExpr()) {
            FieldAccessExpr fa = e.asFieldAccessExpr();
            Expression scope = fa.getScope();
            if (scope.isThisExpr()) {
                Declared d = findField(fa.getNameAsString(), cls, new HashSet<>());
                return d == null ? null : declaredType(d, depth);
            }
            ClassInfo scopeType = typeOf(scope, site, cls, depth + 1);
            if (scopeType != null) {
                Declared d = findField(fa.getNameAsString(), scopeType, new HashSet<>());
                if (d != null) return declaredType(d, depth);
            }
            return index.resolveTypeName(e.toString(), cls);
        }
        if (e.isMethodCallExpr()) {
            MethodCallExpr mc = e.asMethodCallExpr();
            Target t = resolveSite(new CallSite(mc, mc.getScope().orElse(null), mc.getNameAsString(), mc.getArguments().size()), cls, depth + 1);
            return (t == null || t.decl == null) ? null : index.resolveType(t.decl.getType(), t.owner);
        }
        if (e.isCastExpr()) return index.resolveType(e.asCastExpr().getType(), cls);
        if (e.isObjectCreationExpr()) return index.resolveType(e.asObjectCreationExpr().getType(), cls);
        if (e.isTypeExpr()) return index.resolveType(e.asTypeExpr().getType(), cls);
        return null;
    }

    private ClassInfo declaredType(Declared d, int depth) {
        if (d.type == null || d.type.isUnknownType()) return null;
        if (d.type.isVarType()) return d.init == null ? null : typeOf(d.init, d.init, d.ctx, depth + 1);
        return index.resolveType(d.type, d.ctx);
    }

    /** Local variable / parameter visible at {@code site}, else a field of the class (or its supertypes). */
    private Declared findDeclared(String name, Node site, ClassInfo cls) {
        Node child = site;
        Node parent = child.getParentNode().orElse(null);
        while (parent != null) {
            Declared d = null;
            if (parent instanceof BlockStmt) {
                d = scanBlock((BlockStmt) parent, child, name, cls);
            } else if (parent instanceof ForEachStmt) {
                d = fromVariableExpr(((ForEachStmt) parent).getVariable(), name, cls);
            } else if (parent instanceof ForStmt) {
                for (Expression init : ((ForStmt) parent).getInitialization()) {
                    if (init.isVariableDeclarationExpr()) d = orElse(d, fromVariableExpr(init.asVariableDeclarationExpr(), name, cls));
                }
            } else if (parent instanceof TryStmt) {
                for (Expression res : ((TryStmt) parent).getResources()) {
                    if (res.isVariableDeclarationExpr()) d = orElse(d, fromVariableExpr(res.asVariableDeclarationExpr(), name, cls));
                }
            } else if (parent instanceof CatchClause) {
                d = fromParameter(((CatchClause) parent).getParameter(), name, cls);
            } else if (parent instanceof LambdaExpr) {
                d = fromParameters(((LambdaExpr) parent).getParameters(), name, cls);
            } else if (parent instanceof CallableDeclaration) {
                d = fromParameters(((CallableDeclaration<?>) parent).getParameters(), name, cls);
            }
            if (d != null) return d;
            child = parent;
            parent = child.getParentNode().orElse(null);
        }
        return findField(name, cls, new HashSet<>());
    }

    private Declared scanBlock(BlockStmt block, Node upTo, String name, ClassInfo cls) {
        Declared found = null;
        for (Statement st : block.getStatements()) {
            if (st == upTo) break;
            if (st.isExpressionStmt() && st.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
                found = orElse(fromVariableExpr(st.asExpressionStmt().getExpression().asVariableDeclarationExpr(), name, cls), found);
            }
        }
        return found;
    }

    private Declared findField(String name, ClassInfo c, Set<String> seen) {
        if (c == null || !seen.add(c.qualifiedName)) return null;
        for (FieldDeclaration f : c.decl.getFields()) {
            for (VariableDeclarator v : f.getVariables()) {
                if (v.getNameAsString().equals(name)) return new Declared(v.getType(), v.getInitializer().orElse(null), c);
            }
        }
        for (ClassInfo s : index.supertypes(c)) {
            Declared d = findField(name, s, seen);
            if (d != null) return d;
        }
        return findField(name, index.enclosing(c), seen);
    }

    private static Declared fromVariableExpr(VariableDeclarationExpr expr, String name, ClassInfo ctx) {
        for (VariableDeclarator v : expr.getVariables()) {
            if (v.getNameAsString().equals(name)) return new Declared(v.getType(), v.getInitializer().orElse(null), ctx);
        }
        return null;
    }

    private static Declared fromParameters(List<Parameter> params, String name, ClassInfo ctx) {
        for (Parameter p : params) {
            Declared d = fromParameter(p, name, ctx);
            if (d != null) return d;
        }
        return null;
    }

    private static Declared fromParameter(Parameter p, String name, ClassInfo ctx) {
        return p.getNameAsString().equals(name) ? new Declared(p.getType(), null, ctx) : null;
    }

    private static Declared orElse(Declared a, Declared b) {
        return a != null ? a : b;
    }

    private static String variableName(Expression scope) {
        if (scope.isNameExpr()) return scope.asNameExpr().getNameAsString();
        if (scope.isFieldAccessExpr()) return scope.asFieldAccessExpr().getNameAsString();
        return null;
    }

    // ------------------------------------------------------------------ call sites in source order

    private List<CallSite> callSites(MethodDeclaration method) {
        List<CallSite> sites = new ArrayList<>();
        for (MethodCallExpr c : method.findAll(MethodCallExpr.class)) {
            sites.add(new CallSite(c, c.getScope().orElse(null), c.getNameAsString(), c.getArguments().size()));
        }
        for (MethodReferenceExpr r : method.findAll(MethodReferenceExpr.class)) {
            if (!"new".equals(r.getIdentifier())) sites.add(new CallSite(r, r.getScope(), r.getIdentifier(), -1));
        }
        sites.sort((a, b) -> a.begin != b.begin ? Long.compare(a.begin, b.begin) : Long.compare(a.end, b.end));
        return sites;
    }

    private static int startLine(Node n) {
        return n == null ? 0 : n.getBegin().map(p -> p.line).orElse(0);
    }

    private static int endLine(Node n) {
        return n == null ? 0 : n.getEnd().map(p -> p.line).orElse(0);
    }

    private static long key(Position p) {
        return p == null ? 0 : (long) p.line * 100_000L + p.column;
    }

    // ------------------------------------------------------------------ small value types

    private static final class CallSite {
        final Node node;
        final Expression scope;
        final String name;
        final int argc; // -1 = unknown (method reference)
        final long begin;
        final long end;
        final int line; // line of the method name, where the call visibly happens

        CallSite(Node node, Expression scope, String name, int argc) {
            this.node = node;
            this.scope = scope;
            this.name = name;
            this.argc = argc;
            this.begin = key(node.getBegin().orElse(null));
            this.end = key(node.getEnd().orElse(null));
            this.line = node instanceof MethodCallExpr
                    ? ((MethodCallExpr) node).getName().getBegin().map(p -> p.line).orElse(startLine(node))
                    : startLine(node);
        }
    }

    private static final class Target {
        final ClassInfo owner;
        final MethodDeclaration decl; // null when the method is inherited from a library (repository case)
        final String name;
        final int argc;
        List<String> alternatives = List.of(); // implementations, when the interface call cannot be narrowed down

        Target(ClassInfo owner, MethodDeclaration decl, String name, int argc) {
            this.owner = owner;
            this.decl = decl;
            this.name = name;
            this.argc = argc;
        }

        Target withAlternatives(List<String> alternatives) {
            this.alternatives = alternatives;
            return this;
        }

        String id() {
            return owner.qualifiedName + "#" + name + "/" + argc;
        }

        String label() {
            return owner.simpleName() + "." + name + "()";
        }
    }

    private static final class Lookup {
        final ClassInfo owner;
        final MethodDeclaration method;

        Lookup(ClassInfo owner, MethodDeclaration method) {
            this.owner = owner;
            this.method = method;
        }
    }

    private static final class Declared {
        final Type type;
        final Expression init;
        final ClassInfo ctx;

        Declared(Type type, Expression init, ClassInfo ctx) {
            this.type = type;
            this.init = init;
            this.ctx = ctx;
        }
    }
}
