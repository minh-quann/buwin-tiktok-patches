"""Pinned Kyant/Compose runtime inputs for the native bottom-tabs extension.

The runtime is repackaged by R8 so TikTok's obfuscated Compose never supplies its ABI.
Only tag IDs and strings used by this embedded component are provided by its Context.
"""
from pathlib import Path
from zipfile import ZipFile
import re, xml.etree.ElementTree as ET, json

CACHE = Path.home()/'.gradle/caches/modules-2/files-2.1'
COMPOSE_VERSION = '1.12.0'

def version_key(s): return tuple(int(x) for x in re.findall(r'\d+',s))
def inputs(work, android, run):
    jars=[]; resources={}; seen_classes=set(); r_packages={}; strings={}; ids={}; next_id=0x7e010000
    groups=['androidx.compose.runtime','androidx.compose.ui','androidx.compose.foundation',
      'androidx.compose.animation','androidx.collection','androidx.lifecycle','androidx.savedstate',
      'androidx.core','androidx.interpolator','androidx.window','androidx.customview','androidx.emoji2','androidx.tracing','androidx.graphics',
      'androidx.arch.core','androidx.concurrent','androidx.versionedparcelable','androidx.annotation']
    picks=[]
    for group in groups:
        for art in sorted((CACHE/group).glob('*')):
            if group=='androidx.window' and art.name not in ['window','window-android','window-core','window-core-android']:continue
            if any(x in art.name for x in ['test','tooling','lint','samples','compiler','livedata','service','process','extensions','ripple']):continue
            if (art.parent/(art.name+'-android')).is_dir() or (art.parent/(art.name+'-jvm')).is_dir():continue
            if group.startswith('androidx.compose.'):
                ver=COMPOSE_VERSION
                if not (art/ver).is_dir():continue
            else:
                versions=[v for v in art.iterdir() if v.is_dir() and not any(x in v.name for x in ['alpha','beta','rc','SNAPSHOT'])]
                if not versions:continue
                ver=max(versions,key=lambda v:version_key(v.name)).name
            files=[p for p in (art/ver).glob('*/*') if p.suffix in ['.aar','.jar'] and not any(x in p.name for x in ['sources','javadoc','lint','samples'])]
            if files:picks.append((group,art.name,ver,files[0]))
    for art,ver in [('backdrop-android','2.0.1'),('shapes-android','1.2.1'),('capsule','2.1.1')]:
        picks.append(('io.github.kyant0',art,ver,next((CACHE/'io.github.kyant0'/art/ver).glob('*/*.aar'))))
    for art in ['kotlin-stdlib','kotlin-stdlib-jdk7','kotlin-stdlib-jdk8']:
        fs=[p for p in (CACHE/'org.jetbrains.kotlin'/art/'2.4.10').glob('*/*.jar') if not any(x in p.name for x in ['sources','javadoc'])]
        if fs:picks.append(('org.jetbrains.kotlin',art,'2.4.10',fs[0]))
    for art in ['kotlinx-coroutines-core-jvm','kotlinx-coroutines-android']:
        versions=[v for v in (CACHE/'org.jetbrains.kotlinx'/art).iterdir() if v.is_dir()]
        ver=max(versions,key=lambda v:version_key(v.name))
        p=next(p for p in ver.glob('*/*.jar') if not any(x in p.name for x in ['sources','javadoc']))
        picks.append(('org.jetbrains.kotlinx',art,ver.name,p))
    sources=work/'runtime-resources';sources.mkdir()
    for group,art,ver,p in picks:
        target=work/(group+'-'+art+'.jar')
        if p.suffix=='.aar':
            with ZipFile(p) as z:
                if 'classes.jar' not in z.namelist():continue
                target.write_bytes(z.read('classes.jar'))
                manifest=ET.fromstring(z.read('AndroidManifest.xml'))
                package=manifest.attrib.get('package',group+'.'+art.replace('-android','').replace('-','.'))
                fields=r_packages.setdefault(package,{})
                if 'R.txt' in z.namelist():
                    for line in z.read('R.txt').decode().splitlines():
                        parts=line.split(' ',3)
                        if len(parts)!=4:continue
                        typ,kind,name,value=parts
                        if typ=='int[]': fields[(kind,name)]=(typ,value);continue
                        key=(package,kind,name)
                        if kind=='id' or value=='0x0':
                            if key not in ids:ids[key]=next_id;next_id+=1
                            value=hex(ids[key])
                        fields[(kind,name)]=(typ,value)
                for name in z.namelist():
                    if name.startswith('res/values/') and name.endswith('.xml'):
                        try:
                            for item in ET.fromstring(z.read(name)):
                                if item.tag=='string':
                                    key=(package,'string',item.attrib['name'])
                                    strings[key]=''.join(item.itertext()).replace('\\n','\n').replace("\\'", "'")
                        except ET.ParseError:pass
        else:target.write_bytes(p.read_bytes())
        # Prefer the platform-specific artifact; never pass duplicate classes to R8.
        filtered=work/(group+'-'+art+'-program.jar')
        with ZipFile(target) as original,ZipFile(filtered,'w') as out:
            for n in original.namelist():
                if n.endswith(('.kotlin_module','.kotlin_builtins')):
                    out.writestr(n,original.read(n));continue
                if not n.endswith('.class') or n=='module-info.class' or n in seen_classes:continue
                seen_classes.add(n);out.writestr(n,original.read(n))
        jars.append(filtered)
    for package,fields in r_packages.items():
        lines=['package '+package+';','public final class R {']
        for kind in sorted(set(k for k,n in fields)):
            lines.append('public static final class '+kind+' {')
            for (k,n),(typ,value) in fields.items():
                if k==kind:lines.append('public static final '+typ+' '+n+' = '+value+';')
            lines.append('}')
        lines.append('}')
        p=sources/Path(package.replace('.','/'))/'R.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text('\n'.join(lines))
    table=sources/'app/morphe/extension/tiktok/glass/resources/GlassResourceTable.java';table.parent.mkdir(parents=True,exist_ok=True)
    lines=['package app.morphe.extension.tiktok.glass.resources;','public final class GlassResourceTable {','public static String text(int id) { switch(id) {']
    for key,value in strings.items():
        if key in ids and not value.startswith('@'):lines.append('case '+hex(ids[key])+': return '+json.dumps(value,ensure_ascii=True)+';')
    lines+=['default:return null;}}','}'];table.write_text('\n'.join(lines))
    classes=work/'resource-classes';classes.mkdir()
    run(['javac','-source','17','-target','17','-cp',android,'-d',classes,*sources.rglob('*.java')])
    resource_jar=work/'resource-ids.jar'
    with ZipFile(resource_jar,'w') as z:
        for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    jars.append(resource_jar)
    (work/'runtime-inputs.json').write_text(json.dumps([(g,a,v,str(p)) for g,a,v,p in picks],indent=2))
    return jars
