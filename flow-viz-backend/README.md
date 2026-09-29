# flow-viz-backend

Parser + REST API for the microservice call-graph visualizer.
Detects entry points and builds a call graph for **Spring Boot** and
**Quarkus** target repos — detected automatically per class, so a repo
mixing both (or you're just not sure) works without configuration.

## Requirements
- Java 17+
- Maven 3.8+

## 1. Build
```
mvn clean package -DskipTests
```

## 2. Get the target repo's classpath
Required for correct symbol resolution against framework/library types
(`RestTemplate`, `JpaRepository`, Panache, etc.) — without it every such call
falls back to LIBRARY/UNKNOWN instead of being classified correctly.
Run this **inside the TARGET repo** (not this project):
```
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
cat cp.txt
```

## 3. CLI mode — use this FIRST
This is the fast sanity check for symbol resolution, before touching the API
or the frontend at all:
```
mvn compile exec:java -Dexec.mainClass=com.flowviz.cli.AnalyzeCli \
  -Dexec.args="/path/to/target-repo/src/main/java '<contents-of-cp.txt>' graph.json"
```
Check `graph.json` and the `WARNING` lines printed to stdout. If every node
comes back as `LIBRARY`/`UNKNOWN` instead of `SERVICE`/`REPOSITORY`, the
classpath jars almost certainly aren't loading — that's the #1 failure mode.

## 4. API mode
```
mvn spring-boot:run
```
```
curl -X POST http://localhost:8080/analyze \
  -H "Content-Type: application/json" \
  -d '{
        "sourcePath": "/path/to/target-repo/src/main/java",
        "classpath": "<contents-of-cp.txt>"
      }'
```
Returns a JSON array of `EndpointGraph` objects — one per discovered HTTP
endpoint, each with its own `nodes`/`edges`. Feed this straight into the React
Flow frontend.

## Known limitations (by design, for a 1-day build)
- **No LLM summaries yet** — the `summary` field on every node is always
  `null`. Wire this in separately: POST each node's file/method source to the
  Claude API and fill `summary` before returning the response, with a
  hash-based cache so re-runs don't re-summarize unchanged files.
- **No repo cloning** — `sourcePath` must already be a local checkout.
- **REST only** — gRPC/Kafka/messaging entry points are out of scope.
- **Ambiguous framework matches** (e.g. Quarkus's quarkus-spring-web
  compatibility extension) resolve to the first matching adapter; check the
  `warnings` array in the response if a repo's classification looks off.
- **Quarkus external-call detection** (`isExternalCallType` in
  `QuarkusAdapter`) uses a naming heuristic (`*Client`) rather than resolving
  `@RestClient` field annotations — tighten this if your target repo doesn't
  follow that convention.
