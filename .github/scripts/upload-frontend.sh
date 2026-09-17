#!/usr/bin/env bash
set -euo pipefail

# Заливает собранный фронт на прод. Сначала assets/ без --delete и touch живых файлов,
# затем остальное с --delete: новый index.html появляется, когда его бандлы уже на месте,
# а старые бандлы живут ещё 14 дней для открытых вкладок. /runtime/ на проде не трогается.
# Вход: SSH_DEST (user@host), SSH_KEY (путь к ключу); опционально DIST (default dist).

DIST="${DIST:-dist}"
WWW=/opt/workbit/www

rsync -az -e "ssh -i $SSH_KEY" "$DIST/assets/" "$SSH_DEST:$WWW/assets/"
ls "$DIST/assets" | ssh -i "$SSH_KEY" "$SSH_DEST" "cd $WWW/assets && xargs -r -d '\n' touch --"
rsync -az --delete --exclude assets --exclude /runtime/ -e "ssh -i $SSH_KEY" "$DIST/" "$SSH_DEST:$WWW/"
ssh -i "$SSH_KEY" "$SSH_DEST" "find $WWW/assets -type f -mtime +14 -delete"
