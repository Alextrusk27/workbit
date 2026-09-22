import { accentCardClasses } from '@/components/ui/cardStyles'

/** Единый стиль модалок. Раскладку оверлея добавляет модалка: `overflow-y-auto`
 *  для длинных форм (панель центрируется своей обёрткой `min-h-full`) или
 *  `flex items-center justify-center` для коротких. Панель — акцентная карточка
 *  на непрозрачной подложке; `modalShellClasses` отдельно — для контента, который
 *  уже сам акцентная карточка (TopUpCard). */
export const modalOverlayClasses =
  'fixed inset-0 z-100 bg-[rgba(6,9,20,0.65)] p-5 backdrop-blur-[6px]'

export const modalShellClasses = 'bg-pop shadow-chat relative rounded-2xl'

export const modalPanelClasses = `${modalShellClasses} ${accentCardClasses}`
