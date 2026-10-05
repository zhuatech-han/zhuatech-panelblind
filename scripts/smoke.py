#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise actual isolated HTTP/MySQL workflows using TEST records and private random credentials."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
from datetime import datetime,timezone
from zoneinfo import ZoneInfo
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8131').rstrip('/');checks=0

def check(ok,message):
    """Count real acceptance checks without printing credentials or business responses."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

def key():return str(uuid.uuid4())

class Client:
    """Use an isolated session cookie jar and the application's real CSRF endpoint."""
    def __init__(self,name,password):
        """Actual isolated acceptance operation. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        """Actual isolated acceptance operation. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,path+': wrong error')
        return value

def get_detail(id,actor=None):
    """Read safe authorized panel DTOs. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    return (actor or admin).request(f'/sessions/{id}')
def record(id):return get_detail(id)['session']
def own(id,name):return get_detail(id,clients[name])['sheets'][0]
def sheet(id,sheet_id):return next(p for p in get_detail(id,review)['sheets'] if p['id']==sheet_id)
def command(actor,kind,id,action,panel=None,status=200,code=None,body=None):
    """Version-bound commands with private random UUIDs. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    row=record(id) if kind=='sessions' else sheet(panel,id)
    body=body or {'requestKey':key(),'version':row['version'],'note':'TEST 人工完整性核对'}
    return actor.request(f'/{kind}/{id}/commands/{action}','POST',body,status,code)
def capture():
    """Capture read-only stable responses without requesting the identity key. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard','/sessions?size=100&sort=oldest']
    paths += [f'/sessions/{r["id"]}' for r in admin.request('/sessions?size=100')['items']]
    return {p:admin.request(p) for p in paths}
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--capture',action='store_true');args=parser.parse_args()
check(BASE.startswith('http://127.0.0.1:') or BASE.startswith('http://localhost:'),'Acceptance target must be an isolated loopback instance')
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    state=json.loads(STATE.read_text());responses=capture()
    if args.capture:state['responses']=responses;STATE.write_text(json.dumps(state,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(responses),'result':'PASS'}));raise SystemExit
    for path,expected in state['responses'].items():check(responses[path]==expected,'Persistence mismatch: '+path)
    for name,username in state['users'].items():check(Client(username,state['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
    for request in state.get('replays',[]):
        actor=Client(state['users'][request['actor']],state['password']);check(actor.request(request['path'],'POST',request['body'])==request['response'],'Replay mismatch')
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(responses),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable isolated database.')
if STATE.exists():raise SystemExit('QA state exists; use --verify or a new isolated database.')
check(admin.request('/sessions')['total']==0,'Business tables must be empty on first boot')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};userIds={};replays=[];panels=[]
roles={r['name']:r['id'] for r in admin.request('/admin/roles')};dep=admin.request('/admin/departments','POST',{'name':'TEST 内部产品评议 '+suffix})['id'];outside_dep=admin.request('/admin/departments','POST',{'name':'TEST 外部部门 '+suffix})['id']
wide=admin.request('/admin/roles','POST',{'name':'TEST 评分ALL '+suffix,'scope':'ALL','permissions':['session.read','rating.write','export','dashboard']})['id']
for name,role,department,label in [('designer',roles['方案设计'],dep,'方案设计'),('review',roles['独立审签'],dep,'独立审签'),('custodian',roles['样品保管'],dep,'样品保管'),('rater1',roles['内部评分'],dep,'评分员01'),('rater2',wide,dep,'评分员ALL'),('rater3',roles['内部评分'],dep,'评分员03'),('rater4',roles['管理员'],dep,'混合管理员评分员'),('outside',roles['方案设计'],outside_dep,'外部设计')]:
    username='test-'+name+'-'+suffix;users[name]=username;userIds[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+label,'password':password,'roleId':role,'departmentId':department,'enabled':True})['id'];clients[name]=Client(username,password)
designer,review,custodian=[clients[n] for n in ['designer','review','custodian']];rater_names=['rater1','rater2','rater3','rater4']
def draft(complete=True):
    """Create explicitly labelled disposable packaging protocols. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    body={'requestKey':key(),'reference':'TEST-PANEL-'+key()[:8],'name':'TEST 包装外观盲评','departmentId':dep,'category':'PACKAGING','instructions':'按屏幕呈现顺序评价TEST包装外观，仅记录主观喜好，不作产品安全或研究结论。','reviewerId':userIds['review'],'custodianId':userIds['custodian']}
    s=designer.request('/sessions','POST',body);id=s['id'];panels.append(id)
    check(designer.request('/sessions','POST',body)==s,'Create retry differs')
    if complete:
        for code in ['PACK-A','PACK-B']:designer.request('/samples','POST',{'requestKey':key(),'version':record(id)['version'],'sessionId':id,'code':code,'name':'TEST 保密包装 '+code,'description':'TEST 内部样品身份，仅供保管与设计岗位'})
        designer.request('/scales','POST',{'requestKey':key(),'version':record(id)['version'],'sessionId':id,'code':'APPEARANCE','name':'TEST 外观喜好','minimum':1,'maximum':9,'lowAnchor':'非常不喜欢','highAnchor':'非常喜欢','required':True})
        designer.request(f'/sessions/{id}/roster','PUT',{'requestKey':key(),'version':record(id)['version'],'raterIds':[userIds[n] for n in rater_names]})
    return id
