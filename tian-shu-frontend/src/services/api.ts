// src/services/api.ts
import axios from 'axios';

const API_GATEWAY_BASE_URL = process.env.NEXT_PUBLIC_API_GATEWAY_BASE_URL || 'http://localhost'; // Fallback if env var is not set

// Axios instance for Vulnerability Definition Service
export const vulnerabilityDefinitionApi = axios.create({
    baseURL: `${API_GATEWAY_BASE_URL}/definitions/api/v1`, // Path defined in platform-ingress.yaml
    timeout: 10000, // 10 seconds
    headers: {
        'Content-Type': 'application/json',
    },
});

// Axios instance for Lab Orchestration Service
export const labOrchestrationApi = axios.create({
    baseURL: `${API_GATEWAY_BASE_URL}/orchestration/api/v1`, // Path defined in platform-ingress.yaml
    timeout: 15000, // Potentially longer timeout for lab launch operations
    headers: {
        'Content-Type': 'application/json',
    },
});

// Axios instance for accessing launched Lab Instances (靶场实例)
// Note: The baseURL for individual labs will be dynamic (e.g., /labs/<instance-id>/)
// So, we might create a generic Axios instance here or create them on-the-fly.
// For now, let's create a generic one for other potential platform-wide APIs on the gateway.
export const platformApi = axios.create({
    baseURL: API_GATEWAY_BASE_URL,
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Example of how you might make a call to a specific lab instance dynamically:
// export const getLabInstanceData = (instanceId: string, path: string) => {
//   return axios.get(`<span class="math-inline">\{API\_GATEWAY\_BASE\_URL\}/labs/</span>{instanceId}${path}`);
// };

// Interceptors (optional but good for future use, e.g., adding auth tokens, global error handling)
const services = [vulnerabilityDefinitionApi, labOrchestrationApi, platformApi];

services.forEach(service => {
    service.interceptors.request.use(
        (config) => {
            // const token = localStorage.getItem('authToken'); // Example: get token
            // if (token) {
            //   config.headers.Authorization = `Bearer ${token}`;
            // }
            console.log('Starting API Request:', config.method?.toUpperCase(), config.url, config.data || '');
            return config;
        },
        (error) => {
            console.error('API Request Error:', error);
            return Promise.reject(error);
        }
    );

    service.interceptors.response.use(
        (response) => {
            console.log('API Response:', response.status, response.config.url, response.data);
            return response;
        },
        (error) => {
            console.error('API Response Error:', error.response?.status, error.config.url, error.response?.data || error.message);
            // Handle global errors here, e.g., redirect to login for 401
            return Promise.reject(error);
        }
    );
});