"""Bounded AOSP Android16 QPR2 processes-proto subset; NOT vendor qualification.

Schema pins and emitter contracts: docs/ANDROID-DIAGNOSTIC-POLICY.md.
frameworks/base: 45034f0663f960d9ee5fb0a101a4732b71f6e2f4;
frameworks/proto_logging: cef5253cee341d943c2972775e047ca368e9b1ee.
Root1/2 anchor PID/name/UID; root3 TARGET info identifies instrumentation.
Never retains instrumentation arguments, foreign payloads or runner components.
"""
import hashlib
import math
import struct
PARSER='aosp-android16-qpr2-am-processes-v2'
MAX_BYTES=1024*1024

def fields(data):
    if not isinstance(data,bytes) or len(data)>MAX_BYTES:raise ValueError('bounded protobuf required')
    at=0;result={}
    def integer():
        nonlocal at
        value=0
        for shift in range(0,70,7):
            if at>=len(data):raise ValueError('truncated protobuf')
            byte=data[at];at+=1;value|=(byte&127)<<shift
            if not byte&128:
                if value>2**64-1:raise ValueError('protobuf integer overflow')
                return value
        raise ValueError('protobuf varint too long')
    while at<len(data):
        key=integer();number,wire=key>>3,key&7
        if not 1<=number<2**29:raise ValueError('invalid protobuf tag')
        if wire==0:value=integer()
        elif wire in (1,2,5):
            size=integer() if wire==2 else (8 if wire==1 else 4)
            if size>len(data)-at:raise ValueError('truncated protobuf field')
            value=data[at:at+size];at+=size
        else:raise ValueError('unsupported protobuf wire')
        result.setdefault(number,[]).append((wire,value))
        if sum(map(len,result.values()))>16384:raise ValueError('protobuf field bound')
    return result

def one(message,number,wire,default=None):
    values=message.get(number,[])
    if not values:return default
    if len(values)!=1 or values[0][0]!=wire:raise ValueError('ambiguous protobuf scalar')
    return values[0][1]

def string(message,number,required=False):
    value=one(message,number,2)
    if value is None:
        if required:raise ValueError('required protobuf string absent')
        return None
    if not value or len(value)>1024:raise ValueError('invalid bounded identity')
    try:return value.decode('utf-8',errors='strict')
    except UnicodeError as ex:raise ValueError('malformed identity') from ex

# Source-backed subset: pinned QPR2 activitymanagerservice.proto (see module
# docstring), with unchanged android-16.0.0_r1 content/package_item_info.proto,
# content/component_name.proto and os/bundle.proto. Unsupported fields reject;
# there is deliberately no opaque-message or unknown-field skip for absence.
PROCESS = {1:'i32',2:'str',3:'i32',4:'i32',5:'i32',6:'i32',7:'bool',8:'i32'}
PACKAGE_ITEM = {1:'str',2:'str',3:'i32',4:'str',5:'i32',6:'i32',7:'bool'}
APP_VERSION = {1:'bool',2:'i32',3:'i32',4:'i32',5:'i32'}
APP_DETAIL = {n:'str' for n in (1,2,6,7,8,9,11,15)}
APP_DETAIL.update({n:'i32' for n in (3,4,5,12,13,17,18,19,20,23)})
APP_DETAIL.update({n:'bool' for n in (14,16,21,22)})
APP_DETAIL[10]=('repeated','str')
APPLICATION = {1:PACKAGE_ITEM,2:'str',3:'str',4:'i32',5:'i32',6:'i32',7:'i32',
               8:'str',9:'str',10:('repeated','str'),11:('repeated','str'),
               12:('repeated','str'),13:'str',14:'str',15:('repeated','str'),
               16:APP_VERSION,17:APP_DETAIL,18:('repeated','str'),19:('repeated','str')}
INSTRUMENTATION = {1:{1:'str',2:'str'},2:'bool',3:('repeated',PROCESS),
                   4:('repeated','str'),5:APPLICATION,6:'str',7:'str',8:'str',
                   10:{1:'i32',2:'str'}}
