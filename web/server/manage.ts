import { createWorkStore } from './store.ts';

const [command] = process.argv.slice(2);
const store = createWorkStore();
try {
  if (command === 'today' || command === 'rotate') {
    const daily = command === 'rotate' ? store.rotateDailyCode() : store.todayCode();
    console.log(`한국 날짜: ${daily.day}\n오늘의 감독관 암호코드: ${daily.code ?? '사용 중지됨 · rotate로 새 코드 발급'}\n만료: 다음 날 00:00 (한국 시간)\n감독관에게만 전달해 주세요. 여러 감독관이 오늘 같은 코드를 사용할 수 있어요.`);
  } else if (command === 'revoke') {
    store.revokeDailyCode(); console.log('오늘의 코드와 이 코드로 인증한 모든 감독관 권한을 중지했어요. rotate로 새 코드를 발급할 수 있어요.');
  } else { console.log('오늘 코드: npm run supervisor -- today\n즉시 교체: npm run supervisor -- rotate\n오늘 코드 중지: npm run supervisor -- revoke'); process.exitCode = 1; }
} finally { store.close(); }
