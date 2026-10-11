# flow-viz-backend

Shows the flow of each REST endpoint through **your own code only** (Controller -> Service -> Repository/components).
Spring Boot and Quarkus are detected automatically. No classpath, no jars, no `mvn dependency:build-classpath`.

## Run
    mvn spring-boot:run      # API on :8080
    mvn test                 # analyzes src/test/resources/sample-repo and checks the result

Without the UI:
    mvn compile exec:java -Dexec.args="C:\path\to\repo\src\main\java graph.json"

## API
`POST /analyze` with `{ "sourcePath": "C:\\path\\to\\repo\\src\\main\\java" }`
(a repo or module folder that contains src/main/java also works; multi-module is fine).

Response:
- `endpoints[]`: `httpMethod`, `path`, `framework`, `returns`, `inputs[]`, `summary`, `nodes[]`, `edges[]`
  - inputs: `kind` (path|query|body|header|cookie|form), `name`, `type`, `required`
  - summary: `steps`, `layers`, `reads`/`writes`/`touches` (entities), `transactional`, `security`, `integrations`
  - node: `id`, `className`, `methodName`, `file`, `type` (CONTROLLER|SERVICE|REPOSITORY|CLIENT|COMPONENT|OTHER),
    `framework`, `startLine`/`endLine` (0 = inherited from a framework, e.g. findById), `signature`, `doc`,
    `tags` (transaction|cache|async|security|resilience), `integrations` (outbound http/messaging/database/email calls),
    `entity`/`access`/`query` (repositories), `alternatives` (implementations when an interface call is ambiguous)
  - edge: `from`, `to`, `line` + `code` (where the caller calls the next method)
- `sources`: file path -> full source text, for every file that appears in a flow
- `classesAnalyzed`, `warnings[]`: recursion, ambiguous implementation/overload, unresolvable application class, parse problems

## Rules
- Only classes found under the folder you analyze can become nodes; JDK/Spring/Hibernate/etc. never do.
- Interface call -> its implementation if exactly one; several -> stops at the interface and warns.
  A variable named like a class (`stripePaymentService`) picks that implementation.
- Repository methods inherited from Spring Data/Panache (findById, save) end the chain at the repository.
- Getters/setters on plain data classes, constructors and stream/collection plumbing are ignored.

## Limits
- Lombok-generated methods do not exist in source, so they are not followed.
- Static analysis only: reflection and events are not traced.
