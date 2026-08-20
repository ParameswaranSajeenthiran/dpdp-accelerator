# Components

OSGi bundles that extend WSO2 Identity Server itself — configuration parsers, consent management
extension points, identity extensions, shared utilities. Each is built as a `bundle` and installed
into `<IS_HOME>/repository/components/dropins` by the accelerator's antrun step.

- `org.wso2.dpdp.accelerator.identity.extensions` — provisions the DPDP Consent Portal
  application in every tenant via a `TenantMgtListener` hook, the same way WSO2 IS provisions
  Console and My Account. See `tenant-portal-provisioning-plan.md` at the repo root.

This directory has **no aggregator `pom.xml`**, following the Financial Services accelerator. Add each
module to `dpdp-accelerator/pom.xml` with its path prefix:

```xml
<module>components/org.wso2.dpdp.accelerator.common</module>
```

A new component must also be copied into the distribution — add a `<copy>` for its `target` directory
to the antrun `create-solution` execution in `accelerators/dpdp-is/pom.xml`.
