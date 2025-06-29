// src/app/dashboard/page.tsx
'use client';

import React, { useEffect } from 'react';
import { useVulnerabilityStore, LabInstanceInfo } from '@/store/vulnerabilityStore';
import Link from 'next/link';

const RunningLabCard = ({ lab }: { lab: LabInstanceInfo }) => {
    const { terminateLab, isTerminatingLab, terminateLabError } = useVulnerabilityStore();
    const isTerminatingThis = isTerminatingLab[lab.instanceId];
    const errorTerminatingThis = terminateLabError[lab.instanceId];

    const handleTerminate = async () => {
        if (isTerminatingThis) return;
        const confirmed = window.confirm(`Are you sure you want to terminate lab instance <span class="math-inline">\{lab\.instanceId\} \(</span>{lab.vulnerabilityId})?`);
        if (confirmed) {
            // For now, use a hardcoded userId. This should come from an auth context later.
            const success = await terminateLab(lab.instanceId, "frontenduser");
            if (success) {
                alert(`Lab instance ${lab.instanceId} termination initiated.`);
            } else {
                // Error is in store, alert is optional
            }
        }
    };

    return (
        <div className="bg-white p-6 rounded-lg shadow-md border border-gray-200">
            <h3 className="text-xl font-semibold text-blue-600 mb-2">Instance: {lab.instanceId}</h3>
            <p className="text-sm text-gray-500 mb-1">Vulnerability ID: {lab.vulnerabilityId}</p>
            <p className="text-sm text-gray-700 mb-1">Status:
                <span className={`ml-2 font-semibold ${
                    lab.status === 'RUNNING' ? 'text-green-600' :
                        lab.status === 'STARTING' ? 'text-yellow-600 animate-pulse' :
                            lab.status.startsWith('ERROR') ? 'text-red-600' : 'text-gray-600'
                }`}>
          {lab.status}
        </span>
            </p>
            {lab.status === 'RUNNING' && lab.accessUrl && (
                <p className="text-sm text-gray-700 mb-3">
                    Access URL: <Link href={lab.accessUrl} target="_blank" rel="noopener noreferrer" className="text-indigo-600 hover:text-indigo-800 underline break-all">{lab.accessUrl}</Link>
                </p>
            )}
            {lab.status.startsWith('ERROR_LAUNCH') && lab.accessUrl && ( // accessUrl here contains the error message
                <p className="text-sm text-red-500 mb-3 break-all">
                    Details: {lab.accessUrl}
                </p>
            )}

            <button
                onClick={handleTerminate}
                disabled={isTerminatingThis || lab.status === 'STARTING'}
                className={`w-full mt-4 font-semibold py-2 px-4 rounded-md transition duration-150 ease-in-out shadow focus:outline-none focus:ring-2  focus:ring-opacity-75 ${
                    isTerminatingThis || lab.status === 'STARTING'
                        ? 'bg-gray-400 text-gray-800 cursor-not-allowed'
                        : 'bg-red-500 hover:bg-red-700 text-white focus:ring-red-400'
                }`}
            >
                {isTerminatingThis ? 'Terminating...' : 'Terminate Lab'}
            </button>
            {errorTerminatingThis && <p className="text-xs text-red-500 mt-2">{errorTerminatingThis}</p>}
        </div>
    );
};


export default function DashboardPage() {
    const { runningLabs, fetchDefinitions } = useVulnerabilityStore(); // fetchDefinitions to ensure defs are loaded for context if needed

    useEffect(() => {
        // If definitions are not loaded, they might be needed for context (e.g., showing lab names)
        // This is a simple way; a more robust way would be to ensure definitions are loaded application-wide
        // or fetch specific definition details if needed for each running lab.
        fetchDefinitions();
    }, [fetchDefinitions]);


    // In a real app, you might fetch running labs for the current user from a backend endpoint
    // instead of relying solely on client-side state from launch actions.
    // For now, this `runningLabs` array is populated when `launchLab` is called.

    return (
        <div>
            <div className="mb-8">
                <h1 className="text-3xl font-semibold text-gray-800">My Active Labs</h1>
                <p className="mt-1 text-gray-600">Manage your currently running vulnerability lab instances.</p>
            </div>

            {runningLabs.length === 0 ? (
                <div className="text-center py-10 bg-white rounded-lg shadow-md">
                    <svg xmlns="http://www.w3.org/2000/svg" className="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p className="mt-5 text-lg font-medium text-gray-700">No labs currently running.</p>
                    <p className="mt-1 text-sm text-gray-500">
                        Go to the <Link href="/vulnerabilities" className="text-blue-600 hover:underline">Vulnerabilities page</Link> to launch a new lab.
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {runningLabs.map((lab) => (
                        <RunningLabCard key={lab.instanceId} lab={lab} />
                    ))}
                </div>
            )}
        </div>
    );
}