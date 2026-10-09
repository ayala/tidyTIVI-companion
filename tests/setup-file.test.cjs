const fs=require('fs'),vm=require('vm'),assert=require('assert');
const elements={setup:{},status:{},url:{},connect:{requestSubmit(){this.submits=(this.submits||0)+1}}};
const context={URL,document:{getElementById:id=>elements[id]}};vm.createContext(context);
vm.runInContext(fs.readFileSync('assets/pairing.html','utf8').match(/<script>([\s\S]*?)<\/script>/)[1],context);
const link='https://www.dropbox.com/scl/fi/example/bundle.zip?rlkey=private-example&dl=1';
(async()=>{
 for(const text of ['{}','not json',JSON.stringify({schema:'wrong',download_url:link}),JSON.stringify({schema:'tidytivi.connection.v1',download_url:'javascript:alert(1)'}),JSON.stringify({schema:'tidytivi.connection.v1',download_url:'https://user:pass@example.com/file'})])assert.throws(()=>context.parseSetup(text));
 const text=JSON.stringify({schema:'tidytivi.connection.v1',download_url:link});assert.equal(context.parseSetup(text),link);
 elements.setup.files=[{size:text.length,text:async()=>text}];await elements.setup.onchange();assert.equal(elements.url.value,link);assert.equal(elements.connect.submits,1);
 for(const file of [{size:20000,text:async()=>text},{size:2,text:async()=>'{}'}]){elements.setup.files=[file];await elements.setup.onchange();assert.equal(elements.connect.submits,1);assert.match(elements.status.textContent,/valid tidyTIVI/);}
 console.log('Setup file: valid link/query preservation, automatic submit, malformed/oversized/unsafe rejection passed.');
})().catch(e=>{console.error(e);process.exit(1)});
