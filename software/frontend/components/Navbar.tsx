"use client"

import Link from "next/link"

export default function Navbar() {
  return (
    <header className="border-b border-stone-200 bg-white">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        <Link href="/" className="flex items-center space-x-2">
          <div className="h-8 w-8 rounded-lg bg-brand" />
          <span className="font-semibold tracking-tight text-brand">AyuSure</span>
        </Link>
        <nav className="flex items-center gap-6">
          <Link href="/" className="text-sm text-gray-700 hover:text-gray-900">
            Home
          </Link>
          <Link href="/dashboard" className="text-sm text-gray-700 hover:text-gray-900">
            Dashboard
          </Link>
        </nav>
      </div>
    </header>
  )
}
