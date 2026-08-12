/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_STATIC_TOUR?: string
}

interface Window {
  EVENTLAB_CONFIG?: {
    grafanaBaseUrl?: string
  }
}
