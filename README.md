# MTgram

Форк Telegram для Android. Патчи против DPI-блокировок и несколько UI-фиксов.

<p align="center">
  <a href="https://t.me/MTgramApp"><img src="https://img.shields.io/badge/Канал-MTgramApp-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" /></a>
  <a href="https://t.me/MTgramChat"><img src="https://img.shields.io/badge/Чат-MTgramChat-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" /></a>
  <a href="https://github.com/tgmtproxy/MTgram/releases"><img src="https://img.shields.io/github/v/release/tgmtproxy/MTgram?style=for-the-badge&label=Скачать&color=3aa040&logo=android&logoColor=white" /></a>
</p>

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
