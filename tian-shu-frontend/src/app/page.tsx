// src/app/page.tsx
import Link from 'next/link';

export default function HomePage() {
  return (
      <div className="container mx-auto">
        <h1 className="text-4xl font-bold text-center my-8 text-blue-600">
          Welcome to TianShu Platform
        </h1>
        <p className="text-lg text-center mb-8">
          Your integrated environment for learning and practicing Java application security.
        </p>
        <div className="flex justify-center space-x-4">
          <Link href="/vulnerabilities" legacyBehavior>
            <a className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-3 px-6 rounded-lg text-lg shadow-md transition duration-150 ease-in-out">
              Browse Vulnerabilities
            </a>
          </Link>
          <Link href="/dashboard" legacyBehavior>
            <a className="bg-green-500 hover:bg-green-700 text-white font-bold py-3 px-6 rounded-lg text-lg shadow-md transition duration-150 ease-in-out">
              Go to Dashboard
            </a>
          </Link>
        </div>
      </div>
  );
}