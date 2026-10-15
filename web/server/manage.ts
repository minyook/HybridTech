import { createWorkStore } from './store.ts';

const [command, ...args] = process.argv.slice(2);
const store = createWorkStore();
try {
  if (command === 'issue' && args[0] === '--name' && args[1]?.trim()) {
    const invite = store.issueInvite(args[1].trim());
    console.log(`감독관: ${invite.name}\n등록 코드 (1회용): ${invite.code}\n코드 만료: ${invite.expiresAt}\n회수 ID: ${invite.id}\n사이트 설정 → 감독관 인증에서 입력해 주세요. 인증은 8시간 동안 유지돼요.`);
  } else if (command === 'revoke' && args[0]) {
    store.revokeInvite(args[0]); console.log('등록 코드와 이 코드로 부여한 감독관 권한을 회수했어요.');
  } else if (command === 'list') console.table(store.listInvites());
  else { console.log('발급: npm run supervisor -- issue --name "감독관 이름"\n목록: npm run supervisor -- list\n회수: npm run supervisor -- revoke <회수 ID>'); process.exitCode = 1; }
} finally { store.close(); }
