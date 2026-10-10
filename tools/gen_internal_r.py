#!/usr/bin/env python3
"""Generates java/framework/com/android/internal/R.java, the subset of AOSP's com.android.internal.R
that ported framework code uses. Ids resolve by name at class load (InternalRes); styleables are listed
in STY below with their attributes in index order. Run from java/framework:
    cd java/framework && python3 ../../tools/gen_internal_r.py $(grep -rl 'com.android.internal.R\b' --include=*.java .)
"""
import re,glob,sys
files=sys.argv[1:]
refs={}
for f in files:
    s=open(f).read()
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S)
    s=re.sub(r'//[^\n]*','',s)
    imp='import com.android.internal.R;' in s
    for m in re.finditer(r'(?<![\w.])(com\.android\.internal\.)?R\.(\w+)\.(\w+)',s):
        if not m.group(1) and not imp: continue
        refs.setdefault(m.group(2),set()).add(m.group(3))
STY={
 'CheckBoxPreference':['summaryOn','summaryOff','disableDependentsState'],
 'DialogPreference':['dialogTitle','dialogMessage','dialogIcon','positiveButtonText','negativeButtonText','dialogLayout'],
 'FragmentBreadCrumbs':['gravity','itemLayout','itemColor'],
 'ListPreference':['entries','entryValues'],
 'MultiSelectListPreference':['entries','entryValues'],
 'Preference':['icon','persistent','enabled','layout','title','selectable','key','summary','order','widgetLayout','dependency','defaultValue','shouldDisableView','fragment','singleLineTitle','iconSpaceReserved','recycleEnabled'],
 'PreferenceActivity':['layout','headerLayout','headerRemoveIconIfEmpty'],
 'PreferenceFragment':['layout','divider'],
 'PreferenceFrameLayout':['borderTop','borderBottom','borderLeft','borderRight'],
 'PreferenceFrameLayout_Layout':['layout_removeBorders'],
 'PreferenceGroup':['orderingFromXml'],
 'PreferenceHeader':['id','title','summary','breadCrumbTitle','breadCrumbShortTitle','icon','fragment'],
 'PreferenceScreen':['screenLayout','divider'],
 'ProgressBar':['max'],
 'RingtonePreference':['ringtoneType','showDefault','showSilent'],
 'SeekBarPreference':['layout'],
 'SwitchPreference':['summaryOn','summaryOff','switchTextOn','switchTextOff','disableDependentsState'],
}
used=refs.pop('styleable',set())
for u in used:
    if u in STY: continue
    assert [k for k in STY if u.startswith(k+'_') and u[len(k)+1:] in STY[k]], u
out=['package com.android.internal;','','import com.android.internal.util.InternalRes;','',
'/**',' * framework-internal. The subset of AOSP\'s com.android.internal.R that ported framework code uses.',
' * Ids are looked up by name in framework-res when the class loads (0 when absent); styleables are',
' * attribute arrays with their index constants. Regenerate when porting more AOSP code (the script',
' * lists the files that use it; see docs/ARCHITECTURE.md).',' */',
'public final class R {','    private R() {}','']
for typ in sorted(refs):
    out.append(f'    public static final class {typ} {{')
    for n in sorted(refs[typ]):
        if typ=='attr': out.append(f'        public static final int {n} = InternalRes.attr("{n}");')
        else: out.append(f'        public static final int {n} = InternalRes.id("{typ}", "{n}");')
    out.append('    }'); out.append('')
out.append('    public static final class styleable {')
for k in sorted(STY):
    names=', '.join('"%s"'%a for a in STY[k])
    out.append(f'        public static final int[] {k} = InternalRes.attrs({names});')
    for i,a in enumerate(STY[k]):
        out.append(f'        public static final int {k}_{a} = {i};')
out.append('    }'); out.append('}')
open('com/android/internal/R.java','w').write('\n'.join(out)+'\n')
