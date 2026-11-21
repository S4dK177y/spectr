# Spectr — Техническая архитектура

Документ фиксирует архитектуру слоёв, схему локальной и облачной БД, точные клинические формулы и криптографический протокол E2EE.

---

## 1. Слои и пакеты (`app.spectr.mood`)

- `domain/` — чистый Kotlin (без `android.*`, Room и Supabase). Модели (`DailyEntry`, `MomentCheckIn`, `ScaleAssessment`, `ReminderConfig`, `SecurityConfig`), калькуляторы (`ClinicalScaleCalculator`, `SleepDurationCalculator`, `AnalyticsCalculator`), геометрия графиков и календарная сетка (`ChartGeometry`, `CalendarMonthBuilder`), сборка и санитизация врачебного отчёта (`DoctorReportBuilder`, `DoctorReportSnapshot`), планирование напоминаний и снапшот виджета (`ReminderSchedulerCalculator`, `MoodWidgetSnapshotBuilder`, `LocalDataChangeHook`), интерфейсы репозиториев, формат JSON-бэкапа.
- `data/local/` — Room SQLite (offline-first источник правды) + DataStore (настройки тем, напоминаний, локальной защиты `SecurityPreferencesRepository`, sync-курсора).
- `data/remote/` — `supabase-kt` 3.x (`auth-kt`, `postgrest-kt`) + WorkManager `SyncWorker` (delta-sync по `updated_at`, разрешение конфликтов Last-Write-Wins по UTC timestamp).
- `security/` — `PinHashManager` (PBKDF2-HMAC-SHA256 для локального PIN), `BiometricLockManager` (платформенный `BiometricPrompt`), а также `CryptoEngine`, `KeystoreManager`, `RecoveryKeyCodec` (Этап 6).
- `platform/` — `AlarmManager` exact-alarm напоминания с восстановлением после перезагрузки и умной отменой (`ReminderCoordinator`, `SpectrAlarmScheduler`, `SpectrNotificationPublisher`, `ReminderBootReceiver`), нативный векторный `PdfDocument` генератор (`DoctorPdfReportGenerator`, `PdfExportStorage` + `FileProvider`), виджет `Jetpack Glance 1.1.1` (`MoodGlanceWidget`, `MoodWidgetUpdater`), deep-link контракт `SpectrLaunchContract`.
- `ui/` — Jetpack Compose + Material 3, однонаправленный поток состояния (`StateFlow<UiState>`), собственные векторные Canvas-графики, read-only «Режим показа врачу» (`ui/doctor/`), встроенный «Справочник» (`ui/guide/`), экспорт PDF (`ui/pdf/`) и экран блокировки (`ui/security/`).

---

## 2. Локальная схема Room SQLite

Все синхронизируемые сущности содержат:
- `id: String` (UUID v4)
- `updatedAt: Long` (UTC epoch millis)
- `deletedAt: Long?` (soft-delete tombstone для синхронизации удалений)
- `syncState: String` (`PENDING | SYNCED`)

Таблицы:
1. `daily_entries` — 1 активная запись на дату `date` (`YYYY-MM-DD`): `mood (-5..+5)`, `dailyDynamics`, `energy (0..10)`, `sleepMinutesOverride`, `sleepNote`, `anxiety (0..10)`, `irritability (0..10)`, 7 симптомов (`apathy`, `selfCriticism`, `brainFog`, `bingeEating`, `deathThoughts`, `hyperfocus`, `racingThoughts`), `alcoholStatus`, `medicationStatus`, `note`, `stepsCount` (задел под Health Connect).
2. `sleep_intervals` — `entryId`, `startMinuteOfDay`, `endMinuteOfDay`, `awakeDeductionMin`, `source (MANUAL | HEALTH_CONNECT)`. Поддерживает многофазный/разбитый сон и переход через полночь.
3. `moment_check_ins` — `entryDate`, `timestampEpochMs`, `mood`, `energy?`, `anxiety?`, `irritability?`, `note`.
4. `scale_assessments` — универсальная таблица (`scaleType`, `date`, `answersJson`, `totalScore`, `isConfirmed`), поддерживающая PHQ-9, GAD-7, ASRM и будущие опросники без миграций БД.
5. `medication_records` — зарезервирована под будущий расширенный трекер препаратов.

---

## 3. Точные клинические формулы

### Смешанный день
```text
isMixedDay = (mood <= -1) && (energy >= 7 || irritability >= 7 || racingThoughts)
```