# Android16 ProcessStateEnum is NOT ActivityManager's 0..20. Explicit
# UNKNOWN_TO_PROTO (998) / UNKNOWN (999) cannot establish a known status.
PROCESS_STATES = frozenset(range(1000,1021))
DURATION = {1:'i64',2:'i64'}
UID_RECORD = {1:'i32',2:PROCESS_STATES,3:'bool',4:'bool',5:'bool',
              6:DURATION,7:'bool',8:('repeated',frozenset(range(8))),9:'i32',
              10:{1:'i64',2:'i64',3:'i64'}}
OOM_CPU = {1:'i64',2:'i64',3:'float'}
OOM_DETAIL = {n:'i32' for n in (1,2,3,4,5)}
# QPR2 ProcessCapabilityEnum: UNKNOWN=0 is not a known capability.
PROCESS_CAPABILITIES = frozenset({1,2,3,4,5,6,7,8,9})
OOM_DETAIL[16]=('repeated',PROCESS_CAPABILITIES)
OOM_DETAIL.update({7:PROCESS_STATES,8:PROCESS_STATES,9:'str',10:'str',11:'str',
                   12:'bool',13:'bool',14:'bool',15:OOM_CPU})
PROCESS_OOM = {1:'bool',2:'i32',3:'str',4:frozenset(range(4)),5:'bool',6:'bool',
               7:PROCESS_STATES,8:'i32',9:PROCESS,10:'str',11:{1:'str',2:'str'},
               12:'str',13:PROCESS,14:'str',15:OOM_DETAIL}
LRU = {1:'i32',2:'i32',3:'i32',4:('repeated',PROCESS_OOM)}
# Only a fully validated supported root can establish absence. Complex root
# sections not implemented here remain unavailable, never silently ignored.
ROOT_SCHEMA = {1:('repeated',PROCESS),2:('repeated',PROCESS),3:('repeated',INSTRUMENTATION)}
ROOT_SCHEMA.update({n:('repeated',UID_RECORD) for n in (4,5)})
ROOT_SCHEMA[6]=LRU
ROOT_SCHEMA.update({n:('repeated',PROCESS) for n in (7,9,10,11)})
ROOT_SCHEMA.update({n:PROCESS for n in (15,16,18)})
ROOT_SCHEMA.update({n:'bool' for n in (21,36,39,40,41,43,44,45,55)})
ROOT_SCHEMA.update({n:'i32' for n in (38,42,49,50,51,52,53,54,56,57)})
ROOT_SCHEMA.update({n:'i64' for n in (17,46,59)})
ROOT_SCHEMA.update({n:'str' for n in (33,35)})
ROOT_SCHEMA.update({24:('repeated','i32'),25:('repeated','i32'),58:{1:'i64',2:'i64'}})


def validate_message(data, schema):
    message=fields(data)
    if any(n not in schema for n in message):raise ValueError('unknown/unsupported Android16 nested schema')
    for number,values in message.items():
        kind=schema[number];repeated=isinstance(kind,tuple)
        if repeated:kind=kind[1]
        if not repeated and len(values)!=1:raise ValueError('duplicate protobuf scalar/message')
        for wire,value in values:
            expected=2 if isinstance(kind,dict) or kind=='str' else (5 if kind=='float' else 0)
            if wire!=expected:raise ValueError('wrong Android16 field wire')
            if isinstance(kind,dict):validate_message(value,kind)
            elif isinstance(kind,frozenset) and value not in kind:raise ValueError('unknown Android16 enum status')
            elif kind=='float' and not math.isfinite(struct.unpack('<f',value)[0]):raise ValueError('nonfinite CPU utilization')
            elif kind=='bool' and value not in (0,1):raise ValueError('unknown boolean status')
            elif kind=='i32' and not (value<2**31 or 2**64-2**31<=value<2**64):raise ValueError('invalid int32')
            elif kind=='str':
                if len(value)>65536:raise ValueError('bounded protobuf string required')
                try:value.decode('utf-8',errors='strict')
                except UnicodeError as ex:raise ValueError('malformed protobuf string') from ex
    if schema is OOM_DETAIL:
        flags=message.get(16,[])
        if len(set(flags))!=len(flags):raise ValueError('duplicate process capability flag')
    if schema is PROCESS_OOM:
        for a,b in ((5,6),(11,12),(13,14)):
            if a in message and b in message:raise ValueError('ambiguous ProcessOom oneof')
    if schema is APP_DETAIL and 15 in message and 16 in message:raise ValueError('ambiguous backup oneof')
    if schema is INSTRUMENTATION:
        args=one(message,10,2)
        if args is not None and len(fields(args))>1:raise ValueError('ambiguous Bundle oneof')
    return message


