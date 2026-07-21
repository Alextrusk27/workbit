import source from '@docs/privacy-policy.md?raw'
import { LegalDocument } from '@/components/LegalDocument'

export function PrivacyPage() {
  return <LegalDocument title="Политика конфиденциальности" source={source} />
}
