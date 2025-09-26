"use client"

import { useMemo } from "react"
import { Upload, BarChart3, CheckCircle, Download } from "lucide-react"
import type { Sample } from "./types"
import {
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from "recharts"

function toTasteArray(sample: Sample) {
  const keys = Object.keys(sample.tasteProfile)
  return keys.map((k) => ({
    taste: k,
    value: sample.tasteProfile[k],
    reference: sample.tasteReference[k] ?? 0,
  }))
}

function toPhytoArray(sample: Sample) {
  const pairs = Object.entries(sample.phytochemicalProfile)
  return pairs.map(([compound, m]) => ({
    compound,
    detected: m.detected,
    reference: m.reference,
    units: m.units ?? "",
  }))
}

export default function SampleAnalysis({ sample, allSamples }: { sample: Sample; allSamples: Sample[] }) {
  const tasteData = useMemo(() => toTasteArray(sample), [sample])
  const phytoData = useMemo(() => toPhytoArray(sample), [sample])

  const exportJSON = () => {
    const blob = new Blob([JSON.stringify(sample, null, 2)], { type: "application/json" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `${sample.sampleNo}-report.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const exportCSV = () => {
    const rows: string[] = []
    rows.push("Parameter,Detected,Reference,Units")
    Object.entries(sample.phytochemicalProfile).forEach(([name, m]) => {
      rows.push(`${name},${m.detected},${m.reference},${m.units ?? ""}`)
    })
    const csv = rows.join("\n")
    const blob = new Blob([csv], { type: "text/csv" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `${sample.sampleNo}-phytochemicals.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Sample Analysis</h2>
          <p className="text-gray-600 mt-1">Detailed quality assessment and taste fingerprinting</p>
        </div>
        <button className="btn btn-primary">
          <Upload className="h-5 w-5 mr-2" />
          Upload New Sample
        </button>
      </div>

      {/* Sample Selection */}
      <div className="card p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Select Sample for Analysis</h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {allSamples.map((s) => (
            <a
              key={s.sampleNo}
              href={`/dashboard/${encodeURIComponent(s.sampleNo)}`}
              className={`p-4 rounded-lg border-2 text-left transition-all ${
                s.sampleNo === sample.sampleNo ? "border-brand bg-green-50" : "border-stone-200 hover:border-stone-300"
              }`}
            >
              <div className="font-medium text-gray-900">{s.sampleNo}</div>
              <div className="text-sm text-gray-600">{s.herbName}</div>
              <div className="text-xs text-gray-500 mt-1">{s.dateTaken}</div>
            </a>
          ))}
        </div>
      </div>

      {/* Three KPI Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Quality Score */}
        <div className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-900">Quality Score</h3>
            <CheckCircle className="h-6 w-6 text-brand" />
          </div>
          <div className="text-center">
            <div className="text-4xl font-bold text-brand mb-2">{sample.authenticityScore.toFixed(1)}%</div>
            <div className="w-full bg-gray-200 rounded-full h-3 mb-4">
              <div className="bg-brand h-3 rounded-full" style={{ width: `${sample.authenticityScore}%` }} />
            </div>
            <p className="text-sm text-gray-600">Authenticated and safe</p>
          </div>
          <div className="mt-6 space-y-3 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Purity</span>
              <span className="font-medium text-gray-900">{sample.qualityMetrics.purity.toFixed(1)}%</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Potency</span>
              <span className="font-medium text-gray-900">{sample.qualityMetrics.potency.toFixed(1)}%</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Safety</span>
              <span className="font-medium text-brand">{sample.qualityMetrics.safety.toFixed(1)}%</span>
            </div>
          </div>
        </div>

        {/* Adulteration Status */}
        <div className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-900">Adulteration Check</h3>
            <CheckCircle className="h-6 w-6 text-brand" />
          </div>
          <div className="text-center mb-6">
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-3">
              <CheckCircle className="h-8 w-8 text-brand" />
            </div>
            <p className="text-lg font-semibold text-brand">
              {Object.values(sample.adulteration).every((v) => v === "Clear")
                ? "No adulteration detected"
                : "Review required"}
            </p>
            <p className="text-sm text-gray-600">Batch: {sample.batchNo}</p>
          </div>
          <div className="space-y-3">
            {[
              ["Heavy Metals", sample.adulteration.heavyMetals],
              ["Pesticides", sample.adulteration.pesticides],
              ["Foreign Matter", sample.adulteration.foreignMatter],
            ].map(([label, value]) => (
              <div className="flex justify-between items-center" key={label}>
                <span className="text-sm text-gray-600">{label}</span>
                <span
                  className={`px-2 py-1 text-xs rounded-full ${
                    value === "Clear" ? "bg-green-100 text-brand" : "bg-amber-100 text-accent"
                  }`}
                >
                  {value}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* AI Confidence */}
        <div className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-900">AI Confidence</h3>
            <BarChart3 className="h-6 w-6 text-tech" />
          </div>
          <div className="text-center mb-6">
            <div className="text-3xl font-bold text-tech mb-2">{sample.aiConfidence.overall.toFixed(1)}%</div>
            <p className="text-sm text-gray-600">High confidence analysis</p>
          </div>
          <div className="space-y-3 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Pattern Matching</span>
              <span className="font-medium text-gray-900">{sample.aiConfidence.patternMatching.toFixed(1)}%</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Chemical Profile</span>
              <span className="font-medium text-gray-900">{sample.aiConfidence.chemicalProfile.toFixed(1)}%</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Reference Similarity</span>
              <span className="font-medium text-gray-900">{sample.aiConfidence.referenceSimilarity.toFixed(1)}%</span>
            </div>
          </div>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Taste Fingerprint */}
        <div className="card p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Taste Fingerprint Analysis</h3>
          <ResponsiveContainer width="100%" height={300}>
            <RadarChart data={tasteData}>
              <PolarGrid />
              <PolarAngleAxis dataKey="taste" />
              <PolarRadiusAxis angle={90} domain={[0, 100]} />
              <Radar name="Detected" dataKey="value" stroke="#006400" fill="#006400" fillOpacity={0.2} />
              <Radar name="Reference" dataKey="reference" stroke="#E97400" fill="#E97400" fillOpacity={0.12} />
              <Tooltip />
            </RadarChart>
          </ResponsiveContainer>
          <div className="flex justify-center gap-6 mt-4 text-sm text-gray-600">
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full" style={{ background: "#006400" }} /> Sample
            </div>
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full" style={{ background: "#E97400" }} /> Reference
            </div>
          </div>
        </div>

        {/* Phytochemical Profile */}
        <div className="card p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Phytochemical Profile</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={phytoData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="compound" angle={-45} textAnchor="end" height={80} />
              <YAxis />
              <Tooltip />
              <Bar dataKey="detected" fill="#006400" name="Detected" />
              <Bar dataKey="reference" fill="#E97400" name="Reference" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Details and Export */}
      <div className="card">
        <div className="px-6 py-4 border-b border-stone-200 flex justify-between items-center">
          <h3 className="text-lg font-semibold text-gray-900">Detailed Analysis Results</h3>
          <div className="flex gap-3">
            <button onClick={exportCSV} className="btn btn-outline">
              Export CSV
            </button>
            <button onClick={exportJSON} className="btn btn-secondary">
              <Download className="h-4 w-4 mr-2" />
              Export JSON
            </button>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-stone-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Parameter</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Detected</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Reference</th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Units</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-200">
              {Object.entries(sample.phytochemicalProfile).map(([name, m]) => (
                <tr key={name}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{name}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{m.detected}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{m.reference}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-700">{m.units ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="px-6 py-6 grid md:grid-cols-2 gap-6">
          <div className="space-y-2 text-sm">
            <div>
              <span className="text-gray-600">Color Profile:</span> {sample.colorProfile}
            </div>
            <div>
              <span className="text-gray-600">Freshness:</span> {sample.freshness}
            </div>
            <div>
              <span className="text-gray-600">Date Taken:</span> {sample.dateTaken}
            </div>
            <div>
              <span className="text-gray-600">Used In:</span> {sample.usedIn}
            </div>
            <div>
              <span className="text-gray-600">Moisture Content:</span> {sample.moistureContent}
            </div>
          </div>
          <div className="space-y-2 text-sm">
            <div>
              <span className="text-gray-600">Origin Region:</span> {sample.originRegion}
            </div>
            <div>
              <span className="text-gray-600">Batch No:</span> {sample.batchNo}
            </div>
            <div>
              <span className="text-gray-600">Supplier ID:</span> {sample.supplierId}
            </div>
            <div>
              <span className="text-gray-600">Processing Method:</span> {sample.processingMethod}
            </div>
            <div>
              <span className="text-gray-600">Storage Conditions:</span> {sample.storageConditions}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
