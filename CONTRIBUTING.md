# Правила работы с ветками

`master` — это прод: каждый push в него запускает выкат (`deploy.yml`), а тег версии
ставит CI. Поэтому правила ниже строже, чем в обычном репозитории. Часть из них закрыта
настройками GitHub (раздел «Настройки репозитория»), остальное — договорённости.

## Модель

Две постоянные ветки, остальные живут до мержа.

| Ветка | Что это | Как попадают изменения |
|---|---|---|
| `master` | прод: каждый push = выкат, тег ставит CI | только PR: релизный `develop → master` или ветка от `master` (хотфикс, контент) |
| `develop` | интеграция, следующий релиз | только PR из веток от `develop`; плюс пуши CI (бамп версии, обратное слияние) |
| рабочая ветка | одна задача = одна ветка = один PR | создаётся от `origin/<целевая>`, удаляется после мержа |

Постоянных рабочих веток нет: ветка под задачу удаляется после мержа, имя не
переиспользуется. Имена короткие, без префиксов вида `feature/`.

## Правила

1. **Ветка отводится от той ветки, куда пойдёт PR.** PR в `master` — от `origin/master`,
   PR в `develop` — от `origin/develop`. В ветку, нацеленную на `master`, `develop` не
   вливается никогда. *Иначе:* PR тащит невыпущенный бэкенд, `version.sh check` его
   пропускает (версия `develop` выше тега), и CI выпускает незапланированный релиз — так
   вышел v1.4.3 из PR #62.
   Чеклист перед мержем PR в `master` не из `develop`: вкладка Commits — только свои
   коммиты, Files changed — только ожидаемые пути. Коммит `chore: start …-SNAPSHOT` из
   `develop` или правки `src/` в контентном PR значат, что ветка не от `master`: закрыть,
   перенести коммиты на новую ветку от `origin/master` (`git cherry-pick`), открыть заново.

2. **В `master` и `develop` не коммитят и не пушат напрямую.** Локальные `master` и
   `develop` — зеркала origin: на них не работают, новую ветку создают
   `git fetch && git switch -c <имя> --no-track origin/<база>`, первый push —
   `git push -u origin HEAD`. Без `--no-track` ветка следит за `origin/<база>`, обычный
   `git push` падает, и git сам подсказывает `git push origin HEAD:develop` — прямой push,
   который правила `develop` пропустят. Force-push в обе ветки закрыт ruleset'ом.
   Единственное исключение — обратное слияние `master → develop`, пока его не делает CI:
   локальный `git merge origin/master` в `develop` и push либо кнопка Update branch на
   релизном PR (это тот же merge-коммит в `develop`).
   *Иначе:* локальные ветки расходятся с origin, а случайный коммит в `develop`
   приходится откатывать `reset`'ом.

3. **PR мержится только merge-коммитом.** Squash и rebase выключены в настройках репо.
   *Иначе:* после squash `develop` перестаёт быть предком `master`, fast-forward при
   обратном слиянии невозможен, каждый релиз оставляет в `develop` лишний merge-коммит и
   дублирует историю.

4. **Релизный PR живёт минуты.** Открывается, когда `develop` готов, и мержится в том же
   подходе. Пока он открыт, в `master` не мержится ничего другого — ни контент, ни хотфикс.
   Пока CI не вливает `master` в `develop` сам, перед релизным PR это делается руками
   (исключение из правила 2): после каждого релиза `develop` не содержит merge-коммита
   `master`. *Иначе:* пуши CI в `develop` от `GITHUB_TOKEN` сдвигают head релизного PR, а
   CI на новой ревизии сам не запускается; с required checks PR виснет в «expected». Если
   всё же случилось: «Approve workflows to run» в PR или закрыть и открыть заново.

5. **Хотфикс — ветка от `origin/master`, PR в `master`, первым коммитом
   `bash .github/scripts/version.sh set X.Y.(Z+1)-SNAPSHOT`** (та же строка, что уже
   стоит в `develop`, если там не делали минорный бамп). Обратное слияние в `develop` —
   сразу после выката, по исключению из правила 2, не откладывая. Конфликт на строке
   версии при этом ожидаем: CI сразу после тега поднимает `develop` до следующего патча;
   решается в пользу `develop`. *Иначе:* без бампа `release` найдёт существующий тег и
   выпустит без тега; отложенный back-merge добавляет к конфликту версии расхождение по
   коду.

6. **Минорный или мажорный бамп в `develop` — отдельный маленький PR
   (`chore: start X.Y.0-SNAPSHOT`) сразу перед релизным PR, не тогда, когда в работе
   хотфикс.** *Иначе:* хотфикс и ручной бамп меняют строку версии по-разному, а шаг бампа
   в CI ставит следующий патч без сравнения версий: хотфикс v1.4.5 при `develop` на
   `1.5.0-SNAPSHOT` молча вернёт его на `1.4.6-SNAPSHOT`.

