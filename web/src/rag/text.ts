import type { Chunk } from '../types';

// Normalize spelling/spacing for matching, without changing the stored PDF text.
export function aliases(text:string){
  return text.normalize('NFKC').toLowerCase()
    .replace(/심\s*폐\s*소\s*생\s*술|\bcpr\b/g,'심폐소생술')
    .replace(/밀폐\s*공간/g,'밀폐공간').replace(/유해\s*가스/g,'유해가스')
    .replace(/이산화\s*탄소|탄산\s*가스|\bco2\b/g,'이산화탄소')
    .replace(/일산화\s*탄소|\bco\b/g,'일산화탄소').replace(/\bh2s\b/g,'황화수소').replace(/\bo2\b/g,'산소')
    .replace(/감독관/g,'관리감독자').replace(/감시자/g,'감시인')
    .replace(/질소|아르곤|불활성가스/g,'불활성기체')
    .replace(/잠글|잠그|잠기/g,'잠금')
    .replace(/들어가고|입장시킬/g,'입장').replace(/나올|퇴장시킬/g,'퇴장')
    .replace(/들어가기/g,'출입').replace(/공기호흡기/g,'공기호흡기 보호구').replace(/송기마스크/g,'송기마스크 보호구')
    .replace(/측정법/g,'측정 방법').replace(/정상\s*(?:범위|수치)/g,'적정 기준')
    .replace(/조명|랜턴|램프|손전등/g,'전등').replace(/표지판/g,'표지')
    .replace(/바탕색/g,'바탕 색').replace(/글씨색/g,'글씨 색')
    .replace(/일용직/g,'일용근로자').replace(/구조장비/g,'구조 장비')
    .replace(/미끄러움/g,'미끄러').replace(/충분하지\s*않은|충분하지\s*않/g,'불충분')
    .replace(/잠금기/g,'잠금')
    .replace(/오\s*[·ㆍ]\s*폐수/g,'오폐수')
    .replace(/평소/g,'통상').replace(/좁은|협소한/g,'협소')
    .replace(/몇\s*(?:퍼센트|%)/g,'농도 기준');
}
export const compactText=(text:string)=>aliases(text).replace(/[^0-9a-z가-힣]/g,'');

export function pageBody(chunk:Chunk){
  return chunk.body
    .replace(/^제1장\s*질식과\s*밀폐공간제2장[\s\S]*?안전작업\s*가이드\s*\d+\s*/,'')
    .replace(/^\d+\s+/,'').replace(/<[^<>\n]{1,80}>/g,'\n')
    .replace(/심\s+폐\s+소\s+생\s+술/g,'심폐소생술').trim();
}

const vocabulary=['밀폐공간','관리감독자','이산화탄소','일산화탄소','황화수소','심폐소생술','자동심장충격기','흉부압박','인공호흡','공기호흡기','송기마스크','소화설비','불활성가스','불활성기체','환기장치','환기팬','유해가스','감시인','출입뚜껑','출입문','출입구','임의잠김','긴급구조훈련','채기관','눈금','덕트','송풍관','회복자세','재해자','보호구','방폭','인원','입장','퇴장','해체','부착','출입','산소','농도','측정','허가','절차','환기','점검','방지','직무','자격','시기','방법','기준','조치','작업'];
export const domainTerms=new Set(vocabulary);
const wordPattern=new RegExp(`(${vocabulary.sort((a,b)=>b.length-a.length).join('|')})`,'g');
const stops=new Set(['알려줘','알려주세요','설명해줘','설명해주세요','말해줘','말해주세요','물어볼게','가르쳐줘','정리해줘','어떻게','뭐야','무엇','무엇인가요','궁금해','자세히','자세하게','상세히','상세하게','대해서','대해','관련','좀','알고','싶어','싶어요','필요','해야','하나요','해야하나요','해야해','되나요','되는지','얼마','얼마면','몇','어떤','있는','있나요','있어','있어요','문서','내용','나와','나오는','알려','설명','부탁해','전체','순서대로','가능해','정도','여야해','퍼센트']);
export const genericTerms=new Set(['작업','방법','기준','조치','확인','확인해야','절차','마련','방지','측정','농도','시기','직무','자격','주의사항','최초','시간','모르','밀폐공간','가스','장소','내부','종류','상황','경우','장비','수치','상한','하한','범위','이상','이하','미만','초과','단위','크기','유무','공간','확인사항']);
for(const word of ['쉽게','의미','뜻','이유','차이','비교','풀어'])stops.add(word);
for(const word of ['어디','어디야','어디까지','언제까지','뭘','무슨','누구','누구에게','걸','그걸','안에','밖에','안과','전','후','정도','얼마나','어느','아무거나','말고','함께','대비할','있는지','없으면','아니면','다른','다음','오늘','어제','용','작업자','사람','까지','전과','가능한','길게','들어','구해','취해'])stops.add(word);
// Korean request/conditional endings carry no document topic. Keep unknown
// nouns (e.g. 전압/방수/비밀번호) so context words cannot answer for them.
const dialogue=/^(?:써야|써도|쓰는|사용해야|사용해|사용할|준비해|알려주|확보해|갖춰야|받아야|유지돼|넣고|봐도|돼|되지|전이야|줘야|잠그기|내려가|끊어지면|꼬이거나|모르는|모르겠|어떡|적어|만들고|끝나고)/;
export function queryTerms(question:string){
  const text=aliases(question).replace(/언제/g,'시기').replace(/누가/g,'자격').replace(wordPattern,' $1 ');
  return [...new Set(text.split(/[^0-9a-z가-힣]+/).map(word=>vocabulary.includes(word)?word:word.replace(/(?:인가요|입니까|하는지|으로|에서|에게|이랑|하고|이야|이냐|인지|까요|은|는|이|가|을|를|의|에|로|도|만)$/,'')).filter(word=>word.length>=2&&!stops.has(word)&&!dialogue.test(word)))];
}