def running(id):
    """Approve once and verify balanced real persistent presentation rows. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    command(designer,'sessions',id,'submit');body={'requestKey':key(),'version':record(id)['version'],'note':'TEST 一次批准布局'};path=f'/sessions/{id}/commands/approve';response=review.request(path,'POST',body);check(review.request(path,'POST',body)==response,'Approval retry differs');replays.append({'actor':'review','path':path,'body':body,'response':response});review.request(path,'POST',{**body,'note':'TEST changed'},409,'REQUEST_KEY_REUSED')
    mapping=custodian.request(f'/sessions/{id}/key')['mapping'];check(len(mapping)==8,'Presentation count differs');check(len({r['blindCode'] for r in mapping})==8,'Blind codes duplicate')
    for code in ['PACK-A','PACK-B']:
        for position in [1,2]:check(sum(r['sampleCode']==code and r['position']==position for r in mapping)==2,'Unequal position count')
    check(len(record(id)['layoutHash'])==64,'No blind layout digest');command(designer,'sessions',id,'start')
def fill(id,name,score=6,missing=False,submitted=True,accepted=True):
    """Every rater acknowledges and writes only their own blind response. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    actor=clients[name];p=own(id,name)
    if not p['acknowledgedAt']:command(actor,'sheets',p['id'],'acknowledge',id)
    scale=get_detail(id,actor)['scales'][0]
    for i in p['presentations']:
        body={'requestKey':key(),'version':own(id,name)['version'],'presentationId':i['id'],'scaleId':scale['id'],'value':None if missing else score,'missingReason':'TEST 样品未能完成评价' if missing else '','note':'=TEST 主观评价保留'};result=actor.request('/ratings','POST',body)
        check(actor.request('/ratings','POST',body)==result,'Rating retry differs')
    if submitted:command(actor,'sheets',p['id'],'submit',id)
    if accepted:command(review,'sheets',p['id'],'accept',id)
    return own(id,name)
