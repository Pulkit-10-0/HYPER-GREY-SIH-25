"use client"

import { useRouter } from "next/navigation"
import type { Sample } from "./types"

export default function SampleCard({ s }: { s: Sample }) {
  const router = useRouter()
  return (
    <button
      onClick={() => router.push(`/dashboard/${encodeURIComponent(s.sampleNo)}`)}
      className="card p-5 text-left hover:shadow-md transition-shadow"
    >
      <div className="flex items-start justify-between">
        <div>
          <div className="text-sm text-gray-500">Sample</div>
          <div className="text-lg font-semibold text-gray-900">{s.sampleNo}</div>
        </div>
        <div className="text-right">
          <div className="text-sm text-gray-500">Authenticity</div>
          <div className="text-brand font-semibold">{s.authenticityScore.toFixed(1)}%</div>
        </div>
      </div>
      <div className="mt-3 text-gray-700">
        <div className="font-medium">{s.herbName}</div>
        <div className="text-sm text-gray-600 mt-1">
          Freshness: {s.freshness} • Date: {s.dateTaken}
        </div>
      </div>
    </button>
  )
}
