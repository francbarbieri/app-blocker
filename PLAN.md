# Room Database Schema — Implementation Plan

## Changes from Original Plan
1. **Removed** `daily_usage_summaries` table — derive aggregates from `usage_sessions` at query time
2. **Added** `unblock_events` table — replaces the counter with full event tracking
3. **Added** `schedule_days` junction table — replaces comma-separated `days_of_week` string
4. **Removed** `days_of_week` column from `schedules`

---

## Final Schema (5 tables)

### 1. `blocked_apps`
| Column | Type | Notes |
|---|---|---|
| `package_name` | TEXT | PK |
| `app_name` | TEXT | NOT NULL |
| `is_blocking_enabled` | INTEGER (bool) | NOT NULL, DEFAULT 1 |
| `created_at` | INTEGER (epoch ms) | NOT NULL |

### 2. `schedules`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT |
| `app_package_name` | TEXT | FK → blocked_apps, CASCADE |
| `schedule_type` | TEXT | "TIME_WINDOW" or "DAILY_LIMIT" |
| `start_time` | TEXT | Nullable. "09:00" — TIME_WINDOW only |
| `end_time` | TEXT | Nullable. "17:00" — TIME_WINDOW only |
| `daily_limit_minutes` | INTEGER | Nullable. DAILY_LIMIT only |
| `is_active` | INTEGER (bool) | NOT NULL, DEFAULT 1 |

### 3. `schedule_days` (junction table)
| Column | Type | Notes |
|---|---|---|
| `schedule_id` | INTEGER | FK → schedules, CASCADE |
| `day_of_week` | INTEGER | 1=Mon, 7=Sun |

Composite PK: `(schedule_id, day_of_week)`. No rows = every day.

### 4. `usage_sessions`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT |
| `app_package_name` | TEXT | FK → blocked_apps, CASCADE |
| `start_time` | INTEGER (epoch ms) | NOT NULL |
| `end_time` | INTEGER (epoch ms) | Nullable (null while in progress) |
| `duration_ms` | INTEGER | Nullable (computed at session end) |

Index: `(app_package_name, start_time)`

### 5. `unblock_events`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT |
| `app_package_name` | TEXT | FK → blocked_apps, CASCADE |
| `timestamp` | INTEGER (epoch ms) | NOT NULL |
| `user_proceeded` | INTEGER (bool) | NOT NULL. true = unblocked, false = backed off |

### 6. `motivational_messages`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PK AUTOINCREMENT |
| `app_package_name` | TEXT | Nullable FK → blocked_apps, CASCADE |
| `message` | TEXT | NOT NULL |
| `created_at` | INTEGER (epoch ms) | NOT NULL |

---

## File Structure

```
data/
├── local/
│   ├── AppDatabase.kt
│   ├── dao/
│   │   ├── BlockedAppDao.kt
│   │   ├── ScheduleDao.kt
│   │   ├── UsageSessionDao.kt
│   │   ├── UnblockEventDao.kt
│   │   └── MotivationalMessageDao.kt
│   └── entity/
│       ├── BlockedAppEntity.kt
│       ├── ScheduleEntity.kt
│       ├── ScheduleDayEntity.kt
│       ├── UsageSessionEntity.kt
│       ├── UnblockEventEntity.kt
│       └── MotivationalMessageEntity.kt
domain/
├── model/
│   ├── BlockedApp.kt
│   ├── Schedule.kt
│   ├── ScheduleType.kt          (enum)
│   ├── ScheduleDay.kt
│   ├── UsageSession.kt
│   ├── UnblockEvent.kt
│   └── MotivationalMessage.kt
└── repository/
    ├── BlockedAppRepository.kt   (interface)
    ├── UsageRepository.kt        (interface)
    └── MotivationalMessageRepository.kt (interface)
```

---

## Implementation Order
- [x] 1. Domain models + ScheduleType enum
- [x] 2. Room entities
- [x] 3. DAOs
- [x] 4. AppDatabase
- [x] 5. Repository interfaces
- [x] 6. Repository implementations
- [x] 7. `./gradlew assembleDebug`
