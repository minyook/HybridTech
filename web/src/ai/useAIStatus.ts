import { useSyncExternalStore } from 'react';
import { getAIStatus, subscribeAI } from './runtime';
export function useAIStatus(){return useSyncExternalStore(subscribeAI,getAIStatus,getAIStatus);}