def uid_record(data):
    """Validate one UID projection; never a PID/name presence anchor.

    ActiveUids and ValidateUids are independently maintained (ProcessList and
    UidObserverController). Do not equate their optional fields, times or states.
    """
    record=validate_message(data,UID_RECORD);uid=one(record,1,0)
    if type(uid) is not int or not 0<uid<2**31:raise ValueError('UID record identity absent')
    count=one(record,9,0)
    if count is not None and not 0<=count<2**31:raise ValueError('invalid UID process count')
    changes=record.get(8,[])
    if len(set(changes))!=len(changes):raise ValueError('duplicate UID change')
    return uid


def process(data):
    p=validate_message(data,PROCESS);pid=one(p,1,0);name=string(p,2,True);uid=one(p,3,0)
    if type(pid) is not int or not 0<pid<2**31 or type(uid) is not int or not 0<uid<2**31:raise ValueError('process anchor missing')
    return pid,name,uid


def messages(root,number):
    for wire,data in root.get(number,[]):
        if wire!=2:raise ValueError('wrong message wire')
        yield data


def parse_processes(raw,package,pid,uid):
    if not raw or not isinstance(package,str) or not package or type(pid) is not int or type(uid) is not int:raise ValueError('identity/protobuf unavailable')
    root=validate_message(raw,ROOT_SCHEMA)
    # UID collections are keyed by UID, not filtered process-list counts. They
    # cannot provide a target anchor or overwrite a ProcessRecord identity.
    for number in (4,5):
        seen_uids=set()
        for data in messages(root,number):
            key=uid_record(data)
            if key in seen_uids:raise ValueError('duplicate UID record')
            seen_uids.add(key)
    identities={};active=False;seen=set()
    def reconcile(p):
        if p[1]==package and p!=(pid,package,uid):raise ValueError('conflicting target process identity')
        if p[0] in identities and identities[p[0]]!=p:raise ValueError('conflicting process PID identity')
        identities[p[0]]=p
    # AOSP repeats the same process in lists and singleton references. Reconcile
    # identity globally, while retaining root1/2 as the target presence anchor.
    anchors=set()
    for n in (1,2,7,9,10,11,15,16,18):
        for data in messages(root,n):
            p=process(data);reconcile(p)
            if n in (1,2):anchors.add(p)
    for data in messages(root,6):
        for entry in messages(fields(data),4):
            item=fields(entry)
            for number in (9,13):
                for reference in messages(item,number):reconcile(process(reference))
    if [p for p in anchors if p[1]==package]!=[(pid,package,uid)]:raise ValueError('unique target process/PID/UID anchor absent')
    for data in messages(root,3):
        item=fields(data)
        app=one(item,5,2)
        if app is None:raise ValueError('instrumentation target info unknown')
        app=fields(app);package_info=one(app,1,2)
        if package_info is None:raise ValueError('instrumentation target package unknown')
        target=string(fields(package_info),2,True)
        running=[process(data) for data in messages(item,3)]
        for p in running:reconcile(p)
        targets=[value.decode('utf-8',errors='strict') for _,value in item.get(4,[])]
        if any(not value or len(value)>1024 for value in targets) or len(set(targets))!=len(targets):raise ValueError('ambiguous target processes')
        # Canonical semantic identity rejects reordered duplicate records too.
        component=one(item,1,2)
        component=tuple(sorted((n,tuple(v)) for n,v in fields(component).items())) if component is not None else None
        key=(component,target,tuple(sorted(running)),tuple(sorted(targets)))
        if key in seen:raise ValueError('duplicate instrumentation record')
        seen.add(key)
        # Finished records still in OS active collection conservatively block.
        if target==package or package in targets or any(p[1]==package or p[0]==pid for p in running):active=True
    return dict(parser=PARSER,package=package,pid=pid,uid=uid,targetActive=active,
                input_sha256=hashlib.sha256(raw).hexdigest(),input_size_bytes=len(raw))


