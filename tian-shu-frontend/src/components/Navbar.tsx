// src/components/Navbar.tsx
import Link from 'next/link';

const Navbar = () => {
    return (
        <nav className="bg-gray-800 text-white p-4 shadow-lg">
            <div className="container mx-auto flex justify-between items-center">
                <Link href="/" legacyBehavior>
                    <a className="text-2xl font-bold hover:text-blue-400 transition-colors">
                        天枢 (TianShu)
                    </a>
                </Link>
                <div className="space-x-4">
                    <Link href="/vulnerabilities" legacyBehavior>
                        <a className="hover:text-blue-300 transition-colors">Vulnerabilities</a>
                    </Link>
                    <Link href="/dashboard" legacyBehavior>
                        <a className="hover:text-blue-300 transition-colors">Dashboard</a>
                    </Link>
                    {/* Add more links later e.g., Login/Logout */}
                </div>
            </div>
        </nav>
    );
};

export default Navbar;