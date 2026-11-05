# Calendar Backend

## Notification preferences

Notification preferences belong to the authenticated user.

`GET /configuration` includes:

| Field | Default |
|---|---|
| `inAppNotificationsEnabled` | `true` |
| `emailNotificationsEnabled` | `false` |
| `browserPushNotificationsEnabled` | `false` |

Preferences can be updated independently through `PATCH /configuration`:

```json
{
  "emailNotificationsEnabled": true
}
```

Omitted or explicitly null preference fields preserve their current values.
Unrelated configuration settings remain unchanged.

Browser push preferences express user intent only. Browser permissions and
push subscriptions are handled separately.

## Task reminder offsets

`reminderMinutesBefore` is an optional integer representing the number of
minutes before a task occurrence starts.

- `null`: reminders disabled.
- `0`: reminder at the start time.
- Valid range: 0 to 525600 minutes, inclusive (365 days).
- Values outside this range are rejected with HTTP 400.
- The database also enforces this range.

Example task creation using `POST /tasks`:

```json
{
  "title": "Project meeting",
  "startsAt": "2026-11-10T18:00:00Z",
  "reminderMinutesBefore": 45
}
```

### Update semantics

| Operation | Field omitted | Explicit null | Valid integer |
|---|---|---|---|
| POST `/tasks` | Disabled | Disabled | Sets offset |
| PATCH `/tasks/{id}` | Preserves current offset | Disables reminder | Updates offset |
| PUT `/tasks/{id}` | Disables reminder | Disables reminder | Sets offset |

The offset is included in standard task responses and month-view occurrence
responses.

### Database migrations

- V14 adds notification preferences with defaults for existing configurations.
- V15 adds a nullable reminder offset. Existing tasks remain without reminders.

### Scope

These fields store preferences and reminder offsets only. Scheduling,
delivery, retries, and browser push subscriptions are implemented separately.