def require(condition,message):
    if not condition:raise ValueError(message)

def integer(value,minimum=0):
    return type(value) is int and minimum<=value<=2**63-1

def digest(value):
    import re
    return isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value) is not None

def interval(record):
    start,end=record['host_start_ns'],record['host_end_ns']
    require(integer(start) and integer(end) and start<=end and end-start<=5_000_000_000,'invalid bounded host read interval')
    return start,end

def validate_observation(record,package,active,build):
    require(type(record['schema_version']) is int and record['schema_version']==1 and record['parser']==PARSER,'unknown OS parser/schema')
    require(type(record['android_api']) is int and record['android_api']==36 and record['android_build_sha256']==build and digest(build),'unqualified Android version/build')
    require(record['command']==['dumpsys','activity','--proto','processes',package],'unsupported OS command')
    require(type(record['exit_code']) is int and record['exit_code']==0 and record['stderr_empty'] is True and record['truncated'] is False,'unsuccessful/truncated OS command')
    require(record['targetActive'] is active,'OS instrumentation active or unknown')
    require(record['package']==package and integer(record['pid'],1) and integer(record['uid'],1) and integer(record['start_time_ticks'],1),'OS target process identity absent')
    import uuid
    try:uuid.UUID(record['process_instance'])
    except (ValueError,TypeError,AttributeError):raise ValueError('process instance absent')
    require(digest(record['input_sha256']) and integer(record['input_size_bytes'],1) and record['input_size_bytes']<=MAX_BYTES,'OS input hash/size absent')
    start,end=interval(record)
    identity=tuple(record[k] for k in ('pid','uid','start_time_ticks'))
    before,after=record['identity_before'],record['identity_after']
    b0,b1=interval(before);a0,a1=interval(after)
    require(start<=b0<=b1<=a0<=a1<=end,'OS process observations not bracketed')
    require(all(type(p[k]) is int and p[k]==record[k] for p in (before,after) for k in ('pid','uid','start_time_ticks')),'OS PID/starttime/UID changed')
    return identity+(record['process_instance'],)

