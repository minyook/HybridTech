/* 소스 안내
 * 웹 회귀 검사: Safari visual viewport shrink puts the composer above the keyboard, then restores even with focus retained / Chrome layout resize and browsers without VisualViewport use the remembered unobstructed height. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다.
 * 검사 예: Safari visual viewport shrink puts the composer above the keyboard, then restores even with focus retained, Chrome layout resize and browsers without VisualViewport use the remembered unobstructed height, browser toolbar changes, hardware keyboard focus and unfocused window resizing do not hide navigation
 * 실행: web/에서 npm test. 실제 네트워크/모델 검증과 mock·순수 검사의 범위는 각 fixture를 확인한다.
 */
import { expect, it } from 'vitest';
import { createChatViewport, type ChatViewportSample } from '../src/chat/viewport';

const phone:ChatViewportSample={width:390,height:844,layoutHeight:844,offsetTop:0,scale:1,screenHeight:844,editorFocused:false};
it('Safari visual viewport shrink puts the composer above the keyboard, then restores even with focus retained',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,editorFocused:true}).keyboardOpen).toBe(false);
  expect(update({...phone,height:460,offsetTop:20,editorFocused:true})).toEqual({height:460,offsetTop:20,keyboardOpen:true});
  expect(update({...phone,editorFocused:true}).keyboardOpen).toBe(false);
});
it('Chrome layout resize and browsers without VisualViewport use the remembered unobstructed height',()=>{
  const update=createChatViewport(phone);
  update({...phone,editorFocused:true});
  expect(update({...phone,height:470,layoutHeight:470,editorFocused:true}).keyboardOpen).toBe(true);
  expect(update({...phone,height:600,layoutHeight:600}).keyboardOpen).toBe(true);
  expect(update(phone).keyboardOpen).toBe(false);
});
it('browser toolbar changes, hardware keyboard focus and unfocused window resizing do not hide navigation',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,height:780,editorFocused:true}).keyboardOpen).toBe(false);
  expect(update({...phone,height:400,layoutHeight:400}).keyboardOpen).toBe(false);
});
it('pinch zoom neither collapses the chat nor opens keyboard mode',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,height:422,offsetTop:180,scale:2,editorFocused:true})).toEqual({height:844,offsetTop:0,keyboardOpen:false});
  expect(update(phone).keyboardOpen).toBe(false);
});
it('rotation preserves an open keyboard and closing in the new orientation restores navigation',()=>{
  const update=createChatViewport(phone);
  update({...phone,height:460,editorFocused:true});
  expect(update({...phone,width:844,height:200,layoutHeight:200,screenHeight:390,editorFocused:true}).keyboardOpen).toBe(true);
  expect(update({...phone,width:844,height:390,layoutHeight:390,screenHeight:390,editorFocused:true}).keyboardOpen).toBe(false);
});
it('rotation without a keyboard and loss of focus restore normal baseline measurements',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,width:844,height:390,layoutHeight:390,screenHeight:390}).keyboardOpen).toBe(false);
  expect(update(phone).keyboardOpen).toBe(false);
  expect(update({...phone,height:460,editorFocused:true}).keyboardOpen).toBe(true);
  expect(update(phone).keyboardOpen).toBe(false);
});
