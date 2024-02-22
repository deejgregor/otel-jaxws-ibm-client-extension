#
# https://github.com/WASdev/ci.docker.websphere-traditional
#

FROM ibmcom/websphere-traditional:latest AS base

# copy property files and jython scripts, using the flag `--chown=was:root` to set the appropriate permission
COPY --chown=was:root start_server.sh /work/start_server.sh

COPY --chown=was:root install_app.py /work/config/install_app.py

# https://www.ibm.com/docs/en/was/9.0.5?topic=console-obtaining-jax-ws-web-services-samples
# Note: This leaves some bits scattered in /tmp, but this is just meant to be a quick demo at this point.
RUN curl -f -o /tmp/JaxWSServicesSamples-8_5.zip https://public.dhe.ibm.com/software/websphere/appserv/library/v85/samples/JaxWSServicesSamples-8_5.zip
RUN unzip -d /tmp /tmp/JaxWSServicesSamples-8_5.zip JaxWSServicesSamples-8.5/installableApps/JaxWSServicesSamples.ear
RUN mv /tmp/JaxWSServicesSamples-8.5/installableApps/JaxWSServicesSamples.ear /work/config/JaxWSServicesSamples.ear

RUN /work/configure.sh

FROM base AS otel

RUN curl -f -L -o /work/opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.1.0/opentelemetry-javaagent.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/work/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME=was

ENV OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://host.docker.internal:4317
ENV OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=grpc

ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none

COPY build/opentelemetry-java-instrumentation-jaxws-ibm-client-0.1-all.jar /work/opentelemetry-java-instrumentation-jaxws-ibm-client-0.1-all.jar 
ENV OTEL_JAVAAGENT_EXTENSIONS=/work/opentelemetry-java-instrumentation-jaxws-ibm-client-0.1-all.jar 

#ENV OTEL_INSTRUMENTATION_COMMON_EXPERIMENTAL_CONTROLLER_TELEMETRY_ENABLED=true
#ENV OTEL_JAVAAGENT_DEBUG=true
