# Правила работы с ветками

`master` — это прод: каждый push в него запускает выкат, а тег версии ставит CI. Поэтому
правила ниже строже, чем в обычном репозитории. Часть из них закрыта настройками GitHub
(раздел «Настройки репозитория»), остальное — договорённости.

## Модель

| Ветка | Что это | Как попадают изменения |
|---|---|---|
| `master` | прод: каждый push = выкат, тег ставит CI | только PR: релизный `develop → master` или ветка от `master` (хотфикс, контент) |
| `develop` | интеграция, следующий релиз | только PR из веток от `develop`; плюс пуши CI (бамп версии, обратное слияние) |
| рабочая ветка | одна задача = одна ветка = один PR | создаётся от `origin/<целевая>`, удаляется после мержа |

Постоянных рабочих веток нет. Имена короткие, без префиксов вида `feature/`.

## Правила

1. **Ветка отводится от той ветки, куда пойдёт PR.** PR в `master` — от `origin/master`,
   PR в `develop` — от `origin/develop`. В ветку, нацеленную на `master`, `develop` не
   вливается. Перед мержем PR в `master` не из `develop` проверить вкладки Commits и
   Files changed: только свои коммиты и только ожидаемые пути.

2. **В `master` и `develop` не коммитят и не пушат напрямую.** Локальные `master` и
   `develop` — зеркала origin. Новая ветка:
   `git fetch && git switch -c <имя> --no-track origin/<база>`, первый push —
   `git push -u origin HEAD`. Единственное исключение — обратное слияние
   `master → develop` после хотфикса, пока его не делает CI: локальный
   `git merge origin/master` в `develop` и push либо кнопка Update branch на релизном PR.

3. **PR мержится только merge-коммитом.** Squash и rebase выключены в настройках репо.

4. **Релизный PR живёт минуты.** Открывается, когда `develop` готов, и мержится в том же
   подходе. Пока он открыт, в `master` не мержится ничего другого. Перед ним `master`
   должен быть влит в `develop`.

5. **Хотфикс — ветка от `origin/master`, PR в `master`, первым коммитом
   `bash .github/scripts/version.sh set X.Y.(Z+1)-SNAPSHOT`.** Сразу после выката —
   обратное слияние в `develop`; конфликт на строке версии решается в пользу `develop`.

6. **Минорный или мажорный бамп в `develop` — отдельный PR (`chore: start X.Y.0-SNAPSHOT`)
   сразу перед релизным**, не тогда, когда в работе хотфикс.

7. **Откат — только revert-коммитом через PR в `master`.** Старые run'ы `Deploy` не
   перезапускают, `workflow_dispatch` запускают только с `master`. Прод меняется только
   пайплайном.

8. **Dependabot мержится только в `develop`.** Security-PR, которые GitHub открывает в
   `master`, перенацелить на `develop` (Edit → base), не закрывать.

9. **Теги `v*` ставит только CI.** Руками теги не создаются и не двигаются.

10. **Один PR в `master` за раз.** Следующий — после зелёного выката предыдущего. Мерж в
    `master` — это выкат: мержить, когда есть 15 минут посмотреть на пайплайн.

## Настройки репозитория

**Settings → General → Pull Requests:** только merge commits, default message — Pull
request title; squash, rebase и auto-merge выключены; Always suggest updating pull request
branches и Automatically delete head branches включены.

**Settings → Rules → Rulesets** (bypass пустой):

- `master`: Restrict deletions, Block force pushes, Require a pull request (approvals 0,
  merge method — только Merge), Require status checks — `checkstyle`, `test`, `docker`,
  `frontend` (джобы `ci.yml`). «Require branches to be up to date» включается, когда CI
  начнёт сам вливать `master` в `develop`.
- `develop`: Restrict deletions, Block force pushes. Без Require a pull request: CI пушит
  в `develop` от `GITHUB_TOKEN`.

**Settings → Environments → production:** Deployment branches and tags — Selected
branches → `master`.

**Settings → Actions → General:** Workflow permissions — Read repository contents;
«Allow GitHub Actions to create and approve pull requests» выключено.

## Хорошие привычки

- Ветка живёт дни, не недели. Живёт дольше — дробить.
- До открытия PR ветку можно перебазировать и пушить с `--force-with-lease`. После
  открытия — только Update branch, история PR не переписывается.
- `develop` всегда готов к релизу: незаконченное не мержится либо выключено флагом.
- Релиз — PR `develop → master` с заголовком `release: vX.Y.Z`; release notes генерирует
  CI.
- Заголовок PR = заголовок merge-коммита = Conventional Commits на английском; Dependabot
  оставляет свои.
- Перед PR в `master` не из `develop`: `git log --oneline origin/master..HEAD` показывает
  только свои коммиты.
- Локальные `master` и `develop` можно не держать вовсе. Если держать — обновлять только
  `git pull --ff-only`.
- После мержа: `git fetch --prune`, `git branch -d <ветка>`.
