# MTgram

Форк Telegram для Android. Патчи против DPI-блокировок и несколько UI-фиксов.

[![Channel](https://img.shields.io/badge/Канал-Telegram-blue.svg)](https://t.me/MTgramApp)
[![Chat](https://img.shields.io/badge/Чат-Telegram-blue.svg)](https://t.me/MTgramChat)
[![Download](https://img.shields.io/badge/Скачать-Releases-green.svg)](https://github.com/tgmtproxy/MTgram/releases)

Код открытый, можно проверить самому. Никаких посторонних серверов, никакого сбора данных — всё соединение идёт напрямую через серверы Telegram, как в оригинале.

---

## Зачем

В ряде регионов Telegram резается на уровне DPI (провайдер определяет MTProxy и рвёт соединение). Здесь это исправлено. Заодно убрали несколько раздражающих вещей из интерфейса.

## Что изменено

**Сеть**
- Изменён TLS fingerprint MTProxy-соединений — провайдер не распознаёт прокси, блокировка не срабатывает
- Работает там, где официальный клиент блокируется

**Интерфейс**
- Боковое меню вместо нижних вкладок
- Material You (тема подстраивается под систему)
- Настройка скорости анимаций
- Секунды в метках времени

**Приватность**
- Режим скрытия отдельных чатов (из поиска, уведомлений и списка)
- PIN на аккаунт
- Очистка ссылок от трекеров (utm_, fbclid и т.п.)
- Убраны Google Play Integrity и reCAPTCHA

---

<sub>inspired by inugram</sub>
