// Explicit SCAN commands: some older Windows redis-cli builds silently ignore --scan.
const {execFileSync}=require('node:child_process');
function sessionKeys(uid){
  if(!Number.isSafeInteger(uid)||uid<=0)throw new Error('Invalid fixture user ID');
  const prefix=`trip:auth:session:{${uid}}:`,keys=new Set();let cursor='0';
  do{
    const lines=execFileSync('redis-cli',['SCAN',cursor,'MATCH',prefix+'*','COUNT','100'],{encoding:'utf8'}).trimEnd().split(/\r?\n/);
    if(!/^\d+$/.test(lines[0]))throw new Error('Invalid Redis SCAN response');
    cursor=lines[0];for(const key of lines.slice(1)){if(!key.startsWith(prefix))throw new Error('Unexpected fixture session key');keys.add(key)}
  }while(cursor!=='0');
  return [...keys];
}
function cleanSessions(uid){const keys=sessionKeys(uid);for(const key of keys)execFileSync('redis-cli',['DEL',key]);if(sessionKeys(uid).length)throw new Error('Fixture sessions remain');return keys.length;}
function cleanModelState(uid,circuitPrefix='trip:ai:circuit',quotaPrefix='trip:ai:quota'){
  if(!Number.isSafeInteger(uid)||uid<=0||![circuitPrefix,quotaPrefix].every(p=>/^[a-zA-Z0-9:_-]{1,80}$/.test(p)))throw Error('Invalid fixture identity');
  let count=0;
  for(const prefix of [`${circuitPrefix}:{${uid}:`,`${quotaPrefix}:{ai-quota}:user:${uid}:`]){
    const scan=()=>{let cursor='0',keys=new Set();do{const rows=execFileSync('redis-cli',['SCAN',cursor,'MATCH',prefix+'*','COUNT','100'],{encoding:'utf8'}).trimEnd().split(/\r?\n/);if(!/^\d+$/.test(rows[0]))throw Error('Invalid scan');cursor=rows[0];for(const k of rows.slice(1).filter(Boolean)){if(!k.startsWith(prefix))throw Error('Unsafe key');keys.add(k);}}while(cursor!=='0');return [...keys];};
    const keys=scan();for(const k of keys)execFileSync('redis-cli',['DEL',k]);count+=keys.length;if(scan().length)throw Error('Fixture AI state remains');
  }return count;
}
module.exports={sessionKeys,cleanSessions,cleanModelState};