7. **Откат — только revert-коммитом через PR в `master`.** Старые run'ы `Deploy` не
   перезапускают, `workflow_dispatch` запускают только с `master` (окружение `production`
   другие ветки не пустит). Прод меняется только пайплайном: ручных сборок и правок
   `/opt/workbit/.env` нет. *Иначе:* re-run старого run возвращает прод целиком на старый
   коммит вместе с контентом, выкаченным позже; ручная правка `BACKEND_IMAGE` ломает
   `rollback()` следующего деплоя.

8. **Dependabot мержится только в `develop`.** Version updates нацелены туда через
   `dependabot.yml`; security updates GitHub всегда открывает в дефолтную ветку, то есть в
   `master`. Такой PR перенацелить на `develop` (Edit → base); это работает, когда `master`
   уже влит в `develop`, иначе PR покажет чужие коммиты. Закрывать не надо: закрытый PR
   Dependabot эту версию больше не предложит. *Иначе:* мерж зависимости в `master` — это
   полный выкат и релиз из одного `chore`.

9. **Теги `v*` ставит только CI.** Руками теги не создаются и не двигаются. *Иначе:*
   `version.sh check` сравнивает pom с самым большим тегом, и ручной тег выше версии в pom
   заблокирует все следующие релизы.

10. **Один PR в `master` за раз.** Контентные и хотфикс-PR мержатся по очереди, следующий —
    после зелёного выката предыдущего. Мерж в `master` — это выкат: мержить, когда есть
    15 минут посмотреть на пайплайн. *Иначе:* два push подряд встают в очередь `deploy`,
    а порядок исполнения GitHub не гарантирует.

## Настройки репозитория

Правила опираются на такие настройки GitHub.

**Settings → General → Pull Requests**

| Настройка | Значение |
|---|---|
| Allow merge commits | ✔, default message — Pull request title (merge-коммит получает имя `release: vX.Y.Z (#N)` автоматически) |
| Allow squash merging | ✖ |
| Allow rebase merging | ✖ |
| Always suggest updating pull request branches | ✔ |
| Automatically delete head branches | ✔ (безопасно только вместе с ruleset `develop` — иначе мерж релизного PR удалит `develop` как head-ветку) |
| Allow auto-merge | ✖ |

**Settings → Rules → Rulesets**

- `master` (target — include by pattern `master`, bypass пустой): Restrict deletions,
  Block force pushes; Require a pull request before merging (approvals 0, allowed merge
  methods — только Merge); Require status checks to pass — `checkstyle`, `test`, `docker`,
  `frontend` (джобы `ci.yml`, имена чеков = id джоб; пропущенная по `if:` джоба считается
  пройденной, пропущенный целиком workflow — нет, поэтому фильтры путей в `ci.yml` живут на
  уровне джоб). «Require branches to be up to date» включается, когда CI начнёт сам
  вливать `master` в `develop`: до этого каждый релизный PR был бы «out of date». Require
  linear history — не включать, схема на merge-коммитах.
- `develop` (bypass пустой): Restrict deletions, Block force pushes. Без Require a pull
  request: CI пушит в `develop` от `GITHUB_TOKEN`, а он rulesets не обходит (в bypass можно
  добавить только deploy key, PAT или GitHub App). Пока пуши CI не переведены на deploy
  key, правило 2 держится договорённостью.

**Settings → Environments → production**

Deployment branches and tags — Selected branches → `master`. Политика действует только
на джобы с `environment: production`; джобы без окружения (тесты, сборка образа) с чужой
ветки отработали бы, поэтому шаг «Refuse to deploy from a non-master ref» остаётся первым
в workflow, а политика — второй слой.

**Settings → Actions → General**

Workflow permissions — Read repository contents; «Allow GitHub Actions to create and
approve pull requests» выключено. Workflow объявляют `permissions` явно, это страховка.

**Сознательно не сделано**

- Ruleset на теги `v*`: CI создаёт тег через `GITHUB_TOKEN` и был бы заблокирован.
- Дефолтная ветка `develop` (Dependabot security и новые PR шли бы туда сами):
  `schedule`-workflow (`backup`, `canary`, `security`) выполняются из дефолтной ветки и
  должны отражать прод.
- CI-проверка «ветка от `develop` в PR в `master`»: правило 1 и чеклист.

## Хорошие привычки

- Ветка живёт дни, не недели. Живёт дольше — дробить.
- До открытия PR свою ветку можно перебазировать и пушить с `--force-with-lease`. После
  открытия — только merge базы в ветку (кнопка Update branch), история PR не
  переписывается.
- `develop` всегда готов к релизу: незаконченное не мержится либо выключено флагом.
- Релиз — PR `develop → master` с заголовком `release: vX.Y.Z`; release notes генерирует
  CI.
- Заголовок PR = заголовок merge-коммита = Conventional Commits на английском; Dependabot
  оставляет свои.
- Перед PR в `master` не из `develop`: `git log --oneline origin/master..HEAD` показывает
  только свои коммиты.
- Локальные `master` и `develop` можно не держать вовсе:
  `git switch -c fix-x --no-track origin/master` работает без локальной базы. Если
  держать — обновлять только `git pull --ff-only`.
- После мержа: `git fetch --prune`, `git branch -d <ветка>`.
