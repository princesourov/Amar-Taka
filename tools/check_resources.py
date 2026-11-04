import re,glob,os,sys
root=sys.argv[1]
res=os.path.join(root,'res')
kt=glob.glob(root+'/java/**/*.kt',recursive=True)
xmls=glob.glob(res+'/**/*.xml',recursive=True)
names={'string':set(),'color':set(),'dimen':set(),'style':set(),'drawable':set(),'layout':set(),'id':set(),'menu':set(),'xml':set(),'navigation':set(),'mipmap':set(),'plurals':set(),'array':set(),'attr':set()}
for f in xmls:
    t=open(f,encoding='utf8').read(); d=os.path.basename(os.path.dirname(f)); base=os.path.basename(f)[:-4]
    if d.startswith('values'):
        for kind,n in re.findall(r'<(string|color|dimen|style|plurals|string-array|attr)\s+name="([^"]+)"',t):
            names['array' if kind=='string-array' else kind].add(n.replace('.','_'))
    else:
        k=d.split('-')[0]
        if k in names: names[k].add(base)
    for n in re.findall(r'@\+id/(\w+)',t): names['id'].add(n)
for f in glob.glob(res+'/drawable*/*')+glob.glob(res+'/mipmap*/*'):
    names['drawable'].add(os.path.splitext(os.path.basename(f))[0]); names['mipmap'].add(os.path.splitext(os.path.basename(f))[0])
for n in ['layout','navigation']: pass
errs=[]
def chk(kind,name,where):
    if name not in names[kind]: errs.append(f'{where}: missing {kind} {name}')
# Kotlin refs
for f in kt:
    t=open(f,encoding='utf8').read(); w=os.path.relpath(f,root)
    for k in ['string','color','dimen','drawable','menu','layout','plurals','array','xml','mipmap']:
        for n in set(re.findall(r'\bR\.'+k+r'\.(\w+)',t)): chk(k,n,w)
    for n in set(re.findall(r'\bR\.id\.(\w+)',t)): chk('id',n,w)
# XML refs
for f in xmls:
    t=open(f,encoding='utf8').read(); w=os.path.relpath(f,root)
    for k,n in re.findall(r'@(string|color|dimen|drawable|mipmap|layout|menu|xml|style)/([\w.]+)',t):
        if k=='style': k='style'
        chk(k,n.replace('.','_'),w)
    for n in re.findall(r'@id/(\w+)',t): chk('id',n,w)
    for n in re.findall(r'@navigation/(\w+)',t): chk('navigation',n,w)
# ViewBinding field check
def camel(s):
    p=s.split('_'); return p[0]+''.join(x.title() if x.islower() else x[:1].upper()+x[1:] for x in p[1:])
def bind_class(layout):
    return ''.join(x[:1].upper()+x[1:] for x in layout.split('_'))+'Binding'
layout_ids={}
for f in glob.glob(res+'/layout*/*.xml'):
    base=os.path.basename(f)[:-4]
    ids=re.findall(r'android:id="@\+id/(\w+)"',open(f,encoding='utf8').read())
    layout_ids.setdefault(bind_class(base),set()).update(camel(i) if '_' in i else i for i in ids)
for f in kt:
    t=open(f,encoding='utf8').read(); w=os.path.relpath(f,root)
    used=set(re.findall(r'import com\.hisab\.app\.databinding\.(\w+Binding)',t))
    if not used: continue
    for c in used:
        if c not in layout_ids: errs.append(f'{w}: binding class {c} has no layout')
    allowed=set().union(*[layout_ids.get(c,set()) for c in used])|{'root'}
    for var,field in set(re.findall(r'\b(binding|_binding|b|row|d)\.(\w+)',t)):
        if field in allowed: continue
        # skip common non-binding uses
        errs.append(f'{w}: {var}.{field} not an id in {sorted(used)}') if (var in('binding','_binding') or var in('row','d') or (var=='b' and 'Item' in ''.join(used))) and field not in('root',) else None
# nav action/destination ids used in Kotlin exist in nav graph
for e in sorted(set(errs)): print(e)
print(len(set(errs)),'problems')
