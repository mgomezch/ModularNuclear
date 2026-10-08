# ==============================================================================
# ModularNuclear Subproject Makefile
# ==============================================================================

SHELL := /usr/bin/env bash
ROOT_DIR := $(shell cd ../.. && pwd)

.PHONY: all build wasm export test deploy push

all: build test

wasm:
	./gradlew compileJava generateWasm

export:
	./gradlew exportStaticDist

build: wasm export

test:
	./gradlew test

deploy:
	$(MAKE) -C $(ROOT_DIR) sim-deploy

push:
	$(MAKE) -C $(ROOT_DIR) sim-push
