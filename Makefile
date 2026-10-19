# ==============================================================================
# ModularNuclear Subproject Makefile
# ==============================================================================

SHELL := /usr/bin/env bash
ROOT_DIR := $(shell cd ../.. && pwd)

.PHONY: all build textures wasm export test deploy push

all: build test

textures:
	./gradlew generateAllTextures

wasm:
	./gradlew compileJava generateWasm

export:
	./gradlew exportStaticDist

build: textures wasm export

test:
	./gradlew test

deploy:
	$(MAKE) -C $(ROOT_DIR) sim-deploy

push:
	$(MAKE) -C $(ROOT_DIR) sim-push