### Авто-шкалы из дневника
- **Якорная дата (`anchorDate`):** дата последней заполненной записи дневника (если записей нет — авто-шкалы пустые).
- **Окна:** 14 календарных дней `[anchorDate - 13 .. anchorDate]` для PHQ-9 и GAD-7; 7 календарных дней `[anchorDate - 6 .. anchorDate]` для ASRM.
- **Перевод числа дней в балл:**
  - PHQ-9 / GAD-7 (0–3): `0 → 0`, `1..6 → 1`, `7..11 → 2`, `12..14 → 3`.
  - ASRM (0–4): `0 → 0`, `1..2 → 1`, `3..4 → 2`, `5..6 → 3`, `7 → 4`.

#### PHQ-9 (9 пунктов, 0–27)
1. `apathy`
2. `mood <= -2`
3. `(sleepHours > 0.0 && sleepHours < 6.0) || sleepHours > 10.0`
4. `energy in 0..3`
5. `bingeEating`
6. `selfCriticism`
7. `brainFog`
8. `racingThoughts || energy in 0..1`
9. `deathThoughts`

Интерпретация: `0..4` Минимальный · `5..9` Лёгкий · `10..14` Умеренный · `15..19` Умеренно-тяжёлый · `20..27` Выраженный.

#### GAD-7 (7 пунктов, 0–21)
1. `anxiety >= 5`
2. `anxiety >= 6`
3. `anxiety >= 5`
4. `anxiety >= 6`
5. `racingThoughts`
6. `irritability >= 5`
7. `anxiety >= 7`

Интерпретация: `0..4` Минимальная · `5..9` Лёгкая · `10..14` Умеренная · `15..21` Выраженная.

#### ASRM (5 пунктов, 0–20)
1. `mood >= 2`
2. `mood >= 3`
3. `sleepHours > 0.0 && sleepHours < 6.0 && energy >= 7`
4. `racingThoughts && mood >= 1`
5. `hyperfocus`

Интерпретация: `< 6` Обычный фон · `>= 6` Повышенная активация.

---

## 4. Криптографический протокол E2EE (v1)

Спроектирован для **100% совместимости** между Android (JCA `javax.crypto`) и будущей веб-версией (`Web Crypto API`):

1. **Вывод ключей из единого пароля:**
   - `emailNorm = email.trim().lowercase(Locale.ROOT)`
   - `salt = SHA-256(UTF8("spectr-salt-v1:" + emailNorm))`
   - `masterKey = PBKDF2-HMAC-SHA256(password = UTF8(password), salt = salt, iterations = 600_000, keyLen = 32 bytes)`
   - `authSecret = Base64Url(HKDF-SHA256(ikm = masterKey, salt = empty, info = UTF8("spectr-auth-v1"), len = 32))` → отправляется в Supabase Auth вместо сырого пароля.
   - `kek = HKDF-SHA256(ikm = masterKey, salt = empty, info = UTF8("spectr-kek-v1"), len = 32)` → остаётся строго на устройстве.
2. **Оборачивание DEK:**
   - При регистрации генерируется случайный 256-bit `DEK` (`SecureRandom`).
   - `wrapped_dek_password = Base64(iv_12B || AES-256-GCM(kek, iv, DEK, aad = UTF8("spectr-dek-pw:" + userId)))`.
   - Опциональный 24-символьный `Recovery Key` (120+ бит энтропии) оборачивает ту же копию `DEK` (`wrapped_dek_recovery`) для восстановления истории при сбросе пароля.
3. **Кэширование на устройстве:**
   - Развёрнутый `DEK` шифруется ключом в стандартном `AndroidKeyStore` (`AES/GCM/NoPadding`, аппаратный TEE / StrongBox при наличии) и хранится в DataStore.
4. **Шифрование записей:**
   - Полезная нагрузка записи сериализуется в JSON (`schemaVersion = 1`).
   - Для каждой записи и каждого обновления генерируется новый случайный `iv` (12 байт).
   - `ciphertext = AES-256-GCM(DEK, iv, plaintextJson, aad = UTF8("$userId:$entityType:$recordId"))`.
   - На сервер в таблицу `public.encrypted_records` уходят только `id`, `user_id`, `entity_type`, `key_version`, `iv`, `ciphertext`, `updated_at`, `deleted_at`.
5. **Supabase RLS:** строгие политики `TO authenticated` с `(select auth.uid()) = user_id` в `USING` и `WITH CHECK`, индекс `(user_id, updated_at desc)`.
