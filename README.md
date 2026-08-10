# dpdp-accelerator
DPDP Accelerator is a collection of artifacts, reference implementations, and documentation that help organizations accelerate the adoption of DPDP Act.

## Build

Requires JDK 11+, Maven 3.6.3+, and Node.js 20.19+ (or 22.12+) with npm, all
on the `PATH`.

Run from this directory (the repository root) — **not** from `dpdp-accelerator/`,
which only builds the consent portal on its own and skips the accelerator zip:

```
mvn clean install
```

This builds the consent portal (frontend + backend WAR) and packages
`wso2-dpdp-is-accelerator-<version>.zip` under
`dpdp-accelerator/accelerators/dpdp-is/target/` — ready to unzip inside
`<IS_HOME>`. See
[`dpdp-accelerator/accelerators/dpdp-is/README.md`](dpdp-accelerator/accelerators/dpdp-is/README.md)
for installation.

## Documentation

- [`docs/setup-guide.md`](docs/setup-guide.md) — registering the consent
  portal application on a running Identity Server.
- [`dpdp-accelerator/accelerators/dpdp-is/README.md`](dpdp-accelerator/accelerators/dpdp-is/README.md) — full installation guide.
- [`GAP-ANALYSIS.md`](GAP-ANALYSIS.md) — feature gaps vs. the portal this was migrated from.
