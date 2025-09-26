"use client"

import { useEffect, useState } from "react"
import type { Sample } from "@/components/types"
import SampleAnalysis from "@/components/SampleAnalysis"
import { useParams } from "next/navigation"

export default function SampleDetailPage() {
  const { sampleId } = useParams<{ sampleId: string }>()
  const [allSamples, setAllSamples] = useState<Sample[] | null>(null)
  const [sample, setSample] = useState<Sample | null>(null)

  useEffect(() => {
    const run = async () => {
      const res = await fetch("/sampleData.json", { cache: "no-store" })
      const data: Sample[] = await res.json()
      setAllSamples(data)
      const s = data.find((x) => x.sampleNo === sampleId)
      setSample(s ?? null)
    }
    run()
  }, [sampleId])

  if (!allSamples) return <p className="text-gray-600">Loading samples…</p>
  if (!sample) return <p className="text-gray-600">Sample not found.</p>

  return <SampleAnalysis sample={sample} allSamples={allSamples} />
}
