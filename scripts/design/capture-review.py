#!/usr/bin/env python3
"""Capture the installed local fixture app at normal font size for the review gallery.
Run preview.sh first. No live service calls or private fixture accounts are used.
"""
import subprocess,time,xml.etree.ElementTree as E,re,json
from pathlib import Path
import shutil,os
ADB=shutil.which('adb') or str(Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))/'platform-tools/adb')
DEVICE='emulator-5554';PKG='com.kwmobile.hometiming';COMP=PKG+'/.ui.main.MainActivity'
OUT=Path('docs/report/evidence/component-system')
def adb(*args,binary=False):
 r=subprocess.run([ADB,'-s',DEVICE,*args],capture_output=True,timeout=45,check=True)
 return r.stdout if binary else r.stdout.decode(errors='replace')
def sh(*args):return adb('shell',*args)
def tree():
 for attempt in range(3):
  result=sh('uiautomator','dump','/sdcard/ht-window.xml')
  if 'dumped to' in result:
   raw=sh('cat','/sdcard/ht-window.xml');return E.fromstring(raw)
  time.sleep(.7)
 raise AssertionError('UI hierarchy unavailable: '+result)
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def nodes(text=None,id=None,contains=None):
 return [n for n in tree().iter('node') if (text is None or n.get('text')==text) and (id is None or n.get('resource-id')==PKG+':id/'+id) and (contains is None or contains in n.get('text',''))]
def tap(text=None,id=None,contains=None):
 found=nodes(text,id,contains)
 if not found and id and id.startswith('auth_'):
  if 'mInputShown=true' in sh('dumpsys','input_method'):
   sh('input','keyevent','4');time.sleep(.3);found=nodes(text,id,contains)
  for _ in range(3):
   if found:break
   sh('input','swipe','540','1400','540','650','350');time.sleep(.3);found=nodes(text,id,contains)
 if not found:raise AssertionError('Missing tap '+str((text,id,contains)))
 x1,y1,x2,y2=bounds(found[0]);sh('input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.3)
def assert_text(value):
 assert any(value in n.get('text','') for n in tree().iter('node')),'Missing '+value
 print('PASS',value,flush=True)
def snap(name):
 (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True));print('CAPTURE',name,flush=True)
def open_page(page,id='',state='normal',weather='night',condition='clear'):
 args=['am','start','-W','--activity-clear-top','--activity-single-top','-n',COMP,'--es','preview_screen',page,'--es','preview_state',state,'--es','preview_weather',weather,'--es','preview_condition',condition]
 if id:args+=['--es','preview_id',id]
 sh(*args)
 time.sleep(.6)
 for attempt in range(6):
  if 'com.kwmobile.hometiming' in sh('dumpsys','window','windows'):break
  time.sleep(.7)
 else:raise AssertionError('App window did not attach after configuration change')
def swipe_up():sh('input','swipe','540','1800','540','650','350');time.sleep(.3)
def reach(text):
 for _ in range(8):
  ns=nodes(text=text)
  if ns:
   b=bounds(ns[0]);
   if b[1]>150 and b[3]<2250:return
  swipe_up()
 raise AssertionError('Cannot reach '+text)
def type_field(id,value):
 tap(id=id);time.sleep(.8)
 field=nodes(id=id)[0]
 current=field.get('text','')
 if current:
  sh('input','keyevent','123');sh('input','keyevent',*(['67']*(len(current)+2)))
 sh('input','text',value);time.sleep(.5)
 if not field.get('password')=='true':
  assert nodes(id=id)[0].get('text')==value,'Typed value differs for '+id



