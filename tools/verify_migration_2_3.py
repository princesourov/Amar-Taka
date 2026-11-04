import json,re,sqlite3,sys
schema=json.load(open(sys.argv[1]))['database']
db=sqlite3.connect(':memory:')
for e in schema['entities']:
    db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
    for i in e.get('indices',[]): db.execute(i['createSql'].replace('${TABLE_NAME}',e['tableName']))
db.execute("insert into accounts(id,userId,name,type,iconKey,openingBalanceMinor,notes,isActive,createdAtMillis,syncStatus) values('a','u','P','POCKET','x',100,NULL,1,1,'PENDING')")
db.execute("insert into savings_goals values('g1','u','A',1000,400,NULL,1,50),('g2','u','B',500,0,NULL,1,60)")
src=open('app/src/main/java/com/hisab/app/data/local/database/Migrations.kt').read()
blk=src[src.index('fun migrationStatements2To3'):]
stmts=[re.sub(r'"\s*\+\s*"','',m) for m in re.findall(r'^\s*(".*?"(?:\s*\+\s*\n\s*".*?")*)\s*,?\s*$',blk,re.M|re.S)]
stmts=[s.strip('"').replace('\\"','"') for s in stmts]
# simpler: join string literals per statement
import ast
lits=re.findall(r'"((?:[^"\\]|\\.)*)"',blk.split('listOf(')[1])
out=[];cur=''
for l in lits:
    cur+=l
    if not re.search(r'\)$',cur) and not cur.startswith(('ALTER','UPDATE')) and 'SELECT' not in cur and cur.startswith('INSERT'): continue
    out.append(cur);cur=''
if cur: out.append(cur)
# recombine: statements are the elements split by top-level commas; handle manually
out=[]
for m in re.finditer(r'"(ALTER|CREATE|UPDATE|INSERT)((?:[^"\\]|\\.)*)"(?:\s*\+\s*"((?:[^"\\]|\\.)*)")*',blk):
    pass
parts=re.split(r',\n(?=\s*")',blk.split('listOf(')[1].rsplit(')',1)[0])
for p in parts:
    out.append(''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',p)))
print(len(out),'statements')
for s in out: db.execute(s)
print(db.execute("select id,amountMinor,accountId,dateMillis from goal_contributions").fetchall())
print(db.execute("select id,iconKey,updatedAtMillis,deletedAtMillis,accountId from savings_goals").fetchall())
print(db.execute("select id,openingBalanceMinor,deletedAtMillis from accounts").fetchall())
# compare with Room's expected v3 columns from entity
print([r[1] for r in db.execute('pragma table_info(goal_contributions)')])
print(db.execute("pragma integrity_check").fetchall())
