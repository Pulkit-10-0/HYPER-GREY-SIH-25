import type React from "react"
import type { Metadata } from "next"
import { GeistSans } from "geist/font/sans"
import { GeistMono } from "geist/font/mono"
import { Analytics } from "@vercel/analytics/next"
import "./globals.css"
import Navbar from "@/components/Navbar"
import Footer from "@/components/Footer"
import { Suspense } from "react"

export const metadata: Metadata = {
  title: "AyuSure – AI-powered Quality Assurance for Ayurveda",
  description:
    "AyuSure provides objective, AI-driven quality assessment for Ayurvedic herbs using taste fingerprinting and phytochemical profiling.",
  generator: "v0.app",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en" className="bg-sand">
      <body className={`font-sans ${GeistSans.variable} ${GeistMono.variable} text-gray-900 antialiased`}>
        <Suspense fallback={<div>Loading...</div>}>
          <Navbar />
          <main className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-8">{children}</main>
          <Footer />
        </Suspense>
        <Analytics />
      </body>
    </html>
  )
}
