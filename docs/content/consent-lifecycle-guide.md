# Consent Lifecycle Guide

A consent moves through a small set of states from creation to its end, and who is allowed to
approve, reject, or revoke it at each stage depends on how the consent was set up. This guide
explains the states, the three ways a consent can be set up, and who can act at each point.

Complete the [Quickstart](quickstart.md) first, and see the
[Role Management Guide](role-guide.md) for the scopes behind self-service consent actions.

## 1. Consent states

| State | Meaning |
|---|---|
| `PENDING` | Waiting on one or more people to approve or reject it. |
| `ACTIVE` | Approved and currently in effect. |
| `REJECTED` | An authoriser declined it. Final — the consent cannot be reopened. |
| `REVOKED` | Withdrawn after being active. Final. |
| `EXPIRED` | Passed its expiry time. Final. |

A consent created for the subject alone starts `ACTIVE` immediately — there's no one else to wait
on. A consent that names one or more authorisers starts `PENDING` and only becomes `ACTIVE` once
**every** named authoriser has approved it. A single rejection, by anyone named, ends it
immediately.

## 2. Three ways a consent can be set up

### Direct Consent

The subject consents for themself. No one else is named.

```json
{
  "subjectId": "alice@wso2.com",
  "purposes": [ { "id": "...", "elements": [ { "id": "..." } ] } ]
}
```

Alice manages this consent from the start — it's `ACTIVE` immediately, and she can revoke it
herself at any time.

### Delegated Consent

Someone else decides on the subject's behalf — for example, a parent consenting for a child, or a
guardian consenting for a dependent. The subject is not named as an authoriser, so they can see
the consent but cannot act on it. More than one authoriser can be named; every one of them must
approve before the consent becomes active — a child's mother *and* father can both be required to
agree.

```json
{
  "subjectId": "child@wso2.com",
  "purposes": [ { "id": "...", "elements": [ { "id": "..." } ] } ],
  "authorizations": [
    { "userId": "mother@wso2.com", "type": "USER" },
    { "userId": "father@wso2.com", "type": "USER" }
  ]
}
```

### Co-Authorized Consent

The subject is named as one of the authorisers themself, deciding alongside at least one other
person — for example, a joint account holder who must approve alongside a co-holder.

```json
{
  "subjectId": "alice@wso2.com",
  "purposes": [ { "id": "...", "elements": [ { "id": "..." } ] } ],
  "authorizations": [
    { "userId": "alice@wso2.com", "type": "USER" },
    { "userId": "bob@wso2.com", "type": "USER" }
  ]
}
```

## 3. Who can approve, reject, or revoke

- **Each authoriser decides only for themself.** Approving or rejecting doesn't record a decision
  for anyone else named on the same consent — each person's own choice is tracked separately.
- **Once you've decided, your buttons disappear** — even if the consent is still `PENDING` because
  someone else hasn't decided yet. A decision can't be reopened by acting again.
- **Revoking is different from rejecting.** Any authoriser can revoke a consent once it's active —
  not just the one who approved it — mirroring the real-world rule that either parent can withdraw
  a consent they were both asked about.
- **Administrators can revoke, but never approve or reject** on someone else's behalf. This is a
  safety valve for support and compliance cases (for example, an authoriser who can no longer be
  reached), available whenever a consent is `PENDING` or `ACTIVE`.

| Situation | Approve / Reject | Revoke |
|---|---|---|
| Subject of a **Direct Consent** | *Nothing to approve — it's already active* | ✅ Yes, once active |
| Subject of a **Delegated Consent** (someone else decides) | ❌ No — notified, not asked | ❌ No |
| Named as an **authoriser** (Delegated or Co-Authorized, including when you're also the subject) | ✅ Yes, until you've decided | ✅ Yes, once active |
| **Administrator** | ❌ No | ✅ Yes, while pending or active |

## 4. What you'll see instead of a button

When there's nothing for you to do, the consent detail page explains why rather than just hiding
the button:

| You'll see this | When |
|---|---|
| "Waiting for authoriser approval." | You're the subject of a Delegated Consent, still `PENDING`. |
| "This consent has been approved." / "...rejected." | You're the subject of a Delegated Consent, and a decision has been made. |
| "You've made your decision. Waiting for the rest to decide." | You're a named authoriser who has already approved, and the consent is still `PENDING` on someone else. |
| "You've rejected this consent." | You're the authoriser whose rejection ended it. |
| "This consent has been revoked." / "...expired." | The consent has reached a final state — no further action is possible for anyone. |

## 5. Troubleshoot common problems

| Symptom | Explanation |
|---|---|
| No Approve/Reject button, and I expected one | Check whether you're named in the consent's authorisers. The subject of a Delegated Consent is not automatically one. |
| I approved, but the consent is still Pending | Another named authoriser hasn't decided yet. It becomes Active only once everyone has approved. |
| I can't find a Reject button, only Revoke | The consent is already Active — rejection only applies while it's Pending. Use Revoke to withdraw it instead. |
| I can't revoke a consent I'm the subject of | You're the subject of a Delegated Consent and were never named as an authoriser — only the named authorisers (or an administrator) can revoke it. |
