# shizogram

> ещё один форк телеграма. но этот — наш.

Клиент Telegram для Android с фокусом на **приватность**, **чистый интерфейс** и **обход блокировок** (DPI bypass).

Основан на [inugram](https://github.com/teidesu/inugram) — минималистичном patchset-форке без лишнего шума.

---

## Зачем?

Большинство форков Telegram — либо перегружены ненужным функционалом, либо не решают реальных проблем с блокировками.

Shizogram делает три вещи хорошо:

- **Приватность** — убрано всё лишнее (Google Play Integrity, reCAPTCHA, трекинг-параметры в ссылках)
- **Чистый UX** — только нужные твики, никакого хлама
- **DPI bypass** — улучшенный TLS fingerprint в MTProxy-соединениях, чтобы работало там, где обычный Telegram блокируется

---

## Возможности

Полный список — в [FEATURES.md](FEATURES.md). Кратко:

### Приватность
- **Paranoia Mode** — скрывает выбранные диалоги отовсюду: уведомления, поиск, список чатов
- Per-account PIN + паник-код для сброса к стоковому виду
- Удалены Google Play Integrity и reCAPTCHA
- Фильтрация tracking-параметров в ссылках (utm_*, fbclid, si и др.)

### Сеть
- Улучшенный TLS ClientHello для MTProxy — обходит DPI на уровне отпечатка
- Рандомизация расширений TLS (GREASE, порядок cipher suites)

### UI/UX
- Кастомизация двойного нажатия
- Скрытие счётчиков онлайн/просмотров
- Твики интерфейса без потери стокового ощущения

---

## Архитектура

Shizogram — это **набор патчей** поверх официального Telegram Android, а не классический форк.

```
patches/         — патчи по категориям (bugfix, feature, debloat, hooks, misc)
src/kotlin/      — наш кастомный Kotlin-код
src/res/         — ресурсы
series           — порядок применения патчей
upstream-commit  — закреплённый коммит Telegram
worktree/        — локальный checkout Telegram (gitignored)
```

Преимущества подхода:
- Легко ребейзить на новые версии Telegram
- Прозрачный аудит изменений — всё в патчах
- Каждый патч можно убрать и апп соберётся

---

## Сборка

Требования: Node.js 20+, `git`, `stg`

```sh
pnpm install
pnpm run setup
```

Это клонирует upstream в `worktree/` и применит все патчи через stgit.
Открывай `worktree/` в Android Studio — не импортируй, а именно открывай.

Или через Gradle:
```sh
./gradlew assembleRelease
```

Для сборки нужен файл `API_KEYS` в корне проекта:
```
APP_ID = 123456
APP_HASH = abcdef0123456789
```

Свой API_ID: https://core.telegram.org/api/obtaining_api_id

---

## Вклад в проект

PR приветствуются. Перед большими изменениями — создай issue, обсудим.

Ассистированный ИИ-код принимается, если ты его проверил сам.

---

## Благодарности

- [inugram](https://github.com/teidesu/inugram) — база и архитектура patchset
- [Telegram Android](https://github.com/DrKLO/Telegram) — оригинальный исходник
- [exteraGram](https://github.com/exteraSquad/exteraGram) — вдохновение для ряда фич
- [Nekogram](https://github.com/Nekogram/Nekogram), [NagramX](https://github.com/risin42/NagramX), [materialgram](https://github.com/kukuruzka165/materialgram) — отдельные фичи

---

## Лицензия

MIT — делай что хочешь, оставь копирайт.

---

<sub>inspired by inugram</sub>
