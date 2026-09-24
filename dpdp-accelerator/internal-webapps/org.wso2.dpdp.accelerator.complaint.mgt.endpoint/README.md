# Complaint Management REST models

This module's request and response classes are generated from an OpenAPI
specification rather than written by hand.

- **The specification** is `src/main/resources/complaint-server-API.yaml`. It
  defines the API's JSON, so change the API there first.
- **The generated classes** live in `src/gen/java`, in the package
  `org.wso2.dpdp.accelerator.complaint.mgt.endpoint.dto`. They are committed to
  the repository. Don't edit them by hand; regenerate them instead.

## Regenerating the models

A normal build compiles the committed classes and doesn't run the generator. After
changing the specification, regenerate them from the repository root:

```sh
mvn -f dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.complaint.mgt.endpoint/pom.xml \
  -Ddpdp.dto.codegen.skip=false verify
```

This needs the other accelerator modules already installed in your local Maven
repository, so run `mvn clean install` from the root first if you haven't.

Before committing:

- Review the specification change and the regenerated classes together.
- Run the command a second time. It should produce no further changes.
- If you deleted or renamed a schema, delete its old class yourself. The
  generator doesn't remove files.

The generator version is set in the root `pom.xml`.

## How the models are used

Only this module uses the generated classes. The service and DAO modules don't
depend on them.

- Resource classes receive the generated request models, and the handlers pass
  plain values from them to the services.
- The services return DAO models (`Complaint`, `ComplaintEvent`,
  `ComplaintAttachment`, `ComplaintQueueStats`). `ComplaintDtoMapper` turns those
  into the generated response models.
- `ComplaintDtoMapperTest` checks the JSON of every response, so a change to a
  response's shape fails a test.

Generator settings that shape the classes:

- IDs (`format: uuid`) stay plain strings.
- Attachment content (`format: byte`) stays the base64 string the service
  already uses, so it isn't decoded and re-encoded.
- Automatic bean validation is off. The services do their own validation and
  return their own error codes.

Multipart file uploads are described in the specification, but the upload
endpoints still read files through CXF's `@Multipart`. Only their JSON responses
use the generated models.

## Error responses

`ComplaintExceptionMapper` turns every error into a JSON error body:

| Situation | Status | Code |
| --- | --- | --- |
| A `subjectCategory` or `toStatus` value the specification doesn't list | 422 | `CO-4002` |
| Any other request body that can't be read | 400 | `CO-4001` |
| Any other client error with no code of its own, such as 405 or 415 | unchanged | `CO-4000` |

The 422 keeps the code the service returned for unknown values before the
request models used enums.

For the last row, the response keeps the framework's status and headers, such
as `Allow` on a 405. Headers that described the framework's own error body change
because that body is replaced: `Content-Length` and `Content-Encoding` are
dropped, and `Content-Type` becomes `application/json`.
