"""Loopback-only SMTP fixture. No real delivery or persistent log. Never use in production."""
import email.policy, email.parser, json, re, socketserver, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
MAILS={}; LOCK=threading.Lock(); FAIL=set()
class SMTP(socketserver.StreamRequestHandler):
    def reply(self,text): self.wfile.write((text+'\r\n').encode());self.wfile.flush()
    def handle(self):
        self.reply('220 local fixture SMTP');recipient=''
        while True:
            raw=self.rfile.readline()
            if not raw:return
            command=raw.decode(errors='replace').strip();verb=command.split(' ',1)[0].upper()
            if verb=='EHLO': self.reply('250-localhost');self.reply('250 8BITMIME')
            elif verb in {'HELO','MAIL'}:self.reply('250 OK')
            elif verb=='RCPT':
                recipient=re.search(r'<([^>]+)>',command).group(1).lower()
                self.reply('550 fixture rejects recipient' if recipient in FAIL else '250 OK')
            elif verb=='DATA':
                self.reply('354 End with dot');lines=[]
                while True:
                    line=self.rfile.readline()
                    if not line or line==b'.\r\n':break
                    lines.append(line[1:] if line.startswith(b'..') else line)
                message=email.parser.BytesParser(policy=email.policy.default).parsebytes(b''.join(lines))
                content=message.get_content();code=re.search(r'(?<!\d)\d{6}(?!\d)',content)
                with LOCK:MAILS[recipient]={'code':code.group(0) if code else None,'subject':str(message['Subject']),'body':content}
                self.reply('250 stored')
            elif verb=='QUIT':self.reply('221 bye');return
            else:self.reply('250 OK')
class HTTP(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        url=urlparse(self.path);recipient=parse_qs(url.query).get('email',[''])[0].lower()
        with LOCK:
            if url.path=='/message':result=MAILS.get(recipient,{})
            elif url.path=='/reject':FAIL.add(recipient);result={'ok':True}
            elif url.path=='/clear':MAILS.pop(recipient,None);FAIL.discard(recipient);result={'ok':True}
            else:result={'ok':True}
        self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(result,ensure_ascii=False).encode())
if __name__=='__main__':
    smtp=socketserver.ThreadingTCPServer(('127.0.0.1',2527),SMTP);smtp.daemon_threads=True
    threading.Thread(target=smtp.serve_forever,daemon=True).start()
    print('Loopback fixture SMTP 2527 / HTTP 8027',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8027),HTTP).serve_forever()
