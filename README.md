# IBM JAX-WS Client Extension

## Introduction

This is (currently) intended as a demo extension to instrument the IBM JAX-WS client that ships with WebSphere Application Server.

It started out from the example [DemoServlet3InstrumentationModule](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/examples/extension/src/main/java/com/example/javaagent/instrumentation/DemoServlet3InstrumentationModule.java)
in [opentelemetry-java-instrumentation examples](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/examples/extension),
but very little is left from the original example codebase aside from Gradle bits.

## Build instructions

To build this extension project, run `./gradlew -x test build`. You can find the resulting jar file in `build/libs/`.
The tests have not been updated to work, so they should be disabled for now.

To add the extension to the instrumentation agent:

1. Copy the jar file to a host that is running an application to which you've attached the OpenTelemetry Java instrumentation.
2. Modify the startup command to add the full path to the extension file. For example:

   ```bash
   java -javaagent:path/to/opentelemetry-javaagent.jar \
        -Dotel.javaagent.extensions=opentelemetry-java-instrumentation-jaxws-ibm-client-0.1-all.jar \
        -jar myapp.jar
   ```

Note: to load multiple extensions, you can specify a comma-separated list of extension jars or directories (that
contain extension jars) for the `otel.javaagent.extensions` value.

## Test container

The [`demo`](demo/) directory contains a [`Dockerfile`](demo/Dockerfile) and supporting files to
load a sample app into a [WebSphere traditional container](https://hub.docker.com/r/ibmcom/websphere-traditional)
as well as the otel agent and this extension.
It also contains a [`Makefile`](demo/Makefile) with some helper targets.

### Requirements

1. Docker Desktop (if you use another Docker environment, you might need to tweak the value of `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` in `demo/Dockerfile`).
2. An OpenTelemetry receiver (such as the [Jaeger all-in-one container](https://www.jaegertracing.io/docs/latest/getting-started/#all-in-one) with the web server available at http://localhost:16686/).

### Just Do It

To do everything in a one-liner, run this from the top of the repository:

```
./gradlew :spotlessApply && ./gradlew -x test build && make -C demo remove-container && make -C demo copy-extension run wait tail-server-testlog
```

You'll see `curl: (56) Recv failure: Connection reset by peer` for a minute or two while WebSphere starts.

You'll see this once the server is up and the demo page is accessed:
```
[2/22/24 5:13:33:486 UTC] 00000001 WsServerImpl  A   WSVR0001I: Server server1 open for e-business
[2/22/24 5:13:33:592 UTC] 000000ba ServletWrappe I com.ibm.ws.webcontainer.servlet.ServletWrapper init SRVE0242I: [JaxWSServicesSamples] [/wssamplesei] [SampleController]: Initialization successful.
[2/22/24 5:13:35:102 UTC] 000000ba ServletWrappe I com.ibm.ws.webcontainer.servlet.ServletWrapper init SRVE0242I: [JaxWSServicesSamples] [/wssamplesei] [/WEB-INF/jsp/demo.jsp]: Initialization successful.
```

Then visit http://localhost:9080/wssamplesei/demo and hit "Send Message".
You should see some output in the server's log.
And if you have an OTLP gRPC receiver running on `localhost:4317` then you should also see traces.
If you used the default `One-Way Ping` on the page and  everything worked properly, you should see a `POST` server span, a `PingService.PingServicePort/pingOperation` client span (this is from this JAX-WS client instrumentation), and a `/WSSampleSei/PingService/PingService.PingServicePort/pingOperation` server span from the JAX-WS server.

If you want to see what traces look like *without* the extension, comment out this line in the Dockerfile:
```
ENV OTEL_JAVAAGENT_EXTENSIONS=/work/opentelemetry-java-instrumentation-jaxws-ibm-client-0.1-all.jar
```

You can also enable a few more spans by uncommenting this line in the Dockerfile near the end:
```
ENV OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CONTROLLER_TELEMETRY_ENABLED=true
```

You can also build the test container with just the JAX-WS test EAR and no OTel pieces
by editing the `Makefile` and changing `TARGET` to `base`.

### Debugging

#### No traces
If you don't see traces, you can watch the console logs with `make -C demo tail` to see if anything shows up.

You can also look for `otel.javaagent` in the console logs:
```
docker logs was | grep otel.javaagent
```

Lastly, you can edit the Dockerfile and uncomment `ENV OTEL_JAVAAGENT_DEBUG=true` near the end.

### Loose ends

There are two files in [`demo`](demo/) that are modified files from [IBM's repo](https://github.com/WASdev/ci.docker.websphere-traditional/tree/main/) that builds the base WebSphere docker image:

- [install_app.py](demo/install_app.py) -- [samples/hello-world/install_app.py](https://github.com/WASdev/ci.docker.websphere-traditional/blob/main/samples/hello-world/install_app.py) modified for the JAX-WS sample app
- [start_server.sh](demo/start_server.sh) -- [docker-build/9.0.5.x/scripts/start_server.sh](https://github.com/WASdev/ci.docker.websphere-traditional/blob/main/docker-build/9.0.5.x/scripts/start_server.sh) modified to fix a bug when running the linux/amd64 image on ARM Macs under Rosetta where it fails to detect the WebSphere JVM running and eventually shuts down. -- I should submit a PR for this.

## Possible future work

- Exceptions in the outbound request seem to keep a span from being emitted.
- Fix the commented-out argument matching to the `send` method.
- Use compile-stubs instead of reflection.
- Does async work?
- Cleanup the singleton class (should it be split into two)?
- Look at the commented-out bits in the singleton class (normal things like optionally grabbing headers, other attributes, etc..)
- Do we possibly want to get the outbound information a different way (without accessing a private field)?
- Tests (maybe we can use the cli code directly and it'll be faster? there's also a thin client JAR in WebSphere--`com.ibm.jaxws.thinclient_9.0.jar`--not sure if this instrumentation works on that, though)
- Smoke tests (there's an existing dockerfile in otel that builds a websphere container)
- Hardening.
- Review by someone who has done this stuff before. ;-)
- Contribute back the start_server.sh fix.
- Contribute to otel?

## Cleanup

```
./gradlew clean && make -C demo remove-container clean
```
