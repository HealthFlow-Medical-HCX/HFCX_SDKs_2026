# Consumer smoke test

A standalone Maven project that imports `eg.gov.healthflow:hfcx-sdk-client`
from the **local** Maven repository and prints the SDK version. Confirms the
SDK is consumable as a transitive dependency without parent-POM tricks or
classpath surprises.

## Why it lives here

This is an **integration** check: it has its own `pom.xml` (no parent), so
it builds against whatever the parent `sdk-java/` build has just installed
into the local Maven repo. Running it from the parent build would defeat
the purpose.

## Run locally

```bash
# 1. Install the SDK to the local Maven repo.
cd sdk-java
mvn -B install -DskipTests

# 2. Build and run the smoke test against the installed artifact.
cd tests/integration/consumer-smoketest
mvn -B verify
```

The CI workflow `java-test.yml` does the same in a single job.
