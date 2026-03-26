<p align="right">
  <img src="assets/jmu-logo.png" alt="JMU University logo" width="140" />
</p>

# Thesis Defense Project

## Introduction

This repository is prepared for the **Master Thesis defense** and contains the complete project package used in the research, implementation, and presentation phases.
It combines:

- the COOM to RDF/OWL transformation implementation (`coom-transpiler`),
- the web interface and tooling used for demonstrations,
- the written thesis sources in LaTeX (`thesis`),
- and benchmark/validation workflows used to support evaluation results.

The goal is to keep everything needed for the defense in one place: code, documentation, metrics workflow, and thesis materials.

## Table of Contents

- [Overview](#overview)
- [Repository structure](#repository-structure)
- [Development quickstart](#development-quickstart)
- [Prerequisites](#prerequisites)
- [Build everything](#build-everything)
- [Run the web app (Quarkus)](#run-the-web-app-quarkus)
- [Run the CLI (coom-compiler)](#run-the-cli-coom-compiler)
- [Docker (optional)](#docker-optional)
- [Quality metrics and synthetic benchmarks](#quality-metrics-and-synthetic-benchmarks)
- [Troubleshooting](#troubleshooting)

## Overview

This repository contains the implementation and written components for the COOM → RDF/OWL thesis work.
It includes a Java compiler/CLI, a Quarkus web UI, and the LaTeX sources for the thesis itself.

## Repository structure

- `coom-transpiler/` — Multi-module Maven project (compiler + CLI + web UI)
  - `coom-compiler/` — Java compiler library + CLI
  - `coom-web/` — Quarkus web UI for uploading `.coom` / `.coom.py` files
  - `example/` — Sample COOM files used for testing and demos
- `assets/` — Supporting assets (figures, diagrams, etc.)
- `Dockerfile` — Multi-stage build for the web app

## Development quickstart

This repository contains a multi-module Maven project under `coom-transpiler/`:

- `coom-compiler` — Java compiler library + CLI that parses COOM, applies profiles, and emits RDF.
- `coom-web` — Quarkus web UI that lets you upload a `.coom` / `.coom.py` file and view results.

### Prerequisites

- JDK 21 or newer (Temurin/Adoptium recommended)
- Maven 3.9+
- macOS shell examples use zsh
- Optional: Docker (for containerized runs)

### Build everything

From the thesis root, build the reactor once (installs `coom-compiler` to local Maven repo so IDEs and `coom-web` resolve it):

```bash
cd coom-transpiler
mvn -q -DskipTests install
```

### Run the web app (Quarkus)

Dev mode with live reload (recommended during development):

```bash
cd coom-transpiler
mvn -q -pl coom-web -am quarkus:dev
# App: http://localhost:8080/view-coom
```

Packaged (JVM) run:

```bash
cd coom-transpiler
mvn -q -pl coom-web -am package
java -jar coom-web/target/quarkus-app/quarkus-run.jar
# App: http://localhost:8080/view-coom
```

Common options:

```bash
# Change HTTP port
java -Dquarkus.http.port=9090 -jar coom-web/target/quarkus-app/quarkus-run.jar

# Bind on all interfaces (already default in Dockerfile)
java -Dquarkus.http.host=0.0.0.0 -jar coom-web/target/quarkus-app/quarkus-run.jar
```

### Run the CLI (coom-compiler)

Build the CLI jar:

```bash
cd coom-transpiler
mvn -q -pl coom-compiler -am package
```

Run the fat jar:

```bash
java -jar coom-compiler/target/coom-compiler-cli.jar /path/to/file.coom -o /tmp/out.txt
```

Dev run via Maven exec (no jar required):

```bash
cd coom-transpiler
mvn -q -pl coom-compiler -Dexec.args="/path/to/file.coom -o /tmp/out.txt" exec:java
```

Notes:

- Replace `/path/to/file.coom` with your `.coom` or `.coom.py` input file.
- The `-o` argument is the output path; adjust as needed.

### Docker (optional)

A multi-stage Dockerfile is provided at the repo root. Build and run:

```bash
docker build -t coom-web:dev .
docker run --rm -p 8080:8080 coom-web:dev
# App: http://localhost:8080/view-coom
```

This uses a builder image to compile the project and a slim JRE 21 runtime image to run the Quarkus app.

### Quality metrics and synthetic benchmarks

This section covers the full workflow used for Chapter 6 metrics:
model generation, compilation/validation, and table-ready measurements.

Build the compiler tools first:

```bash
cd coom-transpiler
mvn -q -pl coom-compiler -am package
```

From the repository root, generate synthetic benchmark COOM models (G1/G2/G3):

```bash
cd <repository-path>
java -cp coom-transpiler/coom-compiler/target/coom-compiler-cli.jar \
  org.example.coom.compiler.metrics.SyntheticCoomGeneratorCli \
  --out /tmp/coom-bench \
  --g1-depth 3 --g1-branch 2 \
  --g2-n 6 --g2-m 6 \
  --g3-k 5
```

Compile generated synthetic models to RDF with SHACL validation
for all preset shape sets:

```bash
cd <repository-path>
for preset in syntactic-core semantic-consistency profile-refinements; do
  for f in /tmp/coom-bench/*.coom; do
    base="$(basename "$f" .coom)"
    java -jar coom-transpiler/coom-compiler/target/coom-compiler-cli.jar \
      "$f" \
      --validate \
      --shapes "$preset" \
      -f turtle \
      -o "/tmp/${base}.${preset}.ttl"
  done
done
```

Compile measured case-study models to RDF with SHACL validation
for all preset shape sets:

```bash
cd <repository-path>
for preset in syntactic-core semantic-consistency profile-refinements; do
  for f in \
    coom-transpiler/example/coom/rectangle.coom \
    coom-transpiler/example/coom/city-bike.coom \
    coom-transpiler/example/coom/tshirt.coom; do
    base="$(basename "$f" .coom)"
    java -jar coom-transpiler/coom-compiler/target/coom-compiler-cli.jar \
      "$f" \
      --validate \
      --shapes "$preset" \
      -f turtle \
      -o "/tmp/${base}.${preset}.ttl"
  done
done
```

Run the benchmark metrics collector for synthetic datasets (table-ready CSV):

```bash
cd <repository-path>
java -cp coom-transpiler/coom-compiler/target/coom-compiler-cli.jar \
  org.example.coom.compiler.metrics.QualityMetricsCli \
  /tmp/coom-bench/g1-balanced-d3-b2.coom \
  /tmp/coom-bench/g2-attrfanout-n6-m6.coom \
  /tmp/coom-bench/g3-defaultconflict-k5.coom
```

Run the same collector for measured case-study models:

```bash
cd <repository-path>
java -cp coom-transpiler/coom-compiler/target/coom-compiler-cli.jar \
  org.example.coom.compiler.metrics.QualityMetricsCli \
  coom-transpiler/example/coom/rectangle.coom \
  coom-transpiler/example/coom/city-bike.coom \
  coom-transpiler/example/coom/tshirt.coom
```

Collector output columns:
- `Model,Triples,Shapes,SHACL_ms,Reach_ms,Sanity_ms,CrossAttr_ms,Agg_ms,Conforms,Violations`

Map columns to thesis tables:
- SHACL table: `Triples`, `Shapes`, `SHACL_ms`
- SPARQL table: `Reach_ms`, `Sanity_ms`, `CrossAttr_ms`, `Agg_ms`

Optional derived metric:

```text
ViolationDensity = Violations / (Triples / 1000)
```

### Troubleshooting

- VS Code cannot find `org.example.coom.compiler.CoomCompiler` from `coom-web`:
  - Ensure you open the folder `coom-transpiler` (the Maven parent), not only `coom-web`.
  - Run `mvn -q -DskipTests install` at `coom-transpiler/` once to install `coom-compiler` locally.
  - In VS Code: "Maven: Reload Project" and "Java: Clean Java Language Server Workspace".

- Build errors about missing Quarkus dependency versions:
  - The parent POM imports the Quarkus BOM. Make sure you run Maven from `coom-transpiler/` so modules inherit it.

- Upload errors in the web UI:
  - Ensure the HTML form uses `enctype="multipart/form-data"` and the input name is `file`.
  - Only `.coom` and `.coom.py` extensions are accepted by the controller.
