TAG             = foo
TARGET          = otel # base or otel
PLATFORM        = linux/amd64
CONTAINER_NAME  = was
CONTAINER_ID    = $(shell docker ps --filter name=$(CONTAINER_NAME) --format '{{.ID}}')

.PHONY: default
default: run

.PHONY: docker-image
docker-image: Dockerfile
	docker build --platform $(PLATFORM) --target $(TARGET) --tag $(TAG) .

run: docker-image
	docker run --name $(CONTAINER_NAME) -p 9080:9080 -p 9043:9043 -p 9443:9443 --platform $(PLATFORM) --rm -d $(TAG)

tail:
	docker logs -f $(CONTAINER_ID)

wait:
	curl -sSf --retry 180 --retry-delay 1 --retry-all-errors -o /dev/null http://localhost:9080/wssamplesei/demo

tail-server-testlog:
	docker exec $(CONTAINER_NAME) sh -c "tail -f /opt/IBM/WebSphere/AppServer/profiles/AppSrv01/logs/server1/TextLog_*.log"

remove-container:
	if [ -n "$(CONTAINER_ID)" ]; then docker rm -f $(CONTAINER_ID); fi

clean:
	rm -rf build

copy-extension:
	mkdir -p build
	cp ../build/libs/opentelemetry-java-instrumentation-jaxws-ibm-client-0.1-all.jar build/.