def validate_graph(graph, exported, watches, intake=None, session=None):
    """Bind the complete completed-draw copy to preparation, intake and native QA.

    Draw/prepared statistics must be identical at one revision. The worker intake
    may have advanced since preparation, but must name the same complete selected
    series set and the exact native session/reader, never a mutable export label.
    """
    try:
        observed=graph['observedMonotonicNs'];draw=graph['lastDrawMonotonicNs'];age=graph['drawAgeNs']
        require(graph['chartFullyVisible'] is True and graph['paused'] is False and graph['drawPending'] is False,
                'graph workload not freshly drawn/visible')
        require(integer(observed,1) and observed==exported and integer(draw,1) and draw<=observed
                and integer(age) and age==observed-draw and age<=2_000_000_000,'graph draw/observation age incoherent')
        require(integer(graph['preparedRevision'],1) and type(graph['drawnRevision']) is int
                and graph['drawnRevision']==graph['preparedRevision'],'graph prepared/drawn revision mismatch')
        drawn,prepared=graph['drawnSeries'],graph['series']
        require(isinstance(drawn,list) and 1<=len(drawn)<=4 and isinstance(prepared,list)
                and len(drawn)==len(prepared),'graph finite drawn/prepared series absent')
        require(isinstance(intake,dict) and integer(session,1),'graph native intake/session unavailable')
        for key,value in (('capacityPerSeries',600),('windowNs',30_000_000_000),('maximumSeries',4)):
            require(type(intake[key]) is int and intake[key]==value,'graph intake budget/schema mismatch')
        require(integer(intake['invalidationRevision']),'graph intake revision invalid')
        rows=intake['series']
        require(isinstance(rows,list) and len(rows)==len(drawn),'graph selected intake association incomplete')
        identity_keys=('seriesId','fieldPath','slot','sessionGeneration','readerGeneration')
        keys=set(identity_keys)|{'seriesIndex','pointCount','finitePoints','gapPoints','breakBeforePoints','oldestReceiveNs','newestReceiveNs'}
        def validate(rows,latest):
            identities=[]
            for i,s in enumerate(rows):
                require(isinstance(s,dict) and set(s)==keys,'graph series schema mismatch')
                require(type(s['seriesIndex']) is int and s['seriesIndex']==i and integer(s['slot']) and s['slot']<4,
                        'graph slot/index association invalid')
                w=watches[s['slot']]
                require(type(w['slot']) is int and w['slot']==s['slot'] and w['readerActive'] is True
                        and integer(w['readerGeneration'],1) and integer(s['readerGeneration'],1)
                        and s['readerGeneration']==w['readerGeneration'] and integer(s['sessionGeneration'],1)
                        and s['sessionGeneration']==session,'graph native session/reader association mismatch')
                name,path=s['seriesId'],s['fieldPath']
                require(isinstance(name,str) and 1<=len(name)<=1024 and isinstance(path,str) and 1<=len(path)<=256
                        and name.startswith('Watch '+str(s['slot']+1)+' · ') and name.endswith('\n'+path)
                        and name.count('\n')==1,'graph selected series/field identity invalid')
                require(all(integer(s[k]) for k in ('pointCount','finitePoints','gapPoints','breakBeforePoints')),
                        'graph statistics require exact integers')
                require(integer(w['count']) and 1<=s['finitePoints']<=s['pointCount']<=min(600,w['count'])
                        and s['finitePoints']+s['gapPoints']==s['pointCount'] and s['breakBeforePoints']<=s['pointCount'],
                        'graph finite point/count bounds invalid')
                oldest,newest=s['oldestReceiveNs'],s['newestReceiveNs']
                require(integer(oldest,1) and integer(newest,1) and oldest<=newest<=latest
                        and (newest==oldest if s['pointCount']==1 else newest-oldest>=s['pointCount']-1)
                        and newest-oldest<=30_000_000_000 and observed-newest<=2_000_000_000,
                        'graph receive timestamps unavailable/stale/incoherent')
                identities.append(tuple(s[k] for k in identity_keys))
            require(len(set(identities))==len(rows) and len({s['seriesId'] for s in rows})==len(rows),
                    'graph duplicate selected series identity')
            return identities
        a=validate(drawn,draw);b=validate(prepared,draw);c=validate(rows,observed)
        require(drawn==prepared and a==b==c,'graph drawn/prepared/intake association mismatch')
        require(all(current['newestReceiveNs']>=old['newestReceiveNs'] for old,current in zip(drawn,rows)),
                'graph draw newer than native intake')
    except (KeyError,TypeError,AttributeError,IndexError) as error:
        raise ValueError('graph drawn series structure missing/malformed') from error


