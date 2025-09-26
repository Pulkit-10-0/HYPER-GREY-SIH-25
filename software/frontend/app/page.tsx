export default function Landing() {
  return (
    <div className="space-y-16">
      <section className="bg-white card p-8">
        <div className="max-w-3xl">
          <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-brand text-balance">
            Modern Science for Ayurveda Quality Assurance
          </h1>
          <p className="mt-4 text-gray-700 text-pretty leading-relaxed">
            AyuSure provides objective quality assessment for Ayurvedic herbs using an electronic tongue sensor array,
            AI-driven taste fingerprinting, and phytochemical profiling. The platform is designed in a professional,
            government-aligned tone with a modern interface.
          </p>
          <div className="mt-6 flex gap-4">
            <a href="/dashboard" className="btn btn-primary">
              Go to Dashboard
            </a>
            <a href="#features" className="btn btn-outline">
              Learn More
            </a>
          </div>
        </div>
      </section>

      <section id="features" className="grid md:grid-cols-3 gap-6">
        <div className="card p-6">
          <h3 className="text-lg font-semibold text-gray-900">Taste Fingerprinting</h3>
          <p className="mt-2 text-gray-700 leading-relaxed">
            Detects key taste modalities and compares against authenticated references for objective scoring.
          </p>
        </div>
        <div className="card p-6">
          <h3 className="text-lg font-semibold text-gray-900">Phytochemical Profiling</h3>
          <p className="mt-2 text-gray-700 leading-relaxed">
            Maps chemical signatures to known reference ranges to validate purity, potency, and safety.
          </p>
        </div>
        <div className="card p-6">
          <h3 className="text-lg font-semibold text-gray-900">Adulteration Checks</h3>
          <p className="mt-2 text-gray-700 leading-relaxed">
            Flags heavy metals, pesticides, and foreign matter with clear pass/fail indicators.
          </p>
        </div>
      </section>

      <section className="card p-8">
        <h2 className="text-2xl font-bold text-gray-900">Impact</h2>
        <div className="mt-4 grid md:grid-cols-2 gap-6">
          <div>
            <h4 className="font-medium">Consumers</h4>
            <p className="text-gray-700">Greater trust in the safety and effectiveness of Ayurvedic products.</p>
          </div>
          <div>
            <h4 className="font-medium">Regulators</h4>
            <p className="text-gray-700">Data-backed standardization aligned with pharmacopeial benchmarks.</p>
          </div>
          <div>
            <h4 className="font-medium">Industry</h4>
            <p className="text-gray-700">Faster batch release with objective QA and downloadable reports.</p>
          </div>
          <div>
            <h4 className="font-medium">Researchers</h4>
            <p className="text-gray-700">Curated datasets for further validation and model improvement.</p>
          </div>
        </div>
      </section>
    </div>
  )
}
