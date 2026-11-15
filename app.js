'use strict';
const $=id=>document.getElementById(id), audio=$('audio'), plot=$('plot');
const names=['C','C♯','D','E♭','E','F','F♯','G','A♭','A','B♭','B'], roman=['Sa','re','Re','ga','Ga','Ma','Ma♯','Pa','dha','Dha','ni','Ni'], hindi=['सा','रे॒','रे','ग॒','ग','म','म॑','प','ध॒','ध','नि॒','नि'];
const pc=n=>((n%12)+12)%12, clock=t=>Math.floor(t/60)+':'+String(Math.floor(t%60)).padStart(2,'0');
names.forEach((n,i)=>$('root').add(new Option(n,i)));
function detectPitch(s,pos,scores){
   let n=NaN,e=0;for(let j=0;j<512;j++)e+=s[pos+j]**2;
   if(e>0.0128){
    let peak=0;
    for(let lag=6;lag<=102;lag++){
     let a=0,b=0,c=0;
     for(let j=0;j<409;j+=2){let x=s[pos+j],y=s[pos+j+lag];a+=x*x;b+=y*y;c+=x*y}
     scores[lag]=c/Math.sqrt(a*b+1e-16);peak=Math.max(peak,scores[lag]);
    }
    for(let k=7;k<102;k++){let l=scores[k-1],v=scores[k],r=scores[k+1];
     if(v>=Math.max(.80,.94*peak)&&v>l&&v>=r){let lag=k+Math.max(-.5,Math.min(.5,(l-r)/(2*(l-2*v+r))));n=69+12*Math.log2(8000/lag/440);break}
    }
   }
 return n;
}
function estimateKey(hist){
  let best=-Infinity,root=0,mode='';
  const profiles=[[6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88],[6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17]];
  if(hist.filter(Boolean).length>=3)profiles.forEach((p,m)=>{let mean=p.reduce((a,b)=>a+b)/12,norm=Math.sqrt(p.reduce((a,b)=>a+(b-mean)**2,0));for(let k=0;k<12;k++){let score=hist.reduce((a,v,i)=>a+v*(p[(i-k+12)%12]-mean)/norm,0);if(score>best){best=score;root=k;mode=m?'minor':'major'}}});
 return {root,mode};
}
function analyzer(){
 onmessage=({data:s})=>{
  const notes=[],hist=Array(12).fill(0),scores=new Float64Array(103);
  for(let pos=0;pos+512<=s.length;pos+=320){
   const n=detectPitch(s,pos,scores);
   notes.push(n);if(Number.isFinite(n))hist[Math.round(n)%12]++;
  }
  const {root,mode}=estimateKey(hist);
  const events=[];let last=-1,prev=-1,streak=0;
  notes.forEach((v,i)=>{let n=Number.isFinite(v)?Math.round(v):-1;streak=n===prev?streak+1:1;prev=n;if(streak===2&&n!==last){if(n>=0)events.push([i-1,n]);last=n}});
  postMessage({notes,events,root,mode});
 };
}
let track=null,job=0,worker,url,ctx,lastSequence='',hint='Ready';
const workerURL=URL.createObjectURL(new Blob([detectPitch.toString(),estimateKey.toString(),';('+analyzer.toString()+')()'],{type:'text/javascript'}));
async function load(file){
 stopMic();
 const id=++job;audio.pause();worker?.terminate();if(url)URL.revokeObjectURL(url);
 track=null;lastSequence='';$('sequence').replaceChildren();$('root').value=-1;$('demo').hidden=!file;
 $('title').textContent=file?file.name.replace(/\.[^.]+$/,''):'Daybreak';$('detail').textContent=file?'Local recording':'Original instrumental · Scalo demo';
 $('play').disabled=true;$('seek').disabled=true;$('key').textContent='Analyzing…';hint='Finding pitches…';audio.removeAttribute('src');audio.load();draw();
 try{
  if(file&&file.size>150*1024*1024)throw Error('Choose a file smaller than 150 MB.');
  const blob=file||await (await fetch(window.demoAudio)).blob();
  if(id!==job)return;
  ctx??=new AudioContext();const decoded=await ctx.decodeAudioData(await blob.arrayBuffer());
  if(id!==job)return;if(decoded.duration>720)throw Error('Choose a song under 12 minutes.');
  const offline=new OfflineAudioContext(1,Math.ceil(decoded.duration*8000),8000),source=offline.createBufferSource();source.buffer=decoded;source.connect(offline.destination);source.start();
  const pcm=(await offline.startRendering()).getChannelData(0);if(id!==job)return;
  url=URL.createObjectURL(blob);audio.src=url;
  worker=new Worker(workerURL);
  worker.onmessage=({data})=>{if(id!==job)return;track=data;worker.terminate();worker=null;if(live)return;hint='Ready';$('play').disabled=false;$('seek').disabled=false;$('key').textContent=data.mode?names[data.root]+' '+data.mode:'Undetermined';draw()};
  worker.onerror=()=>{hint='Analysis failed. Open another song.';$('key').textContent='Unavailable';draw()};
  worker.postMessage(pcm,[pcm.buffer]);
 }catch(e){if(id===job){hint=e.message||'Unable to read this recording.';$('key').textContent='Unavailable';draw()}}
}
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
let releasedAt=null;
function stringMotion(events,frame,time,current){
 const motion=new Map();
 if(reducedMotion.matches || (!live && audio.paused && releasedAt===null))return motion;
 const release=!live&&audio.paused?Math.exp(-(performance.now()-releasedAt)/105):1;
 let lo=0,hi=events.length;
 while(lo<hi){const m=(lo+hi)>>1;if(events[m][0]<=frame)lo=m+1;else hi=m}
 for(let j=lo-1;j>=0;j--){
  const [onset,note]=events[j],age=Math.max(0,time-(onset*.04+.032));
  const held=j===lo-1&&current===note;
  if(age>1&&!held)break;
  if(motion.has(note))continue;
  const ring=Math.exp(-age*4.6),energy=(ring+(held?.15:0))*release;
  motion.set(note,{phase:age*2*Math.PI*4.8,energy});
 }
 return motion;
}
function pluckedString(g,left,right,row,contact,spacing,{phase,energy}){
 const center=Math.max(.08,Math.min(.92,(contact-left)/(right-left)));
 const amplitude=Math.min(14,spacing*.72)*energy;
 g.save();g.beginPath();
 for(let i=0;i<=80;i++){
  const u=i/80,d=u-center;
  const envelope=Math.sin(Math.PI*u)*Math.exp(-5*d*d);
  const wave=.76*Math.cos(phase-6*Math.abs(d))+.24*Math.cos(1.9*phase+9*d);
  const py=row-amplitude*envelope*wave;
  if(!i)g.moveTo(left,py);else g.lineTo(left+(right-left)*u,py);
 }
 g.strokeStyle='rgba(255,117,160,'+Math.min(.94,.13+energy*.82)+')';
 g.lineWidth=1.3+energy;g.shadowBlur=8*energy;g.shadowColor='#ff4787';g.stroke();g.restore();
}
function draw(){
 const displayed=live?.track??track;
 const t=live?Math.max(0,displayed.notes.length-1)*.04+.032:audio.currentTime||0,d=audio.duration||0,head=Math.max(0,Math.round((t-.032)/.04)),notes=displayed?.notes||[],root=+$('root').value<0?displayed?.root||0:+$('root').value;
 const w=plot.clientWidth,h=plot.clientHeight,dpr=devicePixelRatio||1;
 if(plot.width!==Math.round(w*dpr)||plot.height!==Math.round(h*dpr)){plot.width=Math.round(w*dpr);plot.height=Math.round(h*dpr)}
 const g=plot.getContext('2d');g.setTransform(dpr,0,0,dpr,0,0);g.clearRect(0,0,w,h);
 const end=Math.max(150,head+25),start=end-150,left=38,right=w-40;
 const visible=notes.slice(start,end+1).filter(Number.isFinite);
 const low=Math.min(...visible),high=Math.max(...visible);
 const base=visible.length?root+12*Math.floor((low-root+1)/12):root+60;
 const min=Math.min(base-2,Math.floor(low)-1),max=Math.max(base+13,Math.ceil(high)+1);
 const x=i=>left+(right-left)*(i-start)/150,y=n=>h-28-(h-44)*(n-min)/(max-min);
 const line=(a,b,c,d,color)=>{g.strokeStyle=color;g.lineWidth=1;g.beginPath();g.moveTo(a,b);g.lineTo(c,d);g.stroke()};
 g.font='11px monospace';g.textBaseline='middle';
 for(let i=start;i<=end;i++){if(i%25)continue;line(x(i),12,x(i),h-26,'#282930');g.textAlign='center';g.fillStyle='#96909b';g.fillText(clock(i*.04),x(i),h-12)}
 const n=Number.isFinite(notes[head])?Math.round(notes[head]):null;
 const vibration=stringMotion(displayed?.events||[],head,t,n);
 for(let k=min;k<=max;k++){let s=pc(k-root);if(![0,2,4,5,7,9,11].includes(s)&&k!==n&&!vibration.has(k))continue;
  const ring=vibration.get(k);
  if(ring&&ring.energy>.018)pluckedString(g,left,right,y(k),x(head),Math.abs(y(k+1)-y(k)),ring);
  else line(left,y(k),right,y(k),s===0?'#45444e':'#292b31');
  g.fillStyle=k===n?'#ff789e':'#b1aab7';g.textAlign='right';g.fillText(hindi[s],left-8,y(k));g.textAlign='left';g.fillText(roman[s]+(k>=base+12?'′':''),right+7,y(k))}
 function trace(from,to,color,width){g.beginPath();let pen=false;for(let i=from;i<=Math.min(to,notes.length-1);i++){if(!Number.isFinite(notes[i])){pen=false;continue}if(pen)g.lineTo(x(i),y(notes[i]));else g.moveTo(x(i),y(notes[i]));pen=true}g.lineWidth=width;g.strokeStyle=color;g.lineJoin='round';g.stroke()}
 trace(head,end,'#805267',1.7);trace(start,head,'#ff678f',2.8);
 if(n!==null){g.beginPath();g.arc(x(head),y(notes[head]),4,0,2*Math.PI);g.fillStyle='#ff678f';g.fill()}
 $('swara').textContent=n===null?'—':hindi[pc(n-root)];$('note').textContent=n===null?'—':roman[pc(n-root)]+'  /  '+names[pc(n)]+(Math.floor(n/12)-1);
 const events=displayed?.events||[];let a=0,b=events.length;while(a<b){let m=(a+b)>>1;if(events[m][0]<=head)a=m+1;else b=m}let at=a-1,offset=Math.max(0,Math.min(at-3,events.length-10)),signature=[job,root,at,events.length,n===null].join('/');
 if(signature!==lastSequence){lastSequence=signature;$('sequence').replaceChildren(...events.slice(offset,offset+10).map((v,i)=>{const el=document.createElement('div'),s=pc(v[1]-root);el.className='token'+(offset+i===at&&n!==null?' active':'');el.innerHTML='<b>'+hindi[s]+'</b><span>'+roman[s]+'</span>';return el}));$('count').textContent=events.length?(at+1)+' / '+events.length:''}
 if(live){$('time').textContent=clock(t);$('state').textContent='Listening';return}
 $('time').textContent=clock(t)+' / '+clock(d);$('seek').value=d?t/d*1000:0;$('state').textContent=audio.paused?(audio.ended?'Finished':hint):'Playing';$('play').textContent=audio.paused?'▶':'Ⅱ';$('play').setAttribute('aria-label',audio.paused?'Play':'Pause')
}
let raf;function tick(){
 if(audio.paused&&releasedAt!==null&&performance.now()-releasedAt>500)releasedAt=null;
 draw();if(!audio.paused||releasedAt!==null)raf=requestAnimationFrame(tick)
}
audio.onplay=()=>{releasedAt=null;cancelAnimationFrame(raf);tick()};
audio.onpause=()=>{hint='Paused';releasedAt=reducedMotion.matches?null:performance.now();cancelAnimationFrame(raf);tick()};
audio.onended=draw;audio.onloadedmetadata=draw;
$('play').onclick=async()=>{if(!audio.paused){audio.pause();return}try{await audio.play()}catch{hint='Playback failed. Open another recording.';draw()}};
$('seek').oninput=e=>{if(audio.duration)audio.currentTime=audio.duration*e.target.value/1000;draw()};$('root').onchange=draw;
$('file').onchange=e=>{if(e.target.files[0])load(e.target.files[0]);e.target.value=''};$('demo').onclick=()=>load(null);
let live=null,micRequest=0,micPending=false;
function stopMic(message){
 micRequest++;
 micPending=false;
 if(live){
  clearInterval(live.timer);
  live.source.disconnect();
  live.stream.getTracks().forEach(t=>t.stop());
  $('title').textContent=live.title;
  $('detail').textContent=live.detail;
  $('key').textContent=track?.mode?names[track.root]+' '+track.mode:'Undetermined';
  live=null;
  $('seek').disabled=$('play').disabled=!track;
  lastSequence='';draw();
 }
 $('mic').textContent='Use microphone';
 $('mic').setAttribute('aria-pressed','false');
 $('micinfo').textContent=message||'Sing to check your note and scale.';
}
async function startMic(){
 if(live||micPending){const result=live?.track;stopMic(result?.mode?'Last estimate: '+names[result.root]+' '+result.mode:'Microphone stopped.');return}
 const request=++micRequest;
 micPending=true;
 audio.pause();
 $('mic').textContent='Cancel';
 $('micinfo').textContent='Allow microphone access to begin.';
 let stream;
 try{
  if(!navigator.mediaDevices?.getUserMedia)throw Error('Open Scalo in an HTTPS browser tab to use the microphone.');
  ctx??=new AudioContext();
  await ctx.resume();
  if(request!==micRequest)return;
  stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false}});
  if(request!==micRequest){stream.getTracks().forEach(t=>t.stop());return}
  audio.pause();
  const source=ctx.createMediaStreamSource(stream),an=ctx.createAnalyser();
  an.fftSize=2**Math.ceil(Math.log2(ctx.sampleRate*.064+2));
  source.connect(an);
  const raw=new Float32Array(an.fftSize),frame=new Float32Array(512),scores=new Float64Array(103),hist=Array(12).fill(0);
  let previous=-1,last=-1,streak=0;
  const data={notes:[],events:[],root:0,mode:''};
  live={stream,source,track:data,title:$('title').textContent,detail:$('detail').textContent};
  micPending=false;
  $('mic').textContent='Stop microphone';$('mic').setAttribute('aria-pressed','true');
  $('title').textContent='Your voice';$('detail').textContent='Live microphone';
  $('play').disabled=$('seek').disabled=true;
  $('key').textContent='Listening…';$('micinfo').textContent='Sing several different notes to estimate your key.';
  function sample(){
   an.getFloatTimeDomainData(raw);
   const ratio=ctx.sampleRate/8000,offset=raw.length-Math.ceil(frame.length*ratio);
   for(let i=0;i<frame.length;i++){
    const from=offset+Math.floor(i*ratio),to=Math.max(from+1,offset+Math.floor((i+1)*ratio));
    let sum=0;for(let j=from;j<to;j++)sum+=raw[j];
    frame[i]=sum/(to-from);
   }
   const pitch=detectPitch(frame,0,scores),n=Number.isFinite(pitch)?Math.round(pitch):-1;
   const index=data.notes.push(pitch)-1;
   streak=n===previous?streak+1:1;previous=n;
   if(streak>=2&&n>=0)hist[pc(n)]++;
   if(streak===2&&last!==n){if(n>=0)data.events.push([index-1,n]);last=n}
   if(index%8===0&&hist.filter(v=>v>=3).length>=4){
    Object.assign(data,estimateKey(hist));$('key').textContent=names[data.root]+' '+data.mode;
    $('micinfo').textContent='Estimated from your sung phrase.';
   }
   draw();
  }
  live.timer=setInterval(sample,40);sample();
  stream.getAudioTracks()[0].onended=()=>{if(live?.stream===stream)stopMic('Microphone disconnected.')};
 }catch(e){
  stream?.getTracks().forEach(t=>t.stop());
  if(request===micRequest)stopMic(e.name==='NotAllowedError'?'Microphone access was blocked. Allow it in your browser’s site settings.':e.name==='NotFoundError'?'No microphone found. Connect one and try again.':e.message||'Microphone unavailable.');
 }
}
$('mic').onclick=startMic;
document.addEventListener('visibilitychange',()=>{if(document.hidden&&(live||micPending))stopMic('Microphone stopped.')});

new ResizeObserver(draw).observe(plot);load(null);

