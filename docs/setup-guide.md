# Configuring the Consent Portal application

Complete this after installing the accelerator, as the last step before the
portal is ready to use.

## 1. Start the Identity Server

From `<IS_HOME>`:

```
sh bin/wso2server.sh
```

On Windows, run `bin\wso2server.bat` instead.

Wait for the console to print `WSO2 Identity Server started in ...` before
continuing.

## 2. Register the OAuth application

In the Console (`https://<host>:9443/console`):

1. **Applications → New Application → Standard-Based Application**.
2. Name: `DPDP Consent Portal`. Protocol: **OpenID Connect**.
3. Authorized redirect URL: `https://<host>:9443/consent-portal/auth/callback`
4. Open the new application's **Protocol** tab and enable the **Code** and
   **Refresh Token** grant types.
5. Note the **Client ID** and **Client Secret** on the same tab — you'll need
   them in step 6.

## 3. Skip the consent screen and request the username claim

1. **Advanced** tab → enable **Skip login consent** and **Skip logout
   consent**.
2. **User Attributes** tab → add `http://wso2.org/claims/username` as a
   mandatory requested claim, so the portal can display the signed-in
   user's name.

## 4. Authorize the consent management APIs

On the **API Authorization** tab, authorize all three resources below,
selecting **all scopes** for each and policy **RBAC**:

- Consent Management — Consents
- Consent Management — Purposes
- Consent Management — Elements

## 5. Create the portal administrator role

On the **Roles** tab of the same application, create a new role:

- Name: `dpdp-consent-admin`
- Permissions: every scope authorized in step 4

Create the role here, on the application itself, rather than from the
global roles list, so its permissions apply to this application.

Assign users to this role from **User Management → Users → *user* → Roles**
to grant them portal administration access.

## 6. Add the client credentials to the portal configuration

Edit `<IS_HOME>/repository/conf/dpdp-portal.properties`:

```properties
oauth.client.id=<Client ID>
oauth.client.secret=<Client Secret>
```

## 7. Restart

Restart the Identity Server, then open `https://<host>:9443/consent-portal/`.
