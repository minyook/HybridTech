import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it } from 'vitest';
import { allChats, allChecklists, clearRecords, deleteChat, getSetting, saveChat, saveChecklist, setSetting } from '../src/storage/db';
import type { Chat } from '../src/types';

beforeEach(async () => { await clearRecords(); });
describe('기기 기록',() => {
  it('대화가 새 조회에서도 복원되며 같은 ID를 중복 저장하지 않는다',async () => {
    const chat: Chat = { id:'one',question:'환기 방법',answer:'원문',citationIds:['guide:31:0'],mode:'source',createdAt:'2026-10-14T01:00:00Z' };
    await saveChat(chat); await saveChat({...chat,answer:'갱신된 원문'});
    expect(await allChats()).toEqual([{...chat,answer:'갱신된 원문'}]);
    await deleteChat('one'); expect(await allChats()).toHaveLength(0);
  });
  it('점검 상태와 메모, 초안을 보존한다',async () => {
    const draft = {checked:[0,2,4],site:'집수정',note:'환기 확인'};
    await setSetting('checklist-draft',draft);
    await saveChecklist({id:'check-1',...draft,createdAt:'2026-10-14T01:00:00Z'});
    expect(await getSetting('checklist-draft')).toEqual(draft);
    expect((await allChecklists())[0].checked).toEqual([0,2,4]);
  });
  it('기록 삭제는 모델 설정을 지우지 않는다',async () => {
    await setSetting('model',{name:'gemma.litertlm'});
    await clearRecords();
    expect(await allChats()).toEqual([]); expect(await allChecklists()).toEqual([]);
    expect(await getSetting('model')).toEqual({name:'gemma.litertlm'});
  });
});
