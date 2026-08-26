import { Plus } from 'lucide-react'
import { useUiStore } from '../../stores/uiStore'

export default function FloatingSaveButton() {
  const openSaveModal = useUiStore((s) => s.openSaveModal)

  return (
    <button
      onClick={openSaveModal}
      aria-label="Save a new item"
      className="fixed bottom-8 right-8 w-14 h-14 rounded-full bg-teal-600 text-white shadow-lg hover:bg-teal-700 flex items-center justify-center transition-transform hover:scale-105"
    >
      <Plus className="w-6 h-6" />
    </button>
  )
}
