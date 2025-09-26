export type TasteProfile = Record<string, number>
export type PhytoMetric = { detected: number; reference: number; units?: string }
export type PhytochemicalProfile = Record<string, PhytoMetric>

export type Sample = {
  sampleNo: string
  herbName: string
  colorProfile: string
  freshness: string
  dateTaken: string
  usedIn: string
  originRegion: string
  batchNo: string
  supplierId: string
  processingMethod: string
  storageConditions: string
  moistureContent: string
  authenticityScore: number
  qualityMetrics: {
    purity: number
    potency: number
    safety: number
  }
  aiConfidence: {
    overall: number
    patternMatching: number
    chemicalProfile: number
    referenceSimilarity: number
  }
  adulteration: {
    heavyMetals: string
    pesticides: string
    foreignMatter: string
  }
  tasteProfile: TasteProfile
  tasteReference: TasteProfile
  phytochemicalProfile: PhytochemicalProfile
}