def validate_campaign(data,metadata):
    require(type(data['schema_version']) is int and data['schema_version']==2,'diagnostic soak requires schema2')
    qualification=data['os_transport_validation'];build=qualification['android_build_sha256'];package=metadata['package']
    require(qualification['parser']==PARSER and type(qualification['android_api']) is int and qualification['android_api']==36,'transport version unsupported')
    require(qualification['physical_device_verified'] is True and isinstance(qualification['qualified_by'],str) and qualification['qualified_by'].strip() and digest(qualification['evidence_sha256']),'physical OS positive/negative transport qualification UNVERIFIED')
    validate_observation(qualification['negative_control'],package,False,build)
    positive=qualification['positive_control']
    validate_observation(positive['os'],package,True,build)
    require(positive['app_runner_active'] is False and positive['app_lease_phase']=='INACTIVE' and positive['collection_refused'] is True,'OS-active positive control without app lease required')
    identity=None;last_sequence=-1;last_capture=-1;last_export=-1;last_fence=-1;last_after=-1;stable=None
    for sample in data['samples']:
        proof=sample['diagnostic_proof'];before,after=proof['os_before'],proof['os_after']
        a=validate_observation(before,package,False,build);b=validate_observation(after,package,False,build)
        if identity is None:identity=a
        require(a==b==identity and a[0]==data['pid'],'OS sample process identity changed')
        b0,b1=interval(before);f0,f1=interval(proof['fence_read']);c0,c1=interval(proof['capture_read']);a0,a1=interval(after)
        require(last_after<b0<=b1<=f0<=f1<=c0<=c1<=a0<=a1 and a1-b0<=5_000_000_000,'OS-before/fence/fresh-capture/OS-after deadline or ordering')
        require(type(sample['monotonic_s']) in (int,float) and b0<=sample['monotonic_s']*1e9<=a1,'sample time not inside causal bracket')
        last_after=a1
        d=proof['diagnostic'];fence=proof['export_fence_ns']
        require(type(d['schemaVersion']) is int and d['schemaVersion']==2 and d['valid'] is True and d['capability'] is True and d['contract']==metadata['diagnostic_policy'],'unknown/mixed native diagnostic contract')
        require(all(d[k] is False for k in ('runnerActive','armed','ticketPending','operationInFlight')) and d['leasePhase']=='INACTIVE','app diagnostic work active or unknown')
        require(all(integer(d[k]) for k in ('leaseEpoch','revision','nativeRevision','captureSequence','capturedMonotonicNs','exportedMonotonicNs','sessionGeneration','lifecycle','policy')),'diagnostic integer identity malformed')
        require(type(d['pid']) is int and d['pid']==identity[0] and type(d['startTimeTicks']) is int and d['startTimeTicks']==identity[2] and d['processInstance']==identity[3],'app/OS identity mismatch')
        require(integer(fence,1) and fence>max(last_fence,last_export,last_capture) and d['capturedMonotonicNs']>fence and d['captureSequence']>last_sequence and d['capturedMonotonicNs']>last_capture,'stale/reused worker capture or export fence')
        require(d['capturedMonotonicNs']<=d['exportedMonotonicNs']<=d['capturedMonotonicNs']+2_000_000_000,'stale app export')
        requests=d['requests'];require(isinstance(requests,list) and len(requests)==4,'four diagnostic requests required')
        require(all(type(r['slot']) is int and r['slot']==i and integer(r['version']) and type(r['appliedVersion']) is int and r['appliedVersion']==r['version'] and r['pending'] is False for i,r in enumerate(requests)),'pending/mixed diagnostic requests')
        qa=proof['qa'];require(type(qa['schemaVersion']) is int and qa['schemaVersion']==1 and all(qa[k] is True for k in ('valid','foreground','debuggable')) and qa['applicationId']==package and type(qa['pid']) is int and qa['pid']==identity[0],'ordinary QA invalid')
        for qkey,dkey in (('capturedMonotonicNs','capturedMonotonicNs'),('exportedMonotonicNs','exportedMonotonicNs'),('sessionGeneration','sessionGeneration'),('lifecycleGeneration','lifecycle'),('policyGeneration','policy')):
            require(type(qa[qkey]) is int and qa[qkey]==d[dkey],'mixed ordinary QA/diagnostic capture')
        watches=qa['watches'];require(isinstance(watches,list) and len(watches)==4,'four ordinary QA watches required')
        for i,w in enumerate(watches):
            require(type(w['slot']) is int and w['slot']==i and all(w[k] is True for k in ('valid','readerActive','freshAtExport','nativeFresh')) and w['pending'] is False and w['hasError'] is False and w['state']=='receiving','non-live watch')
            require(integer(w['matched'],1) and integer(w['readerGeneration'],1) and type(w['count']) is int and w['count']==sample['received_counts'][i] and type(w['requestVersion']) is int and w['requestVersion']==requests[i]['version'] and type(w['appliedVersion']) is int and w['appliedVersion']==w['requestVersion'],'ordinary watch identity/count mismatch')
        validate_graph(qa['plotDisplay'],d['exportedMonotonicNs'],watches,qa['plotIntake'],qa['sessionGeneration'])
        current=(d['sessionGeneration'],d['lifecycle'],d['policy'],[(r['version'],w['readerGeneration']) for r,w in zip(requests,watches)])
        if stable is None:stable=current
        require(current==stable,'session/watch/lifecycle changed during soak')
        last_sequence=d['captureSequence'];last_capture=d['capturedMonotonicNs']
        last_export=d['exportedMonotonicNs'];last_fence=fence