def finish(id):
    """Separate actual sealing, request and independent unblinding. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    command(designer,'sessions',id,'end');command(review,'sessions',id,'seal');check(get_detail(id)['statistics']==[],'Scores released before unblinding');command(custodian,'sessions',id,'requestUnblind');command(review,'sessions',id,'unblind');check(record(id)['status']=='UNBLINDED' and len(record(id)['resultHash'])==64,'Release freeze missing')
# Complete business with numeric and missing responses, returns, exclusion and separate role gates.
t1=draft();command(designer,'sessions',t1,'approve',status=403,code='FORBIDDEN');running(t1);command(designer,'sessions',t1,'end',status=409,code='INCOMPLETE_RATINGS')
for name in rater_names:
    d=get_detail(t1,clients[name]);check(len(d['sheets'])==1,'Rater detail leaked other sheets');check(d['samples']==[] and not d['keyAllowed'],'Rater identity key leaked');check('sampleId' not in json.dumps(d) and 'PACK-A' not in json.dumps(d),'Internal identity leaked');clients[name].request(f'/sessions/{t1}/key',status=403,code='KEY_ACCESS_DENIED' if name=='rater4' else 'FORBIDDEN')
clients['rater1'].request('/admin/users',status=403,code='FORBIDDEN');review.request(f'/sessions/{t1}/key',status=403,code='FORBIDDEN')
p0=own(t1,'rater1');scale=get_detail(t1,clients['rater1'])['scales'][0];bad={'requestKey':key(),'version':p0['version'],'presentationId':p0['presentations'][0]['id'],'scaleId':scale['id'],'value':5};clients['rater1'].request('/ratings','POST',bad,409,'RATING_LOCKED');command(clients['rater1'],'sheets',p0['id'],'acknowledge',t1);bad['version']=own(t1,'rater1')['version'];bad['value']=10;clients['rater1'].request('/ratings','POST',bad,409,'OUT_OF_SCALE');bad['value']=5;bad['missingReason']='TEST conflict';clients['rater1'].request('/ratings','POST',bad,409,'VALUE_OR_MISSING_REQUIRED');bad.pop('missingReason');bad['value']=5.5;clients['rater1'].request('/ratings','POST',bad,400);command(clients['rater1'],'sheets',p0['id'],'submit',t1,status=409,code='INCOMPLETE_RATINGS')
p1=fill(t1,'rater1',3,accepted=False);check(get_detail(t1,designer)['sheets'][0]['ratings']==[],'Designer reads submitted scores');command(review,'sheets',p1['id'],'return',t1);p1=fill(t1,'rater1',5);command(clients['rater1'],'sheets',p1['id'],'withdraw',t1,status=409,code='INVALID_STATE');fill(t1,'rater2',7);fill(t1,'rater3',missing=True);p4=fill(t1,'rater4',8,accepted=False);command(review,'sheets',p4['id'],'exclude',t1)
command(review,'sessions',t1,'requestUnblind',status=403,code='FORBIDDEN');finish(t1)
for m in get_detail(t1)['statistics']:check(m['n']==2 and m['missing']==1 and m['mean']==6 and m['min']==5 and m['max']==7,'Incorrect descriptive counts or range')
check(len(get_detail(t1,clients['rater4'])['sheets'])==1,'Released mixed admin still must only see own sheet');check(record(t1)['outcome']=='FINISHED','Outcome differs')
# Empty abort, cancellation, returned draft and a GUI-ready scoring panel.
t2=draft();running(t2);command(designer,'sessions',t2,'abort');command(review,'sessions',t2,'seal');command(custodian,'sessions',t2,'requestUnblind');command(review,'sessions',t2,'unblind');check(record(t2)['outcome']=='ABORTED','Abort presented as normal finish');check(all(m['n']==0 and m['mean'] is None for m in get_detail(t2)['statistics']),'Empty abort invents scores')
t3=draft(False);command(designer,'sessions',t3,'submit',status=409,code='INCOMPLETE_DESIGN');command(designer,'sessions',t3,'cancel')
t4=draft();command(designer,'sessions',t4,'submit');command(review,'sessions',t4,'return')
t5=draft();running(t5);fill(t5,'rater1',4,submitted=False,accepted=False)
for name in rater_names[1:]:fill(t5,name,6)
# Scoped reads/exports, CSV safety, real CSRF and live identity changes.
for name in ['outside']:
    clients[name].request(f'/sessions/{t1}',status=403,code='OUT_OF_SCOPE');clients[name].request(f'/sessions/{t1}/report.json',status=403,code='OUT_OF_SCOPE');check(clients[name].request('/sessions?size=100')['total']==0,'Outside scope list leak')
clients['rater1'].request('/audit',status=403,code='FORBIDDEN');check(len(clients['rater1'].request('/options')['people'])==1,'Rater reads full people directory');check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Password hash leaked');check(len(admin.request('/admin/permissions'))==10 and len(admin.request('/admin/menus'))==10,'Identity catalogs differ');designer.request('/sessions?size=101',status=400,code='INVALID_INPUT');designer.request('/sessions?sort=sql',status=400,code='INVALID_INPUT');designer.request('/sessions','POST',{},403,csrf=False)
for name in ['designer','rater1','review']:
    with clients[name].opener.open(BASE+f'/api/sessions/{t5}/ratings.csv') as response:csv=response.read().decode('utf-8-sig')
    check('PACK-A' not in csv,'CSV leaked unreleased identity')
    if name=='designer':check('=TEST' not in csv,'Designer CSV leaked scores')
    else:check("'=TEST" in csv,'CSV formula not escaped')
x=next(a for a in admin.request('/admin/users') if a['id']==userIds['outside']);admin.request('/admin/users/'+str(x['id']),'PUT',{**x,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(x['id']),'PUT',{**x,'enabled':True});admin.request('/admin/departments/'+str(dep),'DELETE',{},409,'CONFLICT');check(len(admin.request('/audit'))>0,'No real audit')
# One optimistic version cannot win twice under concurrent sessions.
p=own(t5,'rater1');body={'requestKey':key(),'version':p['version'],'presentationId':p['presentations'][0]['id'],'scaleId':get_detail(t5,clients['rater1'])['scales'][0]['id'],'value':4,'missingReason':'','note':'TEST 同时编辑'}
parallel=[Client(users['rater1'],password),Client(users['rater1'],password)]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda item:item[0].request('/ratings','POST',{**body,'requestKey':item[1]},status=(200,409)),zip(parallel,[key(),key()])))
check(sum(r.get('code')=='STALE_VERSION' for r in results)==1,'Concurrent overwrite not prevented')
STATE.parent.mkdir(exist_ok=True);state={'password':password,'users':users,'userIds':userIds,'departmentId':dep,'panelIds':panels,'uiPanelId':t5,'uiPanelReference':record(t5)['reference'],'draftPanelId':t4,'replays':replays};state['responses']=capture();fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'panels':len(panels),'presentations':sum(sum(len(p['presentations']) for p in get_detail(id)['sheets']) for id in panels),'result':'PASS'}))
