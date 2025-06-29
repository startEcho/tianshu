// src/app/layout.tsx
import './globals.css';
import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import Navbar from '@/components/Navbar';
import {ToastContainer} from "react-toastify"; // Import the Navbar

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
    title: 'TianShu Vulnerability Platform',
    description: 'A Top-Tier Java Vulnerability Lab Platform',
};

export default function RootLayout({
                                       children,
                                   }: {
    children: React.ReactNode;
}) {
    return (
        <html lang="en">
        <body className={`${inter.className} bg-gray-100 text-gray-900 flex flex-col min-h-screen`}>
        <Navbar /> {/* Add Navbar here */}
        <main className="flex-grow container mx-auto p-4 sm:p-6 lg:p-8">
            {children}
        </main>
        <footer className="bg-gray-800 text-white text-center p-4">
            © {new Date().getFullYear()} TianShu Platform. All rights reserved.
        </footer>
        <ToastContainer
            position="top-right"
            autoClose={5000}
            hideProgressBar={false}
            newestOnTop={false}
            closeOnClick
            rtl={false}
            pauseOnFocusLoss
            draggable
            pauseOnHover
            theme="colored" // Or "light", "dark"
        />
        </body>
        </html>
    );
}