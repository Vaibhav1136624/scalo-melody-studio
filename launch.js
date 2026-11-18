(() => {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const canvas = document.createElement('canvas');
  canvas.id = 'launch-phrase';
  canvas.setAttribute('aria-hidden', 'true');
  Object.assign(canvas.style, {position:'fixed', inset:'0', width:'100%', height:'100%', zIndex:'1000', background:'#0e1013', pointerEvents:'none'});
  document.body.append(canvas);
  const g = canvas.getContext('2d');
  if (!g) { canvas.remove(); return; }
  const ease = value => { const t = Math.max(0, Math.min(1, value)); return t*t*(3-2*t); };
  let start, frame;
  function finish() { cancelAnimationFrame(frame); canvas.remove(); clearTimeout(failsafe); }
  const failsafe = setTimeout(finish, 2300);
  function paint(time) {
    start ??= time;
    const beat = Math.min(1, (time-start)/1650);
    if (beat === 1) { finish(); return; }
    const w = innerWidth, h = innerHeight, dpr = devicePixelRatio || 1;
    if (canvas.width !== Math.round(w*dpr) || canvas.height !== Math.round(h*dpr)) {
      canvas.width = Math.round(w*dpr); canvas.height = Math.round(h*dpr);
    }
    g.setTransform(dpr,0,0,dpr,0,0); g.clearRect(0,0,w,h);
    canvas.style.opacity = 1-ease((beat-.83)/.17);
    const span = Math.min(w*.78,340), left = (w-span)/2, base = h*.47;
    const head = ease(beat/.77), morph = ease((beat-.15)/.34);
    const y = u => base + 18*Math.sin(Math.PI*u)*Math.cos(beat*26-u*5)*Math.exp(-beat*7)*(1-morph)
      -18*(ease((u-.32)/.08)+ease((u-.62)/.08))*morph;
    g.strokeStyle = '#372f3a'; g.lineWidth = 1;
    for(let i=0;i<3;i++){g.beginPath();g.moveTo(left,base-i*18);g.lineTo(left+span,base-i*18);g.stroke();}
    g.beginPath();
    for(let i=0;i<=100;i++){const u=head*i/100;if(i)g.lineTo(left+span*u,y(u));else g.moveTo(left,y(u));}
    g.lineCap='round';g.lineJoin='round';g.strokeStyle='#ff679322';g.lineWidth=7;g.stroke();
    g.strokeStyle='#ff6793';g.lineWidth=2.4;g.stroke();
    g.fillStyle='#ff6793';g.beginPath();g.arc(left+span*head,y(head),3.6,0,Math.PI*2);g.fill();
    g.textAlign='center';g.font='12px monospace';
    ['Sa','Re','Ga'].forEach((note,i)=>{const u=.14+i*.30;g.globalAlpha=ease((head-u)/.1);g.fillText(note,left+span*u,base+25);});
    g.globalAlpha=1;g.fillStyle='#f5f1ee';g.font='500 42px system-ui';g.fillText('Scalo',w/2,base+88);
    frame=requestAnimationFrame(paint);
  }
  frame=requestAnimationFrame(paint);
  addEventListener('pagehide',finish,{once:true});
})();
