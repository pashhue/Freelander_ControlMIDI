# Freelander Control — исходный прототип

Проект превращает Freelander PD500C (Android 4.2.2, API 17) в проводной сенсорный MIDI-контроллер для FL Studio 26.1.4.

## Что входит

- `AndroidApp/` — исходный код простого Android-приложения (альбомная ориентация, пять вкладок, TCP-сервер на `127.0.0.1:8765`).
- `WindowsBridge/bridge.py` — небольшой Windows-мост. Он принимает сообщения через ADB port-forward и отправляет MIDI в виртуальный MIDI-порт Windows через WinMM.
- `FLStudio/Hardware/FreelanderControl/device_FreelanderControl.py` — пользовательский MIDI Script для FL Studio: Mixer, Playlist и транспорт.

Это **исходный прототип, не готовый установочный APK**. APK можно собрать без Android Studio через GitHub Actions: в репозитории уже лежит workflow `.github/workflows/build-apk.yml`. Он устанавливает JDK 17, Android SDK Platform 34 и Gradle 8.2.1, затем собирает APK и сохраняет его как артефакт сборки. Совместимость на реальном PD500C ещё не проверялась.

## Схема

`PD500C app → USB cable / ADB forward → Windows Bridge → loopMIDI → FL Studio`

Wi-Fi не используется. USB tethering/RNDIS не требуется. ADB должен быть включён на планшете.

## Сборка APK без Android Studio через GitHub Desktop

В проект добавлен GitHub Actions workflow. GitHub Desktop нужен для создания и публикации репозитория, а сам APK собирается на сервере GitHub.

1. Распаковать архив. В корне проекта должны лежать папки `.github`, `AndroidApp`, `WindowsBridge` и `FLStudio`.
2. В GitHub Desktop выбрать `File → New repository...` и создать репозиторий `FreelanderControl`.
3. Открыть созданную локальную папку репозитория через `Repository → Show in Explorer`.
4. Скопировать в неё содержимое распакованной папки `FreelanderControl` (не саму внешнюю папку целиком), согласившись объединить папки и заменить README, если Windows спросит.
5. Вернуться в GitHub Desktop. В списке Changes должны появиться файлы проекта. Ввести summary `Add Freelander Control prototype`, нажать `Commit to main`.
6. Нажать `Publish repository`. Для личного проекта рекомендуется оставить `Keep this code private` включённым.
7. После публикации открыть репозиторий на GitHub → вкладка `Actions` → workflow `Build Freelander Control APK`. Он запускается автоматически после отправки файлов. При необходимости можно нажать `Run workflow`.
8. Дождаться зелёного статуса. Открыть успешный запуск workflow и скачать артефакт `FreelanderControl-debug-apk` внизу страницы.
9. Внутри архива артефакта будет `app-debug.apk`.

Если workflow не запускается автоматически, открой `Actions`, выбери `Build Freelander Control APK` и нажми `Run workflow`. Для публикации и сборки GitHub Actions нужен доступ к GitHub-аккаунту. На GitHub Free для приватных репозиториев действует месячная квота Actions; публичные репозитории имеют бесплатное использование стандартных hosted runners. Не делай репозиторий публичным только ради сборки, если не хочешь публиковать исходники.

## Установка APK на PD500C

После скачивания `app-debug.apk` подключи планшет по USB, включи USB debugging и выполни в PowerShell из папки с ADB:

```powershell
.\adb.exe install -r "C:\путь\к\app-debug.apk"
```

Затем запусти Freelander Control на планшете и оставь приложение на переднем плане.

## Альтернативная сборка локально

При желании APK можно собрать из папки `AndroidApp` с JDK 17, Android SDK Platform 34 и Gradle 8.2.1 командой:

```powershell
gradle --no-daemon assembleDebug
```

## Подготовка Windows

1. Установить loopMIDI с официальной страницы: https://www.tobias-erichsen.de/software/loopmidi.html
2. В loopMIDI создать один порт с названием `Freelander Control` и не закрывать loopMIDI.
3. Установить Python 3 для Windows (скрипт использует только стандартную библиотеку).
4. Открыть PowerShell в папке `WindowsBridge` и выполнить:

   ```powershell
   adb forward tcp:8765 tcp:8765
   py .\bridge.py --port-name "Freelander Control"
   ```

5. Подключить планшет USB-кабелем, включить USB debugging, запустить приложение.

## Подключение к FL Studio 26.1.4

1. Скопировать папку `FLStudio/Hardware/FreelanderControl` в пользовательскую папку FL Studio:

   `%USERPROFILE%\Documents\Image-Line\FL Studio\Settings\Hardware\`

   Итоговый путь должен содержать:

   `...\Hardware\FreelanderControl\device_FreelanderControl.py`

2. Открыть FL Studio → Options → MIDI Settings (F10).
3. Выбрать вход `Freelander Control` (это виртуальный порт loopMIDI) и включить его.
4. В `Controller type` выбрать `Freelander Control (user)`.
5. Для клавиатуры и пэдов выбери инструмент/канал в Channel Rack. Пэды передают MIDI channel 10, ноты 36–51; соответствие звукам зависит от настроек FPC.

## Раскладка MIDI

| Назначение | MIDI |
|---|---|
| Пиано | Note On/Off, канал 1, ноты 48–71 |
| Драм-пэды | Note On/Off, канал 10, ноты 36–51 |
| Mixer volume | CC 20–27 |
| Mixer pan | CC 28–35 |
| Mixer mute | CC 40–47 |
| Mixer solo | CC 48–55 |
| Playlist mute | CC 60–67 |
| Playlist solo | CC 68–75 |
| Playlist select | CC 76–83 |
| EQ controls | CC 100–106 (оставлены для MIDI Learn / Link to controller) |
| Play / Stop / Record | CC 90 / 91 / 92 |

Нумерация Mixer-каналов в FL Studio: 1–8, Master не затрагивается. Playlist-индексы в скрипте начинаются с 0.

## Важные ограничения прототипа

- Для EQ-регуляторов в FL Studio нужно один раз связать семь CC (100–106) с нужными контролами Parametric EQ 2 через `Link to controller`. Разные плагины имеют разные наборы параметров.
- Параметры Mixer и Playlist управляются MIDI Script напрямую.
- При первом запуске проверяй всё на пустом проекте или его копии: Mute/Solo и фейдеры меняют состояние проекта.
- USB latency и надёжность нужно проверить на конкретном кабеле, PD500C и ПК.
- Если ADB-порт занят, поменяй порт в `MainActivity.java`, `bridge.py` и команде `adb forward` согласованно.

## Протокол между Android и Windows

Каждое событие — строка ASCII с тремя десятичными числами:

`status,data1,data2\n`

Это обычное короткое MIDI-сообщение. Например, `144,60,100` — Note On, MIDI note 60, velocity 100.
