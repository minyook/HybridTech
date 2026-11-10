/* 소스 안내
 * runtime의 외부 AI 상태를 React 구독 생명주기에 연결하는 훅. 화면 해제 시 구독을 정리한다.
 * 연결 모듈: ./runtime.
 */
import { useSyncExternalStore } from 'react';
import { getAIStatus, subscribeAI } from './runtime';
export function useAIStatus(){return useSyncExternalStore(subscribeAI,getAIStatus,getAIStatus);}
