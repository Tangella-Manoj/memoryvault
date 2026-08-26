import { create } from 'zustand'

export const useVaultStore = create((set) => ({
  items: [],
  searchResults: [],
  resurfaceFeed: [],
  forgottenGems: [],

  setItems: (items) => set({ items }),
  setSearchResults: (searchResults) => set({ searchResults }),
  setResurfaceFeed: (resurfaceFeed) => set({ resurfaceFeed }),
  setForgottenGems: (forgottenGems) => set({ forgottenGems }),

  addItem: (item) => set((state) => ({ items: [item, ...state.items] })),
}))
