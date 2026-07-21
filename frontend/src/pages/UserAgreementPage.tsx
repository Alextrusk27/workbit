import source from '@docs/user-agreement.md?raw'
import { LegalDocument } from '@/components/LegalDocument'

export function UserAgreementPage() {
  return <LegalDocument title="Пользовательское соглашение" source={source} />
}
