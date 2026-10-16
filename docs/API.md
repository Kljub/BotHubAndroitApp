# BotHub mobile API

The app talks to the dashboard address of the QR code; the dashboard forwards
`/api/v1/*` to the BotHub gateway (`dashboard/cmd/mockapi/mobile.go`,
`guard.go` in BotHub).

## Pairing

`POST /api/v1/mobile/pair` (no auth; the one-time code is the proof)

```json
{ "code": "<48 hex>", "name": "Pixel 9", "platform": "android", "appVersion": "1.0.0", "publicKey": "<SPKI P-256, base64url>" }
```

→ `201 { "token": "bhm_…", "guardSecret": "<base32>", "device": {…}, "user": { "id", "username" } }`

The code comes from the QR code `bothub://pair?server=<dashboard>&code=<code>`,
valid 5 minutes and once. Token and guard secret are given only here.

## Signed requests

Every other call:

```
Authorization: Bearer bhm_…
X-BotHub-Time: <unix seconds>
X-BotHub-Signature: base64url(raw r||s of ECDSA-SHA256 over "bothub-mobile:<time>:<METHOD>:<path?query>")
```

The time may differ by at most 5 minutes; a signature works once. Errors:
`401 error.mobile.unpaired | error.mobile.clock | error.mobile.signature`,
`403 error.mobile.not_allowed`.

## What a phone may call

| Call | |
|---|---|
| `GET /api/v1/auth/me`, `GET /api/v1/mobile/me` | user, device (`guard`, `notify`) |
| `GET /api/v1/bots`, `GET /api/v1/bots/{id}` | bots of the user |
| `POST /api/v1/bots/{id}/start|stop|restart` | |
| `GET /api/v1/bots/{id}/logs?limit=&level=` | entries with text key + params |
| `GET /api/v1/bots/{id}/modules`, `PUT …/modules/{key}` `{ "enabled": bool }` | |
| `GET /api/v1/bots/{id}/plugins`, `PATCH …/plugins/{id}` `{ "enabled": bool }` | |
| `GET /api/v1/stats/overview` | |
| `PUT /api/v1/mobile/push` `{ "token", "platform": "fcm", "notify": [] }` | push token and events |
| `DELETE /api/v1/mobile/device` | unpair this phone |
| `GET /api/v1/mobile/approvals`, `POST /api/v1/mobile/approvals/{id}` `{ "approve": bool }` | BotHub Guard |

Everything else (admin, password, 2FA, secrets, other users) is refused.

## Texts

`GET /mobile/texts/{en|de}` (public): the dashboard's texts of log lines,
errors, bot and task states and module names, `{key: text}` with `{param}`
placeholders.

## BotHub Guard

With Guard on for a phone, the browser sign-in answers
`401 error.auth.totp_required` with `{ ticket, guard: true, approval }` and
polls `POST /api/v1/auth/login/guard {ticket}` (202 pending, 200 + session,
401 denied/expired). The app lists the pending sign-in and approves or
denies it. The app's code is `TOTP(guardSecret, 30 s, 6 digits, SHA-1)` and is
accepted at `POST /api/v1/auth/login/totp` like an authenticator code.
