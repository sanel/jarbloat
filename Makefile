LEIN         ?= lein
NATIVE_IMAGE ?= native-image
BINARY       ?= target/jarbloat

.PHONY: help clean uberjar uberjar-graalvm test repl native lint project-version

help: ## show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

# tasks

all: test uberjar

clean: ## clean compiled files
	$(LEIN) clean
	$(RM) $(BINARY) $(BINARY).build_artifacts.txt

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

project-version: ## print project version (used by github actions)
	@$(LEIN) project-version

native: ## create binary by using graalvm
native: uberjar-graalvm
	$(NATIVE_IMAGE) \
	--report-unsupported-elements-at-runtime \
	--initialize-at-run-time=com.sun.jna.platform.win32.User32 \
	--initialize-at-run-time=com.sun.jna.platform.win32.Kernel32 \
	--initialize-at-run-time=org.apache.bcel.util.ClassPath \
	--no-fallback \
	-H:+ReportExceptionStackTraces \
	--initialize-at-build-time \
	--allow-incomplete-classpath \
	-O2 \
	-jar target/jarbloat.jar -H:Name=$(BINARY)
