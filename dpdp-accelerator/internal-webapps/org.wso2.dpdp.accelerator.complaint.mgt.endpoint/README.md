# Complaint Management REST models

`src/main/resources/complaint-server-API.yaml` owns the public JSON contract.
OpenAPI Generator produces endpoint-local models in `src/gen/java`
(`org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto`). Keep these generated
files under version control and do not edit them by hand.

From the repository root, regenerate and verify this module with:

```sh
mvn -f dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.complaint.mgt.endpoint/pom.xml \
  -Ddpdp.dto.codegen.skip=false verify
```

This command requires the current reactor dependencies to be installed locally.
Normal builds compile the committed models with generation disabled. The generator
version is pinned in the parent POM. Review the specification and generated-source
diff together; a second regeneration must produce identical Java sources. Remove
obsolete generated models explicitly when deleting or renaming schemas.

## Mapping and compatibility

The service layer returns the DAO models (`Complaint`, `ComplaintEvent`,
`ComplaintAttachment`, `ComplaintQueueStats`); it has no DTOs of its own. Handlers
take the generated request models, pass plain values to the services, and build
the generated response models from the returned DAO models with
`ComplaintDtoMapper`. The service and DAO modules never depend on the generated
models.

`ComplaintDtoMapperTest` pins every response's JSON. Two nullable fields the spec
declares but nothing populates yet serialize as `null`:
`ComplaintCreateResponse.userName` and `ComplaintAttachmentDownloadResponse.uploadedTime`.

Generation maps `format: uuid` to `String`, so IDs stay opaque, and `format: byte`
to `String`, so attachment content passes through as the service's base64 string.
Automatic bean validation is disabled to keep the service's own validation and
error codes.

Request enums (`subjectCategory`, `toStatus`) accept only the exact values declared
in the specification. `ComplaintExceptionMapper` returns an unknown enum value as
422 `CO-4002`, the same code the service returned before the models were typed, and
any other unreadable body as 400 `CO-4001`.

Multipart attachment uploads are documented in the specification but still bound
directly through CXF's `@Multipart` in the resource classes; only their JSON
responses use generated models.
