LEIN         ?= lein
NATIVE_IMAGE ?= /opt/graalvm/bin/native-image
BINARY       ?= jarbloat

.PHONY: help clean uberjar uberjar-graalvm test repl

help: ## show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

# tasks

all: test uberjar

clean: ## clean compiled files
	$(LEIN) clean

uberjar: ## create uberjar
	$(LEIN) uberjar

uberjar-graalvm: ## create uberjar compiled for graalvm
	$(LEIN) with-profile +graalvm uberjar

test: ## run all tests
	$(LEIN) test

repl: ## start clojure repl
	LEIN_FAST_TRAMPOLINE=1 $(LEIN) -o trampoline with-profile +test repl :headless

lint: ## run clj-kondo linter
	$(LEIN) lint

binary: ## create binary by using graalvm
binary: uberjar-graalvm
	$(NATIVE_IMAGE) \
	--report-unsupported-elements-at-runtime \
	--initialize-at-run-time=org.apache.logging.log4j.core.pattern.JAnsiTextRenderer \
	--initialize-at-run-time=org.apache.logging.log4j.core.async.AsyncLoggerContext \
	--initialize-at-run-time=com.jcraft.jsch.agentproxy.connector.PageantConnector$User32 \
	--initialize-at-run-time=com.sun.jna.platform.win32.User32 \
	--initialize-at-run-time=com.sun.jna.platform.win32.Kernel32 \
	--no-server \
	--no-fallback \
	-H:+ReportExceptionStackTraces \
	--initialize-at-build-time \
	--allow-incomplete-classpath \
	-jar target/jarbloat.jar -H:Name=$(BINARY)
