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
module.exports={sessionKeys,cleanSessions};
