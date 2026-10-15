const letters = (text:string) => text.match(/\p{L}/gu) ?? [];
const korean = /\p{Script=Hangul}/u;
const usualScripts = /[\p{Script=Hangul}\p{Script=Latin}\p{Script=Han}]/u;

// A small corruption check, not a factual accuracy or safety verifier. Technical
// names, units and characters actually present in the evidence remain valid.
export function outputIssue(answer:string,referenceText:string):string|undefined {
  if (!answer.trim()) return 'AI가 답변을 반환하지 않았어요.';
  if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\ufffd]/u.test(answer)) {
    return '생성된 답변에서 깨진 문자가 감지됐어요.';
  }
  const characters = letters(answer);
  const unexpected = characters.filter(character => !usualScripts.test(character) && !referenceText.includes(character));
  if (unexpected.length >= 3) return '생성된 답변에 문서와 관계없는 언어가 섞여 있어요.';
  if (characters.length >= 30 && characters.filter(character => korean.test(character)).length / characters.length < .3) {
    return 'AI가 한국어 안내를 정상적으로 생성하지 못했어요.';
  }
  const lines = answer.split('\n').map(line => line.replace(/^\s*(?:#{1,6}\s*|(?:\d+[.)]|[-*+>])\s*)+/g,'').replace(/[*_`]/g,'').trim()).filter(line => letters(line).length >= 4);
  const counts = new Map<string,number>();
  for (const line of lines) counts.set(line,(counts.get(line) ?? 0)+1);
  if (lines.length >= 4 && [...counts.values()].some(count => count >= 3 && count / lines.length >= .5)) {
    return '생성된 답변에 같은 문장이 비정상적으로 반복됐어요.';
  }
  return undefined;
}
