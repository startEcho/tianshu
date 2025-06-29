'use client'; // Required for components that use client-side hooks like useEffect and useState

import React, { useEffect } from 'react';
import { useVulnerabilityStore, VulnerabilityDefinition } from '@/store/vulnerabilityStore'; // Adjust path if needed
import Link from 'next/link'; // For future detail pages or launch actions

// Placeholder for a Launch Button component or function
// We will implement its functionality later
const LaunchButton = ({ vulnId,vulnName }: { vulnId: string,vulnName: string }) => {
    const { launchLab, isLaunchingLab: isLaunchingGlobal, runningLabs } = useVulnerabilityStore();


    const labAlreadyExists = runningLabs.find(lab => lab.vulnerabilityId === vulnId && (lab.status === 'RUNNING' || lab.status === 'STARTING'));
    const isCurrentlyLaunchingThis = isLaunchingGlobal && runningLabs.find(lab => lab.vulnerabilityId === vulnId && lab.status === 'STARTING');



    const handleLaunch = async () => {
        // For now, use a hardcoded userId. This should come from an auth context later.
        const userId = "frontenduser";
        console.log(`Requesting launch for: ${vulnId} by user: ${userId}`);
        const launchedLabInfo = await launchLab(vulnId, userId);

        if (launchedLabInfo) {
            alert(`Lab "${vulnName}" (Instance: ${launchedLabInfo.instanceId}) is launching! Status: ${launchedLabInfo.status}. Access URL (when ready): ${launchedLabInfo.accessUrl}`);
            // Navigation or more sophisticated UI updates can happen here
        } else {
            // Error is handled in the store, an alert here is optional
            // alert(`Failed to launch lab "${vulnName}". Check console or error messages.`);
        }
    };

    const isDisabled = !!isCurrentlyLaunchingThis || !!labAlreadyExists;
    let buttonText = "Launch Lab";
    if (isCurrentlyLaunchingThis) {
        buttonText = "Launching...";
    } else if (labAlreadyExists) {
        buttonText = "Running/Starting";
    }


    return (
        <button
            onClick={handleLaunch}
            disabled={isDisabled} // Disable button if this lab is already starting/running or a global launch is in progress
            className={`mt-4 w-full text-white font-bold py-2 px-4 rounded-md transition duration-150 ease-in-out shadow focus:outline-none focus:ring-2 focus:ring-opacity-75 ${
                isDisabled
                    ? 'bg-gray-400 cursor-not-allowed'
                    : 'bg-green-500 hover:bg-green-700 focus:ring-green-400'
            }`}
        >
            {buttonText}
        </button>
    );
};

// Card component for displaying a single vulnerability
const VulnerabilityCard = ({ vuln }: { vuln: VulnerabilityDefinition }) => {
    return (
        <div className="bg-white rounded-xl shadow-lg overflow-hidden transition-all hover:shadow-2xl transform hover:-translate-y-1">
            <div className="p-6">
                <h2 className="text-2xl font-bold text-gray-800 mb-2">{vuln.name}</h2>
                <div className="mb-3 space-x-2">
                    {vuln.category && (
                        <span className="inline-block bg-blue-100 text-blue-800 text-xs font-semibold px-2.5 py-0.5 rounded-full">
              {vuln.category}
            </span>
                    )}
                    {vuln.difficulty && (
                        <span
                            className={`inline-block text-xs font-semibold px-2.5 py-0.5 rounded-full ${
                                vuln.difficulty === 'Easy' ? 'bg-green-100 text-green-800' :
                                    vuln.difficulty === 'Medium' ? 'bg-yellow-100 text-yellow-800' :
                                        vuln.difficulty === 'Hard' ? 'bg-red-100 text-red-800' :
                                            'bg-gray-100 text-gray-800' // Default or other difficulties
                            }`}
                        >
              {vuln.difficulty}
            </span>
                    )}
                </div>
                <p className="text-gray-600 text-sm mb-4 leading-relaxed h-20 overflow-y-auto custom-scrollbar"> {/* Fixed height for description */}
                    {vuln.description}
                </p>
                {vuln.exploitationGuide && (
                    <div className="mt-3 mb-3 p-3 bg-gray-50 rounded-md border border-gray-200">
                        <h4 className="font-semibold text-gray-700 text-sm mb-1">

                            Exploitation Guide:</h4>
                        <p className="text-xs text-gray-500 whitespace-pre-wrap">{vuln.exploitationGuide}</p>
                    </div>
                )}
                {vuln.tags && vuln.tags.length > 0 && (
                    <div className="mt-3 mb-3">
                        {vuln.tags.map(tag => (
                            <span key={tag} className="inline-block bg-gray-200 text-gray-700 text-xs font-medium mr-2 px-2.5 py-0.5 rounded-full">
                        #{tag}
                    </span>
                        ))}
                    </div>
                )}
                {/* Placeholder for future actions like "View Details" or "Launch" */}
                <div className="mt-auto pt-4 border-t border-gray-200"> {/* Pushes button to bottom */}
                    <LaunchButton vulnId={vuln.id} vulnName={vuln.name} />
                </div>
            </div>
        </div>
    );
};


export default function VulnerabilitiesPage() {
    // Use the Zustand store
    const { definitions, isLoadingDefinitions, errorLoadingDefinitions, fetchDefinitions } = useVulnerabilityStore();

    // Fetch definitions when the component mounts
    useEffect(() => {
        fetchDefinitions();
    }, [fetchDefinitions]); // fetchDefinitions is stable, so this runs once on mount

    if (isLoadingDefinitions) {
        return (
            <div className="flex justify-center items-center h-64">
                <div className="animate-spin rounded-full h-16 w-16 border-t-4 border-blue-500"></div>
                <p className="ml-4 text-xl text-gray-600">Loading vulnerabilities...</p>
            </div>
        );
    }

    if (errorLoadingDefinitions) {
        return (
            <div className="text-center py-10">
                <h1 className="text-3xl font-semibold text-red-600 mb-4">Error Loading Vulnerabilities</h1>
                <p className="text-red-500 bg-red-100 p-4 rounded-md">{errorLoadingDefinitions}</p>
                <button
                    onClick={fetchDefinitions}
                    className="mt-6 bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline"
                >
                    Retry
                </button>
            </div>
        );
    }

    if (!definitions || definitions.length === 0) {
        return (
            <div className="text-center py-10">
                <h1 className="text-3xl font-semibold text-gray-700">No Vulnerabilities Defined</h1>
                <p className="mt-2 text-gray-600">
                    It seems there are no vulnerabilities configured in the platform yet.
                </p>
                <button
                    onClick={fetchDefinitions} // Allow retrying even if empty
                    className="mt-6 bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline"
                >
                    Refresh List
                </button>
            </div>
        );
    }

    return (
        <div>
            <div className="mb-8 text-center">
                <h1 className="text-4xl font-bold text-gray-800">Available Vulnerability Labs</h1>
                <p className="mt-2 text-lg text-gray-600">
                    Select a lab to start practicing your application security skills.
                </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                {definitions.map((vuln) => (
                    <VulnerabilityCard key={vuln.id} vuln={vuln} />
                ))}
            </div>
        </div>
    );
}