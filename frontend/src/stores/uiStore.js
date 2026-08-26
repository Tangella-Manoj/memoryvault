import { create } from 'zustand'

export const useUiStore = create((set) => ({
  saveModalOpen: false,
  openSaveModal: () => set({ saveModalOpen: true }),
  closeSaveModal: () => set({ saveModalOpen: false }),
}))
