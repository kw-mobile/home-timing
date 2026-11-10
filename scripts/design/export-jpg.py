#!/usr/bin/env python3
"""Export approved screenshot boards and an optional individual-JPG ZIP on macOS.
Requires sips and rsvg-convert. Reads only the current capture manifest.
"""
import argparse,base64,html,json,math,shutil,subprocess,tempfile,zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--input',type=Path,default=Path('docs/report/evidence/component-system'))
parser.add_argument('--out',type=Path,default=Path('docs/design-jpg'))
parser.add_argument('--zip',type=Path)
args=parser.parse_args()
for command in ['sips','rsvg-convert']:
 if not shutil.which(command):parser.error(command+' is required')
manifest=json.loads((args.input/'manifest.json').read_text());records=manifest['records']
args.out.mkdir(parents=True,exist_ok=True)
def jpg(source,dest):
 subprocess.run(['sips','-s','format','jpeg','-s','formatOptions','92',str(source),'--out',str(dest)],check=True,stdout=subprocess.DEVNULL)
with tempfile.TemporaryDirectory(prefix='home-timing-jpg-') as directory:
 temporary=Path(directory)
 for theme in ['light','dark']:jpg(args.input/('overview-'+theme+'.png'),args.out/('overview-'+theme+'.jpg'))
 for key,title,bg,fg,muted in [('light','Light screens','#f2f2f4','#1c1c1e','#66666d'),('dark','Dark screens','#09090b','#f5f5f7','#acacb4'),('common','Shared states & weather','#f2f2f4','#1c1c1e','#66666d')]:
  selected=[r for r in records if r['theme']==key]
  for page,start in enumerate(range(0,len(selected),20),1):
   batch=selected[start:start+20];rows=math.ceil(len(batch)/4);height=130+rows*785
   svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="{height}"><rect width="1440" height="{height}" fill="{bg}"/><text x="32" y="42" font-family="sans-serif" font-size="26" font-weight="600" fill="{fg}">Home Timing · {html.escape(title)} · {page}</text><text x="32" y="70" font-family="sans-serif" font-size="14" fill="{muted}">Approved Android design · Actual screenshots · fontScale 1.0</text>'
   for index,record in enumerate(batch):
    x=32+(index%4)*352;y=120+(index//4)*785
    encoded=base64.b64encode((args.input/record['file']).read_bytes()).decode()
    svg+=f'<text x="{x}" y="{y-15}" font-family="sans-serif" font-size="16" fill="{fg}">{html.escape(record["label"])}</text><image x="{x}" y="{y}" width="320" height="711.111" href="data:image/png;base64,{encoded}"/><text x="{x}" y="{y+735}" font-family="sans-serif" font-size="12" fill="{muted}">{html.escape(record["id"])}</text>'
   svg+=f'<text x="32" y="{height-16}" font-family="sans-serif" font-size="13" fill="{muted}">Sample data · Services not connected · Shared states use light-mode captures</text></svg>'
   source=temporary/'board.svg';source.write_text(svg);png=temporary/'board.png'
   subprocess.run(['rsvg-convert',str(source),'-o',str(png)],check=True)
   jpg(png,args.out/f'all-{key}-{page:02}.jpg')
 if args.zip:
  individual=temporary/'screens';individual.mkdir()
  def convert(record):jpg(args.input/record['file'],individual/(record['id']+'.jpg'))
  with ThreadPoolExecutor(max_workers=4) as executor:list(executor.map(convert,records))
  args.zip.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(args.zip,'w',zipfile.ZIP_DEFLATED) as package:
   for image in sorted(args.out.glob('*.jpg')):package.write(image,'boards/'+image.name)
   for image in sorted(individual.glob('*.jpg')):package.write(image,'screens/'+image.name)
   index='때맞춤 확정 디자인 JPG\n\nboards: 요약 보드 및 페이지별 전체 화면\nscreens: 원본 해상도 개별 캡처 79장\n공통 상태/날씨는 라이트 캡처입니다. 실제 서비스는 연결하지 않았습니다.\n\n'
   index+='\n'.join(r['id']+'.jpg | '+r['label'] for r in records)
   package.writestr('index.txt',index)
print(str(args.out)+': '+str(len(list(args.out.glob('*.jpg'))))+' JPG boards; '+str(len(records))+' individual JPGs in ZIP' if args.zip else str(args.out))