if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--serial',default='emulator-5554');parser.add_argument('--out',type=Path,default=OUT)
 args=parser.parse_args();DEVICE=args.serial;OUT=args.out;OUT.mkdir(parents=True,exist_ok=True)
 assert sh('settings','get','system','font_scale').strip() in ['1.0','1'], 'Use the default font scale; this is not an enlarged-font run.'
 source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
 records=[]
 def capture(key,label,theme,group='screens',note=''):
  snap(key);records.append(dict(id=key,label=label,theme=theme,group=group,note=note,file=key+'.png'))
 def top():
  for _ in range(2):sh('input','swipe','900','300','900','2200','150')
  time.sleep(.3)
 def end():
  for _ in range(3):sh('input','swipe','900','2200','900','250','150')
  time.sleep(.4)
 def reset():
  open_page('gallery',weather='auto');end();reach('예시 데이터 초기화');tap(text='예시 데이터 초기화');tap(text='초기화')
 pages=[('login','','S1 로그인'),('signup','','S1 회원가입'),('reset','','S1 비밀번호 재설정'),('home','','S2 홈'),('items','','S3 생활용품'),('editor','','S4 등록'),('detail','filter','S5 상세·이력'),('calendar','','S6 캘린더'),('weather','','S7 날씨·제안'),('settings','','S8 설정'),('family','','S8 가족 관리'),('preferences','region','S8 지역'),('preferences','notifications','S8 알림 설정'),('preferences','join','S8 가족 참여'),('preferences','weather','S8 테스트 날씨')]
 try:
  reset()
  for theme,mode in [('light','no'),('dark','yes')]:
   sh('cmd','uimode','night',mode);time.sleep(1.2);reset()
   for page,identity,label in pages:
    key=theme+'-'+page+('-'+identity if page=='preferences' else '')
    open_page(page,identity,weather='auto');top();capture(key,label,theme)
    if page in ['signup','editor','detail','weather','family']:
     end();capture(key+'-end',label+' · 아래 영역',theme,note='같은 화면을 아래로 스크롤한 상태')
    if page=='editor':
     reach('추가 정보 (선택)');tap(id='optional_toggle');end();capture(theme+'-editor-optional','S4 추가 정보 펼침',theme,'components')
   open_page('home',weather='auto',condition='rain');capture(theme+'-home-rain','S2 홈 날씨 제안',theme,'components')
   open_page('home',weather='auto');tap(id='sheet_handle');capture(theme+'-home-expanded','S2 펼친 패널·빠른 완료',theme,'components')
   sh('input','keyevent','4');capture(theme+'-home-hidden','S2 닫힌 패널·복귀',theme,'components')
   open_page('components',weather='auto');top();capture(theme+'-components','제안 · Suggested / Kept',theme,'components')
   for index in range(1,4):
    swipe_up();capture(theme+'-components-'+str(index),'컴포넌트 상태 · '+str(index),theme,'components')
  sh('cmd','uimode','night','no');reset()
  for state,label in [('empty','빈 목록'),('loading','불러오는 중'),('error','불러오기 오류'),('offline','오프라인')]:
   open_page('items',state=state,weather='auto');capture('state-'+state,label,'common','states')
  open_page('login',weather='auto');tap(id='auth_submit');capture('state-login-error','로그인 필드 오류','common','states')
  open_page('weather',state='offline',weather='auto');end();capture('state-weather-unavailable','날씨 판정 불가','common','states')
  for period,condition,label in [('morning','clear','아침'),('day','clear','낮'),('evening','clear','저녁'),('night','clear','밤'),('day','clouds','흐림'),('day','rain','비')]:
   open_page('home',weather=period,condition=condition);capture('photo-'+label,'날씨 사진 · '+label,'common','states')
 finally:
  sh('cmd','uimode','night','no');reset();open_page('home',weather='auto');capture('ready-home','기본 홈','light','states')
 for record in records:record['app_commit']=source
 # Optional, separately verified input states. Their capture source is preserved per image.
 extras=[('preset-confirm','입력 주기 변경 확인','light','76b6633'),('editor-optional-filled','선택 정보 입력 예시','light','76b6633'),('dark-editor-draft','다크 · 초안 유지','dark','76b6633'),('detail-metadata','저장한 제품명·메모','light','76b6633'),('metadata-edit','추가 정보 다시 수정','light','76b6633'),('completion-picker','실제 완료일 선택','light','69d2289'),('detail-selected-date','과거 완료일 선택','light','69d2289'),('actual-completed','실제 날짜로 완료·되돌리기','light','69d2289'),('quick-completed','홈 빠른 완료·되돌리기','light','76b6633'),('quick-undone','홈 완료 되돌림','light','76b6633')]
 for key,label,theme,sha in extras:
  if (OUT/(key+'.png')).exists():records.append(dict(id=key,label=label,theme=theme,group='components',note='별도 입력 검증 상태',file=key+'.png',app_commit=sha))
 manifest=dict(app_commit=source,font_scale='1.0',serial=DEVICE,records=records)
 (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 print('DONE',len(records),'actual screenshots; default Home restored.',flush=True)
