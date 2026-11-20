# AGENTS.md — Правила и архитектура проекта Spectr

Этот файл автоматически загружается в каждый новый чат Antigravity в репозитории **Spectr**.
Всегда сверяйтесь с `docs/SPEC.md`, `docs/ARCHITECTURE.md` и `docs/ROADMAP.md` перед началом работы над очередным этапом.

---

## 1. Суть проекта и продуктовые границы

- **Spectr** — нативное open-source Android-приложение (Kotlin + Jetpack Compose + Material 3) для ежедневного трекинга настроения, сна, энергии, тревоги, раздражительности и симптомов с авторасчётом клинических шкал (**PHQ-9**, **GAD-7**, **ASRM**), экспортом PDF для врача и опциональной облачной синхронизацией (**Supabase + E2EE**).
- **Фокус:** лёгкий универсальный трекер настроения. В приложение **НЕ** добавляются тяжёлые медицинские таблицы анамнеза, анализов крови или списка вопросов к врачу.
- **Клиническая нейтральность:** в UI, подсказках и PDF-отчёте запрещены самодиагнозы (не утверждать наличие БАР / циклотимии / дистимии у пользователя; справочные ориентиры шкалы `-5..+5` допустимы только в «Справочнике» с пометкой «справочно»), названия конкретных клиник и любые чувствительные личные маркеры.

---

## 2. Безопасность и приватность (КРИТИЧНО)

1. **Никаких личных данных в Git:** никогда не коммитьте реальные записи дневника, персональные JSON-бэкапы, файлы ключей (`.jks`, `.keystore`), `local.properties` или секреты Supabase.
2. **Offline-first и режим без аккаунта:** приложение обязано полноценно работать локально без входа в аккаунт.
3. **Zero-knowledge E2EE для облака:**
   - Никакого «второго мастер-пароля»: единый email + пароль на входе.
   - `masterKey = PBKDF2-HMAC-SHA256(password, SHA256("spectr-salt-v1:" + lower(email)), 600_000, 32B)`.
   - `authSecret = HKDF-SHA256(masterKey, "spectr-auth-v1")` (только это уходит в Supabase Auth — сервер не видит исходный пароль).
   - `KEK = HKDF-SHA256(masterKey, "spectr-kek-v1")` оборачивает случайный 256-bit `DEK`.
   - Все записи шифруются `AES-256-GCM` с уникальным 12-байтным `IV` и `AAD = "$userId:$entityType:$recordId"`.
   - На сервере в открытом виде допустимы **только** метаданные синхронизации (`id`, `user_id`, `entity_type`, `updated_at`, `deleted_at`, `key_version`, `iv`, `ciphertext`).
   - Все примитивы должны оставаться 100% совместимыми с браузерным `Web Crypto API` (для будущей веб-версии на CachyOS/любой ОС).
4. **Защита на устройстве:** стандартный `AndroidKeyStore` (совместимый с любым Android 8.0+ смартфоном, не только Pixel), опциональная биометрия/PIN (`BiometricPrompt`) и опциональный `FLAG_SECURE`.
5. **Уведомления:** на экране блокировки — только нейтральные формулировки без медицинских терминов.

---

## 3. Архитектура и стандарты кода

- **Стек:** Kotlin 2.x, Gradle Kotlin DSL, Version Catalog (`gradle/libs.versions.toml`), KSP (без KAPT), Jetpack Compose + Material 3, Room SQLite, Hilt DI, Coroutines/Flow, Jetpack Glance (виджет), нативный `PdfDocument`, `supabase-kt 3.x`.
- **Границы слоёв:**
  - `domain/` — **чистый Kotlin** без зависимостей от `android.*` и Room. Вся клиническая математика (PHQ-9, GAD-7, ASRM, смешанные дни, расчёт отрезков сна, KPI) живёт здесь и покрывается быстрыми JVM unit-тестами.
  - `data/` — Room entities/DAOs, DataStore, репозитории (offline-first), Supabase + WorkManager delta-sync.
  - `security/` — `CryptoEngine`, `KeystoreManager`, `RecoveryKeyCodec`, `BiometricLockManager`.
  - `platform/` — `AlarmManager` уведомления, `PdfDocument` генератор, Glance-виджет.
  - `ui/` — Compose-экраны, ViewModels (однонаправленный поток данных `StateFlow<UiState>`), гибкая система тем (Material You Dynamic Color + пресеты + AMOLED).
- **Схема БД:** все синхронизируемые сущности обязаны иметь `id: String` (UUID v4), `updatedAt: Long` (UTC epoch ms), `deletedAt: Long?` (soft-delete) и `syncState`.
- **Локализация и тексты:** основной язык UI — русский, все строки хранить в `res/values/strings.xml` (готово к будущему добавлению английского `values-en`).

---

## 4. Процесс работы и обязательные проверки

- Работаем по этапам из `docs/ROADMAP.md` (1 этап = 1 чистый чат + осмысленный Git-коммит).
- **JDK:** для всех команд `./gradlew` использовать **JDK 21** (`/home/admin/.local/share/jdks/jdk-21`, либо системный JDK 21 при наличии), а не системный JDK 27.
- Перед завершением любого этапа и перед коммитом **обязательно** выполнить и получить зелёный результат:
  ```bash
  ./gradlew ktlintCheck detekt testDebugUnitTest lintDebug assembleDebug
  ```
- По завершении этапа обязательно обновить чеклист в `docs/ROADMAP.md` (и при изменении архитектуры — `docs/ARCHITECTURE.md`).
