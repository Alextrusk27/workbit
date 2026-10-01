/** Кодировка из Content-Type, по умолчанию UTF-8. */
export function charsetOf(contentType: string): string {
  const match = /;\s*charset="?([^";\s]+)/i.exec(contentType)
  return match?.[1] ?? 'utf-8'
}

/** TXT читаем по `charset` из `blob.type`: `blob.text()` всегда декодирует как UTF-8,
 *  а резюме бывают в windows-1251. */
export async function decodeText(blob: Blob): Promise<string> {
  return new TextDecoder(charsetOf(blob.type)).decode(await blob.arrayBuffer())
}

/** Имя берём из списка: Content-Disposition в dev кросс-доменно не читается. */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url))
}
